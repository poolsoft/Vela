package app.vela.carlauncher.media

/**
 * Tek kaynak el degistirmesinde eski sahibin durdurulma karari. Android turlerini
 * icermez; JVM birim testlerinde dogrulanabilir.
 */
internal enum class HandoffAction { NONE, PAUSE_INTERNAL, PAUSE_CONTROLLER, PAUSE_ADAPTER, WARN_CONFLICT }

internal object SourceHandoff {
    /**
     * Bir kaynaktan ayrilirken durdurma komutu "su an caliyor" kanitina bagli
     * olmamali: OEM oynaticilari MediaSession state yayinlamayabilir ve
     * adaptorlerin caliyorMu() degeri bilinmeyebilir. Eski sahip biliniyorsa
     * pause gonderilir; durdurulamayan kaynak kullaniciya bildirilir.
     */
    fun decide(
        previous: String,
        target: String,
        controllerCanPause: Boolean,
        hasAdapter: Boolean,
        isRadio: Boolean,
        radioManuallyRequested: Boolean,
    ): HandoffAction = when {
        previous == target -> HandoffAction.NONE
        previous == "internal" -> HandoffAction.PAUSE_INTERNAL
        controllerCanPause -> HandoffAction.PAUSE_CONTROLLER
        hasAdapter && !isRadio -> HandoffAction.PAUSE_ADAPTER
        isRadio && radioManuallyRequested -> HandoffAction.WARN_CONFLICT
        isRadio -> HandoffAction.NONE
        else -> HandoffAction.WARN_CONFLICT
    }
}
