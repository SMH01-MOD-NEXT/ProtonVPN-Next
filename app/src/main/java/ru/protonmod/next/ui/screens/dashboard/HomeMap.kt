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

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.SystemClock
import android.provider.Settings
import android.view.View
import android.view.animation.DecelerateInterpolator
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import ru.protonmod.next.R
import ru.protonmod.next.data.network.LogicalServer
import ru.protonmod.next.ui.theme.ProtonNextTheme
import ru.protonmod.next.utils.inCoordsOf
import ru.protonmod.next.utils.relativePadding
import ru.protonmod.next.utils.scale
import ru.protonmod.next.utils.toPx
import ru.protonmod.next.utils.withPadding
import kotlin.math.roundToInt
import kotlin.math.sqrt

// --- Constants & Coordinates ---

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

    val oldMapLocations = mapOf(
        "AE" to PointF(3103.0f, 976.0f),
        "AL" to PointF(2560.0f, 665.0f),
        "AR" to PointF(1300.0f, 2000.0f),
        "AT" to PointF(2485.0f, 550.0f),
        "AU" to PointF(4355.0f, 1855.0f),
        "BA" to PointF(2527.0f, 661.0f),
        "BE" to PointF(2343.0f, 495.0f),
        "BG" to PointF(2660.0f, 631.0f),
        "BR" to PointF(1469.0f, 1577.0f),
        "CA" to PointF(875.0f, 400.0f),
        "CH" to PointF(2390.0f, 564.0f),
        "CL" to PointF(1170.0f, 1951.0f),
        "CO" to PointF(1100.0f, 1339.0f),
        "CR" to PointF(925.0f, 1231.0f),
        "CY" to PointF(2759.0f, 777.0f),
        "CZ" to PointF(2482.0f, 509.0f),
        "DE" to PointF(2420.0f, 495.0f),
        "DK" to PointF(2413.0f, 401.0f),
        "EC" to PointF(1010.0f, 1440.0f),
        "EE" to PointF(2615.0f, 356.0f),
        "EG" to PointF(2742.0f, 863.0f),
        "ES" to PointF(2215.0f, 690.0f),
        "FI" to PointF(2615.0f, 295.0f),
        "FR" to PointF(2310.0f, 567.0f),
        "GB" to PointF(2265.0f, 475.0f),
        "GE" to PointF(2915.0f, 648.0f),
        "GR" to PointF(2600.0f, 720.0f),
        "HK" to PointF(4033.0f, 999.0f),
        "HR" to PointF(2495.0f, 608.0f),
        "HU" to PointF(2550.0f, 558.0f),
        "ID" to PointF(4159.0f, 1481.0f),
        "IE" to PointF(2176.0f, 458.0f),
        "IL" to PointF(2793.0f, 830.0f),
        "IN" to PointF(3483.0f, 1071.0f),
        "IS" to PointF(2080.0f, 260.0f),
        "IT" to PointF(2456.0f, 647.0f),
        "JP" to PointF(4330.0f, 755.0f),
        "KH" to PointF(3911.0f, 1194.0f),
        "KR" to PointF(4171.0f, 743.0f),
        "LT" to PointF(2604.0f, 420.0f),
        "LU" to PointF(2363.0f, 513.0f),
        "LV" to PointF(2612.0f, 388.0f),
        "MA" to PointF(2145.0f, 860.0f),
        "MD" to PointF(2679.0f, 561.0f),
        "MK" to PointF(2585.0f, 657.0f),
        "MM" to PointF(3755.0f, 1032.0f),
        "MT" to PointF(2483.0f, 765.0f),
        "MX" to PointF(667.0f, 976.0f),
        "MY" to PointF(3878.0f, 1335.0f),
        "NG" to PointF(2385.0f, 1235.0f),
        "NL" to PointF(2355.0f, 466.0f),
        "NO" to PointF(2411.0f, 311.0f),
        "NZ" to PointF(4760.0f, 2171.0f),
        "PE" to PointF(1056.0f, 1589.0f),
        "PH" to PointF(4159.0f, 1135.0f),
        "PK" to PointF(3330.0f, 860.0f),
        "PL" to PointF(2554.0f, 472.0f),
        "PR" to PointF(1216.0f, 1076.0f),
        "PS" to PointF(2798.0f, 828.0f),
        "PT" to PointF(2148.0f, 688.0f),
        "RO" to PointF(2636.0f, 583.0f),
        "RS" to PointF(2569.0f, 607.0f),
        "RU" to PointF(2833.0f, 366.0f),
        "SE" to PointF(2485.0f, 300.0f),
        "SG" to PointF(3905.0f, 1379.0f),
        "SI" to PointF(2481.0f, 578.0f),
        "SK" to PointF(2552.0f, 527.0f),
        "TH" to PointF(3848.0f, 1128.0f),
        "TR" to PointF(2779.0f, 696.0f),
        "TW" to PointF(4135.0f, 975.0f),
        "UA" to PointF(2715.0f, 517.0f),
        "UK" to PointF(2265.0f, 475.0f),
        "US" to PointF(760.0f, 700.0f),
        "VN" to PointF(3961.0f, 1144.0f),
        "ZA" to PointF(2629.0f, 1950.0f)
    )
}

