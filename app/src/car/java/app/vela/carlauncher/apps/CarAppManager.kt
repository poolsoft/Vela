package app.vela.carlauncher.apps

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import app.vela.carlauncher.model.AracUygulamasi
import app.vela.carlauncher.model.InternalApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.graphics.drawable.Drawable
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.core.content.ContextCompat

/**
 * Arac Uygulama Yoneticisi.
 * Cihazda yuklu baslatilabilir uygulamalari listeler ve acar.
 */
class CarAppManager private constructor(private val context: Context) {

    companion object {
        private const val TAG = "CarAppManager"

        @Volatile
        private var instance: CarAppManager? = null

        fun getInstance(context: Context): CarAppManager {
            return instance ?: synchronized(this) {
                instance ?: CarAppManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val packageManager: PackageManager = context.packageManager

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val revision = MutableStateFlow(0)
    data class AppSnapshot(val generation: Int, val items: List<AracUygulamasi>)
    private val _apps = MutableStateFlow<AppSnapshot?>(null)
    val apps: StateFlow<AppSnapshot?> = _apps.asStateFlow()
    private val icons = object : LruCache<String, Drawable>(32 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Drawable): Int =
            (value as? BitmapDrawable)?.bitmap?.allocationByteCount?.coerceAtLeast(1) ?: 4096
    }
    private val iconLoads = HashMap<String, Deferred<Drawable?>>()
    private val iconPermits = kotlinx.coroutines.sync.Semaphore(8)
    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) { invalidate() }
    }

