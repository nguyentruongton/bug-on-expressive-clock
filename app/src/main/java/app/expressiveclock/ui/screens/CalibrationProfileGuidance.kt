package app.expressiveclock.ui.screens

import app.expressiveclock.data.IslandProfile
import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.Looper
import android.database.ContentObserver
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.Display
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

@Composable
internal fun CalibrationProfileGuidance(profile: IslandProfile, currentProfile: IslandProfile, foldable: Boolean, onBack: () -> Unit, onContinue: () -> Unit) {
    val title: String
    val description: String
    when (profile) {
        IslandProfile.FOLDED_PORTRAIT -> {
            title = if (currentProfile.ordinal >= 2) "Fold your phone and hold it upright" else "Switch to portrait mode"
            description = if (foldable) "Close your foldable device and hold the outer display upright to accurately adjust this display configuration."
                else "Hold your phone upright to accurately adjust this display configuration."
        }
        IslandProfile.FOLDED_LANDSCAPE -> {
            title = if (currentProfile.ordinal >= 2) "Fold and rotate your phone" else "Rotate your phone to landscape"
            description = if (foldable) "Close your foldable device and rotate the outer display sideways to accurately adjust this display configuration."
                else "Rotate your phone sideways to accurately adjust this display configuration."
        }
        IslandProfile.UNFOLDED_PORTRAIT -> {
            title = "Unfold your phone"
            description = "Open your foldable device and hold it in portrait orientation to accurately adjust this display configuration."
        }
        IslandProfile.UNFOLDED_LANDSCAPE -> {
            title = "Unfold and rotate your phone"
            description = "Open your foldable device and rotate it to landscape to accurately adjust this display configuration."
        }
    }
    AlertDialog(
        onDismissRequest = onBack,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(32.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(title, fontFamily = MaterialTheme.typography.bodyLarge.fontFamily) },
        text = { Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            CalibrationDeviceIllustration(profile)
            Text(description, fontFamily = MaterialTheme.typography.bodyLarge.fontFamily)
            Text("You can still edit this profile now.", style = MaterialTheme.typography.bodySmall, fontFamily = MaterialTheme.typography.bodyLarge.fontFamily)
        } },
        dismissButton = { TextButton(onClick = onBack, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { Text("Back", fontFamily = MaterialTheme.typography.bodyLarge.fontFamily) } },
        confirmButton = { TextButton(onClick = onContinue, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.primary)) { Text("Continue Anyway", fontFamily = MaterialTheme.typography.bodyLarge.fontFamily) } },
    )
}

@Composable
private fun CalibrationDeviceIllustration(profile: IslandProfile) {
    val resolver = LocalContext.current.contentResolver
    var motion by remember { mutableStateOf(Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f) }
    DisposableEffect(resolver) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                motion = Settings.Global.getFloat(resolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
            }
        }
        resolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { resolver.unregisterContentObserver(observer) }
    }
    val progress = remember(profile) { Animatable(0f) }
    LaunchedEffect(profile, motion) {
        if (!motion) { progress.snapTo(1f); return@LaunchedEffect }
        while (true) {
            progress.animateTo(1f, tween(1400, easing = FastOutSlowInEasing)); delay(650)
            progress.animateTo(0f, tween(1400, easing = FastOutSlowInEasing)); delay(450)
        }
    }
    val blue = MaterialTheme.colorScheme.primary
    val illustrationHeight = if (LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE) 96.dp else 156.dp
    Canvas(Modifier.fillMaxWidth().height(illustrationHeight)) {
        val p = progress.value
        val foldable = profile == IslandProfile.UNFOLDED_PORTRAIT || profile == IslandProfile.UNFOLDED_LANDSCAPE
        val opening = if (foldable) FastOutSlowInEasing.transform((p / .65f).coerceIn(0f, 1f)) else 0f
        val rotation = when (profile) {
            IslandProfile.FOLDED_PORTRAIT -> 90f * (1f - p)
            IslandProfile.FOLDED_LANDSCAPE -> 90f * p
            IslandProfile.UNFOLDED_LANDSCAPE -> 90f * FastOutSlowInEasing.transform(((p - .6f) / .4f).coerceIn(0f, 1f))
            else -> 0f
        }
        val h = size.height * .76f
        val w = h * (.49f + .45f * opening)
        val origin = Offset(center.x - w / 2, center.y - h / 2)
        rotate(rotation) {
            drawRoundRect(blue.copy(alpha = .10f), origin, Size(w, h), CornerRadius(16.dp.toPx()))
            drawRoundRect(blue, origin, Size(w, h), CornerRadius(16.dp.toPx()), style = Stroke(2.dp.toPx()))
            if (foldable && opening > .01f) drawLine(blue.copy(alpha = .35f * opening),
                Offset(center.x, origin.y + 8.dp.toPx()), Offset(center.x, origin.y + h - 8.dp.toPx()), 1.dp.toPx())
            drawCircle(blue, 2.dp.toPx(), Offset(center.x, origin.y + 8.dp.toPx()))
        }
    }
}
