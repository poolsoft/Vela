package app.vela.carlauncher.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DisplaySettings
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.launch
import app.vela.carlauncher.apps.CarAppManager
import app.vela.R
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vela.carlauncher.hardware.CarHardwareManager
import app.vela.carlauncher.settings.CarLauncherSettings

private val ClPrimary = Color(0xFF0A84FF)
private val ClCardBg = Color(0xFF141419)
private val ClSidebarBg = Color(0xFF101014)
private val ClDivider = Color(0x1FFFFFFF)

/**
 * OsmAnd CarLauncherSettingsFragment Uyumlu Iki Kolonlu (Split-Screen) Ayarlar Ekrani.
 * Yatay arac bas uniteleri (Head Unit) icin ozel olarak tasarlanmistir.
 * Sol Kolon: Kategori Secimi ve Kapatma Butonu
 * Sag Kolon: Secilen Kategoriye Ait Detayli Dokunmatik Ayar Kartlari
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
@Composable
fun CarLauncherSettingsView(
    onKapat: () -> Unit,
    onCarModeKapatildi: () -> Unit = {},
    onPermissions: () -> Unit = {},
    onBackup: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val panelHeight by CarLauncherSettings.panelYukseklikYuzdesi.collectAsState()
    val expansion by CarLauncherSettings.panelGenislemeDavranisi.collectAsState()
    val largeVisualizer by CarLauncherSettings.largeVisualizer.collectAsState()
    val visualizerFps by CarLauncherSettings.visualizerFps.collectAsState()
    val preferredMusic by CarLauncherSettings.tercihEdilenMuzikUygulamasi.collectAsState()
    var aktifKategori by androidx.compose.runtime.saveable.rememberSaveable { mutableStateOf("GORUNUM") } // GORUNUM, DOCK, MUZIK, SISTEM

    // Reaktif Ayar Degerleri
    val tamEkran by CarLauncherSettings.tamEkranModu.collectAsState()
    val durumCubugu by CarLauncherSettings.durumCubuguGoster.collectAsState()
    val dockKonumu by CarLauncherSettings.dockKonumu.collectAsState()
    val dockBoyutu by CarLauncherSettings.dockBoyutu.collectAsState()
    val panelKonumu by CarLauncherSettings.panelKonumu.collectAsState()
    val panelGenislikYuzdesi by CarLauncherSettings.panelGenislikYuzdesi.collectAsState()
    val otomatikOynat by CarLauncherSettings.otomatikOynat.collectAsState()
    val gorsellestiriciTipi by CarLauncherSettings.gorsellestiriciTipi.collectAsState()
    val geceKarartma by CarLauncherSettings.geceKarartmaEtkin.collectAsState()
    val geceKarartmaSeviyesi by CarLauncherSettings.geceKarartmaSeviyesi.collectAsState()
    val desktopModu by CarLauncherSettings.desktopModu.collectAsState()
    val desktopDongudeEtkin by CarLauncherSettings.desktopDongudeEtkin.collectAsState()
    val baslangicEkrani by CarLauncherSettings.baslangicEkrani.collectAsState()
    val ambiyansGorsellestirici by CarLauncherSettings.ambiyansGorsellestirici.collectAsState()
    val floatingButtonModu by CarLauncherSettings.floatingButtonModu.collectAsState()
    val floatingButtonBoyutu by CarLauncherSettings.floatingButtonBoyutu.collectAsState()

    androidx.activity.compose.BackHandler(onBack = onKapat)

    androidx.compose.foundation.layout.BoxWithConstraints(modifier.fillMaxSize()) {
    val sidebarWidth = if (maxWidth < 600.dp) 120.dp else 220.dp
    Row(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null
            ) { /* alttaki harita ve bilesenlere dokunmatik gecisini engelle */ }
            .background(Color(0xFF09090C))
    ) {
        // ═══════════════════════════════════════════════════════════════
        // SOL KOLON: KATEGORI LISTESI & BASLIK
        // ═══════════════════════════════════════════════════════════════
        Column(
            modifier = Modifier
                .width(sidebarWidth)
                .fillMaxHeight()
                .background(ClSidebarBg)
                .verticalScroll(rememberScrollState())
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Baslik ve Kapat Butonu
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ayarlar",
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .clickable(onClick = onKapat),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            HorizontalDivider(color = ClDivider)

            // Kategori Menuleri
            KategoriSecimButonu(
                baslik = "Görünüm",
                ikon = Icons.Default.DisplaySettings,
                secili = aktifKategori == "GORUNUM",
                onClick = { aktifKategori = "GORUNUM" }
            )

            KategoriSecimButonu(
                baslik = "Kenar Çubuğu (Dock)",
                ikon = Icons.Default.ViewSidebar,
                secili = aktifKategori == "DOCK",
                onClick = { aktifKategori = "DOCK" }
            )

            KategoriSecimButonu(
                baslik = "Müzik ve Ses",
                ikon = Icons.Default.MusicNote,
                secili = aktifKategori == "MUZIK",
                onClick = { aktifKategori = "MUZIK" }
            )

            KategoriSecimButonu(
                baslik = "Sistem & Donanım",
                ikon = Icons.Default.Tune,
                secili = aktifKategori == "SISTEM",
                onClick = { aktifKategori = "SISTEM" }
            )
        }

        // ═══════════════════════════════════════════════════════════════
        // SAG KOLON: SECILEN KATEGORIYE AIT AYARLAR
        // ═══════════════════════════════════════════════════════════════
        val sagScrollState = rememberScrollState()
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .padding(16.dp)
                .verticalScroll(sagScrollState),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            when (aktifKategori) {
                "GORUNUM" -> {
                    Text(
                        text = "Görünüm ve Panel Ayarları",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    AyarKategoriKarti(baslik = "Pencereler ve Düzen", ikon = Icons.Default.DisplaySettings) {
                        AyarAnahtarSatiri(
                            baslik = "Tam Ekran Modu (Immersive)",
                            aciklama = "Gezinti ve durum çubuklarını gizler, tüm ekranı dokunmaya açar",
                            secili = tamEkran,
                            onDegisim = { CarLauncherSettings.setTamEkranModu(it) }
                        )

                        HorizontalDivider(color = ClDivider)

                        if (!tamEkran) {
                            AyarAnahtarSatiri(
                                baslik = "Durum Çubuğu (Status Bar)",
                                aciklama = "Üst sistem bildirim çubuğunu şeffaf olarak gösterir",
                                secili = durumCubugu,
                                onDegisim = { CarLauncherSettings.setDurumCubuguGoster(it) }
                            )

                            HorizontalDivider(color = ClDivider)
                        }

                        // Panel Konumu (Sol / Sag)
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(text = "Widget Panel Konumu", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Haritaya göre küçük panelin yerleşeceği taraf", color = Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SegmentButon(
                                    metin = "Sol Taraf",
                                    secili = panelKonumu == "left",
                                    onClick = { CarLauncherSettings.setPanelKonumu("left") }
                                )
                                SegmentButon(
                                    metin = "Sağ Taraf",
                                    secili = panelKonumu == "right",
                                    onClick = { CarLauncherSettings.setPanelKonumu("right") }
                                )
                            }
                        }

                        HorizontalDivider(color = ClDivider)

                        // Panel Genislik Slider'i (%15 - %65)
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Panel Genişlik Oranı", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(text = "%${(panelGenislikYuzdesi * 100).toInt()}", color = ClPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(text = "Ekran genişliğine göre küçük panelin kapladığı oran", color = Color.Gray, fontSize = 12.sp)
                            Slider(
                                value = panelGenislikYuzdesi,
                                onValueChange = { CarLauncherSettings.setWidgetPanelWidthPercent(it, persist = true) },
                                valueRange = 0.20f..0.50f,
                                colors = SliderDefaults.colors(thumbColor = ClPrimary, activeTrackColor = ClPrimary)
                            )
                        }

                        HorizontalDivider(color = ClDivider)

                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(stringResource(R.string.car_panel_height), color = Color.White)
                            Slider(value = panelHeight,
                                onValueChange = { CarLauncherSettings.setWidgetPanelHeightPortrait(it, true) },
                                valueRange = 0.15f..0.65f)
                            Text(stringResource(R.string.car_panel_expansion), color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SegmentButon(stringResource(R.string.car_panel_swap), expansion == "swap", { CarLauncherSettings.setPanelGenislemeDavranisi("swap") })
                                SegmentButon(stringResource(R.string.car_panel_overlay), expansion == "overlay", { CarLauncherSettings.setPanelGenislemeDavranisi("overlay") })
                            }
                        }

                        AyarAnahtarSatiri(
                            baslik = "Masaüstü Modu (Desktop)",
                            aciklama = "Haritayı gizleyip tek büyük masaüstü paneli gösterir",
                            secili = desktopModu,
                            onDegisim = { CarLauncherSettings.setDesktopModu(it) }
                        )

                        HorizontalDivider(color = ClDivider)

                        AyarAnahtarSatiri(
                            baslik = "Masaüstünü Döngüye Dahil Et",
                            aciklama = "Dock mod butonuna basıldığında Masaüstü Modunu 3. adım olarak döngüye sokar",
                            secili = desktopDongudeEtkin,
                            onDegisim = { CarLauncherSettings.setDesktopDongudeEtkin(it) }
                        )

                        HorizontalDivider(color = ClDivider)

                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(text = "İlk Açılış Sayfası", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Uygulama başladığında varsayılan olarak açılacak ekran modu", color = Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SegmentButon(
                                    metin = "Normal (Bölünmüş)",
                                    secili = baslangicEkrani == "normal",
                                    onClick = { CarLauncherSettings.setBaslangicEkrani("normal") }
                                )
                                SegmentButon(
                                    metin = "Tam Harita",
                                    secili = baslangicEkrani == "map_only",
                                    onClick = { CarLauncherSettings.setBaslangicEkrani("map_only") }
                                )
                                SegmentButon(
                                    metin = "Masaüstü",
                                    secili = baslangicEkrani == "desktop",
                                    onClick = { CarLauncherSettings.setBaslangicEkrani("desktop") }
                                )
                            }
                        }
                    }

                    AyarKategoriKarti(baslik = "Yüzen Hız Butonu (Floating Assistant)", ikon = Icons.Default.Tune) {
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(text = "Gösterim Durumu", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Ekranda sürüklenebilir canlı hız ve asistan butonunun çalışma modu", color = Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                SegmentButon(
                                    metin = "Her Zaman",
                                    secili = floatingButtonModu == "always",
                                    onClick = {
                                        CarLauncherSettings.setFloatingButtonModu("always")
                                        CarFloatingButtonManager.getInstance(context).updateButtonState()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SegmentButon(
                                    metin = "Arka Planda",
                                    secili = floatingButtonModu == "background_only",
                                    onClick = {
                                        CarLauncherSettings.setFloatingButtonModu("background_only")
                                        CarFloatingButtonManager.getInstance(context).updateButtonState()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                                SegmentButon(
                                    metin = "Kapalı",
                                    secili = floatingButtonModu == "never",
                                    onClick = {
                                        CarLauncherSettings.setFloatingButtonModu("never")
                                        CarFloatingButtonManager.getInstance(context).updateButtonState()
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        if (floatingButtonModu != "never") {
                            HorizontalDivider(color = ClDivider)

                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Buton Boyutu", color = Color.White, fontSize = 14.sp)
                                    Text(text = "${floatingButtonBoyutu} dp", color = ClPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = floatingButtonBoyutu.toFloat(),
                                    onValueChange = {
                                        CarLauncherSettings.setFloatingButtonBoyutu(it.toInt())
                                    },
                                    valueRange = 60f..110f,
                                    colors = SliderDefaults.colors(thumbColor = ClPrimary, activeTrackColor = ClPrimary)
                                )
                            }

                            // Overlay Izni Kontrolu
                            val overlayIzniVar = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                android.provider.Settings.canDrawOverlays(context)
                            } else true

                            if (!overlayIzniVar) {
                                HorizontalDivider(color = ClDivider)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                val intent = android.content.Intent(
                                                    android.provider.Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                                    android.net.Uri.parse("package:${context.packageName}")
                                                )
                                                context.startActivity(intent)
                                            }
                                        }
                                        .padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(text = "Ekran Üzerinde Gösterme İzni", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                        Text(text = "Yüzen butonun çalışabilmesi için sistem pencere izni gerekir", color = Color.Gray, fontSize = 12.sp)
                                    }
                                    Text(text = "İzin Ver →", color = ClPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    AyarKategoriKarti(baslik = "Gece Modu ve Karartma", ikon = Icons.Default.DisplaySettings) {
                        AyarAnahtarSatiri(
                            baslik = "Gece Karartma Katmanı",
                            aciklama = "Gece sürüşünde göz almayan yumuşak filtre katmanı",
                            secili = geceKarartma,
                            onDegisim = { CarLauncherSettings.setGeceKarartmaEtkin(it) }
                        )

                        if (geceKarartma) {
                            HorizontalDivider(color = ClDivider)
                            Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(text = "Karartma Yoğunluğu", color = Color.White, fontSize = 14.sp)
                                    Text(text = "%${(geceKarartmaSeviyesi * 100).toInt()}", color = ClPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value = geceKarartmaSeviyesi,
                                    onValueChange = { CarLauncherSettings.setGeceKarartmaSeviyesi(it) },
                                    valueRange = 0.1f..0.8f,
                                    colors = SliderDefaults.colors(thumbColor = ClPrimary, activeTrackColor = ClPrimary)
                                )
                            }
                        }
                    }
                }

                "DOCK" -> {
                    Text(
                        text = "Kenar Çubuğu (Dock) Ayarları",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    AyarKategoriKarti(baslik = "Dock Konumlandırma ve Boyut", ikon = Icons.Default.ViewSidebar) {
                        // Dock Konumu (Sol / Alt / Sag)
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Text(text = "Dock Ekran Konumu", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Text(text = "Hızlı uygulama çubuğunun ekrandaki yerleşimi", color = Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                SegmentButon(
                                    metin = "Sol Kenar",
                                    secili = dockKonumu == "left",
                                    onClick = { CarLauncherSettings.setDockKonumu("left") }
                                )
                                SegmentButon(
                                    metin = "Alt Çubuk",
                                    secili = dockKonumu == "bottom",
                                    onClick = { CarLauncherSettings.setDockKonumu("bottom") }
                                )
                                SegmentButon(
                                    metin = "Sağ Kenar",
                                    secili = dockKonumu == "right",
                                    onClick = { CarLauncherSettings.setDockKonumu("right") }
                                )
                            }
                        }

                        HorizontalDivider(color = ClDivider)

                        // Dock Boyutu Olcegi Slider'i
                        Column(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(text = "Dock Boyutu ve İkon Ölçeği", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(text = "%$dockBoyutu", color = ClPrimary, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(text = "Dock'un kalınlığını ve butonların büyüklüğünü ayarlar", color = Color.Gray, fontSize = 12.sp)
                            Slider(
                                value = dockBoyutu.toFloat(),
                                onValueChange = { CarLauncherSettings.setDockBoyutu(it.toInt()) },
                                valueRange = 20f..100f,
                                colors = SliderDefaults.colors(thumbColor = ClPrimary, activeTrackColor = ClPrimary)
                            )
                        }
                    }
                }

                "MUZIK" -> {
                    app.vela.ui.settings.SmartFocusPreferences()
                    Text(
                        text = "Müzik ve Ses Ayarları",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    AyarKategoriKarti(baslik = "Oynatma ve Görselleştirici", ikon = Icons.Default.MusicNote) {
                        Column(Modifier.fillMaxWidth().padding(14.dp).clickable {
                            scope.launch {
                                val apps = CarAppManager.getInstance(context).yukluUygulamalariGetir().filter { !it.isInternal }
                                val labels = arrayOf(context.getString(R.string.car_music_internal)) + apps.map { it.ad }
                                val selected = apps.indexOfFirst { it.paketAdi == preferredMusic } + 1
                                android.app.AlertDialog.Builder(context)
                                    .setTitle(R.string.car_music_preferred)
                                    .setSingleChoiceItems(labels, selected) { dialog, which ->
                                        CarLauncherSettings.setTercihEdilenMuzikUygulamasi(if (which == 0) null else apps[which - 1].paketAdi)
                                        dialog.dismiss()
                                    }.setNegativeButton(android.R.string.cancel, null).show()
                            }
                        }) {
                            Text(stringResource(R.string.car_music_preferred), color = Color.White)
                            Text(if (preferredMusic == null) stringResource(R.string.car_music_internal) else
                                runCatching { context.packageManager.getApplicationLabel(context.packageManager.getApplicationInfo(preferredMusic!!, 0)).toString() }
                                    .getOrDefault(context.getString(R.string.car_app_launch_failed)), color = Color.Gray)
                        }
                        AyarAnahtarSatiri(
                            baslik = "Otomatik Müzik Çalma",
                            aciklama = "Uygulama açıldığında son çalınan parçayı otomatik devam ettirir",
                            secili = otomatikOynat,
                            onDegisim = { CarLauncherSettings.setOtomatikOynat(it) }
                        )

                        HorizontalDivider(color = ClDivider)

                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            val modes = context.resources.getStringArray(R.array.car_visualizer_modes)
                            Text(stringResource(R.string.car_visualizer_small), color = Color.White)
                            Text(modes[gorsellestiriciTipi.coerceIn(0, 7)], color = ClPrimary,
                                modifier = Modifier.padding(vertical = 12.dp).clickable {
                                    android.app.AlertDialog.Builder(context).setTitle(R.string.car_visualizer_small)
                                        .setSingleChoiceItems(modes, gorsellestiriciTipi) { dialog, which ->
                                            CarLauncherSettings.setGorsellestiriciTipi(which); dialog.dismiss()
                                        }.setNegativeButton(android.R.string.cancel, null).show()
                                })
                            Text(stringResource(R.string.car_visualizer_large), color = Color.White)
                            Text(modes[largeVisualizer.coerceIn(0, 7)], color = ClPrimary,
                                modifier = Modifier.padding(vertical = 12.dp).clickable {
                                    android.app.AlertDialog.Builder(context).setTitle(R.string.car_visualizer_large)
                                        .setSingleChoiceItems(modes, largeVisualizer) { dialog, which ->
                                            CarLauncherSettings.setLargeVisualizer(which); dialog.dismiss()
                                        }.setNegativeButton(android.R.string.cancel, null).show()
                                })
                            Text(stringResource(R.string.car_visualizer_rate), color = Color.White)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(15, 30, 60).forEach { fps ->
                                    SegmentButon("$fps fps", visualizerFps == fps, { CarLauncherSettings.setVisualizerFps(fps) })
                                }
                            }
                        }

                        HorizontalDivider(color = ClDivider)

                        AyarAnahtarSatiri(
                            baslik = "Ambiyans Görselleştirici",
                            aciklama = "Görselleştirici renklerini çalan albüm kapağının baskın rengine dinamik olarak uyarlar",
                            secili = ambiyansGorsellestirici,
                            onDegisim = { CarLauncherSettings.setAmbiyansGorsellestirici(it) }
                        )

                        HorizontalDivider(color = ClDivider)

                        // Bildirim Erisim Izni (Spotify, YouTube Music)
                        val bildirimIzniVar = app.vela.carlauncher.media.MediaNotificationListener.bildirimIzniVerildiMi(context)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { app.vela.carlauncher.media.MediaNotificationListener.bildirimAyarlariniAc(context) }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = "Harici Medya İzni (Spotify vb.)", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(text = "Harici çalan şarkıları okumak için bildirim erişimi gerekir", color = Color.Gray, fontSize = 12.sp)
                            }
                            Text(
                                text = if (bildirimIzniVar) "Etkin ✓" else "İzin Ver →",
                                color = if (bildirimIzniVar) Color(0xFF30D158) else ClPrimary,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        HorizontalDivider(color = ClDivider)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { app.vela.carlauncher.hardware.CarHardwareManager.dspAc(context) }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(imageVector = Icons.Default.Equalizer, contentDescription = null, tint = ClPrimary, modifier = Modifier.size(20.dp))
                                Text(text = "Donanım Ekolayzırını (DSP) Başlat", color = Color.White, fontSize = 14.sp)
                            }
                            Text(text = "Aç →", color = ClPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                "SISTEM" -> {
                    androidx.compose.material3.TextButton(onClick = onPermissions) {
                        Text(stringResource(R.string.car_permissions_title))
                    }
                    androidx.compose.material3.TextButton(onClick = onBackup) {
                        Text(stringResource(R.string.backup_title))
                    }
                    app.vela.carlauncher.tools.LauncherToolsSettings()
                    Text(
                        text = "Donanım ve Sistem İşlemleri",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    AyarKategoriKarti(baslik = "Sistem Eylemleri", ikon = Icons.Default.Tune) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { CarHardwareManager.getInstance(context).cleanRam(context) }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Hafızayı Temizle (RAM)", color = Color.White, fontSize = 14.sp)
                                Text(text = "Arka planda kullanılmayan belleği boşaltır", color = Color.Gray, fontSize = 12.sp)
                            }
                            Text(text = "Temizle", color = ClPrimary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }

                        HorizontalDivider(color = ClDivider)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    CarLauncherSettings.setCarModeEtkin(false)
                                    onCarModeKapatildi()
                                    onKapat()
                                }
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(text = "Araç Baş Ünitesi Modundan Çık", color = Color(0xFFFF453A), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Text(text = "Standart tam ekran harita görünümüne döner", color = Color.Gray, fontSize = 12.sp)
                            }
                            Text(text = "Çıkış", color = Color(0xFFFF453A), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}
}

@Composable
private fun KategoriSecimButonu(
    baslik: String,
    ikon: ImageVector,
    secili: Boolean,
    onClick: () -> Unit
) {
    val bgRenk = if (secili) Color(0x330A84FF) else Color.Transparent
    val kenar = if (secili) Color(0xFF0A84FF) else Color.Transparent
    val metinRenk = if (secili) Color.White else Color(0xFFAAAAAA)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(bgRenk)
            .border(1.dp, kenar, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = ikon,
            contentDescription = null,
            tint = if (secili) ClPrimary else Color.Gray,
            modifier = Modifier.size(18.dp)
        )
        Text(
            text = baslik,
            color = metinRenk,
            fontSize = 13.sp,
            fontWeight = if (secili) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

@Composable
private fun SegmentButon(
    metin: String,
    secili: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (secili) ClPrimary else Color(0x22FFFFFF)
    val textC = if (secili) Color.White else Color(0xFFCCCCCC)
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text = metin, color = textC, fontSize = 12.sp, fontWeight = if (secili) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun AyarKategoriKarti(
    baslik: String,
    ikon: ImageVector,
    modifier: Modifier = Modifier,
    icerik: @Composable () -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ClCardBg)
            .border(1.dp, Color(0x1AFFFFFF), RoundedCornerShape(14.dp))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(imageVector = ikon, contentDescription = null, tint = ClPrimary, modifier = Modifier.size(18.dp))
            Text(text = baslik, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
        HorizontalDivider(color = ClDivider)
        icerik()
    }
}

@Composable
private fun AyarAnahtarSatiri(
    baslik: String,
    aciklama: String,
    secili: Boolean,
    onDegisim: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onDegisim(!secili) }
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = baslik, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            Text(text = aciklama, color = Color.Gray, fontSize = 12.sp)
        }
        Switch(
            checked = secili,
            onCheckedChange = onDegisim,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = ClPrimary,
                uncheckedThumbColor = Color.Gray,
                uncheckedTrackColor = Color(0x33FFFFFF)
            )
        )
    }
}
