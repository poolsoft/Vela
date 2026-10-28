package app.vela.carlauncher.ui

import android.graphics.drawable.Drawable
import android.view.View
import android.widget.ImageView
import app.vela.R
import app.vela.carlauncher.model.MedyaParcasi

/** Bind on source changes, not on every playback-position tick. */
internal object MediaSourceIcon {
    private val icons = android.util.LruCache<String, Drawable.ConstantState>(24)
    fun bind(view: ImageView?, media: MedyaParcasi, externalOnly: Boolean = false) {
        view ?: return
        view.visibility = if (externalOnly && media.sourceKind == "internal") View.GONE else View.VISIBLE
        view.contentDescription = media.sourceLabel
        val key = "${media.sourceKind}:${media.paketAdi}"
        if (view.getTag(R.id.app_icon) == key) return
        view.setTag(R.id.app_icon, key)
        val pkg = media.paketAdi.takeIf { it.isNotBlank() && media.sourceKind != "bluetooth" }
        val icon = pkg?.let { packageName ->
            icons.get(packageName)?.newDrawable(view.resources) ?: runCatching {
                view.context.packageManager.getApplicationIcon(packageName).also { drawable ->
                    drawable.constantState?.let { icons.put(packageName, it) }
                }
            }.getOrNull()
        }
        if (icon != null) view.setImageDrawable(icon)
        else view.setImageResource(when (media.sourceKind) {
            "bluetooth" -> R.drawable.ic_source_bluetooth
            "radio" -> R.drawable.ic_source_radio
            else -> R.drawable.ic_internal_music
        })
    }
}
