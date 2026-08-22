package com.aeibi.design.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Immutable
data class Dimensions(
  val borderThin: Dp = 1.dp,
  val iconLarge: Dp = 36.dp,
  val projectListIcon: Dp = 84.dp,
  val projectPickerIcon: Dp = 96.dp,
)

internal val LocalDimensions = staticCompositionLocalOf { Dimensions() }

val MaterialTheme.dimensions: Dimensions
  @Composable
  @ReadOnlyComposable
  get() = LocalDimensions.current
