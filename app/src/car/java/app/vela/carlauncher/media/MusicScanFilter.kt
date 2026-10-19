package app.vela.carlauncher.media

import java.util.Locale

/** Shared exclusions for indexed audio, file crawling and saved library entries. */
internal object MusicScanFilter {
    private val folders = setOf(
        "whatsapp", "whatsapp business", "telegram", "signal", "viber", "messenger", "wechat", "weixin",
        "ringtones", "ringtone", "notifications", "notification", "alarms", "alarm",
        "zilsesleri", "zil sesleri", "bildirimler", "bildirim sesleri", "alarmlar",
        "recordings", "recording", "voice notes", "voice messages", "audio messages", "call recordings",
        "whatsapp audio", "whatsapp voice notes", "telegram audio", "telegram documents",
    )
    private val packages = setOf(
        "com.whatsapp", "com.whatsapp.w4b", "org.telegram.messenger", "org.telegram.messenger.web",
        "org.thunderdog.challegram", "org.thoughtcrime.securesms", "com.viber.voip", "com.facebook.orca",
        "com.tencent.mm", "com.skype.raider", "com.discord", "jp.naver.line.android",
    )

    fun excludes(path: String, isRingtone: Boolean = false, isAlarm: Boolean = false,
        isNotification: Boolean = false): Boolean {
        if (isRingtone || isAlarm || isNotification) return true
        return path.replace('\\', '/').replace('İ', 'I').lowercase(Locale.ROOT).split('/').any { segment ->
            segment in folders || segment in packages
        }
    }
}
