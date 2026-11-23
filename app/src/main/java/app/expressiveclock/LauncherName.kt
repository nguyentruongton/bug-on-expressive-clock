package app.expressiveclock

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

/** PackageManager persists this choice across process death and app updates. */
object LauncherName {
    private fun component(context: Context, short: Boolean) = ComponentName(
        context.packageName, "app.expressiveclock." + if (short) "ShortLauncherActivity" else "LauncherActivity",
    )

    fun isShort(context: Context): Boolean = context.packageManager.getComponentEnabledSetting(
        component(context, true),
    ) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED

    fun select(context: Context, short: Boolean) {
        if (isShort(context) == short) return
        val pm = context.packageManager
        val enabled = component(context, short)
        val disabled = component(context, !short)
        if (Build.VERSION.SDK_INT >= 33) {
            pm.setComponentEnabledSettings(listOf(
                PackageManager.ComponentEnabledSetting(enabled, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP),
                PackageManager.ComponentEnabledSetting(disabled, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP),
            ))
        } else {
            // Enable first so there is always a launchable entry on older Android versions.
            pm.setComponentEnabledSetting(enabled, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP)
            pm.setComponentEnabledSetting(disabled, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP)
        }
    }
}
