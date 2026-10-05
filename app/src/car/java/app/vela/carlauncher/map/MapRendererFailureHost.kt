package app.vela.carlauncher.map

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp

/** Scoped to the launcher map slot, above MapScreen's downloads and navigation panels. */
internal class MapRendererFailureHost {
    var failure by mutableStateOf<IsolatedMapClient.State?>(null)
    var retry: () -> Unit = {}
}
internal val LocalMapRendererFailureHost = staticCompositionLocalOf<MapRendererFailureHost?> { null }

@Composable
internal fun MapRendererStatusPanel(state: IsolatedMapClient.State, onRetry: () -> Unit) {
    Surface(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp).padding(bottom = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically)) {
            Text(stringResource(if (state.failed) app.vela.R.string.car_map_renderer_failed else app.vela.R.string.car_map_renderer_loading))
            Text(state.stage, style = MaterialTheme.typography.bodySmall)
            if (state.failed) Button(onClick = onRetry) {
                Text(stringResource(app.vela.R.string.car_map_renderer_retry))
            } else CircularProgressIndicator()
        }
    }
}
