package com.screendrop.ui

import android.app.Activity
import android.view.WindowManager
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.screendrop.MainViewModel
import com.screendrop.model.ServerState
import com.screendrop.util.Fmt

private val Bg = Color(0xFF0E1116)
private val Card = Color(0xFF161B23)
private val Field = Color(0xFF0E1116)
private val Accent = Color(0xFF4F8CFF)
private val Ok = Color(0xFF66D9A5)
private val Warn = Color(0xFFFFB454)
private val Err = Color(0xFFFF6B6B)
private val TextC = Color(0xFFE6EAF2)
private val Muted = Color(0xFF8B94A7)

private const val PORT = 8080

@Composable
fun ScreenDropApp(
    vm: MainViewModel,
    onOpenWifiSettings: () -> Unit,
    onOpenFile: (String) -> Unit,
    onOpenFolder: () -> Unit
) {
    val ui by vm.ui.collectAsState()
    val ctx = LocalContext.current

    DisposableEffect(ui.serverState) {
        val window = (ctx as? Activity)?.window
        if (ui.serverState != ServerState.STOPPED) {
            window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose { }
    }

    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Accent,
            background = Bg,
            surface = Card,
            onPrimary = Color.White,
            onBackground = TextC,
            onSurface = TextC
        )
    ) {
        Surface(modifier = Modifier.fillMaxSize(), color = Bg) {
            BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                val wide = maxWidth >= 840.dp
                if (wide) {
                    Row(modifier = Modifier.fillMaxSize().padding(28.dp), horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            Header(ui)
                            LeftPanel(ui, vm, onOpenWifiSettings)
                        }
                        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                            FilesPanel(ui, vm, onOpenFile, onOpenFolder)
                        }
                    }
                } else {
                    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
                        Header(ui)
                        LeftPanel(ui, vm, onOpenWifiSettings)
                        FilesPanel(ui, vm, onOpenFile, onOpenFolder)
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(ui: MainViewModel.UiState) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("ScreenDrop", fontSize = 34.sp, fontWeight = FontWeight.Bold, color = TextC)
        StatusChip(ui.serverState)
    }
}

@Composable
private fun StatusChip(state: ServerState) {
    val (label, color) = when (state) {
        ServerState.STOPPED -> "Остановлено" to Muted
        ServerState.STARTING, ServerState.CHECKING_NETWORK -> "Проверка сети…" to Warn
        ServerState.WAITING_NETWORK -> "Нет сети" to Warn
        ServerState.READY -> "Готов к приёму" to Ok
        ServerState.RECEIVING -> "Принимает файлы" to Accent
        ServerState.ERROR -> "Ошибка" to Err
    }
    Surface(color = color.copy(alpha = 0.15f), shape = RoundedCornerShape(20.dp)) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
            color = color,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun LeftPanel(ui: MainViewModel.UiState, vm: MainViewModel, onOpenWifiSettings: () -> Unit) {
    if (!ui.network.connected || ui.network.ipv4 == null) {
        GuideCard(onOpenWifiSettings)
    } else if (ui.serverState == ServerState.STOPPED || ui.serverState == ServerState.ERROR) {
        StoppedCard(ui, vm)
    } else {
        QrCard(ui, vm)
    }
    Spacer(modifier = Modifier.height(16.dp))
}

@Composable
private fun GuideCard(onOpenWifiSettings: () -> Unit) {
    CardBox {
        Text("Экран не подключён к сети", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextC)
        Spacer(Modifier.height(12.dp))
        Step("1", "На смартфоне включите точку доступа Wi-Fi (Hotspot).")
        Step("2", "Подключите этот экран к точке доступа.")
        Button(
            onClick = onOpenWifiSettings,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Accent)
        ) {
            Text("Открыть настройки Wi-Fi", fontSize = 18.sp)
        }
        Spacer(Modifier.height(10.dp))
        Step("3", "Вернитесь в ScreenDrop и нажмите «Начать приём файлов».")
        Spacer(Modifier.height(14.dp))
        Text(
            "Альтернатива: подключите экран и телефоны отправителей к одной общей Wi-Fi сети.",
            fontSize = 15.sp, color = Muted
        )
    }
}

