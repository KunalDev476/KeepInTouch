package com.pragmaapps.keepintouch.domain.model

/**
 * A synced address-book contact, enriched with WhatsApp presence detected via a com.whatsapp
 * raw-contact / Data MIME rows - never via any WhatsApp API.
 */
data class Contact(
    val id: Long,
    val lookupKey: String,
    val contactId: Long,
    val displayName: String,
    val normalizedName: String,
    val primaryNumber: String?,
    val hasWhatsApp: Boolean,
    val whatsappRawContactId: Long?,
    /** SHA-256 hash of a past call notification's avatar image. Never seeded from the address-book photo. */
    val learnedAvatarHash: String?
)
