package androidx.compose.ui.graphics

fun android.graphics.RenderEffect.asComposeRenderEffect(): RenderEffect {
    val tileMode = when (edgeTreatment) {
        android.graphics.Shader.TileMode.CLAMP -> TileMode.Clamp
        android.graphics.Shader.TileMode.REPEAT -> TileMode.Repeated
        android.graphics.Shader.TileMode.MIRROR -> TileMode.Mirror
        android.graphics.Shader.TileMode.DECAL -> TileMode.Decal
    }
    return BlurEffect(radiusX = radiusX, radiusY = radiusY, edgeTreatment = tileMode)
}
