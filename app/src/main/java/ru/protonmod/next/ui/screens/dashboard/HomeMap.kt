/*
 * Copyright (C) 2026 SMH01
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package ru.protonmod.next.ui.screens.dashboard

import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.caverock.androidsvg.RenderOptions
import com.caverock.androidsvg.SVG
import kotlinx.coroutines.*
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.theme.ProtonNextTheme
import kotlin.math.max
import kotlin.math.min

// --- Constants & Coordinates ---

private const val MAP_ASSET_NAME = "world.svg"
private const val MAP_ORIGINAL_WIDTH = 1538.434f
private const val MAP_ORIGINAL_HEIGHT = 700f

object MapCoordinates {
    val codeToMapCountryName = mapOf(
        "AD" to "Andorra", "AE" to "UnitedArabEmirates", "AF" to "Afghanistan",
        "AL" to "Albania", "AM" to "Armenia", "AO" to "Angola", "AR" to "Argentina",
        "AT" to "Austria", "AU" to "Australia", "AW" to "Aruba", "AZ" to "Azerbaijan",
        "BA" to "Bosnia_Herz", "BD" to "Bangladesh", "BE" to "Belgium", "BG" to "Bulgaria",
        "BH" to "Bahrain", "BI" to "Burundi", "BN" to "Brunei", "BO" to "Bolivia",
        "BR" to "Brazil", "BS" to "Bahamas", "BT" to "Bhutan", "BW" to "Botswana",
        "BY" to "Belarus", "BZ" to "Belize", "CA" to "Canada", "CD" to "DemRepofCongo",
        "CF" to "CentralAfricanRep", "CH" to "Switzerland", "CI" to "IvoryCoast",
        "CL" to "Chile", "CM" to "Cameroon", "CN" to "China", "CO" to "Colombia",
        "CR" to "CostaRica", "CU" to "Cuba", "CV" to "CapeVerde", "CW" to "Curacao",
        "CY" to "Cyprus", "CZ" to "CzechRep", "DE" to "Germany", "DK" to "Denmark",
        "DO" to "DominicanRep", "DZ" to "Algeria", "EC" to "Ecuador", "EE" to "Estonia",
        "EG" to "Egypt", "ER" to "Eritrea", "ES" to "Spain", "ET" to "Ethiopia",
        "FI" to "Finland", "FR" to "France", "GB" to "UnitedKingdom", "GE" to "Georgia",
        "GH" to "Ghana", "GL" to "Greenland", "GM" to "Gambia", "GN" to "Guinea",
        "GQ" to "EqGuinea", "GR" to "Greece", "GT" to "Guatemala", "HK" to "HongKong",
        "HN" to "Honduras", "HR" to "Croatia", "HT" to "Haiti", "HU" to "Hungary",
        "ID" to "Indonesia", "IE" to "Ireland", "IL" to "Israel", "IN" to "India",
        "IQ" to "Iraq", "IS" to "Iceland", "IT" to "Italy", "JO" to "Jordan",
        "JP" to "Japan", "KE" to "Kenya", "KG" to "Kyrgyzstan", "KH" to "Cambodia",
        "KM" to "Comoros", "KR" to "SouthKorea", "KW" to "Kuwait", "KZ" to "Kazakhstan",
        "LA" to "Laos", "LB" to "Lebanon", "LI" to "Liechtenstein", "LK" to "SriLanka",
        "LT" to "Lithuania", "LU" to "Luxembourg", "LV" to "Latvia", "LY" to "Libya",
        "MA" to "Morocco", "MC" to "Monaco", "MD" to "Moldova", "ME" to "Montenegro",
        "MK" to "Macedonia", "MM" to "Myanmar", "MN" to "Mongolia", "MO" to "Macao",
        "MR" to "Mauritania", "MT" to "Malta", "MU" to "Mauritius", "MX" to "Mexico",
        "MY" to "Malaysia", "MZ" to "Mozambique", "NG" to "Nigeria", "NL" to "Netherlands",
        "NO" to "Norway", "NP" to "Nepal", "NZ" to "NewZealand", "OM" to "Oman",
        "PA" to "Panama", "PE" to "Peru", "PH" to "Phillipines", "PK" to "Pakistan",
        "PL" to "Poland", "PR" to "PuertoRico", "PS" to "Palestine", "PT" to "Portugal",
        "QA" to "Qatar", "RO" to "Romania", "RS" to "Serbia", "RU" to "Russia",
        "RW" to "Rwanda", "SA" to "SaudiArabia", "SD" to "Sudan", "SE" to "Sweden",
        "SG" to "Singapore", "SI" to "Slovenia", "SK" to "Slovakia", "SN" to "Senegal",
        "SY" to "Syria", "TH" to "Thailand", "TJ" to "Tajikistan", "TM" to "Turkmenistan",
        "TN" to "Tunisia", "TR" to "Turkey", "TW" to "Taiwan", "TZ" to "Tanzania",
        "UA" to "Ukraine", "UG" to "Uganda", "UK" to "UnitedKingdom", "US" to "UnitedStatesofAmerica",
        "UY" to "Uruguay", "UZ" to "Uzbekistan", "VA" to "Vatican", "VE" to "Venezuela",
        "VN" to "Vietnam", "YE" to "Yemen", "ZA" to "SouthAfrica", "ZW" to "Zimbabwe"
    )

    val tvMapNameToBounds = mapOf(
        "Afghanistan" to RectF(953.3968f, 199.4880f, 1010.7480f, 245.5879f),
        "AlandIslands" to RectF(774.6121f, 92.8440f, 778.5470f, 94.6760f),
        "Albania" to RectF(777.9768f, 178.9820f, 785.4478f, 193.4160f),
        "Algeria" to RectF(659.2271f, 206.5010f, 748.5139f, 298.2070f),
        "AmericanSamoa" to RectF(1532.8369f, 466.2410f, 1534.0000f, 466.7590f),
        "Andorra" to RectF(705.3571f, 179.0060f, 706.6841f, 180.0210f),
        "Angola" to RectF(747.5631f, 416.5509f, 802.2094f, 485.2549f),
        "Anguilla" to RectF(417.9520f, 301.8250f, 418.7960f, 302.3221f),
        "AntiguaandBarbuda" to RectF(422.9990f, 304.6350f, 423.8930f, 308.2580f),
        "Argentina" to RectF(396.0918f, 504.3880f, 464.6610f, 670.2539f),
        "Armenia" to RectF(877.0279f, 185.6490f, 891.3170f, 197.3840f),
        "Aruba" to RectF(384.7570f, 330.4140f, 385.4650f, 331.3791f),
        "Australia" to RectF(1183.4761f, 444.9870f, 1362.9520f, 668.9041f),
        "Austria" to RectF(738.4578f, 147.3410f, 768.4167f, 160.1931f),
        "Azerbaijan" to RectF(883.2840f, 182.6960f, 906.0871f, 199.7700f),
        "Bahamas" to RectF(354.7890f, 257.9890f, 378.3920f, 288.3391f),
        "Bahrain" to RectF(915.2470f, 261.5000f, 916.0471f, 263.7240f),
        "Bangladesh" to RectF(1078.5698f, 259.8290f, 1101.5741f, 289.0801f),
        "Barbados" to RectF(431.1960f, 326.8570f, 432.1500f, 328.1470f),
        "Belarus" to RectF(790.6941f, 112.7940f, 826.3413f, 136.2260f),
        "Belgium" to RectF(711.6890f, 135.1300f, 726.3570f, 144.8191f),
        "Belize" to RectF(301.6310f, 300.7491f, 309.2970f, 313.8631f),
        "Benin" to RectF(698.7841f, 331.5821f, 712.4232f, 362.7451f),
        "Bermuda" to RectF(421.5130f, 230.5150f, 422.4640f, 231.1580f),
        "Bhutan" to RectF(1080.3191f, 251.0590f, 1094.9681f, 259.1960f),
        "Bolivia" to RectF(386.3380f, 443.2379f, 442.8969f, 509.8789f),
        "Bosnia_Herz" to RectF(763.2628f, 165.7250f, 778.8359f, 179.4180f),
        "Botswana" to RectF(783.3259f, 484.0709f, 824.5419f, 529.8970f),
        "Brazil" to RectF(365.7050f, 367.5682f, 540.2469f, 564.6563f),
        "BritishVirginIslands" to RectF(411.3320f, 299.3850f, 413.3479f, 301.1711f),
        "Brunei" to RectF(1204.0369f, 368.7860f, 1209.7112f, 373.8360f),
        "Bulgaria" to RectF(790.0829f, 171.2150f, 815.1920f, 185.8900f),
        "BurkinaFaso" to RectF(670.8389f, 317.9490f, 706.0509f, 346.5331f),
        "Burundi" to RectF(824.6879f, 405.8650f, 832.6990f, 416.7021f),
        "Cambodia" to RectF(1148.8190f, 319.8440f, 1172.4958f, 341.5490f),
        "Cameroon" to RectF(733.2628f, 328.0640f, 767.3988f, 385.7193f),
        "Canada" to RectF(200.6940f, 1.3660f, 551.5890f, 182.4753f),
        "CapeVerde" to RectF(584.0820f, 307.2620f, 595.5860f, 319.2740f),
        "CaymanIslands" to RectF(338.4260f, 294.2630f, 346.0450f, 296.7580f),
        "CentralAfricanRep" to RectF(759.5640f, 338.5732f, 817.4299f, 382.6802f),
        "Chad" to RectF(755.1300f, 275.5981f, 801.6561f, 356.3170f),
        "Chile" to RectF(350.5960f, 482.6690f, 455.9510f, 674.3409f),
        "China" to RectF(1002.7844f, 125.1471f, 1232.6929f, 302.0810f),
        "Colombia" to RectF(342.2650f, 331.3247f, 396.4891f, 415.5551f),
        "Comoros" to RectF(887.3199f, 451.6400f, 893.0200f, 456.6909f),
        "CostaRica" to RectF(313.7960f, 337.6210f, 328.1740f, 353.3820f),
        "Croatia" to RectF(754.3350f, 159.5200f, 777.8799f, 180.0380f),
        "Cuba" to RectF(324.9880f, 276.9470f, 370.9481f, 293.7989f),
        "Cyprus" to RectF(833.9230f, 213.8650f, 843.1880f, 219.4530f),
        "CzechRep" to RectF(748.5081f, 137.3450f, 774.7451f, 149.4489f),
        "DemRepofCongo" to RectF(749.6871f, 367.3048f, 834.8172f, 462.1551f),
        "Denmark" to RectF(733.6250f, 105.2830f, 759.6920f, 120.0180f),
        "Ecuador" to RectF(285.8020f, 386.8250f, 359.0979f, 419.3969f),
        "Egypt" to RectF(802.9929f, 234.1840f, 857.3770f, 282.9700f),
        "ElSalvador" to RectF(296.5170f, 321.2300f, 307.1260f, 327.6310f),
        "Estonia" to RectF(783.5709f, 96.3950f, 805.8314f, 106.2660f),
        "Ethiopia" to RectF(842.2330f, 319.0960f, 908.9171f, 376.7193f),
        "Finland" to RectF(775.5179f, 50.0680f, 815.4690f, 95.5460f),
        "France" to RectF(682.5427f, 137.0430f, 738.4230f, 185.1750f),
        "Georgia" to RectF(861.3540f, 174.4740f, 890.1160f, 186.7260f),
        "Germany" to RectF(724.4791f, 117.9650f, 759.6792f, 155.8471f),
        "Ghana" to RectF(680.8099f, 337.7461f, 700.5388f, 370.1302f),
        "Greece" to RectF(779.9139f, 183.4290f, 816.4100f, 217.5951f),
        "Greenland" to RectF(502.3360f, -0.0040f, 683.5747f, 95.5459f),
        "Guatemala" to RectF(287.5490f, 304.1200f, 305.9970f, 324.7420f),
        "HongKong" to RectF(1193.6440f, 280.1190f, 1195.6860f, 281.9880f),
        "Hungary" to RectF(764.4309f, 149.5460f, 790.9579f, 163.3810f),
        "Iceland" to RectF(624.5690f, 65.2990f, 661.2050f, 79.1509f),
        "India" to RectF(993.3101f, 213.8570f, 1116.7290f, 360.0700f),
        "Indonesia" to RectF(1119.7272f, 364.3139f, 1324.5229f, 449.3200f),
        "Iran" to RectF(880.6329f, 192.8539f, 970.4191f, 267.3179f),
        "Iraq" to RectF(861.9750f, 205.0730f, 905.2890f, 247.2570f),
        "Ireland" to RectF(662.5389f, 116.5109f, 679.7780f, 135.2278f),
        "Israel" to RectF(843.5510f, 225.2410f, 850.0191f, 245.1710f),
        "Italy" to RectF(726.7981f, 156.8180f, 775.0891f, 208.5800f),
        "Japan" to RectF(1234.6680f, 164.5880f, 1312.2219f, 271.5190f),
        "Kazakhstan" to RectF(883.6282f, 116.3949f, 1041.9971f, 189.0230f),
        "Kenya" to RectF(846.5470f, 366.4110f, 882.1051f, 417.8670f),
        "Latvia" to RectF(781.1628f, 103.7510f, 807.7517f, 115.0720f),
        "Lithuania" to RectF(781.1089f, 111.5490f, 802.9690f, 123.5481f),
        "Luxembourg" to RectF(723.8110f, 141.5960f, 726.7861f, 145.1380f),
        "Malaysia" to RectF(1139.3459f, 357.0150f, 1227.0774f, 389.8119f),
        "Mexico" to RectF(187.9360f, 228.8440f, 316.5830f, 320.6327f),
        "Moldova" to RectF(805.5360f, 149.9230f, 820.1810f, 164.8690f),
        "Netherlands" to RectF(714.9170f, 124.8230f, 729.9021f, 138.7359f),
        "NewZealand" to RectF(1341.2169f, 437.3740f, 1535.2009f, 658.4430f),
        "Nigeria" to RectF(707.2449f, 324.0470f, 760.3759f, 372.5559f),
        "Norway" to RectF(680.4290f, 9.6210f, 808.5273f, 103.9641f),
        "Pakistan" to RectF(958.0673f, 206.7940f, 1022.2393f, 274.0960f),
        "Peru" to RectF(332.3840f, 394.3871f, 391.9140f, 486.9030f),
        "Phillipines" to RectF(1216.1191f, 288.8400f, 1259.1555f, 368.5979f),
        "Poland" to RectF(756.1528f, 119.0100f, 794.8150f, 147.2181f),
        "Portugal" to RectF(569.7160f, 181.4770f, 674.0070f, 229.1940f),
        "Romania" to RectF(781.0290f, 150.9770f, 819.0482f, 173.9879f),
        "Russia" to RectF(776.5129f, 5.1650f, 1347.1597f, 186.1269f),
        "Serbia" to RectF(775.4900f, 161.3270f, 792.7161f, 180.9810f),
        "Singapore" to RectF(1158.0310f, 386.8549f, 1159.5741f, 387.7690f),
        "SouthAfrica" to RectF(767.7308f, 506.1070f, 850.6570f, 630.9420f),
        "SouthKorea" to RectF(1224.2739f, 198.6370f, 1243.8589f, 226.4020f),
        "Spain" to RectF(618.4191f, 173.5330f, 716.7820f, 254.4190f),
        "Sweden" to RectF(744.8058f, 54.4280f, 788.5458f, 116.5910f),
        "Switzerland" to RectF(724.3099f, 153.3880f, 742.1210f, 163.0020f),
        "Taiwan" to RectF(1211.0430f, 266.4040f, 1226.3870f, 283.3530f),
        "Thailand" to RectF(1124.1492f, 290.9480f, 1162.3911f, 365.6991f),
        "Turkey" to RectF(804.6140f, 181.6940f, 885.2080f, 212.9928f),
        "Ukraine" to RectF(787.8859f, 130.9510f, 857.8768f, 170.4529f),
        "UnitedArabEmirates" to RectF(920.8321f, 262.4020f, 941.5073f, 279.8479f),
        "UnitedKingdom" to RectF(672.5562f, 90.8860f, 709.0702f, 142.3049f),
        "UnitedStatesofAmerica" to RectF(61.6110f, 44.4350f, 432.8538f, 270.1190f),
        "Vietnam" to RectF(1142.3848f, 276.1580f, 1180.8430f, 350.7600f)
    )

    fun getPointForCountry(countryCode: String?): PointF {
        val code = countryCode?.uppercase() ?: return PointF(MAP_ORIGINAL_WIDTH/2, MAP_ORIGINAL_HEIGHT/2)
        val name = codeToMapCountryName[code]
        val bounds = tvMapNameToBounds[name]

        if (bounds != null) {
            return PointF(bounds.centerX(), bounds.centerY())
        }

        // Fallback for completely unknown countries - deterministic scatter
        val hash = code.hashCode()
        val x = MAP_ORIGINAL_WIDTH * (0.2f + (Math.abs(hash % 100) / 100f) * 0.6f)
        val y = MAP_ORIGINAL_HEIGHT * (0.2f + (Math.abs((hash / 100) % 100) / 100f) * 0.6f)
        return PointF(x, y)
    }

    fun getFocusRegion(countryCode: String?): RectF {
        if (countryCode == null) {
            // Default full map view (zoomed out)
            return RectF(50f, 0f, MAP_ORIGINAL_WIDTH - 50f, MAP_ORIGINAL_HEIGHT)
        }
        val name = codeToMapCountryName[countryCode.uppercase()]
        val bounds = tvMapNameToBounds[name]

        if (bounds != null) {
            val cx = bounds.centerX()
            val cy = bounds.centerY()
            // Make the viewport size generous enough so we don't zoom in extremely close on small nations
            val w = max(bounds.width() * 2.5f, 600f)
            val h = max(bounds.height() * 2.5f, 600f)
            return RectF(cx - w/2, cy - h/2, cx + w/2, cy + h/2)
        }
        // Fallback
        return RectF(50f, 0f, MAP_ORIGINAL_WIDTH - 50f, MAP_ORIGINAL_HEIGHT)
    }
}

// --- Native MapView Implementation ---

/**
 * A custom View that renders the SVG map using AndroidSVG and handles automatic
 * smooth pan & zoom animations to the focused country.
 */
