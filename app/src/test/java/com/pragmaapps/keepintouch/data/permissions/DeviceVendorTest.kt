package com.pragmaapps.keepintouch.data.permissions

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceVendorTest {
    @Test
    fun `verified makers and brands as Android reports them`() {
        listOf("OPPO", "oppo", "OnePlus", "ONEPLUS", "Google", "google", "motorola", "Motorola Mobility", "moto")
            .forEach { assertTrue(it, DeviceVendor.isVerified(it)) }
    }

    @Test
    fun `every other maker or brand gets the call-screening page`() {
        listOf("Xiaomi", "Redmi", "POCO", "samsung", "vivo", "realme", "Nothing", "HONOR", "HUAWEI", "Infinix", "Tecno", "")
            .forEach { assertFalse(it, DeviceVendor.isVerified(it)) }
    }
}
