package android.graphics

class Rect(
    var left: Int = 0,
    var top: Int = 0,
    var right: Int = 0,
    var bottom: Int = 0,
) {
    fun width(): Int = right - left
    fun height(): Int = bottom - top
}
