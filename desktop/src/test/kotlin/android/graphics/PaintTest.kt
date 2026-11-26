package android.graphics

import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import app.expressiveclock.ui.theme.ClockFace
import org.jetbrains.skia.Data
import org.jetbrains.skia.FontFeature
import org.jetbrains.skia.FontMgr
import org.jetbrains.skia.FontVariation
import org.jetbrains.skia.Typeface as SkiaTypeface
import org.jetbrains.skia.paragraph.FontCollection
import org.jetbrains.skia.paragraph.ParagraphBuilder
import org.jetbrains.skia.paragraph.ParagraphStyle
import org.jetbrains.skia.paragraph.TextStyle as SkiaParagraphTextStyle
import org.jetbrains.skia.paragraph.TypefaceFontProvider
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.test.Test
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PaintTest {

    private fun condensedAlarmTypeface(opticalSize: Float = 144f, weight: Int = ClockFace.WEIGHT_ON): SkiaTypeface {
        val stream = PaintTest::class.java.classLoader.getResourceAsStream("font/google_sans_flex.ttf")
        assertNotNull(stream, "Bundled google_sans_flex.ttf must exist on test classpath")
        val bytes = stream.use { it.readBytes() }
        val base = Data.makeFromBytes(bytes).use { data ->
            FontMgr.default.makeFromData(data)
        }
        assertNotNull(base, "Failed to load google_sans_flex.ttf with Skia FontMgr")
        val cloned = base.makeClone(
            arrayOf(
                FontVariation("opsz", opticalSize.coerceIn(6f, 144f)),
                FontVariation("wdth", ClockFace.CONDENSED),
                FontVariation("wght", weight.toFloat()),
            ),
        )
        base.close()
        return cloned
    }

    private fun measureSkiaParagraphWidth(
        typeface: SkiaTypeface,
        text: String,
        fontSizePx: Float,
        letterSpacingPx: Float,
        features: Array<FontFeature>,
    ): Float {
        TypefaceFontProvider().use { provider ->
            provider.registerTypeface(typeface, "test-face")
            FontCollection().use { collection ->
                collection.setDefaultFontManager(provider)
                ParagraphStyle().use { pStyle ->
                    SkiaParagraphTextStyle().use { tStyle ->
                        tStyle.setFontFamilies(arrayOf("test-face"))
                        tStyle.setFontSize(fontSizePx)
                        tStyle.setLetterSpacing(letterSpacingPx)
                        if (features.isNotEmpty()) {
                            tStyle.addFontFeatures(features)
                        }
                        ParagraphBuilder(pStyle, collection).use { builder ->
                            builder.pushStyle(tStyle)
                            builder.addText(text)
                            builder.popStyle()
                            builder.build().use { paragraph ->
                                paragraph.layout(Float.POSITIVE_INFINITY)
                                return paragraph.maxIntrinsicWidth
                            }
                        }
                    }
                }
            }
        }
    }

    @Test
    fun tabularFeatureParsesCommaSeparatedSettings() {
        condensedAlarmTypeface().use { tf ->
            val tabularPaint = Paint().apply {
                typeface = tf
                textSize = 200f
                letterSpacing = ClockFace.CONDENSED_TRACKING
                fontFeatureSettings = "tnum, lnum"
            }
            val proportionalPaint = Paint().apply {
                typeface = tf
                textSize = 200f
                letterSpacing = ClockFace.CONDENSED_TRACKING
                fontFeatureSettings = "pnum, lnum"
            }
            val slashedZeroPaint = Paint().apply {
                typeface = tf
                textSize = 200f
                letterSpacing = ClockFace.CONDENSED_TRACKING
                fontFeatureSettings = "tnum, lnum, zero"
            }

            val adv00 = tabularPaint.measureText("00")
            val adv11 = tabularPaint.measureText("11")
            val adv11Prop = proportionalPaint.measureText("11")
            val adv00Slashed = slashedZeroPaint.measureText("00")

            assertTrue(
                abs(adv00 - adv11) < 0.05f,
                "Expected tabular '00' ($adv00) and '11' ($adv11) to share identical advance",
            )
            assertTrue(
                adv11 > adv11Prop * 1.05f,
                "Expected tabular '11' ($adv11) to be wider than proportional '11' ($adv11Prop)",
            )
            assertTrue(
                abs(adv00Slashed - adv00) < 0.05f,
                "Expected 'tnum, lnum, zero' ($adv00Slashed) to preserve tabular advance ($adv00)",
            )
        }
    }

    @Test
    fun commaWhitespaceAndUnknownFeatureTokensDoNotDropValidTags() {
        condensedAlarmTypeface().use { tf ->
            val paint = Paint().apply {
                typeface = tf
                textSize = 200f
                letterSpacing = ClockFace.CONDENSED_TRACKING
                fontFeatureSettings = " tnum , invalid_tag, lnum , "
            }

            val adv00 = paint.measureText("00")
            val adv11 = paint.measureText("11")
            assertTrue(
                abs(adv00 - adv11) < 0.05f,
                "Expected mixed/comma feature string to keep valid 'tnum' tag: '00'=$adv00 vs '11'=$adv11",
            )
        }
    }

    @Test
    fun w3cQuotedAndOnOffNumericFeatureSettingsAreParsed() {
        condensedAlarmTypeface().use { tf ->
            val quotedOn = Paint().apply {
                typeface = tf
                textSize = 200f
                letterSpacing = ClockFace.CONDENSED_TRACKING
                fontFeatureSettings = "\"tnum\", 'lnum'"
            }
            val tnumOff = Paint().apply {
                typeface = tf
                textSize = 200f
                letterSpacing = ClockFace.CONDENSED_TRACKING
                fontFeatureSettings = "\"tnum\" off, 'lnum' on"
            }
            val tnumZero = Paint().apply {
                typeface = tf
                textSize = 200f
                letterSpacing = ClockFace.CONDENSED_TRACKING
                fontFeatureSettings = "tnum 0, lnum 1"
            }

            assertTrue(
                abs(quotedOn.measureText("00") - quotedOn.measureText("11")) < 0.05f,
                "Expected quoted W3C '\"tnum\", \\'lnum\\'' to enable tabular digits",
            )
            assertTrue(
                tnumOff.measureText("00") > tnumOff.measureText("11") * 1.05f,
                "Expected '\"tnum\" off' to keep '11' narrower than '00'",
            )
            assertTrue(
                tnumZero.measureText("00") > tnumZero.measureText("11") * 1.05f,
                "Expected 'tnum 0' to disable tabular figures",
            )
        }
    }

    @Test
    fun getTextBoundsReturnsTabularInkAndConsistentBearings() {
        condensedAlarmTypeface().use { tf ->
            val paint = Paint().apply {
                typeface = tf
                textSize = 200f
                letterSpacing = ClockFace.CONDENSED_TRACKING
                fontFeatureSettings = "tnum, lnum"
            }
            for (sample in listOf("00", "11", "AM")) {
                val rect = Rect()
                paint.getTextBounds(sample, 0, sample.length, rect)
                val adv = paint.measureText(sample)
                assertTrue(rect.width() > 0, "Expected positive ink width for '$sample'")
                assertTrue(rect.height() > 0, "Expected positive ink height for '$sample'")
                assertTrue(rect.right <= ceil(adv).toInt(), "Expected right (${rect.right}) <= ceil(adv) (${ceil(adv)}) for '$sample'")
                val rightBearing = adv - rect.right
                assertTrue(rightBearing >= -1f, "Expected non-negative right bearing for '$sample', got $rightBearing")
            }
        }
    }

    @Test
    fun unwrapSkiaTypefaceHandlesPackagePrivateGetterAndFieldWrappers() {
        condensedAlarmTypeface().use { tf ->
            class PrivateNativeGetterWrapper(private val inner: SkiaTypeface) {
                @Suppress("unused")
                private fun getNativeTypeface(): SkiaTypeface = inner
            }
            class PublicTypefaceGetterWrapper(private val inner: SkiaTypeface) {
                @Suppress("unused")
                fun getTypeface(): SkiaTypeface = inner
            }

            val directPaint = Paint().apply {
                typeface = tf
                textSize = 200f
                fontFeatureSettings = "tnum, lnum"
            }
            val nativeGetterPaint = Paint().apply {
                typeface = PrivateNativeGetterWrapper(tf)
                textSize = 200f
                fontFeatureSettings = "tnum, lnum"
            }
            val publicGetterPaint = Paint().apply {
                typeface = PublicTypefaceGetterWrapper(tf)
                textSize = 200f
                fontFeatureSettings = "tnum, lnum"
            }

            val directAdv = directPaint.measureText("00")
            assertTrue(
                abs(nativeGetterPaint.measureText("00") - directAdv) < 0.01f,
                "Expected package/private getNativeTypeface() wrapper to unwrap SkiaTypeface cleanly",
            )
            assertTrue(
                abs(publicGetterPaint.measureText("00") - directAdv) < 0.01f,
                "Expected getTypeface() wrapper to unwrap SkiaTypeface cleanly",
            )
        }
    }

    @Test
    fun fittedTimeProducesNonNegativePaddingAcrossTileWidthsAndDensities() {
        val refCapDp = 120.dp
        val insetDp = 18.1.dp
        val minuteCapFraction = 0.743f
        val gapFraction = 8.3f / 155f - 2f * (ClockFace.TRACKING_DELTA / ClockFace.DIGIT_EM)

        condensedAlarmTypeface(opticalSize = 144f).use { hourTf ->
            condensedAlarmTypeface(opticalSize = 122.47f).use { minTf ->
                for (densityValue in listOf(0.8f, 1.0f, 1.28f, 1.6f, 2.0f, 2.4f)) {
                    val density = Density(densityValue)
                    val hPaint = Paint().apply {
                        typeface = hourTf
                        textSize = with(density) { ClockFace.fontSizeFor(refCapDp).toPx() }
                        letterSpacing = ClockFace.CONDENSED_TRACKING
                        fontFeatureSettings = "tnum, lnum"
                    }
                    val mPaint = Paint().apply {
                        typeface = minTf
                        textSize = with(density) { ClockFace.fontSizeFor(refCapDp * minuteCapFraction).toPx() }
                        letterSpacing = ClockFace.CONDENSED_TRACKING
                        fontFeatureSettings = "tnum, lnum"
                    }
                    val hRect = Rect().also { hPaint.getTextBounds("00", 0, 2, it) }
                    val mRect = Rect().also { mPaint.getTextBounds("00", 0, 2, it) }
                    val hAdv = hPaint.measureText("00")
                    val mAdv = mPaint.measureText("00")
                    val hLeft = hRect.left.toFloat()
                    val mRight = mAdv - mRect.left.toFloat() - mRect.width().toFloat()

                    with(density) {
                        val k = (hAdv + mAdv).toDp() / refCapDp + gapFraction
                        val lf = hLeft.toDp() / refCapDp
                        val rf = mRight.toDp() / refCapDp

                        for (tileWidthDp in listOf(140.dp, 165.dp, 184.dp, 220.dp, 280.dp, 360.dp)) {
                            val cap = (tileWidthDp - insetDp * 2f) / (k - lf - rf)
                            val slack = tileWidthDp - cap * k
                            val padStart = (slack + cap * (rf - lf)) / 2f
                            val padEnd = slack - padStart
                            assertTrue(
                                padStart >= 0.dp && padEnd >= 0.dp,
                                "Negative padding at density=$densityValue tile=$tileWidthDp: padStart=$padStart padEnd=$padEnd (lf=$lf rf=$rf cap=$cap)",
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun twoDigitRowBudgetCoversTwoSingleDigitSkiaParagraphsAcrossScales() {
        val refCapDp = 120.dp
        val minuteCapFraction = 0.743f
        val gapFraction = 8.3f / 155f - 2f * (ClockFace.TRACKING_DELTA / ClockFace.DIGIT_EM)
        val features = arrayOf(FontFeature("tnum"), FontFeature("lnum"))

        condensedAlarmTypeface(opticalSize = 144f).use { hourTf ->
            condensedAlarmTypeface(opticalSize = 122.47f).use { minRefTf ->
                for (densityValue in listOf(0.8f, 1.0f, 1.28f, 1.6f, 2.0f, 2.4f)) {
                    val density = Density(densityValue)
                    val refHourFontPx = with(density) { ClockFace.fontSizeFor(refCapDp).toPx() }
                    val refMinFontPx = with(density) { ClockFace.fontSizeFor(refCapDp * minuteCapFraction).toPx() }

                    val refHourPaint = Paint().apply {
                        typeface = hourTf
                        textSize = refHourFontPx
                        letterSpacing = ClockFace.CONDENSED_TRACKING
                        fontFeatureSettings = "tnum, lnum"
                    }
                    val refMinPaint = Paint().apply {
                        typeface = minRefTf
                        textSize = refMinFontPx
                        letterSpacing = ClockFace.CONDENSED_TRACKING
                        fontFeatureSettings = "tnum, lnum"
                    }
                    val hAdv = refHourPaint.measureText("00")
                    val mAdv = refMinPaint.measureText("00")
                    val k = with(density) { (hAdv + mAdv).toDp() / refCapDp + gapFraction }

                    for (capDp in listOf(80.dp, 95.dp, 110.dp, 120.dp, 145.dp, 165.dp)) {
                        val minCapDp = capDp * minuteCapFraction
                        val hourFontPx = with(density) { ClockFace.fontSizeFor(capDp).toPx() }
                        val minFontPx = with(density) { ClockFace.fontSizeFor(minCapDp).toPx() }
                        val rowBudgetPx = with(density) { (capDp * k).roundToPx() }
                        val spacerPx = with(density) { (capDp * gapFraction).roundToPx() }
                        // Worst-case 2 px loss from padStart/padEnd roundToPx()
                        val usableRowPx = rowBudgetPx - spacerPx - 2

                        condensedAlarmTypeface(opticalSize = ClockFace.fontSizeFor(minCapDp).value).use { minTf ->
                            for (digit in '0'..'9') {
                                val singleHourPx = ceil(
                                    measureSkiaParagraphWidth(
                                        typeface = hourTf,
                                        text = digit.toString(),
                                        fontSizePx = hourFontPx,
                                        letterSpacingPx = ClockFace.CONDENSED_TRACKING * hourFontPx,
                                        features = features,
                                    ),
                                ).toInt()
                                val singleMinPx = ceil(
                                    measureSkiaParagraphWidth(
                                        typeface = minTf,
                                        text = digit.toString(),
                                        fontSizePx = minFontPx,
                                        letterSpacingPx = ClockFace.CONDENSED_TRACKING * minFontPx,
                                        features = features,
                                    ),
                                ).toInt()
                                val renderedRowDigitsPx = 2 * singleHourPx + 2 * singleMinPx
                                assertTrue(
                                    usableRowPx >= renderedRowDigitsPx,
                                    "Clipped at density=$densityValue cap=$capDp digit='$digit': usableRowPx=$usableRowPx < rendered=$renderedRowDigitsPx",
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
