package net.sigmabeta.chipbox.features.folderpicker

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.zacsweers.metrox.viewmodel.assistedMetroViewModel
import net.sigmabeta.chipbox.appcomm.ChipboxEvent
import net.sigmabeta.chipbox.common.ui.components.api.CrossfadeText
import net.sigmabeta.chipbox.common.ui.list.api.ChipboxListEntry
import net.sigmabeta.chipbox.strings.api.ChipboxStringId
import net.sigmabeta.chipbox.strings.api.text

/**
 * Android actual. The bespoke picker browses (and the scanner later reads) raw filesystem paths,
 * which on API 30+ requires All Files Access (`MANAGE_EXTERNAL_STORAGE`) and on API <30 the legacy
 * `READ_EXTERNAL_STORAGE` runtime permission. Gate the picker behind that grant: until it's held,
 * show a prompt that launches the grant flow; once held, show the picker rooted at external storage.
 */
@Composable
actual fun FolderPickerRoute(
    onEvent: (ChipboxEvent) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var hasAccess by remember { mutableStateOf(hasStorageAccess(context)) }

    if (hasAccess) {
        // External storage is `/storage/emulated/0` on most devices — the same root the old SAF
        // picker landed users in by default.
        val defaultPath = remember { Environment.getExternalStorageDirectory().absolutePath }
        val viewModel = assistedMetroViewModel<FolderPickerViewModel, FolderPickerViewModel.Factory> {
            create(defaultPath)
        }
        ChipboxListEntry(viewModel, onEvent, modifier)
        return
    }

    // API 30+: All Files Access is granted from a system settings screen (not a runtime dialog),
    // so re-check on return. API <30: a normal runtime permission request.
    val allFilesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { hasAccess = hasStorageAccess(context) }
    val readPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasAccess = granted }

    StoragePermissionPrompt(
        modifier = modifier,
        onGrantClicked = {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                allFilesLauncher.launch(
                    Intent(
                        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                        Uri.fromParts("package", context.packageName, null),
                    ),
                )
            } else {
                readPermissionLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        },
    )
}

private fun hasStorageAccess(context: Context): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
            PackageManager.PERMISSION_GRANTED
    }

@Composable
private fun StoragePermissionPrompt(
    modifier: Modifier,
    onGrantClicked: () -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CrossfadeText(
            text = ChipboxStringId.FOLDER_PICKER_PERMISSION_TITLE.text(),
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
        )
        CrossfadeText(
            text = ChipboxStringId.FOLDER_PICKER_PERMISSION_RATIONALE.text(),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            textModifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
        )
        Button(onClick = onGrantClicked) {
            CrossfadeText(text = ChipboxStringId.FOLDER_PICKER_PERMISSION_CTA.text())
        }
    }
}
