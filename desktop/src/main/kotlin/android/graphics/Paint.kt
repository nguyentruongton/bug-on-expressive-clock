package android.graphics

import org.jetbrains.skia.Font as SkiaFont
import org.jetbrains.skia.FontFeature
import org.jetbrains.skia.Typeface as SkiaTypeface
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle as SkiaParagraphTextStyle
import org.jetbrains.skia.paragraph.TypefaceFontProvider
import org.jetbrains.skia.shaper.Shaper
import org.jetbrains.skia.shaper.ShapingOptions
import kotlin.math.ceil
import kotlin.math.floor

class Paint {
    var renderEffect: RenderEffect? = null
    var typeface: Typeface? = null
    var textSize: Float = 16f
    var letterSpacing: Float = 0f
    var fontFeatureSettings: String? = null
    var isAntiAlias: Boolean = true

    private fun safeTracking(): Float = if (letterSpacing.isFinite()) letterSpacing else 0f

    private fun unwrapSkiaTypeface(): SkiaTypeface? {
        val raw = typeface ?: return null
        if (raw is SkiaTypeface) return raw
        for (methodName in arrayOf("getTypeface", "getNativeTypeface")) {
            val found = runCatching {
                val method = runCatching { raw.javaClass.getMethod(methodName) }.getOrNull()
                    ?: raw.javaClass.getDeclaredMethod(methodName)
                method.isAccessible = true
                method.invoke(raw) as? SkiaTypeface
            }.getOrNull()
            if (found != null) return found
        }
        for (fieldName in arrayOf("typeface", "nativeTypeface")) {
            val found = runCatching {
                val field = raw.javaClass.getDeclaredField(fieldName)
                field.isAccessible = true
                field.get(raw) as? SkiaTypeface
            }.getOrNull()
            if (found != null) return found
        }
        return null
    }

    private fun skiaFont(): SkiaFont? {
        val tf = unwrapSkiaTypeface() ?: return null
        return runCatching { SkiaFont(tf, textSize) }.getOrNull()
    }

    private fun parsedFeatures(): Array<FontFeature> = parseFontFeatures(fontFeatureSettings)

    private fun shapingOptions(features: Array<FontFeature>): ShapingOptions {
        if (features.isEmpty()) return ShapingOptions.DEFAULT
        return runCatching { ShapingOptions.DEFAULT.withFeatures(features) }
            .getOrDefault(ShapingOptions.DEFAULT)
    }

    private fun shapeBoundsAndAdvance(
        sub: String,
        features: Array<FontFeature>,
    ): Pair<org.jetbrains.skia.Rect, Float>? {
        val font = skiaFont() ?: return null
        return runCatching {
            font.use { f ->
                Shaper.makeShapeDontWrapOrReorder().use { shaper ->
                    shaper.shapeLine(sub, f, shapingOptions(features)).use { line ->
                        val advance = line.width
                        val blobBounds = line.textBlob?.use { it.bounds } ?: f.measureText(sub)
                        blobBounds to advance
                    }
                }
            }
        }.getOrNull()
    }

    private fun measureParagraphWidth(
        tf: SkiaTypeface,
        sub: String,
        features: Array<FontFeature>,
    ): Float? {
        val trackingPx = safeTracking() * textSize
        return runCatching {
            TypefaceFontProvider().use { provider ->
                provider.registerTypeface(tf, PAINT_FONT_ALIAS)
                FontCollection().use { collection ->
                    collection.setDefaultFontManager(provider)
                    ParagraphStyle().use { pStyle ->
                        SkiaParagraphTextStyle().use { tStyle ->
                            tStyle.setFontFamilies(arrayOf(PAINT_FONT_ALIAS))
                            tStyle.setFontSize(textSize)
                            tStyle.setLetterSpacing(trackingPx)
                            if (features.isNotEmpty()) {
                                tStyle.addFontFeatures(features)
                            }
                            ParagraphBuilder(pStyle, collection).use { builder ->
                                builder.pushStyle(tStyle)
                                builder.addText(sub)
                                builder.popStyle()
                                builder.build().use { paragraph ->
                                    paragraph.layout(Float.POSITIVE_INFINITY)
                                    paragraph.maxIntrinsicWidth
                                }
                            }
                        }
                    }
                }
            }
        }.getOrNull()
    }

