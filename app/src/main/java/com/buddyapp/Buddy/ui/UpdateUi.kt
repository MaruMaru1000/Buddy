package com.buddyapp.Buddy.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.buddyapp.Buddy.manager.UpdateManager
import kotlinx.coroutines.launch
import java.io.File

/**
 * "Update available" dialog: Update downloads the APK, then opens Android's installer
 * (Android asks the user to confirm). Later closes it.
 */
@Composable
fun UpdateDialog(release: UpdateManager.Release, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val updateManager = remember { UpdateManager(context) }
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var message by remember { mutableStateOf<String?>(null) }
    var apk by remember { mutableStateOf<File?>(null) }

    fun installNow(file: File) {
        message = if (updateManager.install(file)) null
                  else "Allow Buddy to install updates on the screen that opened, then tap Update again."
    }

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("Buddy ${release.version} is available") },
        text = {
            Column {
                Text("You have version ${updateManager.currentVersion}.", fontSize = 14.sp)
                if (release.notes.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Column(Modifier.heightIn(max = 200.dp).verticalScroll(rememberScrollState())) {
                        Text(release.notes, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                if (downloading) {
                    Spacer(Modifier.height(12.dp))
                    if (progress >= 0f) {
                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
                message?.let {
                    Spacer(Modifier.height(12.dp))
                    Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !downloading,
                onClick = {
                    message = null
                    apk?.takeIf { it.exists() }?.let { installNow(it); return@TextButton }
                    downloading = true
                    progress = 0f
                    scope.launch {
                        updateManager.download(release) { p -> progress = p }
                            .onSuccess { file ->
                                apk = file
                                installNow(file)
                            }
                            .onFailure { message = it.message ?: "Download failed" }
                        downloading = false
                    }
                }
            ) { Text(if (downloading) "Downloading..." else "Update") }
        },
        dismissButton = {
            TextButton(enabled = !downloading, onClick = onDismiss) { Text("Later") }
        }
    )
}
