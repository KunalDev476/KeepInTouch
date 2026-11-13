package com.pragmaapps.keepintouch.data.permissions

import android.os.Build

/**
 * The phone's maker and brand, read from [Build] - the same values Crashlytics attaches to every
 * report. No permission needed.
 *
 * Call detection through notifications alone is verified on Oppo, OnePlus, Google Pixel and
 * Motorola; every other phone (Samsung, Xiaomi/Redmi/POCO, Vivo, Realme, Nothing, ...) is shown
 * the optional call-screening page. A phone counts as verified if EITHER its maker or its brand
 * is on the list, since some report a parent company as the maker (e.g. Redmi phones say "Xiaomi").
 */
object DeviceVendor {
    /** Normalized prefixes: lowercase, no spaces. "moto" covers "motorola" and "Motorola Mobility". */
    private val VERIFIED_PREFIXES = listOf("oppo", "oneplus", "google", "moto")

    val maker: String get() = Build.MANUFACTURER.orEmpty()
    val brand: String get() = Build.BRAND.orEmpty()

    val isVerified: Boolean
        get() = isVerified(maker) || isVerified(brand)

    internal fun isVerified(value: String): Boolean {
        val normalized = value.lowercase().replace(" ", "")
        return normalized.isNotEmpty() && VERIFIED_PREFIXES.any { normalized.startsWith(it) }
    }
}
