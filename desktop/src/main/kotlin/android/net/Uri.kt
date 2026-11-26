package android.net

data class Uri(private val raw: String) {
    val lastPathSegment: String?
        get() = raw.substringAfterLast('/', raw).ifBlank { null }

    override fun toString(): String = raw

    companion object {
        @JvmStatic
        fun parse(uriString: String): Uri = Uri(uriString)
    }
}
