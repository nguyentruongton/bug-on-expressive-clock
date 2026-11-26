package android.content

interface Context {
    val packageName: String get() = "app.expressiveclock"
    val contentResolver: Any? get() = null
}
