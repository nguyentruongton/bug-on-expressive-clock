package androidx.lifecycle

open class AndroidViewModel(private val application: Any? = null) : ViewModel() {
    @Suppress("UNCHECKED_CAST")
    fun <T> getApplication(): T = application as T
}
