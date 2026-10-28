package app.vela.carlauncher.map

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign

/** Scoped to the launcher map slot, above MapScreen's downloads and navigation panels. */
internal class MapRendererFailureHost {
    var state by mutableStateOf(IsolatedMapClient.State())
    var failure by mutableStateOf<IsolatedMapClient.State?>(null)
    var retry: () -> Unit = {}
}
internal val LocalMapRendererFailureHost = staticCompositionLocalOf<MapRendererFailureHost?> { null }

@Composable
internal fun MapRendererStatusPanel(state: IsolatedMapClient.State, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Outlined.LocationOn, contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(16.dp).size(32.dp))
            }
            Text(stringResource(if (state.failed) app.vela.R.string.car_map_renderer_failed else app.vela.R.string.car_map_renderer_loading),
                style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            if (state.failed) Button(onClick = onRetry) {
                Text(stringResource(app.vela.R.string.car_map_renderer_retry))
            } else {
                Text(stringResource(app.vela.R.string.car_map_renderer_loading_detail),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                LinearProgressIndicator(Modifier.widthIn(max = 200.dp).fillMaxWidth())
            }
        }
    }
}
