/*
 * XY IPTV – TV-Guide-Quellen je Anbieter (wie bei TiviMate je Playlist).
 * Teil von XY IPTV, basierend auf OpenTV. Lizenz: GNU GPL v3.0 oder später.
 */
package app.opentv.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.opentv.R
import app.opentv.core.ServiceLocator
import app.opentv.data.model.Source
import kotlinx.coroutines.launch

/**
 * Aufklappbarer Bereich in der Anbieter-Karte: die automatische Guide-Quelle des Anbieters (an/aus) und beliebig viele
 * zusätzliche XMLTV-Quellen nur für diesen Anbieter (hinzufügen, an/aus, entfernen). „Jetzt aktualisieren“ lädt neu.
 */
@Composable
fun ProviderEpgSection(source: Source) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val feeds by remember(source.id) { graph.epgRepository.observeFeedsForProvider(source.id) }.collectAsState(initial = emptyList())
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column {
        OutlinedButton(onClick = { open = !open }) {
            Text(stringResource(R.string.xy_provider_epg_title) + " (" + feeds.size + ")")
        }
        if (!open) return@Column
        Spacer(Modifier.height(8.dp))
        feeds.forEach { f ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        if (f.url == null) stringResource(R.string.xy_provider_epg_auto) else f.name,
                        style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    if (f.lastResult.isNotEmpty()) Text(
                        f.lastResult, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
                    )
                }
                Switch(checked = f.enabled, onCheckedChange = { on -> scope.launch { graph.epgRepository.setFeedEnabled(f.id, on) } })
                if (f.url != null) TextButton(onClick = { scope.launch { graph.epgRepository.removeFeed(f) } }) {
                    Text(stringResource(R.string.common_remove))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        if (adding) {
            OutlinedTextField(
                value = name, onValueChange = { name = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.epg_name_optional)) },
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = url, onValueChange = { url = it }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.epg_xmltv_url)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
            )
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    enabled = url.trim().startsWith("http"),
                    onClick = {
                        scope.launch { graph.epgRepository.addFeedForProvider(source.id, name, url) }
                        adding = false; name = ""; url = ""
                    },
                ) { Text(stringResource(R.string.epg_add_guide)) }
                TextButton(onClick = { adding = false }) { Text(stringResource(R.string.common_cancel)) }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { adding = true }) { Text(stringResource(R.string.xy_provider_epg_add)) }
            OutlinedButton(
                enabled = !busy,
                onClick = {
                    busy = true
                    scope.launch {
                        runCatching { graph.epgRepository.syncAll(System.currentTimeMillis(), force = true) }
                        busy = false
                    }
                },
            ) { Text(stringResource(if (busy) R.string.epg_updating else R.string.epg_update_now)) }
        }
    }
}
