package com.pragmaapps.keepintouch.domain.voice

import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.util.NameNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceContactMatcherTest {

    private fun contact(id: Long, name: String) = Contact(
        id = id, lookupKey = "k$id", contactId = id, displayName = name, normalizedName = NameNormalizer.normalize(name),
        primaryNumber = "98765$id", hasWhatsApp = false, whatsappRawContactId = null, learnedAvatarHash = null
    )

    private val contacts = listOf(
        contact(1, "Rahul Sharma"),
        contact(2, "Rahul Verma"),
        contact(3, "Rahil Khan"),
        contact(4, "Amit Sharma"),
        contact(5, "Mummy"),
        contact(6, "Sanjay Kumar")
    )

    private fun names(words: List<String>) = VoiceContactMatcher.match(words, contacts).map { it.displayName }

    @Test
    fun `first name alone lists every exact match but not near misses`() {
        assertEquals(listOf("Rahul Sharma", "Rahul Verma"), names(listOf("Rahul")))
    }

    @Test
    fun `full name narrows to one`() {
        assertEquals(listOf("Rahul Sharma"), names(listOf("Rahul", "Sharma")))
    }

    @Test
    fun `devanagari and misspelled names still match`() {
        assertEquals(listOf("Rahul Sharma", "Rahul Verma"), names(listOf("राहुल")))
        assertEquals(listOf("Rahul Sharma", "Rahul Verma"), names(listOf("Raahul")))
        assertEquals(listOf("Sanjay Kumar"), names(listOf("संजय")))
        assertEquals(listOf("Mummy"), names(listOf("मम्मी")))
    }

    @Test
    fun `surname finds everyone with it`() {
        assertEquals(listOf("Amit Sharma", "Rahul Sharma"), names(listOf("Sharma")))
    }

    @Test
    fun `prefix like the people search`() {
        assertEquals(listOf("Sanjay Kumar"), names(listOf("Sanj")))
    }

    @Test
    fun `unknown name finds nothing`() {
        assertTrue(names(listOf("Deepika")).isEmpty())
    }
}
