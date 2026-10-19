package app.vela.carlauncher.ui

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import app.vela.R
import app.vela.carlauncher.widgets.InterlockingClockView

/** Native RGB controls with local preview; cancel never changes the saved clock. */
internal object ClockColorDialog {
    fun show(context: Context, primary: Int, secondary: Int, twoColors: Boolean,
        save: (Int, Int, Boolean) -> Unit): AlertDialog {
        fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
        val colors = intArrayOf(primary, secondary)
        var selected = 0
        var binding = false
        val body = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), 0, dp(20), dp(8))
        }
        val preview = InterlockingClockView(context).apply {
            text = "12:48"
            textSize = 64f
            setTextColor(Color.WHITE)
            fitTextToBounds = true
        }
        body.addView(preview, LinearLayout.LayoutParams(-1, dp(80)))
        val dual = CheckBox(context).apply {
            setText(R.string.car_small_clock_two_colors)
            setTextColor(Color.WHITE)
            isChecked = twoColors
        }
        body.addView(dual)
        val targets = LinearLayout(context)
        val hour = Button(context).apply { setText(R.string.car_small_clock_hours) }
        val minute = Button(context).apply { setText(R.string.car_small_clock_minutes) }
        targets.addView(hour, LinearLayout.LayoutParams(0, dp(48), 1f))
        targets.addView(minute, LinearLayout.LayoutParams(0, dp(48), 1f))
        body.addView(targets)
        val value = TextView(context).apply { setTextColor(Color.WHITE); textSize = 16f }
        body.addView(value)
        val sliders = List(3) { channel ->
            val row = LinearLayout(context).apply { gravity = android.view.Gravity.CENTER_VERTICAL }
            row.addView(TextView(context).apply {
                text = listOf("R", "G", "B")[channel]
                setTextColor(Color.WHITE)
            }, LinearLayout.LayoutParams(dp(24), -2))
            val slider = SeekBar(context).apply { max = 255; minimumHeight = dp(48) }
            row.addView(slider, LinearLayout.LayoutParams(0, dp(48), 1f))
            body.addView(row)
            slider
        }
        fun render() {
            preview.fillColor = colors[0]
            preview.minuteFillColor = if (dual.isChecked) colors[1] else null
            value.text = context.getString(if (selected == 0) R.string.car_small_clock_hours
                else R.string.car_small_clock_minutes) + "  #%06X".format(colors[selected] and 0xFFFFFF)
            hour.alpha = if (selected == 0) 1f else 0.6f
            minute.alpha = if (selected == 1) 1f else 0.6f
            minute.isEnabled = dual.isChecked
        }
        fun select(index: Int) {
            selected = index
            binding = true
            sliders[0].progress = Color.red(colors[index])
            sliders[1].progress = Color.green(colors[index])
            sliders[2].progress = Color.blue(colors[index])
            binding = false
            render()
        }
        sliders.forEach { slider ->
            slider.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (binding || !fromUser) return
                    colors[selected] = Color.rgb(sliders[0].progress, sliders[1].progress, sliders[2].progress)
                    render()
                }
                override fun onStartTrackingTouch(bar: SeekBar?) = Unit
                override fun onStopTrackingTouch(bar: SeekBar?) = Unit
            })
        }
        hour.setOnClickListener { select(0) }
        minute.setOnClickListener { select(1) }
        dual.setOnCheckedChangeListener { _, checked -> select(if (checked) selected else 0) }
        select(0)
        val dialog = AlertDialog.Builder(context, android.R.style.Theme_Material_Dialog_Alert)
            .setTitle(R.string.car_small_clock_custom)
            .setView(ScrollView(context).apply { addView(body) })
            .setNegativeButton(R.string.car_small_clock_cancel, null)
            .setPositiveButton(R.string.car_small_clock_save) { _, _ -> save(colors[0], colors[1], dual.isChecked) }
            .create()
        dialog.show()
        dialog.window?.let { app.vela.ui.bindVelaSystemBars(it) }
        val frame = android.graphics.Rect()
        dialog.window?.decorView?.getWindowVisibleDisplayFrame(frame)
        val height = if (frame.height() > 0) frame.height() else context.resources.displayMetrics.heightPixels
        dialog.window?.setLayout(minOf(dp(560), context.resources.displayMetrics.widthPixels - dp(24)),
            minOf(dp(520), height - dp(16)).coerceAtLeast(1))
        return dialog
    }
}
