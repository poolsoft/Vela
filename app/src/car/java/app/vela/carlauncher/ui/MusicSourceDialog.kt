package app.vela.carlauncher.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import app.vela.R
import app.vela.carlauncher.media.MediaNotificationListener
import app.vela.carlauncher.media.MusicManager
import app.vela.carlauncher.model.MedyaParcasi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Tracking mode, explicit source selection, and automatic-takeover permissions are separate. */
internal object MusicSourceDialog {
    fun show(context: Context, manager: MusicManager, scope: CoroutineScope): AlertDialog {
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        val accent = Color.rgb(63, 217, 202)
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(8))
        }
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.car_sources_title)
            .setView(ScrollView(context).apply { addView(body) })
            .setPositiveButton(R.string.car_sources_done, null)
            .create()

        fun text(value: String, large: Boolean = false) = TextView(context).apply {
            text = value
            textSize = if (large) 18f else 14f
            setTextColor(if (large) Color.WHITE else Color.rgb(185, 195, 207))
        }
        fun note(value: String) {
            body.addView(text(value).apply { setPadding(0, dp(8), 0, dp(12)) })
        }
        fun card(icon: ImageView, title: String, detail: String, checked: Boolean, action: () -> Unit) {
            body.addView(LinearLayout(context).apply {
                gravity = android.view.Gravity.CENTER_VERTICAL
                minimumHeight = dp(72)
                setPadding(dp(14), dp(12), dp(14), dp(12))
                background = GradientDrawable().apply {
                    cornerRadius = dp(14).toFloat()
                    setColor(if (checked) Color.rgb(28, 68, 69) else Color.rgb(35, 42, 53))
                    if (checked) setStroke(dp(1), accent)
                }
                addView(icon, LinearLayout.LayoutParams(dp(36), dp(36)))
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(14), 0, dp(8), 0)
                    addView(text(title, true))
                    addView(text(detail).apply { setPadding(0, dp(3), 0, 0) })
                }, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
                if (checked) addView(text("✓", true).apply { setTextColor(accent) })
                contentDescription = "$title. $detail"
                isFocusable = true
                setOnClickListener { action() }
            }, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                bottomMargin = dp(8)
            })
        }
        fun render() {
            val options = manager.sourceOptions()
            val current = options.firstOrNull { it.key == manager.selectedSourceKey() }
            body.removeAllViews()
            val status = if (current?.playbackKnown == false) context.getString(R.string.car_sources_state_unknown) else context.getString(if (current?.playing == true) R.string.car_sources_playing else R.string.car_sources_waiting)
            body.addView(text(context.getString(R.string.car_sources_current, current?.label.orEmpty()) + " · " + status, true).apply {
                setPadding(0, 0, 0, dp(12))
            })
            card(ImageView(context).apply {
                setImageResource(android.R.drawable.ic_popup_sync)
                setColorFilter(accent)
            }, context.getString(R.string.car_sources_auto), context.getString(R.string.car_sources_auto_hint), manager.isAutomaticTracking()) {
                manager.allowAutomaticSourceTracking()
                Toast.makeText(context, R.string.car_sources_auto_selected, Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            card(ImageView(context).apply {
                setImageResource(android.R.drawable.ic_lock_lock)
                setColorFilter(accent)
            }, context.getString(R.string.car_sources_lock), context.getString(R.string.car_sources_lock_hint), manager.isSourceLocked()) {
                manager.setSourceLocked(true)
            }
            body.addView(text(context.getString(R.string.car_sources_manual), true))
            options.forEach { source ->
                val selected = source.key == manager.selectedSourceKey()
                val status = listOfNotNull(
                    context.getString(R.string.car_sources_controlled).takeIf { selected },
                    context.getString(if (source.playing) R.string.car_sources_playing else R.string.car_sources_waiting)
                ).joinToString(" · ").let { if (source.playbackKnown) it else context.getString(R.string.car_sources_state_unknown) }
                val icon = ImageView(context).apply {
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    MediaSourceIcon.bind(this, MedyaParcasi(paketAdi = source.packageName.orEmpty(),
                        sourceId = source.key, sourceKind = source.kind, sourceLabel = source.label))
                }
                card(icon, source.label, status, selected) {
                    manager.selectSource(source.key, play = source.key != "internal" || manager.internalPlayer.anlikParca.value != null, lock = manager.isSourceLocked())
                    dialog.dismiss()
                }
                if (source.key != "internal") body.addView(android.widget.Switch(context).apply {
                    text = context.getString(R.string.car_sources_allow_follow)
                    setTextColor(Color.WHITE)
                    isChecked = manager.isSourceTrackingEnabled(source.key)
                    contentDescription = source.label + ". " + text
                    setOnCheckedChangeListener { _, checked -> manager.setSourceTracking(source.key, checked) }
                })
            }
            note(context.getString(R.string.car_sources_manual_hint))
            note(context.getString(R.string.car_sources_phone_hint))
            if (!MediaNotificationListener.bildirimIzniVerildiMi(context)) {
                note(context.getString(R.string.car_sources_access))
                body.addView(android.widget.Button(context).apply {
                    text = context.getString(R.string.car_sources_access_action)
                    setOnClickListener {
                        dialog.dismiss()
                        MediaNotificationListener.bildirimAyarlariniAc(context)
                    }
                })
            }
        }
        render()
        val updates = scope.launch {
            combine(manager.medyaDurumu.map { it.sourceId to it.caliyorMu }.distinctUntilChanged(),
                manager.sourcesRevision) { _, _ -> Unit }.collect { render() }
        }
        dialog.setOnDismissListener { updates.cancel() }
        dialog.show()
        dialog.window?.let { app.vela.ui.bindVelaSystemBars(it) }
        val visible = android.graphics.Rect()
        dialog.window?.decorView?.getWindowVisibleDisplayFrame(visible)
        val height = visible.height().takeIf { it > 0 } ?: context.resources.displayMetrics.heightPixels
        dialog.window?.setLayout(minOf(dp(640), context.resources.displayMetrics.widthPixels - dp(32)),
            minOf(dp(560), height - dp(16)))
        return dialog
    }
}
