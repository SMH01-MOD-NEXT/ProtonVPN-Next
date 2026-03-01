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

package ru.protonmod.next.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.Picture
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.util.SizeF
import androidx.annotation.DrawableRes
import androidx.appcompat.content.res.AppCompatResources
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.core.graphics.record
import androidx.core.graphics.withSave
import androidx.core.graphics.withScale
import kotlin.math.max

object FlagDimensions {
    val DefaultWidth = 30.dp
    val DefaultHeight = 20.dp
    val DefaultCornerRadius = 4f // в dp, как в оригинале
    val DefaultSize = DpSize(DefaultWidth, DefaultHeight)
}

/**
 * Флаг с кастомной отрисовкой, повторяющей логику Proton VPN.
 * Обеспечивает идеальное масштабирование (Center Crop) и скругление углов для векторов.
 */
@Composable
fun FlagIcon(
    @DrawableRes countryFlag: Int,
    modifier: Modifier = Modifier,
    size: DpSize = FlagDimensions.DefaultSize,
    cornerRadius: Float = FlagDimensions.DefaultCornerRadius
) {
    val context = LocalContext.current
    val density = LocalDensity.current.density

    Spacer(
        modifier = modifier
            .size(size)
            .drawBehind {
                drawWithNativeCanvas(context, density) {
                    drawFlag(countryFlag, size, cornerRadius)
                }
            }
    )
}

private fun DrawScope.drawWithNativeCanvas(
    context: Context,
    density: Float,
    block: FlagDrawScope.() -> Unit
) {
    val scope = FlagDrawScope(
        context = context,
        canvas = drawContext.canvas.nativeCanvas,
        size = SizeF(drawContext.size.width / density, drawContext.size.height / density)
    )
    scope.canvas.withScale(density, density) {
        scope.block()
    }
}

private class FlagDrawScope(
    val context: Context,
    val canvas: Canvas,
    val size: SizeF
) {
    fun getDrawable(@DrawableRes id: Int): Drawable? =
        AppCompatResources.getDrawable(context, id)?.mutate()

    fun drawFlag(@DrawableRes resId: Int, size: DpSize, cornerRadius: Float) {
        val drawable = getDrawable(resId) ?: return
        val dstSize = SizeF(size.width.value, size.height.value)

        // Используем Picture для записи отрисовки вектора с его нативными размерами
        val picture = Picture()
        val srcWidth = drawable.intrinsicWidth
        val srcHeight = drawable.intrinsicHeight
        
        if (srcWidth <= 0 || srcHeight <= 0) return

        picture.record(srcWidth, srcHeight) {
            drawable.setBounds(0, 0, srcWidth, srcHeight)
            drawable.draw(this)
        }

        // Логика Center Crop
        val fillScale = max(dstSize.width / srcWidth, dstSize.height / srcHeight)
        val pictureRect = RectF(0f, 0f, srcWidth * fillScale, srcHeight * fillScale)
        
        // Центрирование
        pictureRect.offset(
            (dstSize.width - pictureRect.width()) / 2f,
            (dstSize.height - pictureRect.height()) / 2f
        )

        canvas.withSave {
            if (cornerRadius > 0f) {
                val path = Path().apply {
                    addRoundRect(
                        0f, 0f, dstSize.width, dstSize.height,
                        cornerRadius, cornerRadius, Path.Direction.CW
                    )
                }
                clipPath(path)
            }
            drawPicture(picture, pictureRect)
        }
    }
}