    init {
        var localeTags = context.resources.configuration.locales.toLanguageTags()
        context.registerComponentCallbacks(object : android.content.ComponentCallbacks2 {
            override fun onConfigurationChanged(config: android.content.res.Configuration) {
                val updated = config.locales.toLanguageTags()
                if (updated != localeTags) { localeTags = updated; invalidate() }
            }
            override fun onLowMemory() { icons.evictAll() }
            override fun onTrimMemory(level: Int) {
                if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_COMPLETE) {
                    icons.evictAll()
                } else if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_MODERATE) {
                    icons.trimToSize(icons.maxSize() / 2)
                }
            }
        })
        val packages = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        val configuration = IntentFilter().apply {
            addAction(Intent.ACTION_LOCALE_CHANGED)
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_AVAILABLE)
            addAction(Intent.ACTION_EXTERNAL_APPLICATIONS_UNAVAILABLE)
        }
        ContextCompat.registerReceiver(context, packageReceiver, packages, ContextCompat.RECEIVER_EXPORTED)
        ContextCompat.registerReceiver(context, packageReceiver, configuration, ContextCompat.RECEIVER_EXPORTED)
        scope.launch {
            revision.collectLatest { generation ->
                val loaded = scanApps()
                ensureActive()
                _apps.value = AppSnapshot(generation, loaded)
                prewarmIcons(loaded)
            }
        }
    }

    private fun prewarmIcons(apps: List<AracUygulamasi>) {
        scope.launch(Dispatchers.IO) {
            for (app in apps) {
                ensureActive()
                if (icons.get(app.paketAdi) == null && !InternalApp.isInternalUri(app.paketAdi)) {
                    loadIcon(app.paketAdi)
                }
            }
        }
    }

    fun invalidate() {
        val pending = synchronized(iconLoads) {
            revision.value++
            icons.evictAll()
            iconLoads.values.toList().also { iconLoads.clear() }
        }
        pending.forEach { it.cancel() }
    }

    /** Cache hits do not wait for an IO dispatcher or replace an already displayed icon. */
    fun peekIcon(packageName: String): Drawable? = icons.get(packageName)?.let(::copyIcon)

    private fun copyIcon(icon: Drawable): Drawable = icon.constantState?.newDrawable()?.mutate() ?: icon

    suspend fun loadIcon(packageName: String): Drawable? {
        peekIcon(packageName)?.let { return it }
        val task = synchronized(iconLoads) {
            iconLoads[packageName] ?: scope.async(Dispatchers.IO, start = CoroutineStart.LAZY) {
                iconPermits.acquire()
                try {
                    val generation = revision.value
                    val icon = runCatching { packageManager.getApplicationIcon(packageName) }.getOrNull()
                        ?.let { source ->
                            // Cache display-sized pixels instead of full-size launcher artwork.
                            val size = (64 * context.resources.displayMetrics.density).toInt().coerceIn(32, 192)
                            runCatching {
                                val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
                                source.setBounds(0, 0, size, size)
                                source.draw(android.graphics.Canvas(bitmap))
                                BitmapDrawable(context.resources, bitmap)
                            }.getOrDefault(source)
                        }
                    ensureActive()
                    synchronized(iconLoads) {
                        if (generation != revision.value) null
                        else icon?.also { icons.put(packageName, it) }
                    }
                } finally { iconPermits.release() }
            }.also { job ->
                iconLoads[packageName] = job
                job.invokeOnCompletion {
                    synchronized(iconLoads) { if (iconLoads[packageName] === job) iconLoads.remove(packageName) }
                }
            }
        }
        task.start()
        return task.await()?.let(::copyIcon)
    }

    suspend fun yukluUygulamalariGetir(): List<AracUygulamasi> = apps.filterNotNull().first().items.map { app ->
        if (app.ikon != null) app else app.copy(ikon = loadIcon(app.paketAdi))
    }

    /**
     * Cihazdaki tum baslatilabilir uygulamalari asenkron olarak yukler.
     */
    private suspend fun scanApps(): List<AracUygulamasi> = withContext(Dispatchers.IO) {
        Log.i(TAG, "apps scan start generation=${revision.value}")
        val uygulamaListesi = mutableListOf<AracUygulamasi>()

        try {
            val anaIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }

            val cozumlenenler = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.queryIntentActivities(
                    anaIntent,
                    PackageManager.ResolveInfoFlags.of(0L)
                )
            } else {
                packageManager.queryIntentActivities(anaIntent, 0)
            }

            for (bilgi in cozumlenenler) {
                val paketAdi = bilgi.activityInfo.packageName
                // Kendimizi uygulama cekmecesinden gizleyebiliriz veya gosteririz
                if (paketAdi == context.packageName) continue

                val ad = bilgi.loadLabel(packageManager).toString()
                val aktiviteAdi = bilgi.activityInfo.name
                val ikon: Drawable? = null // Loaded only for visible cells, through the bounded cache.

                uygulamaListesi.add(
                    AracUygulamasi(
                        ad = ad,
                        paketAdi = paketAdi,
                        aktiviteAdi = aktiviteAdi,
                        ikon = ikon
                    )
                )
            }

            uygulamaListesi.sortBy { it.ad.lowercase() }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Log.e(TAG, "Uygulama listesi alinamadi: ${e.message}")
        }

        InternalApp.values().map { internal ->
            AracUygulamasi(internal.getAd(context), internal.uri, "", internal.getIkon(context), true)
        } + uygulamaListesi.distinctBy { it.paketAdi }
    }

    /**
     * Belirtilen uygulamayi acar.
     */
    fun uygulamayiBaslat(paketAdi: String, onInternal: ((InternalApp) -> Unit)? = null): Boolean {
        if (InternalApp.isInternalUri(paketAdi)) {
            val internal = InternalApp.fromUri(paketAdi) ?: return false
            val open = onInternal ?: return false
            open(internal)
            return true
        }
        return try {
            val baslatIntent = packageManager.getLaunchIntentForPackage(paketAdi)
            if (baslatIntent != null) {
                baslatIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                val mode = AppDockManager.getInstance(context).kisayollar.value.firstOrNull { it.paketAdi == paketAdi }?.launchMode
                when (mode) {
                    app.vela.carlauncher.model.LaunchMode.SPLIT_SCREEN,
                    app.vela.carlauncher.model.LaunchMode.PANEL -> {
                        baslatIntent.addFlags(Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT or Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        context.startActivity(baslatIntent)
                    }
                    app.vela.carlauncher.model.LaunchMode.OVERLAY,
                    app.vela.carlauncher.model.LaunchMode.POPUP -> {
                        if (!supportsFreeform()) {
                            android.widget.Toast.makeText(context, app.vela.R.string.car_app_freeform_unavailable, android.widget.Toast.LENGTH_LONG).show()
                            return false
                        }
                        val metrics = context.resources.displayMetrics
                        val bounds = android.graphics.Rect(metrics.widthPixels / 3, metrics.heightPixels / 8,
                            metrics.widthPixels * 15 / 16, metrics.heightPixels * 7 / 8)
                        val options = android.app.ActivityOptions.makeBasic().setLaunchBounds(bounds)
                        baslatIntent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
                        context.startActivity(baslatIntent, options.toBundle())
                    }
                    else -> context.startActivity(baslatIntent)
                }
                true
            } else {
                Log.w(TAG, "Uygulama intent'i bulunamadi: $paketAdi")
                false
            }
        } catch (e: Exception) {
            Log.e(TAG, "Uygulama baslatilamadi ($paketAdi): ${e.message}")
            false
        }
    }

    fun supportsFreeform(): Boolean = packageManager.hasSystemFeature("android.software.freeform_window_management") ||
        android.provider.Settings.Global.getInt(context.contentResolver, "enable_freeform_support", 0) == 1

    /**
     * Arac telefon / arama ekranini acar.
     */
    fun telefonArayuzunuAc() {
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "Telefon arayuzu acilamadi: ${e.message}")
        }
    }

    /**
     * Arac radyo uygulamasini tespit edip acar.
     */
    fun radyoUygulamasiniAc(): Boolean {
        val olasiRadyoPaketleri = listOf(
            "com.acloud.stub.localradio",
            "com.syu.radio",
            "com.microntek.radio",
            "com.ts.radio",
            "com.android.fmradio"
        )
        for (paket in olasiRadyoPaketleri) {
            if (uygulamayiBaslat(paket)) return true
        }
        return false
    }

    /**
     * Arac muzik uygulamasini tespit edip acar.
     */
    fun muzikUygulamasiniAc(): Boolean {
        val preferred = app.vela.carlauncher.settings.CarLauncherSettings.tercihEdilenMuzikUygulamasi.value
        if (!preferred.isNullOrBlank() && uygulamayiBaslat(preferred)) return true
        val olasiMuzikPaketleri = listOf(
            "com.spotify.music",
            "com.google.android.apps.youtube.music",
            "com.acloud.stub.localmusic",
            "com.syu.music",
            "com.microntek.music",
            "com.ts.music"
        )
        for (paket in olasiMuzikPaketleri) {
            if (uygulamayiBaslat(paket)) return true
        }
        return false
    }
}