@Composable
private fun StoppedCard(ui: MainViewModel.UiState, vm: MainViewModel) {
    CardBox {
        Text(
            if (ui.serverState == ServerState.ERROR) "Ошибка запуска сервера" else "Сеть подключена",
            fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextC
        )
        Spacer(Modifier.height(8.dp))
        Text("IP экрана: ${ui.network.ipv4 ?: "—"} · порт $PORT", fontSize = 16.sp, color = Muted)
        ui.info?.let {
            Spacer(Modifier.height(6.dp))
            Text(it, fontSize = 15.sp, color = Err)
        }
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { vm.startReceiving() },
            modifier = Modifier.fillMaxWidth().height(64.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Ok)
        ) {
            Text("Начать приём файлов", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun QrCard(ui: MainViewModel.UiState, vm: MainViewModel) {
    CardBox {
        val receiving = ui.serverState == ServerState.RECEIVING
        if (receiving) {
            Text("Принимается файл… (${ui.activeUploads})", fontSize = 20.sp, color = Accent, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(8.dp))
        }
        if (ui.serverState == ServerState.WAITING_NETWORK) {
            Text("Сеть потеряна — QR недействителен. Проверьте подключение Wi-Fi.", fontSize = 18.sp, color = Warn)
            Spacer(Modifier.height(12.dp))
            OutlinedButton(
                onClick = {},
                modifier = Modifier.fillMaxWidth().height(52.dp),
                enabled = false
            ) {
                Text("Ожидание сети…", fontSize = 16.sp)
            }
        } else {
            Text("Отсканируйте камерой телефона", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = TextC)
            Spacer(Modifier.height(14.dp))
            ui.qr?.let { qr ->
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.size(320.dp).align(Alignment.CenterHorizontally)
                ) {
                    Image(
                        bitmap = qr,
                        contentDescription = "QR-код для подключения",
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentScale = ContentScale.Fit
                    )
                }
            }
            Spacer(Modifier.height(14.dp))
            Text(
                ui.qrUrl ?: "",
                modifier = Modifier.fillMaxWidth(),
                fontSize = 15.sp,
                fontFamily = FontFamily.Monospace,
                color = Muted,
                textAlign = TextAlign.Center
            )
            ui.info?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, fontSize = 16.sp, color = Ok, textAlign = TextAlign.Center)
            }
        }
        Spacer(Modifier.height(18.dp))
        OutlinedButton(
            onClick = { vm.stopReceiving() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Err)
        ) {
            Text("Остановить приём", fontSize = 18.sp)
        }
    }
}

@Composable
private fun FilesPanel(
    ui: MainViewModel.UiState,
    vm: MainViewModel,
    onOpenFile: (String) -> Unit,
    onOpenFolder: () -> Unit
) {
    CardBox {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Полученные файлы", fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = TextC)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("${ui.files.size}", fontSize = 20.sp, color = Accent, fontWeight = FontWeight.Bold)
                if (ui.files.isNotEmpty()) {
                    TextButton(onClick = onOpenFolder) {
                        Text("В проводник", fontSize = 15.sp, color = Accent)
                    }
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        if (ui.files.isEmpty()) {
            Text(
                "Пока ничего не получено.\nПосле отправки файлы появятся здесь.",
                fontSize = 16.sp, color = Muted
            )
        } else {
            ui.files.forEach { f ->
                FileRow(f, onOpenFile = { onOpenFile(f.id) }, onDelete = { vm.deleteFile(f.id) })
                Spacer(Modifier.height(10.dp))
            }
        }
    }
}

@Composable
private fun FileRow(f: com.screendrop.model.FileMetadata, onOpenFile: () -> Unit, onDelete: () -> Unit) {
    Surface(color = Field, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(f.displayName, fontSize = 18.sp, color = TextC, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(4.dp))
            Text(
                "${Fmt.size(f.sizeBytes)} · ${f.senderName ?: "без имени"} · ${Fmt.time(f.receivedAt)}",
                fontSize = 14.sp, color = Muted
            )
            Text(
                "SHA-256: ${f.sha256.take(16)}…",
                fontSize = 13.sp, color = Muted, fontFamily = FontFamily.Monospace
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Button(onClick = onOpenFile, modifier = Modifier.height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = Accent)) {
                    Text("Открыть", fontSize = 16.sp)
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.height(48.dp)) {
                    Text("Удалить", fontSize = 16.sp, color = Err)
                }
            }
        }
    }
}

@Composable
private fun CardBox(content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Surface(color = Card, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), content = content)
    }
}

@Composable
private fun Step(num: String, text: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        Surface(color = Accent.copy(alpha = 0.2f), shape = RoundedCornerShape(12.dp)) {
            Text(num, modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), color = Accent, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, fontSize = 16.sp, color = TextC, modifier = Modifier.align(Alignment.CenterVertically))
    }
}
