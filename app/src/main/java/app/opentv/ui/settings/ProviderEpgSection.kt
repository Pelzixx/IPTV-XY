/*
 * XY IPTV – EPG-Quellen je Anbieter zuweisen und ordnen (wie bei TiviMate je Playlist).
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
import app.opentv.data.model.EpgFeed
import app.opentv.data.model.Source
import app.opentv.xy.XyEpgAssign
import kotlinx.coroutines.launch

/**
 * Aufklappbarer Bereich in der Anbieter-Karte: „EPG-Quellen (Name)“. Oben die zugewiesenen Quellen in
 * Prioritätsreihenfolge (▲/▼), darunter die nicht zugewiesenen; ein Schalter weist zu bzw. entfernt die Zuweisung.
 * Neue Quellen landen im gemeinsamen Vorrat (für alle Anbieter wählbar) und werden diesem Anbieter gleich zugewiesen.
 */
@Composable
fun ProviderEpgSection(source: Source) {
    val context = LocalContext.current
    val graph = remember { ServiceLocator.get(context) }
    val scope = rememberCoroutineScope()
    var open by remember { mutableStateOf(false) }
    val all by remember { graph.database.epgFeeds().observeAll() }.collectAsState(initial = emptyList())
    val assignMap by XyEpgAssign.state.collectAsState()
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    // Vorrat: alles außer abgeschalteten Vorschlägen von OpenTV (Gratis-Guides, die nie benutzt wurden)
    val pool = all.filter { !it.builtIn || it.enabled }
    // Ohne eigene Zuweisung: alle eingeschalteten Quellen, in der Reihenfolge ihrer Anlage
    val assigned: List<Long> = assignMap[source.id]?.filter { id -> pool.any { it.id == id } }
        ?: pool.filter { it.enabled }.map { it.id }
    val assignedFeeds = assigned.mapNotNull { id -> pool.firstOrNull { it.id == id } }
    val others = pool.filter { it.id !in assigned }

    fun save(list: List<Long>) = XyEpgAssign.set(context, source.id, list)

    Column {
        OutlinedButton(onClick = { open = !open }) {
            Text(stringResource(R.string.xy_provider_epg_title, source.name) + " (" + assignedFeeds.size + ")")
        }
        if (!open) return@Column
        Spacer(Modifier.height(8.dp))
        assignedFeeds.forEachIndexed { i, f ->
            FeedLine(f, source, assigned = true,
                onToggle = { save(assigned - f.id) },
                onUp = if (i > 0) ({ save(assigned.toMutableList().apply { add(i - 1, removeAt(i)) }) }) else null,
                onDown = if (i < assigned.lastIndex) ({ save(assigned.toMutableList().apply { add(i + 1, removeAt(i)) }) }) else null,
            )
        }
        if (others.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.xy_epg_unassigned), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            others.forEach { f ->
                FeedLine(f, source, assigned = false, onToggle = {
                    // Zuweisen: hinten anhängen und die Quelle einschalten, damit sie geladen wird
                    save(assigned + f.id)
                    if (!f.enabled) scope.launch { graph.epgRepository.setFeedEnabled(f.id, true) }
                })
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
                        val u = url.trim(); val n = name
                        scope.launch {
                            graph.epgRepository.addCustomFeed(n, u)
                            graph.database.epgFeeds().byUrl(u)?.let { f -> save(assigned + f.id) }
                        }
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

@Composable
private fun FeedLine(
    f: EpgFeed, source: Source, assigned: Boolean, onToggle: () -> Unit,
    onUp: (() -> Unit)? = null, onDown: (() -> Unit)? = null,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                when {
                    f.url == null && f.providerSourceId == source.id -> stringResource(R.string.xy_provider_epg_auto)
                    else -> f.name.replace("(provider guide)", "(" + stringResource(R.string.xy_epg_provider_suffix) + ")")
                },
                style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            // Keine Adressen anzeigen (können Zugangsdaten enthalten) – nur den Stand
            if (f.lastResult.isNotEmpty()) Text(
                f.lastResult, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2,
            )
        }
        if (onUp != null) TextButton(onClick = onUp) { Text("▲") }
        if (onDown != null) TextButton(onClick = onDown) { Text("▼") }
        Switch(checked = assigned, onCheckedChange = { onToggle() })
    }
}