    fun getTextBounds(text: String, start: Int, end: Int, bounds: Rect) {
        val sub = text.substring(start.coerceAtLeast(0), end.coerceAtMost(text.length))
        if (sub.isEmpty()) {
            bounds.left = 0
            bounds.top = 0
            bounds.right = 0
            bounds.bottom = 0
            return
        }

        val tracking = safeTracking()
        val trackBetween = if (sub.length > 1) tracking * textSize * (sub.length - 1) else 0f
        val features = parsedFeatures()
        val measured = shapeBoundsAndAdvance(sub, features)

        if (measured != null && measured.first.width > 0.5f && measured.second > 0.5f) {
            val (rect, shaperAdvance) = measured
            val left = floor(rect.left).toInt()
            val trueRightBearing = (shaperAdvance - rect.right).coerceAtLeast(0f)
            val totalAdvance = measureText(sub)
            val right = ceil((totalAdvance - trueRightBearing).coerceAtLeast(left + 1f)).toInt()
            bounds.left = left
            bounds.top = floor(rect.top).toInt()
            bounds.right = right
            bounds.bottom = ceil(rect.bottom).toInt()
        } else {
            val advance = (sub.length * textSize * 0.52f + trackBetween).coerceAtLeast(textSize * 0.3f)
            val bearing = (textSize * 0.04f).toInt()
            bounds.left = bearing
            bounds.top = -(textSize * 0.72f).toInt()
            bounds.right = (advance - bearing).toInt().coerceAtLeast(bearing + 1)
            bounds.bottom = 0
        }
    }

    fun measureText(text: String): Float {
        if (text.isEmpty()) return 0f
        val features = parsedFeatures()
        val tf = unwrapSkiaTypeface()

        if (tf != null) {
            if (text.all { it.isDigit() }) {
                var sum = 0f
                var ok = true
                for (ch in text) {
                    val w = measureParagraphWidth(tf, ch.toString(), features)
                    if (w == null || w <= 0.5f) {
                        ok = false
                        break
                    }
                    // Each digit in AnimatedAlarmNumber is its own BasicText with ceil(width) px.
                    // Adding 2f + w * 0.05f per digit at reference size covers both fixed
                    // per-digit ceil() / Row roundToPx() losses and proportional optical-size
                    // (opsz) width drift at smaller minute cap heights across densities 0.8x..2.4x.
                    sum += ceil(w) + 2f + w * 0.05f
                }
                if (ok && sum > 0.5f) return sum
            } else {
                val paraWidth = measureParagraphWidth(tf, text, features)
                if (paraWidth != null && paraWidth > 0.5f) {
                    return ceil(paraWidth)
                }
            }
        }

        val tracking = safeTracking()
        val measured = shapeBoundsAndAdvance(text, features)
        val base = if (measured != null && measured.second > 0.5f) {
            measured.second
        } else {
            text.length * textSize * 0.52f
        }
        return (base + tracking * textSize * text.length).coerceAtLeast(textSize * 0.25f)
    }

    internal companion object {
        private val WHITESPACE_REGEX = Regex("\\s+")
        private val TAG_4_REGEX = Regex("^[A-Za-z0-9]{4}$")
        private const val PAINT_FONT_ALIAS = "__paint__"

        private fun String.unquoteFeatureTag(): String {
            var s = trim()
            if (s.length >= 2 && ((s.startsWith('"') && s.endsWith('"')) || (s.startsWith('\'') && s.endsWith('\'')))) {
                s = s.substring(1, s.length - 1).trim()
            }
            return s
        }

        internal fun parseFontFeatures(raw: String?): Array<FontFeature> {
            val trimmed = raw?.trim()
            if (trimmed.isNullOrEmpty()) return emptyArray()
            val result = ArrayList<FontFeature>(4)
            for (clause in trimmed.split(',')) {
                val part = clause.trim()
                if (part.isEmpty()) continue
                val tokens = part.split(WHITESPACE_REGEX).filter { it.isNotEmpty() }
                if (tokens.size == 2) {
                    val tag = tokens[0].unquoteFeatureTag()
                    val arg = tokens[1].lowercase()
                    val value = when (arg) {
                        "on" -> 1
                        "off" -> 0
                        else -> arg.toIntOrNull()?.takeIf { it >= 0 }
                    }
                    if (TAG_4_REGEX.matches(tag) && value != null) {
                        result.add(FontFeature(tag, value))
                        continue
                    }
                }
                for (tok in tokens) {
                    val clean = tok.unquoteFeatureTag()
                    if (clean.isEmpty()) continue
                    runCatching { FontFeature.parseOne(clean) }.getOrNull()?.let(result::add)
                }
            }
            return result.toTypedArray()
        }
    }
}