// --- Native MapView Implementation ---

data class PinInfo(val pos: MapRegion, val highlight: CountryHighlight)

private val FUZZY_BORDER_COUNTRIES = setOf("India")

@SuppressLint("ClickableViewAccessibility")
class MapView constructor(
    context: Context
) : View(context) {

    // When and what highlight stage started.
    private var renderTimeInfo: Pair<Long, CountryHighlight?>? = null
    private var currentRenderData: RenderData? = null
    private var targetRenderData: RenderData? = null
    private var renderedMap: RenderedMap? = null

    data class RenderData(
        val region: MapRegion,
        val pins: List<PinInfo>,
        val stage: CountryHighlight?,
        val id: Long // id will link rendered map to region and pins
    )

    // Pre-allocated structs to avoid allocations in onDraw
    private var viewRect = RectF(0f, 0f, 1f, 1f)
    private val drawRect = Rect()
    private val pinInterpolator = DecelerateInterpolator(1.5f)

    private val outerPinBitmapDisconnected by lazy { BitmapFactory.decodeResource(resources, R.drawable.map_pin_outer_disconnected) }
    private val outerPinBitmapProtected by lazy { BitmapFactory.decodeResource(resources, R.drawable.map_pin_outer_protected) }
    private val innerPinPaintOutside = Paint().apply { color = Color.WHITE; isAntiAlias = true }

    private lateinit var mapRenderer: TvMapRenderer
    private lateinit var elapsedClockMs: () -> Long
    private lateinit var pinColorPaints: Map<CountryHighlight, Paint>
    private var animate: Boolean = true

    fun init(
        config: MapRendererConfig,
        pinColorConfig: Map<CountryHighlight, Int>,
        fadeInDurationMs: Long,
        elapsedClockMs: () -> Long,
        scope: CoroutineScope
    ) {
        this.elapsedClockMs = elapsedClockMs
        this.pinColorPaints = pinColorConfig.mapValues { Paint().apply { color = it.value; isAntiAlias = true } }
        alpha = 0f
        animate = Settings.Global.getFloat(
            context.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE, 1f
        ) != 0f
        setLayerType(LAYER_TYPE_SOFTWARE, null)
        mapRenderer = TvMapRenderer(
            context,
            scope,
            config,
            FUZZY_BORDER_COUNTRIES
        ) { map, id ->
            targetRenderData?.let { renderData ->
                if (id == renderData.id) {
                    if (renderedMap == null) {
                        animate()
                            .alpha(1f)
                            .duration = fadeInDurationMs
                    }
                    renderedMap = map
                    renderTimeInfo = Pair(elapsedClockMs(), renderData.stage)
                    currentRenderData = targetRenderData

                    invalidate()
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.TRANSPARENT)
        if (width == 0 || height == 0)
            return

        val renderData = currentRenderData ?: return
        val region = renderData.region
        renderedMap?.let { renderedMap ->
            if (region.w == 0f || renderedMap.region.isEmpty)
                return

            val regionRect = region.toRectF()
            val src = regionRect
                .and(renderedMap.region)
                .inCoordsOf(renderedMap.region)
                .scale(renderedMap.bitmap.width.toFloat(), renderedMap.bitmap.height.toFloat())

            val dstHeight = width.toFloat() * src.height() / src.width()
            val dstTop = (height - dstHeight) / 2
            viewRect.set(0f, dstTop, width.toFloat(), dstTop + dstHeight)
            src.round(drawRect)
            canvas.drawBitmap(renderedMap.bitmap, drawRect, viewRect, null)

            canvas.drawPins(regionRect, renderData.pins)
        }
    }

    private fun innerPinRadius(elapsedS: Float, stage: CountryHighlight?): Float? {
        if (!animate || stage == CountryHighlight.CONNECTED) return INNER_PIN_SIZE

        if (elapsedS < INNER_PIN_START_DELAY_S) return null
        val stageS = elapsedS - INNER_PIN_START_DELAY_S
        return if (stageS < INNER_PIN_SHOW_DURATION_S)
            INNER_PIN_SIZE * pinInterpolator.getInterpolation(stageS / INNER_PIN_SHOW_DURATION_S)
        else
            INNER_PIN_SIZE
    }

    private fun outerPinRadius(elapsedS: Float, stage: CountryHighlight?): Float? {
        if (!animate) return OUTER_PIN_FULL_SIZE

        val startDelay = if (stage == CountryHighlight.CONNECTED) 0f else OUTER_PIN_START_DELAY_S
        if (elapsedS < startDelay) return null
        val stageS = elapsedS - startDelay
        return if (stageS < OUTER_PIN_SHOW_DURATION_S) {
            // Showing
            OUTER_PIN_FULL_SIZE * pinInterpolator.getInterpolation(stageS / OUTER_PIN_SHOW_DURATION_S)
        } else {
            // Pulsing
            val pulseStageS = (stageS - OUTER_PIN_SHOW_DURATION_S) % OUTER_PIN_PULSE_DURATION_S
            val halfPulse = OUTER_PIN_PULSE_DURATION_S / 2
            val size = if (pulseStageS > halfPulse) {
                // Growing phase
                pinInterpolator.getInterpolation((pulseStageS - halfPulse) / halfPulse)
            } else {
                // Shrinking phase
                1f - pinInterpolator.getInterpolation(pulseStageS / halfPulse)
            }
            val diff = OUTER_PIN_FULL_SIZE - OUTER_PIN_SMALL_SIZE
            OUTER_PIN_SMALL_SIZE + diff * size
        }
    }

    private fun Canvas.drawPins(regionRect: RectF, pins: List<PinInfo>) {
        renderTimeInfo?.let { timeInfo ->
            val animationElapsedS = (elapsedClockMs() - timeInfo.first) / 1000f

            for (pin in pins) {
                val pinInViewCoord = pin.pos.toRectF()
                    .inCoordsOf(regionRect)
                    .scale(width.toFloat(), height.toFloat())

                val outerPinBitmap = when (pin.highlight) {
                    CountryHighlight.SELECTED -> outerPinBitmapDisconnected
                    CountryHighlight.CONNECTED -> outerPinBitmapProtected
                    CountryHighlight.CONNECTING -> null // No outer bitmap when connecting
                }
                if (outerPinBitmap != null) {
                    outerPinRadius(animationElapsedS, timeInfo.second)?.let { sizePx ->
                        val left = pinInViewCoord.centerX() - sizePx / 2f
                        val top = pinInViewCoord.centerY() - sizePx / 2f
                        val right = left + sizePx
                        val bottom = top + sizePx
                        drawBitmap(
                            outerPinBitmap,
                            null,
                            RectF(left, top, right, bottom),
                            null
                        )
                    }
                }
                innerPinRadius(animationElapsedS, timeInfo.second)?.let { radius ->
                    pinColorPaints[pin.highlight]?.let { innerPaint ->
                        drawCircle(pinInViewCoord.centerX(), pinInViewCoord.centerY(), radius, innerPinPaintOutside)
                        drawCircle(pinInViewCoord.centerX(), pinInViewCoord.centerY(), radius / 2, innerPaint)
                    }
                }
            }
            // Keep animating pins
            if (pins.isNotEmpty() && animate)
                invalidate()
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        super.onLayout(changed, l, t, r, b)
        var w = r - l
        var h = b - t

        if (w > 0 && h > 0) {
            // Limit bitmap size to avoid OOM on devices with very high res
            if (w * h > BITMAP_MAX_PIXELS) {
                val normalH = h.toFloat() / w
                w = sqrt(BITMAP_MAX_PIXELS / normalH).roundToInt()
                h = (w * normalH).roundToInt()
            }
            val newId = mapRenderer.updateSize(w, h)
            if (newId != null)
                targetRenderData = targetRenderData?.copy(id = newId)
        }
    }

    // Crops to show given region without animation putts it in the center of the viewport
    // (with given bias). Will not keep resulting region in map bounds (will add padding to keep
    // focused region in the center).
    fun focusRegionInCenter(
        mainScope: CoroutineScope,
        focusRegion: MapRegion,
        newHighlights: List<CountryHighlightInfo>?,
        newPins: List<PinInfo>,
        highlightStage: CountryHighlight?,
        bias: Float,
    ) = mainScope.launch {
        if (width <= 0 || height <= 0) return@launch
        val viewportNormalH = height / width.toFloat()
        val newRegion = focusRegion.expandToAspectRatio(viewportNormalH, bias)
        val id = mapRenderer.update(
            newMapRegion = newRegion,
            newHighlights = newHighlights,
        )
        targetRenderData = RenderData(newRegion, newPins, highlightStage, id)
    }

    companion object {
        const val BITMAP_MAX_PIXELS = 5_000_000 // ~2.7k

        const val INNER_PIN_START_DELAY_S = 0.2f
        const val INNER_PIN_SHOW_DURATION_S = 0.4f

        const val OUTER_PIN_START_DELAY_S = 0.7f
        const val OUTER_PIN_SHOW_DURATION_S = 1f
        const val OUTER_PIN_PULSE_DURATION_S = 3f

        val OUTER_PIN_FULL_SIZE = 48.toPx().toFloat()
        val OUTER_PIN_SMALL_SIZE = 32.toPx().toFloat()
        val INNER_PIN_SIZE = 12.toPx().toFloat()
    }

    private fun RectF.and(other: RectF): RectF {
        return RectF(
            kotlin.math.max(left, other.left),
            kotlin.math.max(top, other.top),
            kotlin.math.min(right, other.right),
            kotlin.math.min(bottom, other.bottom)
        )
    }
}

// --- Compose Wrapper ---

@Composable
fun HomeMap(
    allServers: ImmutableList<LogicalServer>,
    connectedServer: LogicalServer?,
    isConnecting: Boolean,
    modifier: Modifier = Modifier,
    userCountryCode: String? = null,
    isInteractive: Boolean = false,
    onNodeClick: ((String) -> Unit)? = null
) {
    val colors = ProtonNextTheme.colors
    val scope = rememberCoroutineScope()

    val mapConfig = MapRendererConfig(
        background = Color.TRANSPARENT,
        country = colors.shade15.toArgb(),
        border = colors.shade50.toArgb(),
        selected = colors.shade40.toArgb(),
        connecting = colors.shade40.toArgb(),
        connected = colors.shade40.toArgb(),
        borderWidth = 3f,
        zoomIndependentBorderWidth = true
    )

    val pinColorConfig = mapOf(
        CountryHighlight.SELECTED to colors.notificationError.toArgb(),
        CountryHighlight.CONNECTING to colors.brandNorm.toArgb(),
        CountryHighlight.CONNECTED to colors.notificationSuccess.toArgb(),
    )

    val mapState = remember(connectedServer, isConnecting, userCountryCode) {
        val isConnected = connectedServer != null && !isConnecting
        val highlight = when {
            isConnected -> CountryHighlight.CONNECTED
            isConnecting -> CountryHighlight.CONNECTING
            else -> CountryHighlight.SELECTED
        }
        val targetCode = if (isConnected || isConnecting) {
            connectedServer?.exitCountry ?: userCountryCode
        } else {
            userCountryCode
        }
        targetCode?.let { it to highlight }
    }

    AndroidView(
        modifier = modifier.fillMaxSize(),
        factory = { context ->
            MapView(context).apply {
                init(
                    config = mapConfig,
                    pinColorConfig = pinColorConfig,
                    fadeInDurationMs = 250L,
                    elapsedClockMs = { SystemClock.elapsedRealtime() },
                    scope = scope
                )
            }
        },
        update = { mapView ->
            updateMapView(mapView, scope, mapState)
        }
    )
}

private fun updateMapView(
    mapView: MapView,
    scope: CoroutineScope,
    mapHighlight: Pair<String, CountryHighlight>?
) {
    var region = TvMapRenderer.DEFAULT_PORTRAIT_REGION
    var highlights = emptyList<CountryHighlightInfo>()
    var pins = emptyList<PinInfo>()

    mapHighlight?.let { (countryCode, highlight) ->
        val countryName = MapCoordinates.codeToMapCountryName[countryCode]
        val bounds = MapCoordinates.tvMapNameToBounds[countryName]
        if (bounds != null && countryName != null) {
            region = bounds
                .relativePadding(.1f)
                .translateMapCoordinatesToRegion()
                .withPadding(0.015f)

            highlights = listOf(CountryHighlightInfo(countryName, highlight))

            val translatedPinPosition = MapCoordinates.oldMapLocations[countryCode]?.let {
                PointF(it.x, it.y).translateOldToNewMapCoordinates()
            }

            val pinPosition = if (translatedPinPosition != null && bounds.contains(translatedPinPosition))
                translatedPinPosition
            else
                RectF(bounds.centerX(), bounds.centerY(), bounds.centerX(), bounds.centerY())

            pins = listOf(PinInfo(pinPosition.translateMapCoordinatesToRegion(), highlight))
        }
    }
    mapView.focusRegionInCenter(scope, region, highlights, pins, bias = 0.4f, highlightStage = mapHighlight?.second)
}
