package app.vela.carlauncher.media.adapters

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.os.Build
import android.view.KeyEvent
import java.util.Locale

/** Protocols ported from the launcher adapters; hardware acceptance is still required. */
class HeadUnitRadioAdapter(
    private val context: Context,
    val xyAuto: Boolean,
    private val changed: () -> Unit
) : BaseMediaAdapter() {
    private val packages = if (xyAuto) listOf("com.acloud.stub.extradio", "com.acloud.stub.localradio")
        else listOf("com.hcn.autoradio")
    val packageName: String? get() = packages.firstOrNull {
        runCatching { context.packageManager.getPackageInfo(it, 0) }.isSuccess
    }
    private var selected = false
    private var playing = false
    private var frequency = ""
    private var band = ""
    private var registered = false
    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(ctx: Context?, intent: Intent?) {
            if (!selected || packageName == null || intent == null) return
            val value = runCatching { intent.getIntExtra("freq", 0) }.getOrDefault(0)
            val incomingBand = runCatching { intent.getStringExtra("fmoram") }.getOrNull().orEmpty()
            if (value <= 0 || incomingBand.isBlank()) return
            band = incomingBand.take(16)
            frequency = if (band.startsWith("AM", true)) "$value kHz"
                else String.format(Locale.getDefault(), "%.1f MHz",
                    value / if (xyAuto && band.equals("FM1", true)) 1000.0 else 100.0)
            playing = true
            changed()
        }
    }

    init {
        if (packageName != null) runCatching {
            val filter = IntentFilter("com.android.radio.widget.freq_volue")
            if (!xyAuto) {
                filter.addAction("com.hcn.android.radio.freq")
                filter.addAction("com.hcn.autoradio.FREQ_CHANGED")
            }
            if (Build.VERSION.SDK_INT >= 33) context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
            else context.registerReceiver(receiver, filter)
            registered = true
        }
    }

    fun select(value: Boolean) {
        selected = value
        if (!value) playing = false
    }
    fun open() {
        val pkg = packageName ?: return
        context.packageManager.getLaunchIntentForPackage(pkg)?.let {
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            runCatching { context.startActivity(it) }
        }
    }
    private fun command(xyAction: String, key: Int) {
        val pkg = packageName ?: return
        runCatching {
            if (xyAuto) context.sendBroadcast(Intent(xyAction))
            else {
                // Target the radio receiver; global media dispatch can loop into our MediaSession.
                for (action in intArrayOf(KeyEvent.ACTION_DOWN, KeyEvent.ACTION_UP)) {
                    context.sendBroadcast(Intent(Intent.ACTION_MEDIA_BUTTON).setPackage(pkg)
                        .putExtra(Intent.EXTRA_KEY_EVENT, KeyEvent(action, key)))
                }
            }
        }
    }
    override fun oynat() {
        if (!playing) open()
    }
    override fun duraklat() {
        if (playing) command("xy.android.playpause", KeyEvent.KEYCODE_MEDIA_PAUSE)
        playing = false
        changed()
    }
    override fun sonraki() = command("xy.android.fm_scan_next", KeyEvent.KEYCODE_MEDIA_NEXT)
    override fun onceki() = command("xy.android.fm_scan_prev", KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    override fun konumaGit(konumMs: Long) {}
    override fun aktifMi() = selected && packageName != null
    override fun kaynakAdi() = if (xyAuto) "XYAuto Radio" else "HCN Radio"
    override fun baslik() = frequency.ifBlank { kaynakAdi() }
    override fun sanatci() = band
    override fun albumKapagi(): Bitmap? = null
    override fun toplamSureMs() = 0L
    override fun anlikKonumMs() = 0L
    override fun caliyorMu() = selected && playing
    override fun serbestBirak() {
        if (registered) runCatching { context.unregisterReceiver(receiver) }
        registered = false
    }
}

class HcnMusicAdapter(private val context: Context) : BaseMediaAdapter() {
    val packageName = "com.hcn.AutoMediaPlayer"
    fun installed() = runCatching { context.packageManager.getPackageInfo(packageName, 0) }.isSuccess
    private fun command(action: String) {
        if (installed()) runCatching {
            context.sendBroadcast(Intent("com.auto.apimediaplayer.notification.$action").setPackage(packageName))
        }
    }
    fun open() {
        context.packageManager.getLaunchIntentForPackage(packageName)?.let {
            runCatching { context.startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
        }
    }
    override fun oynat() = command("PLAY")
    override fun duraklat() = command("PAUSE")
    override fun sonraki() = command("NEXT")
    override fun onceki() = command("PREV")
    override fun konumaGit(konumMs: Long) {}
    override fun aktifMi() = installed()
    override fun kaynakAdi() = "HCN Music"
    override fun baslik() = kaynakAdi()
    override fun sanatci() = ""
    override fun albumKapagi(): Bitmap? = null
    override fun toplamSureMs() = 0L
    override fun anlikKonumMs() = 0L
    // No fabricated playback state: HCN metadata is supplied by its real MediaSession.
    override fun caliyorMu() = false
}
