package app.vela.carlauncher.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.vela.carlauncher.hardware.CarHardwareManager
import app.vela.carlauncher.media.MusicRepository
import app.vela.carlauncher.model.MedyaParcasi
import app.vela.carlauncher.model.SesKlasoru
import app.vela.carlauncher.model.SesParcasi
import kotlinx.coroutines.launch

private val ClPrimary = Color(0xFF0A84FF)
private val ClSideDockBg = Color(0xEE111116)
private val ClCardBg = Color(0xCC1A1B22)

enum class MusicTab {
    QUEUE,
    ALL_TRACKS,
    FOLDERS,
    PLAYLISTS
}

/**
 * OsmAnd fragment_music_player.xml Birebir Tam Donanimli Muzik Calar.
 * - Sol Dikey Dock: Kapat, DSP Ekolayzir, Kutuphane Tara
 * - 4 Sekme: Sira (Queue), Tum Parcalar, Klasorler (Dahili/USB), Listeler
 * - Canli Arama Cubugu (Search Bar)
 * - Sure Cubugu (Seekbar), Gecen/Kalan Sure
 * - Calma Kontrolleri: Shuffle, Prev, Play/Pause, Next, Repeat
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
@Composable
fun CarMusicPlayerView(
    medya: MedyaParcasi,
    onOynatDuraklat: () -> Unit,
    onSonraki: () -> Unit,
    onOnceki: () -> Unit,
    onKonumaGit: (Long) -> Unit = {},
    onKapat: () -> Unit,
    onAppIconClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val musicRepo = remember { MusicRepository.getInstance(context) }

    val parcalar by musicRepo.parcalar.collectAsState()
    val klasorler by musicRepo.klasorler.collectAsState()
    val taraniyorMu by musicRepo.taraniyorMu.collectAsState()

    var aktifSekme by remember { mutableStateOf(MusicTab.ALL_TRACKS) }
    var aramaMetni by remember { mutableStateOf("") }
    var aramaAcikMi by remember { mutableStateOf(false) }
    var secilenKlasor by remember { mutableStateOf<SesKlasoru?>(null) }
    var isShuffle by remember { mutableStateOf(false) }
    var repeatModu by remember { mutableStateOf(0) } // 0: kapali, 1: tek parca, 2: tum liste

    LaunchedEffect(Unit) {
        if (parcalar.isEmpty()) {
            musicRepo.muzikKutuphanesiniTara()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF08080C))
    ) {
        // 1. KATMAN: Album Kapagi Ambiance Blur Yansimasi
        if (medya.albumKapagi != null) {
            Image(
                bitmap = medya.albumKapagi.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.25f)
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(0.18f)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(Color(0xFF1E3C72), Color(0xFF2A5298), Color(0xFF000000))
                        )
                    )
            )
        }

        // 2. KATMAN: Koyu Gradyan
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(Color(0xF508080C), Color(0xDD08080C), Color(0xF508080C))
                    )
                )
        )

        // 3. KATMAN: Ana Icerik (Sol Dock + Sag Panel)
        Row(modifier = Modifier.fillMaxSize()) {
            // SOL DIKEY DOCK (72dp)
            Column(
                modifier = Modifier
                    .width(72.dp)
                    .fillMaxHeight()
                    .background(ClSideDockBg)
                    .padding(vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Kapat Butonu
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x33FFFFFF))
                        .clickable(onClick = onKapat),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // DSP / Ekolayzir Butonu
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .clickable { CarHardwareManager.getInstance(context).openEqualizer(context) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Ekolayzır",
                        tint = ClPrimary,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Kutuphane Yeniden Tara
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .clickable { scope.launch { musicRepo.muzikKutuphanesiniTara() } },
                    contentAlignment = Alignment.Center
                ) {
                    if (taraniyorMu) {
                        CircularProgressIndicator(
                            color = ClPrimary,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(22.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Müzik Tara",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Arama Ac/Kapa
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (aramaAcikMi) ClPrimary else Color(0x22FFFFFF))
                        .clickable { aramaAcikMi = !aramaAcikMi },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Ara",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            // SAG ANA PANEL (Sekmeler, Sarki Listesi ve Alt Calar)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Arama Kutusu (Aciksa)
                AnimatedVisibility(visible = aramaAcikMi) {
                    TextField(
                        value = aramaMetni,
                        onValueChange = { aramaMetni = it },
                        placeholder = { Text("Şarkı veya sanatçı ara...", color = Color.Gray) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = ClCardBg,
                            unfocusedContainerColor = ClCardBg,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            cursorColor = ClPrimary
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                }

                // Sekme Butonlari (Sira, Tum Parcalar, Klasorler, Listeler)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MuzikSekmeButonu(
                        baslik = "Tüm Parçalar (${parcalar.size})",
                        ikon = Icons.Default.MusicNote,
                        secili = aktifSekme == MusicTab.ALL_TRACKS,
                        onClick = { aktifSekme = MusicTab.ALL_TRACKS; secilenKlasor = null }
                    )
                    MuzikSekmeButonu(
                        baslik = "Klasörler (${klasorler.size})",
                        ikon = Icons.Default.Folder,
                        secili = aktifSekme == MusicTab.FOLDERS,
                        onClick = { aktifSekme = MusicTab.FOLDERS }
                    )
                    MuzikSekmeButonu(
                        baslik = "Çalma Sırası",
                        ikon = Icons.Default.QueueMusic,
                        secili = aktifSekme == MusicTab.QUEUE,
                        onClick = { aktifSekme = MusicTab.QUEUE }
                    )
                }

                // Orta Alan: Liste Icerigi
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    val gosterilecekParcalar = remember(parcalar, aramaMetni, secilenKlasor) {
                        var liste = if (secilenKlasor != null) {
                            parcalar.filter { it.dosyaYolu.startsWith(secilenKlasor!!.yol) }
                        } else {
                            parcalar
                        }
                        if (aramaMetni.isNotBlank()) {
                            liste = liste.filter {
                                it.baslik.contains(aramaMetni, ignoreCase = true) ||
                                it.sanatci.contains(aramaMetni, ignoreCase = true)
                            }
                        }
                        liste
                    }

                    when (aktifSekme) {
                        MusicTab.ALL_TRACKS, MusicTab.QUEUE -> {
                            if (gosterilecekParcalar.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (taraniyorMu) "Müzikler taranıyor..." else "Müzik bulunamadı",
                                        color = Color.Gray,
                                        fontSize = 15.sp
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    items(gosterilecekParcalar) { parca ->
                                        ParcaSatiri(parca = parca, onClick = { /* Secilen parca oynatimi */ })
                                    }
                                }
                            }
                        }

                        MusicTab.FOLDERS -> {
                            if (secilenKlasor != null) {
                                Column(modifier = Modifier.fillMaxSize()) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 8.dp)
                                    ) {
                                        Text(
                                            text = "← ${secilenKlasor!!.ad}",
                                            color = ClPrimary,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 15.sp,
                                            modifier = Modifier.clickable { secilenKlasor = null }
                                        )
                                    }
                                    LazyColumn(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        items(gosterilecekParcalar) { parca ->
                                            ParcaSatiri(parca = parca, onClick = {})
                                        }
                                    }
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.fillMaxSize(),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(klasorler) { klasor ->
                                        KlasorSatiri(klasor = klasor, onClick = { secilenKlasor = klasor })
                                    }
                                }
                            }
                        }

                        MusicTab.PLAYLISTS -> {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Çalma listesi henüz oluşturulmadı", color = Color.Gray)
                            }
                        }
                    }
                }

                // Alt Kısım: Now Playing Player Kontrolleri
                AltPlayerPaneli(
                    medya = medya,
                    isShuffle = isShuffle,
                    repeatModu = repeatModu,
                    onShuffleClick = { isShuffle = !isShuffle },
                    onRepeatClick = { repeatModu = (repeatModu + 1) % 3 },
                    onPrevClick = onOnceki,
                    onPlayPauseClick = onOynatDuraklat,
                    onNextClick = onSonraki,
                    onKonumaGit = onKonumaGit
                )
            }
        }
    }
}

