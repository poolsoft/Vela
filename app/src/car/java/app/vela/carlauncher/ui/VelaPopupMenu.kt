package app.vela.carlauncher.ui

import android.annotation.SuppressLint
import android.content.Context
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.widget.ListPopupWindow

/** A focusable native list popup whose actual window follows launcher system-bar preferences. */
@SuppressLint("RestrictedApi")
internal class VelaPopupMenu(private val context: Context, private val anchor: View) {
    private val builder = MenuBuilder(context)
    val menu: Menu get() = builder
    private var itemClick: ((MenuItem) -> Boolean)? = null
    private var popup: ListPopupWindow? = null
    private val levels = mutableListOf<Menu>()

    init {
        builder.setCallback(object : MenuBuilder.Callback {
            override fun onMenuItemSelected(menu: MenuBuilder, item: MenuItem) = itemClick?.invoke(item) ?: false
            override fun onMenuModeChange(menu: MenuBuilder) {}
        })
    }
    fun setOnMenuItemClickListener(listener: (MenuItem) -> Boolean) { itemClick = listener }

    fun show() {
        levels.clear()
        levels.add(menu)
        showLevel(menu)
    }

    private fun showLevel(level: Menu) {
        val items = (0 until level.size()).map(level::getItem).filter { it.isVisible }
        if (items.isEmpty()) { popup?.dismiss(); return }
        val density = context.resources.displayMetrics.density
        val adapter = object : ArrayAdapter<MenuItem>(anchor.context, android.R.layout.simple_list_item_1, items) {
            override fun isEnabled(position: Int) = items[position].isEnabled
            override fun areAllItemsEnabled() = items.all { it.isEnabled }
            override fun getView(position: Int, recycled: View?, parent: ViewGroup): View {
                val item = items[position]
                return (super.getView(position, recycled, parent) as TextView).apply {
                    text = (if (item.isChecked) "✓  " else "") + item.title + if (item.hasSubMenu()) "  ›" else ""
                    isEnabled = item.isEnabled
                    alpha = if (item.isEnabled) 1f else 0.45f
                    minHeight = (48 * density).toInt()
                    item.icon?.let { icon ->
                        icon.setBounds(0, 0, (24 * density).toInt(), (24 * density).toInt())
                        setCompoundDrawablesRelative(icon, null, null, null)
                    } ?: setCompoundDrawablesRelative(null, null, null, null)
                }
            }
        }
        val listPopup = popup ?: ListPopupWindow(anchor.context).also {
            it.anchorView = anchor
            it.isModal = true
            it.inputMethodMode = ListPopupWindow.INPUT_METHOD_NOT_NEEDED
            it.setContentWidth(minOf((320 * density).toInt(), (anchor.rootView.width - 32 * density).toInt()).coerceAtLeast(1))
            popup = it
        }
        // Ignore-bottom-decoration measurement can put the last row behind the API-30
        // navigation inset even after the bar is hidden. Bound the list to stable space.
        val frame = android.graphics.Rect()
        anchor.getWindowVisibleDisplayFrame(frame)
        val rootLocation = IntArray(2).also { anchor.rootView.getLocationOnScreen(it) }
        val anchorLocation = IntArray(2).also { anchor.getLocationOnScreen(it) }
        @Suppress("DEPRECATION")
        val stableBottom = anchor.rootWindowInsets?.stableInsetBottom ?: 0
        frame.bottom = minOf(frame.bottom, rootLocation[1] + anchor.rootView.height - stableBottom)
        val available = maxOf(anchorLocation[1] - frame.top,
            frame.bottom - anchorLocation[1] - anchor.height).coerceAtLeast(1)
        val backgroundPadding = android.graphics.Rect()
        listPopup.background?.getPadding(backgroundPadding)
        listPopup.height = minOf(items.size * (48 * density).toInt() +
            backgroundPadding.top + backgroundPadding.bottom, available)
        listPopup.setAdapter(adapter)
        listPopup.setOnItemClickListener { parent, _, position, _ ->
            // AppCompat retains this listener when the open popup changes levels.
            // Resolve from the displayed adapter, never a captured parent menu.
            if (position !in 0 until parent.count) return@setOnItemClickListener
            val item = parent.getItemAtPosition(position) as? MenuItem ?: return@setOnItemClickListener
            if (!item.isEnabled || !item.isVisible) return@setOnItemClickListener
            if (item.hasSubMenu()) {
                item.subMenu?.let { levels.add(it); showLevel(it) }
            } else {
                listPopup.dismiss()
                builder.performItemAction(item, Menu.FLAG_PERFORM_NO_CLOSE)
            }
        }
        listPopup.show()
        listPopup.listView?.let { list ->
            app.vela.ui.bindVelaPopupSystemBars(list)
            list.setOnKeyListener { _, key, event ->
                if (key == KeyEvent.KEYCODE_DPAD_LEFT && levels.size > 1) {
                    if (event.action == KeyEvent.ACTION_UP) { levels.removeAt(levels.lastIndex); showLevel(levels.last()) }
                    true
                } else false
            }
        }
    }
}
