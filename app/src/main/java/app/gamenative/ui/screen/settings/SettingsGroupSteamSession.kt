package app.gamenative.ui.screen.settings

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.gamenative.PrefManager
import app.gamenative.R
import app.gamenative.ui.theme.settingsTileColors
import com.alorma.compose.settings.ui.SettingsGroup
import com.alorma.compose.settings.ui.SettingsMenuLink
import org.json.JSONObject
import timber.log.Timber
import kotlin.system.exitProcess

/**
 * The Steam session and the name Steam sees this device as.
 *
 * The session file holds the account name and the refresh token in plain text: anyone with the
 * file can sign in as that account until the token is revoked (Steam → Account → Deauthorize).
 */
@Composable
fun SettingsGroupSteamSession() {
    val context = LocalContext.current
    var machineName by remember { mutableStateOf(PrefManager.customMachineName) }
    var editing by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val record = PrefManager.exportSteamSession()
        if (record == null) {
            Toast.makeText(context, R.string.settings_steam_session_none, Toast.LENGTH_LONG).show()
            return@rememberLauncherForActivityResult
        }
        try {
            val json = JSONObject()
                .put("username", record.username)
                .put("refreshToken", record.refreshToken)
                .put("steamId64", record.steamId64.toString())
            context.contentResolver.openOutputStream(uri, "wt")!!.use { it.write(json.toString(2).toByteArray()) }
            Toast.makeText(context, R.string.settings_steam_session_exported, Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Timber.w(e, "Steam session export failed")
            Toast.makeText(context, R.string.settings_steam_session_failed, Toast.LENGTH_LONG).show()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        try {
            val text = context.contentResolver.openInputStream(uri)!!.use { it.readBytes().decodeToString() }
            val json = JSONObject(text)
            val username = json.optString("username").trim()
            val token = json.optString("refreshToken").trim()
            require(username.isNotEmpty() && token.isNotEmpty()) { "username or refreshToken missing" }
            val steamId = json.optString("steamId64").toLongOrNull() ?: 0L
            PrefManager.importSteamSession(PrefManager.SteamSessionRecord(username, token, steamId))
            Toast.makeText(context, R.string.settings_steam_session_imported, Toast.LENGTH_LONG).show()
            // The service read the old session at start: start over so it signs in with this one.
            context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
                context.startActivity(Intent.makeRestartActivityTask(it.component))
            }
            exitProcess(0)
        } catch (e: Exception) {
            Timber.w(e, "Steam session import failed")
            Toast.makeText(context, R.string.settings_steam_session_failed, Toast.LENGTH_LONG).show()
        }
    }

    SettingsGroup {
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.settings_steam_machine_name)) },
            subtitle = { Text(machineName.ifBlank { stringResource(R.string.settings_steam_machine_name_default) }) },
            icon = { Icon(Icons.Filled.Computer, contentDescription = null) },
            onClick = { editing = true },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.settings_steam_session_export)) },
            subtitle = { Text(stringResource(R.string.settings_steam_session_export_hint)) },
            icon = { Icon(Icons.Filled.Upload, contentDescription = null) },
            onClick = { exportLauncher.launch("steam-session.json") },
        )
        SettingsMenuLink(
            colors = settingsTileColors(),
            title = { Text(stringResource(R.string.settings_steam_session_import)) },
            subtitle = { Text(stringResource(R.string.settings_steam_session_import_hint)) },
            icon = { Icon(Icons.Filled.Download, contentDescription = null) },
            onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
        )
    }

    if (editing) {
        var draft by remember { mutableStateOf(machineName) }
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.settings_steam_machine_name)) },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { draft = it.take(64) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.settings_steam_machine_name_default)) },
                    supportingText = { Text(stringResource(R.string.settings_steam_machine_name_hint)) },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    PrefManager.customMachineName = draft
                    machineName = draft.trim()
                    editing = false
                }) { Text(stringResource(android.R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text(stringResource(android.R.string.cancel)) }
            },
        )
    }
}
