package app.vela.carlauncher.desktop

import android.app.ActivityManager
import android.content.Context
import android.os.BatteryManager
import android.os.Environment
import android.os.StatFs
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Locale

data class DeviceStats(
    val batteryPercent: Int,
    val storageFreeBytes: Long,
    val storageTotalBytes: Long,
    val memoryUsedBytes: Long,
    val memoryTotalBytes: Long
)

object DeviceStatsReader {
    fun read(context: Context): DeviceStats {
        val battery = runCatching {
            (context.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager)
                ?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        }.getOrDefault(-1)

        val fs = runCatching { StatFs(Environment.getDataDirectory().absolutePath) }.getOrNull()
        val freeStorage = fs?.availableBytes ?: 0L
        val totalStorage = fs?.totalBytes ?: 0L

        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mem = ActivityManager.MemoryInfo().also { am?.getMemoryInfo(it) }
        val totalMem = mem.totalMem
        val usedMem = totalMem - mem.availMem

        return DeviceStats(
            batteryPercent = battery,
            storageFreeBytes = freeStorage,
            storageTotalBytes = totalStorage,
            memoryUsedBytes = usedMem,
            memoryTotalBytes = totalMem
        )
    }
}

@Composable
fun StatusWidgetView(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val stats by produceState(initialValue = DeviceStatsReader.read(context)) {
        while (true) {
            value = DeviceStatsReader.read(context)
            delay(4000L)
        }
    }

    Surface(
        color = Color(0xF0141624),
        contentColor = Color.White,
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 6.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StatRow(
                icon = Icons.Default.BatteryFull,
                text = if (stats.batteryPercent in 0..100) "${stats.batteryPercent}%" else "100% (Arac)"
            )
            StatRow(
                icon = Icons.Default.Storage,
                text = "${toGb(stats.storageFreeBytes)} bos / ${toGb(stats.storageTotalBytes)}"
            )
            StatRow(
                icon = Icons.Default.Memory,
                text = "RAM: ${toGb(stats.memoryUsedBytes)} / ${toGb(stats.memoryTotalBytes)}"
            )
        }
    }
}

@Composable
private fun StatRow(icon: ImageVector, text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF00E5FF),
            modifier = Modifier.size(16.dp)
        )
        Text(text = text, color = Color.White, fontSize = 13.sp)
    }
}

private fun toGb(bytes: Long): String {
    return String.format(Locale.US, "%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0))
}
