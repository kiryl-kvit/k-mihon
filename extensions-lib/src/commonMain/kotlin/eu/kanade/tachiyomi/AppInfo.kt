package eu.kanade.tachiyomi

/**
 * Compile-time surface for extensions. The app provides the real implementation at runtime.
 */
@Suppress("UNUSED")
object AppInfo {
    fun getVersionCode(): Int = 0

    fun getVersionName(): String = "0"

    fun getSupportedImageMimeTypes(): List<String> = listOf(
        "image/avif",
        "image/gif",
        "image/heif",
        "image/jpeg",
        "image/jxl",
        "image/png",
        "image/webp",
    )
}
