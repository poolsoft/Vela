package app.vela.carlauncher.hardware

/**
 * Bas Unitesi (Head Unit / CAN-Bus / MCU) olaylarini dinleyen arayuz.
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
interface HeadUnitListener {
    fun onHizGuncellendi(hizKmh: Float) {}
    fun onAküVoltajGuncellendi(voltaj: Float) {}
    fun onFarDurumuDegisti(farAcikMi: Boolean) {}
    fun onCalmaDurumuDegisti(caliyorMu: Boolean) {}
    fun onParcaBilgisiDegisti(baslik: String, sanatci: String, albumKapakYolu: String?) {}
    fun onRadyoFrekansDegisti(band: String, frekansMhz: Float) {}
    fun onMedyaTusuBasildi(tusKodu: Int) {}
}
