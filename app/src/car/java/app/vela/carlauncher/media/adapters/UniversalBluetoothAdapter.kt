package app.vela.carlauncher.media.adapters

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import app.vela.carlauncher.hardware.CarHardwareManager

/** Incoming phone audio on a head unit; an outgoing A2DP route is not a player. */
class UniversalBluetoothAdapter(
    private val context: Context,
    private val onDurumDegisti: () -> Unit
) : BaseMediaAdapter() {
    private val platform = CarHardwareManager.getInstance(context).getPlatform()
    private var connected = false
    private var playing = false
    private var title = ""
    private var artist = ""
    private var registered = false
    val canControl: Boolean get() = platform != CarHardwareManager.Platform.STANDARD
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (intent == null) return
            when (intent.action) {
                "android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED" -> {
                    connected = intent.getIntExtra("android.bluetooth.profile.extra.STATE", 0) == 2
                    if (!connected) { playing = false; title = ""; artist = "" }
                }
                "android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED" -> {
                    playing = intent.getIntExtra("android.bluetooth.profile.extra.STATE", 0) == 10
                    if (playing) connected = true
                }
                "com.acloud.intent.play_status" -> {
                    if (platform != CarHardwareManager.Platform.XY_AUTO) return
                    val status = intent.getByteExtra("play_status", 16.toByte()).toInt()
                    playing = status == 1
                    if (playing) connected = true
                    title = intent.getStringExtra("songname") ?: title
                    artist = intent.getStringExtra("singer") ?: artist
                }
                else -> return
            }
            onDurumDegisti()
        }
    }
    init {
        val filter = IntentFilter().apply {
            addAction("android.bluetooth.a2dp-sink.profile.action.CONNECTION_STATE_CHANGED")
            addAction("android.bluetooth.a2dp-sink.profile.action.PLAYING_STATE_CHANGED")
            if (platform == CarHardwareManager.Platform.XY_AUTO) addAction("com.acloud.intent.play_status")
        }
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, filter)
            registered = true
        }
    }
    private fun command(xy: String, hcn: String) {
        val action = when (platform) {
            CarHardwareManager.Platform.XY_AUTO -> xy
            CarHardwareManager.Platform.HCN -> hcn
            CarHardwareManager.Platform.STANDARD -> return
        }
        // Vendor protocol from OsmAnd. Never dispatch global media keys into our own session.
        runCatching { context.sendBroadcast(Intent(action)) }
    }
    override fun oynat() = command("xy.android.forceplay", "com.hcn.intent.action.BT_PLAY")
    override fun duraklat() = command("xy.android.forcepause", "com.hcn.intent.action.BT_PAUSE")
    override fun sonraki() = command("xy.android.nextmedia", "com.hcn.intent.action.BT_NEXT")
    override fun onceki() = command("xy.android.previousmedia", "com.hcn.intent.action.BT_PREV")
    override fun konumaGit(konumMs: Long) {}
    override fun aktifMi() = connected
    override fun kaynakAdi() = "Bluetooth"
    override fun baslik() = title.ifBlank { kaynakAdi() }
    override fun sanatci() = artist
    override fun albumKapagi(): Bitmap? = null
    override fun toplamSureMs() = 0L
    override fun anlikKonumMs() = 0L
    override fun caliyorMu() = connected && playing
    override fun serbestBirak() {
        if (registered) runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }
}
