package com.pragmaapps.keepintouch.ui.theme

import androidx.compose.ui.graphics.Color

// Brand fallback palette - used when dynamic color is off or unavailable for this user.
// A warm teal-green, deliberately not a cold cyan/blue: this is a calm daily-habit app, not a
// utility tool, and the palette should read as warm rather than clinical.

val BrandPrimaryLight = Color(0xFF1F7A6C)
val BrandOnPrimaryLight = Color(0xFFFFFFFF)
val BrandPrimaryContainerLight = Color(0xFFB7ECDD)
val BrandOnPrimaryContainerLight = Color(0xFF00201A)
val BrandSecondaryLight = Color(0xFF4D6360)
val BrandOnSecondaryLight = Color(0xFFFFFFFF)
val BrandSecondaryContainerLight = Color(0xFFCFE9E3)
val BrandBackgroundLight = Color(0xFFFBF7F1)
val BrandOnBackgroundLight = Color(0xFF21201C)
val BrandSurfaceLight = Color(0xFFFBF7F1)
val BrandOnSurfaceLight = Color(0xFF21201C)
val BrandSurfaceVariantLight = Color(0xFFECE6DC)
val BrandOnSurfaceVariantLight = Color(0xFF4C4A42)
val BrandOutlineLight = Color(0xFF7D7A70)
val BrandErrorLight = Color(0xFFBA1A1A)
val BrandOnErrorLight = Color(0xFFFFFFFF)
val BrandErrorContainerLight = Color(0xFFFFDAD6)

val BrandPrimaryDark = Color(0xFF8FD9C6)
val BrandOnPrimaryDark = Color(0xFF003731)
val BrandPrimaryContainerDark = Color(0xFF00504A)
val BrandOnPrimaryContainerDark = Color(0xFFB7ECDD)
val BrandSecondaryDark = Color(0xFFB3CCC7)
val BrandOnSecondaryDark = Color(0xFF1E3532)
val BrandSecondaryContainerDark = Color(0xFF354B48)
val BrandBackgroundDark = Color(0xFF1B1917)
val BrandOnBackgroundDark = Color(0xFFEAE6E0)
val BrandSurfaceDark = Color(0xFF1B1917)
val BrandOnSurfaceDark = Color(0xFFEAE6E0)
val BrandSurfaceVariantDark = Color(0xFF3F3D36)
val BrandOnSurfaceVariantDark = Color(0xFFCFC9BC)
val BrandOutlineDark = Color(0xFF999488)
val BrandErrorDark = Color(0xFFFFB4AB)
val BrandOnErrorDark = Color(0xFF690005)
val BrandErrorContainerDark = Color(0xFF93000A)

// Semantic colors - not part of the dynamic/brand palette, always these regardless of theming.
// "Success green" is reserved exclusively for the detected-call check; amber is reserved for streaks.
val SuccessGreenLight = Color(0xFF2E7D32)
val SuccessGreenDark = Color(0xFF7EDB86)
val StreakAmberLight = Color(0xFFA85E1D)
val StreakAmberDark = Color(0xFFE6A25B)
