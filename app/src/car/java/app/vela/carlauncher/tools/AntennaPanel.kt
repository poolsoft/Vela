package app.vela.carlauncher.tools

import android.location.Location
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.vela.R
import kotlin.math.atan2

/** Geographic alignment only; independent of any map engine or fabricated sensor values. */
@Composable
fun AntennaPanel(onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("vela_launcher_tools", 0) }
    val keys = listOf("antenna_from_lat", "antenna_from_lon", "antenna_from_alt", "antenna_to_lat", "antenna_to_lon", "antenna_to_alt")
    val labels = listOf(R.string.car_antenna_from_lat, R.string.car_antenna_from_lon, R.string.car_antenna_from_alt,
        R.string.car_antenna_to_lat, R.string.car_antenna_to_lon, R.string.car_antenna_to_alt)
    val fields = remember { keys.map { mutableStateOf(prefs.getString(it, "").orEmpty()) } }
    val values = fields.map { it.value.replace(',', '.').toDoubleOrNull() }
    val valid = values.all { it != null && it.isFinite() } &&
        values[0]!! in -90.0..90.0 && values[3]!! in -90.0..90.0 &&
        values[1]!! in -180.0..180.0 && values[4]!! in -180.0..180.0
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text(stringResource(R.string.car_internal_antenna), style = MaterialTheme.typography.titleLarge)
        Text(stringResource(R.string.car_antenna_help))
        fields.forEachIndexed { index, field ->
            OutlinedTextField(value = field.value, onValueChange = { field.value = it.take(24) },
                label = { Text(stringResource(labels[index])) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        }
        if (valid) {
            val result = FloatArray(3)
            Location.distanceBetween(values[0]!!, values[1]!!, values[3]!!, values[4]!!, result)
            val bearing = (result[1] + 360f) % 360f
            val elevation = Math.toDegrees(atan2(values[5]!! - values[2]!!, result[0].toDouble()))
            if (result[0] > 0.1f) Text(stringResource(R.string.car_antenna_result, bearing, result[0] / 1000f, elevation))
            else Text(stringResource(R.string.car_antenna_same_point))
        }
        TextButton(enabled = valid, onClick = {
            val editor = prefs.edit()
            keys.forEachIndexed { index, key -> editor.putString(key, fields[index].value) }
            editor.apply()
        }) { Text(stringResource(R.string.car_save)) }
        TextButton(onClick = onClose) { Text(stringResource(android.R.string.cancel)) }
    }
}
