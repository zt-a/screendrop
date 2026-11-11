package com.screendrop

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.FileProvider
import com.screendrop.ui.ScreenDropApp

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ScreenDropApp(
                vm = vm,
                onOpenWifiSettings = { openWifiSettings() },
                onOpenFile = { id -> openFile(id) },
                onOpenFolder = { openInFileManager() }
            )
        }
    }

    private fun openWifiSettings() {
        runCatching { startActivity(Intent(Settings.ACTION_WIFI_SETTINGS)) }
    }

    private fun openFile(id: String) {
        val meta = vm.metadataFor(id)
        if (meta == null) {
            toast("Файл не найден")
            return
        }
        val uri: Uri = when {
            meta.mediaUri != null -> Uri.parse(meta.mediaUri)
            else -> vm.contentFileFor(id)?.let {
                FileProvider.getUriForFile(this, "$packageName.fileprovider", it)
            } ?: run {
                toast("Файл не найден")
                return
            }
        }
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, meta.mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            toast("На экране нет приложения для просмотра «${meta.mimeType}»")
        } catch (e: Exception) {
            toast("Не удалось открыть файл")
        }
    }

    private fun openInFileManager() {
        runCatching {
            val folder = DocumentsContract.buildDocumentUri(
                "com.android.externalstorage.documents",
                "primary:${Environment.DIRECTORY_DOCUMENTS}/ScreenDrop"
            )
            startActivity(Intent(Intent.ACTION_VIEW).apply { data = folder })
            return
        }
        val fileManagers = listOf(
            "com.mi.android.globalFileexplorer",
            "com.android.filemanager",
            "com.sec.android.app.myfiles"
        )
        for (pkg in fileManagers) {
            val launch = packageManager.getLaunchIntentForPackage(pkg)
            if (launch != null) {
                runCatching { startActivity(launch); return }
            }
        }
        toast("Файлы хранятся в Documents/ScreenDrop")
    }

    private fun toast(msg: String) {
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
    }
}
