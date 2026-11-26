package app.expressiveclock.ui.screens

import android.graphics.Paint
import org.jetbrains.skia.Canvas

internal fun Canvas.saveLayer(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    paint: Paint,
): Int = save()
