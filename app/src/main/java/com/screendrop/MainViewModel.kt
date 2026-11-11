package com.screendrop

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.screendrop.model.FileMetadata
import com.screendrop.model.NetworkStatus
import com.screendrop.model.ServerState
import com.screendrop.network.NetworkMonitor
import com.screendrop.server.ScreenDropServer
import com.screendrop.server.ServerCore
import com.screendrop.session.SessionManager
import com.screendrop.storage.FileStorage
import com.screendrop.transfer.UploadManager
import com.screendrop.util.QrGenerator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import java.io.File

class MainViewModel(app: Application) : AndroidViewModel(app) {

    data class UiState(
        val serverState: ServerState = ServerState.STOPPED,
        val network: NetworkStatus = NetworkStatus(),
        val qrUrl: String? = null,
        val qr: ImageBitmap? = null,
        val files: List<FileMetadata> = emptyList(),
        val activeUploads: Int = 0,
        val info: String? = null,
        val wifiSsid: String? = null
    )

    private val network = NetworkMonitor(app)
    private val storage = FileStorage(app)
    private val sessions = SessionManager()
    private val uploads = UploadManager(storage)
    private val core = ServerCore(
        sessions = sessions,
        storage = storage,
        uploads = uploads,
        assetReader = { path -> app.assets.open(path).use { it.readBytes() } }
    )
    private val server = ScreenDropServer(core)

    private val _ui = MutableStateFlow(UiState())
    val ui: StateFlow<UiState> = _ui.asStateFlow()

    init {
        network.start()
        storage.cleanupTemp(olderThanMs = 24L * 60 * 60 * 1000)
        // Move files received before the Documents/ScreenDrop switch into
        // the public folder so everything lives in one place.
        viewModelScope.launch(Dispatchers.IO) {
            val migrated = storage.migrateLegacy()
            if (migrated) _ui.update { it.copy(files = storage.list()) }
        }
        viewModelScope.launch { network.status.collect { onNetwork(it) } }
        viewModelScope.launch { uploads.activeUploads.collect { onUploads(it) } }
        // Read current Wi-Fi SSID once at startup
        _ui.update { it.copy(wifiSsid = "Wi-Fi") }
    }

    private fun onNetwork(status: NetworkStatus) {
        _ui.update { ui ->
            var newUrl: String? = null
            var newQr: ImageBitmap? = null
            var newState: ServerState? = null
            // The HTTP server keeps running through READY / RECEIVING / WAITING_NETWORK.
            // Waiting for a network must not lock the UI: once connectivity returns,
            // the QR has to regenerate and the state must recover to READY.
            val serverRunning = ui.serverState == ServerState.READY ||
                ui.serverState == ServerState.RECEIVING ||
                ui.serverState == ServerState.WAITING_NETWORK
            if (serverRunning) {
                if (!status.connected || status.ipv4 == null) {
                    if (ui.serverState != ServerState.WAITING_NETWORK) {
                        newState = ServerState.WAITING_NETWORK
                    }
                } else {
                    val session = sessions.currentSession
                    if (session != null) {
                        val url = "http://${status.ipv4}:$PORT/?token=${session.token}"
                        if (url != ui.qrUrl) {
                            newUrl = url
                            newQr = QrGenerator.generate(url).asImageBitmap()
                        }
                        // Recover even when the URL is unchanged (network flap,
                        // same IP assigned back) — otherwise WAITING_NETWORK sticks.
                        if (ui.serverState == ServerState.WAITING_NETWORK) {
                            newState = ServerState.READY
                        }
                        // READY / RECEIVING keep their state: an upload in progress
                        // is not visually downgraded by a network callback.
                    }
                }
            }
            ui.copy(
                network = status,
                qrUrl = newUrl ?: ui.qrUrl,
                qr = newQr ?: ui.qr,
                serverState = newState ?: ui.serverState
            )
        }
    }

    private fun onUploads(count: Int) {
        _ui.update { ui ->
            when {
                count > 0 && (ui.serverState == ServerState.READY || ui.serverState == ServerState.WAITING_NETWORK) ->
                    ui.copy(activeUploads = count, serverState = ServerState.RECEIVING)
                count > 0 ->
                    ui.copy(activeUploads = count)
                else -> {
                    val finished = ui.activeUploads > 0
                    val files = if (finished) storage.list() else ui.files
                    val info = if (finished) {
                        files.firstOrNull()?.let { "Получен файл: ${it.displayName}" } ?: ui.info
                    } else ui.info
                    ui.copy(
                        activeUploads = 0,
                        serverState = if (ui.serverState == ServerState.RECEIVING) ServerState.READY else ui.serverState,
                        files = files,
                        info = info
                    )
                }
            }
        }
    }

    fun startReceiving() {
        viewModelScope.launch(Dispatchers.IO) {
            _ui.update { it.copy(serverState = ServerState.CHECKING_NETWORK, info = null) }
            val ip = network.status.value.ipv4
            if (ip == null) {
                _ui.update { it.copy(serverState = ServerState.WAITING_NETWORK) }
                return@launch
            }
            val started = server.start()
            if (!started) {
                _ui.update {
                    it.copy(serverState = ServerState.ERROR, info = "Не удалось запустить сервер (порт $PORT занят?)")
                }
                return@launch
            }
            val session = sessions.createNew()
            val url = "http://$ip:$PORT/?token=${session.token}"
            val qr = QrGenerator.generate(url).asImageBitmap()
            _ui.update {
                it.copy(
                    serverState = ServerState.READY,
                    qrUrl = url,
                    qr = qr,
                    files = storage.list(),
                    info = null
                )
            }
        }
    }

    fun stopReceiving() {
        server.stop()
        sessions.invalidateCurrent()
        _ui.update { it.copy(serverState = ServerState.STOPPED, qrUrl = null, qr = null, info = null) }
    }

    fun deleteFile(id: String) {
        viewModelScope.launch(Dispatchers.IO) {
            storage.delete(id)
            _ui.update { it.copy(files = storage.list()) }
        }
    }

    fun onShareWifi() {
        // TODO: will generate and show Wi-Fi QR
        _ui.update { it.copy(qrUrl = "WIFI:S:TestNet;T:WPA;P:;;") }
    }

    /** Metadata of one received file (null if unknown id). */
    fun metadataFor(id: String): FileMetadata? =
        _ui.value.files.firstOrNull { it.id == id }

    /** Private-storage file for legacy records without a MediaStore URI. */
    fun contentFileFor(id: String): File? = storage.openFile(id)

    override fun onCleared() {
        server.stop()
        sessions.invalidateCurrent()
        network.stop()
        super.onCleared()
    }

    companion object {
        const val PORT = 8080
    }
}
