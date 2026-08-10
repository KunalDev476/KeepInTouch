package com.pragmaapps.keepintouch.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 16dp is the app's default card radius; 24dp is reserved for full-screen sheets (e.g. Create Schedule). */
val KeepInTouchShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

/** Radius used explicitly by bottom sheets (top corners only), since M3's ModalBottomSheet default isn't theme-driven. */
val SheetTopCornerShape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
