package app.vela.carlauncher.desktop

import android.appwidget.AppWidgetProviderInfo
import android.os.Build

/**
 * AppWidgetProviderInfo bildirimini hucre kuralina cevirir (resize C).
 *
 * Android widget'lari boyutlarini dp ile bildirir:
 * - minWidth/minHeight: varsayilan (ilk) boyut.
 * - minResizeWidth/minResizeHeight: kullanicinin kucultebilecegi en kucuk boyut.
 * - maxResizeWidth/maxResizeHeight: buyutebilecegi en buyuk boyut (API 31; 0 = sinirsiz).
 * - targetCellWidth/targetCellHeight: widget'in istedigi hucre sayisi (API 31).
 * - resizeMode: 0 = yeniden boyutlandirilamaz, 1 = yatay, 2 = dikey, 3 = ikisi.
 *
 * Bu bildirimler okunmadan 1x1'lik bir widget 6x3 yapilabiliyordu; artik MovableWidget
 * min/max span olarak bu sinirlari kullaniyor ve CellGrid.solve kayitli span'i da
 * sinirlara kirpiyor.
 *
 * Kod icerisinde Turkce karakter kullanilmamistir (identifier ve degiskenlerde).
 */
fun AppWidgetProviderInfo.spanRuleFor(grid: CellGrid.Grid): WidgetSpanRule {
    // resizeMode sabitleri API 31'de eklendi; degerleri 21'den beri aynidir.
    val horizontal = (resizeMode and 0x1) != 0
    val vertical = (resizeMode and 0x2) != 0

    val minW = if (horizontal) minResizeWidth.takeIf { it > 0 } ?: minWidth else minWidth
    val minH = if (vertical) minResizeHeight.takeIf { it > 0 } ?: minHeight else minHeight
    val maxW = if (Build.VERSION.SDK_INT >= 31) maxResizeWidth else 0
    val maxH = if (Build.VERSION.SDK_INT >= 31) maxResizeHeight else 0

    val limits = WidgetSpanSizing.limits(
        minWidthDp = minW,
        minHeightDp = minH,
        maxWidthDp = maxW,
        maxHeightDp = maxH,
        grid = grid,
        resizableX = horizontal,
        resizableY = vertical
    )

    // Ilk yerlestirme boyutu: widget hucre istiyorsa (API 31 targetCell*) onu kullan;
    // yoksa dogal dp boyutunu kaplayan en kucuk span.
    val defaultX = if (Build.VERSION.SDK_INT >= 31 && targetCellWidth > 0) {
        targetCellWidth.coerceIn(limits.minX, limits.maxX)
    } else {
        limits.minX
    }
    val defaultY = if (Build.VERSION.SDK_INT >= 31 && targetCellHeight > 0) {
        targetCellHeight.coerceIn(limits.minY, limits.maxY)
    } else {
        limits.minY
    }
    return WidgetSpanRule(defaultX, defaultY, limits)
}
