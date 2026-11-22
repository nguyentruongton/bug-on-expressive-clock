package app.expressiveclock

import android.appwidget.AppWidgetManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import app.expressiveclock.data.*
import app.expressiveclock.ui.theme.ClockTheme
import app.expressiveclock.widget.*

/** Compares the production editor preview with the production RemoteViews, without placing/editing widgets. */
class WidgetComparisonActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            val settings = ClockSettings()
            var face by remember { mutableStateOf(0xFFDD7788L) }
            val config = WidgetConfig(analogPrimaryColor = 0xFF007744L, analogFaceColor = face)
            ClockTheme(settings.theme) {
                Surface {
                    Column(Modifier.fillMaxSize().systemBarsPadding().padding(12.dp)) {
                        Text("Production editor preview")
                        AnalogPreview(config, settings)
                        Text("Production home-screen RemoteViews")
                        key(face) {
                            AndroidView(factory = { context ->
                                val options = Bundle().apply {
                                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 220)
                                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 220)
                                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 220)
                                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 220)
                                }
                                ClockWidgetRenderer.build(context, config, settings, options).apply(context, null)
                            }, modifier = Modifier.size(220.dp))
                        }
                        Row {
                            Button(onClick = { face = 0xFFDD7788 }) { Text("Pink face") }
                            Button(onClick = { face = 0xFF7788DDL }) { Text("Blue face") }
                        }
                    }
                }
            }
        }
    }
}
