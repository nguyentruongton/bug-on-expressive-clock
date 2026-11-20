package app.expressiveclock.overlay

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.Display
import app.expressiveclock.data.IslandProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

internal fun islandProfile(foldable: Boolean, unfolded: Boolean, width: Int, height: Int): IslandProfile? {
    if (!foldable) return null
    return when {
        unfolded && width > height -> IslandProfile.UNFOLDED_LANDSCAPE
        unfolded -> IslandProfile.UNFOLDED_PORTRAIT
        width > height -> IslandProfile.FOLDED_LANDSCAPE
        else -> IslandProfile.FOLDED_PORTRAIT
    }
}

data class IslandDisplayState(val width: Int = 0, val height: Int = 0, val density: Float = 1f,
    val rotation: Int = 0, val displayId: Int = 0, val foldable: Boolean = false, val unfolded: Boolean = false) {
    val calibrationProfile get() = islandProfile(true, unfolded, width, height)!!
    val profile get() = islandProfile(foldable, unfolded, width, height)
}

/** Process lifetime observer, shared by Activity settings and the independently rotating service window. */
object IslandDisplayMonitor {
    private val mutable = MutableStateFlow(IslandDisplayState())
    val state = mutable.asStateFlow()
    private var context: Context? = null
    private var hingeAngle: Float? = null
    private var hasHingeSensor = false
    private val main = Handler(Looper.getMainLooper())
    fun start(ctx: Context) {
        if (context != null) return
        context = ctx.applicationContext
        ctx.getSystemService(DisplayManager::class.java).registerDisplayListener(object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(id: Int) = refresh()
            override fun onDisplayRemoved(id: Int) = refresh()
            override fun onDisplayChanged(id: Int) = refresh()
        }, main)
        val sensors = ctx.getSystemService(SensorManager::class.java)
        // TYPE_HINGE_ANGLE is 36 (API30); numeric ID keeps the API26 fallback available.
        val hinge = sensors.getDefaultSensor(36)
        hasHingeSensor = hinge != null
        hinge?.let { sensor ->
            sensors.registerListener(object : SensorEventListener {
                override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
                override fun onSensorChanged(event: SensorEvent) { hingeAngle = event.values.firstOrNull(); refresh() }
            }, sensor, SensorManager.SENSOR_DELAY_NORMAL, main)
        }
        refresh()
    }
    @Suppress("DEPRECATION")
    fun refresh() {
        val ctx = context ?: return
        val display = ctx.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY) ?: return
        val metrics = DisplayMetrics(); display.getRealMetrics(metrics)
        val foldable = hasHingeSensor || ctx.packageManager.hasSystemFeature("android.hardware.sensor.hinge_angle") ||
            ctx.packageManager.hasSystemFeature("android.hardware.type.foldable")
        // Hinge posture is primary. Only known foldables use the window-size fallback while it is unavailable.
        val unfolded = foldable && (hingeAngle?.let { it >= 30f } ?: (minOf(metrics.widthPixels, metrics.heightPixels) / metrics.density >= 600f))
        mutable.value = IslandDisplayState(metrics.widthPixels, metrics.heightPixels, metrics.density,
            display.rotation, display.displayId, foldable, unfolded)
    }
}
