package com.aeibi.design.theme

import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.core.graphics.drawable.toDrawable
import kotlin.math.roundToInt

/** A Compose shape that matches the current device's adaptive app icon mask. */
object SystemAppIconShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val drawable =
            AdaptiveIconDrawable(
                Color.BLACK.toDrawable(),
                Color.TRANSPARENT.toDrawable()
            ).apply {
                setBounds(
                    0,
                    0,
                    size.width.roundToInt(),
                    size.height.roundToInt()
                )
            }

        return Outline.Generic(drawable.iconMask.asComposePath())
    }
}

@Composable
fun systemAppIconShape(): Shape = if (LocalInspectionMode.current) {
    RoundedCornerShape(percent = 22)
} else {
    SystemAppIconShape
}