@SuppressLint("ClickableViewAccessibility")
class MapView(context: Context) : View(context) {

    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var renderJob: Job? = null

    private var mapBitmap: Bitmap? = null

    // Matrix interpolation for smooth zooms
    private val currentMatrix = Matrix()
    private val targetMatrix = Matrix()
    private val startMatrix = Matrix()

    private val startVals = FloatArray(9)
    private val endVals = FloatArray(9)
    private val currVals = FloatArray(9)
    private var matrixAnimator: ValueAnimator? = null
    private var isFirstZoom = true
    private var isSvgRendered = false

    // Server data
    var allServers: List<LogicalServer> = emptyList()
    var connectedServer: LogicalServer? = null
    var isConnecting: Boolean = false
    var isInteractive: Boolean = false
    var onNodeClick: ((String) -> Unit)? = null

    // Colors
    var bgColor: Int = android.graphics.Color.BLACK
    var baseMapColor: Int = android.graphics.Color.DKGRAY
    var borderMapColor: Int = android.graphics.Color.GRAY
    var highlightMapColor: Int = android.graphics.Color.GREEN
    var pinColor: Int = android.graphics.Color.GREEN
    var inactivePinColor: Int = android.graphics.Color.GRAY

    // Continuous Animations
    private var pulseScale = 1f
    private var pulseAlpha = 0f
    private var dataFlowPhase = 0f
    private var pulseAnimator: ValueAnimator? = null