@Composable
private fun MuzikSekmeButonu(
    baslik: String,
    ikon: androidx.compose.ui.graphics.vector.ImageVector,
    secili: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(if (secili) ClPrimary else Color(0x22FFFFFF))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(imageVector = ikon, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            Text(text = baslik, color = Color.White, fontSize = 13.sp, fontWeight = if (secili) FontWeight.Bold else FontWeight.Normal)
        }
    }
}

@Composable
private fun ParcaSatiri(parca: SesParcasi, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ClCardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = parca.baslik, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(text = parca.sanatci, color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Text(text = formatSure(parca.sureMs), color = Color.Gray, fontSize = 12.sp)
    }
}

@Composable
private fun KlasorSatiri(klasor: SesKlasoru, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(ClCardBg)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(
                imageVector = if (klasor.isUsb) Icons.Default.Usb else Icons.Default.Folder,
                contentDescription = null,
                tint = if (klasor.isUsb) ClPrimary else Color(0xFFFFB300),
                modifier = Modifier.size(24.dp)
            )
            Column {
                Text(text = klasor.ad, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Text(text = if (klasor.isUsb) "USB Sürücü" else "Dahili Depolama", color = Color.Gray, fontSize = 11.sp)
            }
        }
        Text(text = "${klasor.parcaSayisi} parça", color = Color.LightGray, fontSize = 12.sp)
    }
}

