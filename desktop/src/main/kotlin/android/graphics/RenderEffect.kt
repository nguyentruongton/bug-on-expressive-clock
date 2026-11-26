package android.graphics

class RenderEffect internal constructor(
    val radiusX: Float = 0f,
    val radiusY: Float = 0f,
    val edgeTreatment: Shader.TileMode = Shader.TileMode.CLAMP,
) {
    companion object {
        @JvmStatic
        fun createBlurEffect(
            radiusX: Float,
            radiusY: Float,
            edgeTreatment: Shader.TileMode,
        ): RenderEffect = RenderEffect(radiusX, radiusY, edgeTreatment)
    }
}