    // Paints
    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val pulsePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 6f }
    private val dotPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = android.graphics.Color.WHITE; style = Paint.Style.FILL }
    private val inactivePaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tunnelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE; strokeWidth = 5f }
    private val tunnelPath = android.graphics.Path()

    private var pendingCountryFocus: String? = null

    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
            if (!isInteractive) return false

            // Map click from Screen coordinates -> SVG Original coordinates
            val pts = floatArrayOf(e.x, e.y)
            val inverse = Matrix()
            currentMatrix.invert(inverse)
            inverse.mapPoints(pts)

            val svgX = pts[0]
            val svgY = pts[1]

            var closest: String? = null
            var minDist = Float.MAX_VALUE

            val uniqueCodes = allServers.map { it.exitCountry }.distinct()
            for (code in uniqueCodes) {
                val pt = MapCoordinates.getPointForCountry(code)
                val dx = pt.x - svgX
                val dy = pt.y - svgY
                val distSq = dx*dx + dy*dy
                if (distSq < minDist) {
                    minDist = distSq
                    closest = code
                }
            }

            // Allow tap within ~40 units in SVG space
            if (minDist < 40f * 40f) {
                closest?.let { onNodeClick?.invoke(it) }
            }

            return true
        }
    })

    init {
        pulseAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1500
            repeatCount = ValueAnimator.INFINITE
            interpolator = LinearInterpolator()
            addUpdateListener { anim ->
                val fraction = anim.animatedFraction
                pulseScale = 1f + fraction * 2f
                pulseAlpha = 1f - fraction
                // Speed up flow
                dataFlowPhase = fraction * 200f
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        scope.cancel()
        pulseAnimator?.cancel()
        matrixAnimator?.cancel()
        mapBitmap?.recycle()
        mapBitmap = null
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (!isInteractive) return super.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        // We always return true to consume the touch event, preventing underlying UI from scrolling
        return true
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            animateToCountry(connectedServer?.exitCountry)
        }
    }

    fun onServerStateChanged(newServer: LogicalServer?) {
        val oldCountry = connectedServer?.exitCountry
        connectedServer = newServer

        // Always render if not rendered yet, or if country changed
        if (!isSvgRendered || oldCountry != newServer?.exitCountry) {
            renderSvgInBackground(newServer?.exitCountry)
            if (width > 0 && height > 0) {
                animateToCountry(newServer?.exitCountry)
            } else {
                pendingCountryFocus = newServer?.exitCountry
            }
        } else {
            invalidate()
        }
    }

    private fun animateToCountry(countryCode: String?) {
        if (width == 0 || height == 0) return

        val targetRect = MapCoordinates.getFocusRegion(countryCode)

        val scaleX = width.toFloat() / targetRect.width()
        val scaleY = height.toFloat() / targetRect.height()
        val fitScale = min(scaleX, scaleY)

        val dx = (width.toFloat() - targetRect.width() * fitScale) / 2f - targetRect.left * fitScale
        val dy = (height.toFloat() - targetRect.height() * fitScale) / 2f - targetRect.top * fitScale

        targetMatrix.reset()
        targetMatrix.postScale(fitScale, fitScale)
        targetMatrix.postTranslate(dx, dy)

        if (isFirstZoom) {
            isFirstZoom = false
            currentMatrix.set(targetMatrix)
            invalidate()
            return
        }

        startMatrix.set(currentMatrix)

        matrixAnimator?.cancel()
        matrixAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 800
            interpolator = FastOutSlowInInterpolator()
            addUpdateListener { anim ->
                val f = anim.animatedFraction
                startMatrix.getValues(startVals)
                targetMatrix.getValues(endVals)
                for (i in 0..8) {
                    currVals[i] = startVals[i] + (endVals[i] - startVals[i]) * f
                }
                currentMatrix.setValues(currVals)
                invalidate()
            }
            start()
        }
    }

    private fun renderSvgInBackground(targetCountry: String?) {
        isSvgRendered = true
        renderJob?.cancel()
        renderJob = scope.launch(Dispatchers.IO) {
            try {
                val svg = SVG.getFromAsset(context.assets, MAP_ASSET_NAME)

                val baseHex = String.format("#%06X", (0xFFFFFF and baseMapColor))
                val borderHex = String.format("#%06X", (0xFFFFFF and borderMapColor))
                val highlightHex = String.format("#%06X", (0xFFFFFF and highlightMapColor))

                val cssBuilder = StringBuilder()
                cssBuilder.append("path { fill: $baseHex; stroke: $borderHex; stroke-width: 1.5px; } ")

                if (targetCountry != null) {
                    val countryName = MapCoordinates.codeToMapCountryName[targetCountry.uppercase()]
                    if (countryName != null) {
                        cssBuilder.append("#$countryName { fill: $highlightHex; } ")
                    }
                }

                val renderOptions = RenderOptions().css(cssBuilder.toString())

                val docW = if (svg.documentWidth > 0) svg.documentWidth else MAP_ORIGINAL_WIDTH
                val docH = if (svg.documentHeight > 0) svg.documentHeight else MAP_ORIGINAL_HEIGHT

                // High-res rendering to ensure it stays crisp when zoomed
                val bmWidth = 2500
                val bmHeight = (bmWidth * (docH / docW)).toInt()

                val bm = Bitmap.createBitmap(bmWidth, bmHeight, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bm)

                val scaleX = bmWidth.toFloat() / docW
                val scaleY = bmHeight.toFloat() / docH
                canvas.scale(scaleX, scaleY)

                svg.renderToCanvas(canvas, renderOptions)

                withContext(Dispatchers.Main) {
                    mapBitmap?.recycle()
                    mapBitmap = bm
                    invalidate()
                }
            } catch (e: Exception) {
                Log.e("HomeMap", "Failed to render SVG. Check if world.svg is in assets folder.", e)
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(bgColor)

        if (mapBitmap == null) return

        // 1. Draw the pre-rendered Map
        val drawMatrix = Matrix()
        drawMatrix.postScale(MAP_ORIGINAL_WIDTH / mapBitmap!!.width, MAP_ORIGINAL_HEIGHT / mapBitmap!!.height)
        drawMatrix.postConcat(currentMatrix)
        canvas.drawBitmap(mapBitmap!!, drawMatrix, null)

        // 2. Draw connections and pins
        inactivePaint.color = inactivePinColor
        pinPaint.color = pinColor

        // Find screen scale factor from matrix
        val matrixValues = FloatArray(9)
        currentMatrix.getValues(matrixValues)
        val currentScale = matrixValues[Matrix.MSCALE_X]

        // Keep pin size consistent regardless of zoom
        val baseRadius = 12f / currentScale

        val pts = FloatArray(2)
        val uniqueCodes = allServers.map { it.exitCountry }.distinct()
        val connectedCode = connectedServer?.exitCountry

        var targetScreenX = 0f
        var targetScreenY = 0f

        // Explicitly calculate connected server coords first
        if (connectedCode != null) {
            val pt = MapCoordinates.getPointForCountry(connectedCode)
            pts[0] = pt.x; pts[1] = pt.y
            currentMatrix.mapPoints(pts)
            targetScreenX = pts[0]
            targetScreenY = pts[1]
        }

        for (code in uniqueCodes) {
            if (code == connectedCode) continue // drawn later
            val pt = MapCoordinates.getPointForCountry(code)
            pts[0] = pt.x; pts[1] = pt.y
            currentMatrix.mapPoints(pts)
            val px = pts[0]; val py = pts[1]
            canvas.drawCircle(px, py, baseRadius * 0.6f, inactivePaint)
        }

        // Draw Active Connection state
        if (connectedCode != null && (isConnected() || isConnecting)) {
            val startX = width / 2f
            val startY = height.toFloat() * 0.95f // User location near bottom

            tunnelPath.reset()
            tunnelPath.moveTo(startX, startY)
            tunnelPath.quadTo(startX, targetScreenY, targetScreenX, targetScreenY)

            tunnelPaint.color = pinColor
            tunnelPaint.alpha = 150
            tunnelPaint.pathEffect = DashPathEffect(floatArrayOf(30f, 30f), -dataFlowPhase)

            canvas.drawPath(tunnelPath, tunnelPaint)

            // Pulsing target
            pulsePaint.color = pinColor
            pulsePaint.alpha = (pulseAlpha * 255).toInt()
            canvas.drawCircle(targetScreenX, targetScreenY, baseRadius * currentScale * pulseScale, pulsePaint)

            // Solid target pin
            canvas.drawCircle(targetScreenX, targetScreenY, baseRadius * currentScale, pinPaint)
            canvas.drawCircle(targetScreenX, targetScreenY, baseRadius * currentScale * 0.4f, dotPaint)
        }
    }

    private fun isConnected() = connectedServer != null && !isConnecting
}

// --- Compose Wrapper ---

@Composable
fun HomeMap(
    modifier: Modifier = Modifier,
    allServers: List<LogicalServer>,
    connectedServer: LogicalServer?,
    isConnecting: Boolean,
    isInteractive: Boolean = false,
    onNodeClick: ((String) -> Unit)? = null
) {
    val colors = ProtonNextTheme.colors

    val bgColor = colors.backgroundNorm.toArgb()
    val baseMapColor = colors.shade15.toArgb()
    val borderMapColor = colors.shade50.toArgb()

    val isConnected = connectedServer != null && !isConnecting
    val pinColorValue = animateColorAsState(
        targetValue = when {
            isConnected -> colors.notificationSuccess
            isConnecting -> colors.brandNorm
            else -> androidx.compose.ui.graphics.Color.Transparent
        },
        animationSpec = tween(durationMillis = 500),
        label = "pinColor"
    )
    val highlightColorValue = if (isConnected) colors.notificationSuccess.copy(alpha = 0.5f) else colors.brandNorm.copy(alpha = 0.5f)
    val inactiveColor = colors.iconWeak.copy(alpha = 0.5f).toArgb()

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            MapView(context).apply {
                this.bgColor = bgColor
                this.baseMapColor = baseMapColor
                this.borderMapColor = borderMapColor
                this.inactivePinColor = inactiveColor
            }
        },
        update = { mapView ->
            mapView.allServers = allServers
            mapView.isConnecting = isConnecting
            mapView.isInteractive = isInteractive
            mapView.onNodeClick = onNodeClick
            mapView.pinColor = pinColorValue.value.toArgb()
            mapView.highlightMapColor = highlightColorValue.toArgb()

            // Passes state down safely, triggering background SVG renders and Matrix animations if needed
            mapView.onServerStateChanged(connectedServer)
        }
    )
}