@Composable
private fun AltPlayerPaneli(
    medya: MedyaParcasi,
    isShuffle: Boolean,
    repeatModu: Int,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onPrevClick: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onNextClick: () -> Unit,
    onKonumaGit: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Color(0xE614151B))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (medya.baslik.isNotBlank()) medya.baslik else "Parça Adı",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = if (medya.sanatci.isNotBlank()) medya.sanatci else "Sanatçı",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Oynatma Kontrolleri
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Karışık",
                    tint = if (isShuffle) ClPrimary else Color.Gray,
                    modifier = Modifier.size(22.dp).clickable(onClick = onShuffleClick)
                )

                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Önceki",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp).clickable(onClick = onPrevClick)
                )

                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(ClPrimary)
                        .clickable(onClick = onPlayPauseClick),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (medya.caliyorMu) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = "Oynat/Duraklat",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Sonraki",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp).clickable(onClick = onNextClick)
                )

                Icon(
                    imageVector = Icons.Default.Repeat,
                    contentDescription = "Tekrar",
                    tint = if (repeatModu > 0) ClPrimary else Color.Gray,
                    modifier = Modifier.size(22.dp).clickable(onClick = onRepeatClick)
                )
            }
        }

        // Seekbar ve Sureler
        val maxMs = if (medya.toplamSureMs > 0L) medya.toplamSureMs else 1L
        val curMs = medya.anlikKonumMs.coerceIn(0L, maxMs)

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(text = formatSure(curMs), color = Color.Gray, fontSize = 11.sp)
            Slider(
                value = curMs.toFloat(),
                onValueChange = { onKonumaGit(it.toLong()) },
                valueRange = 0f..maxMs.toFloat(),
                colors = SliderDefaults.colors(
                    thumbColor = ClPrimary,
                    activeTrackColor = ClPrimary,
                    inactiveTrackColor = Color(0x33FFFFFF)
                ),
                modifier = Modifier.weight(1f)
            )
            Text(text = formatSure(maxMs), color = Color.Gray, fontSize = 11.sp)
        }
    }
}

private fun formatSure(ms: Long): String {
    val toplamSaniye = ms / 1000
    val dakika = toplamSaniye / 60
    val saniye = toplamSaniye % 60
    return String.format("%02d:%02d", dakika, saniye)
}
