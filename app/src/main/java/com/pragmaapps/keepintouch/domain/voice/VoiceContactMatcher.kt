package com.pragmaapps.keepintouch.domain.voice

import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.util.SearchMatcher

/**
 * Finds the contact(s) a spoken name refers to. Same spirit as the People -> All contacts search
 * (a word that's the start of a name part matches - see [SearchMatcher]), plus sound-alike
 * tolerance for what speech recognition does to Indian names ("Raahul", "राहुल" -> Rahul).
 *
 * Voice-only, and always followed by an explicit confirmation in the conversation - unlike
 * caller -> schedule matching, which stays exact-only (see MatchCallToScheduleUseCase).
 */
object VoiceContactMatcher {

    const val MAX_RESULTS = 8

    private const val EXACT = 3
    private const val PREFIX = 2
    private const val CLOSE = 1

    private data class Scored(val contact: Contact, val matchedWords: Int, val score: Int, val allExact: Boolean)

    /**
     * [nameWords] is what the parser left after removing keywords/filler - see
     * ParsedCommand.nameWords. Returns the best matches, best first: when someone said the full
     * "Rahul Sharma", Rahul Verma drops out; a bare "Rahul" keeps every Rahul.
     */
    fun match(nameWords: List<String>, contacts: List<Contact>): List<Contact> = search(nameWords, contacts).contacts

    /**
     * [exact] = exactly one contact, and every name word it matched on matched exactly (allowing for
     * script/spelling - "राहुल" is an exact "Rahul"). Safe to pick without asking; anything looser
     * (a prefix, a near-miss, several candidates) gets confirmed by the user first.
     */
    data class Result(val contacts: List<Contact>, val exact: Boolean)

    fun search(nameWords: List<String>, contacts: List<Contact>): Result {
        val none = Result(emptyList(), exact = false)
        if (nameWords.isEmpty()) return none
        val spokenKeys = nameWords.map { PhoneticKey.of(it) }.filter { it.isNotEmpty() }
        if (spokenKeys.isEmpty()) return none
        val spokenLatin = Devanagari.toLatin(nameWords.joinToString(" "))

        val scored = contacts.mapNotNull { contact -> score(contact, spokenKeys, spokenLatin) }
        if (scored.isEmpty()) return none

        val bestCount = scored.maxOf { it.matchedWords }
        val top = scored.filter { it.matchedWords == bestCount }
        val bestScore = top.maxOf { it.score }
        // Once something matched exactly, near-misses are noise ("Rahul" shouldn't also offer "Rahil").
        val kept = if (top.any { it.allExact }) top.filter { it.score == bestScore } else top
        val sorted = kept.sortedWith(compareByDescending<Scored> { it.score }.thenBy { it.contact.displayName.lowercase() })
            .take(MAX_RESULTS)
        return Result(sorted.map { it.contact }, exact = sorted.size == 1 && sorted.single().allExact)
    }

    private fun score(contact: Contact, spokenKeys: List<String>, spokenLatin: String): Scored? {
        val nameKeys = PhoneticKey.tokenize(contact.displayName).map { PhoneticKey.of(it) }.filter { it.isNotEmpty() }
        if (nameKeys.isEmpty()) return null

        val unusedSpoken = spokenKeys.toMutableList()
        var matched = 0
        var total = 0
        var allExact = true
        for (nameKey in nameKeys) {
            var bestIndex = -1
            var best = 0
            unusedSpoken.forEachIndexed { index, spoken ->
                val s = when {
                    spoken == nameKey -> EXACT
                    spoken.length >= 3 && nameKey.startsWith(spoken) -> PREFIX
                    PhoneticKey.isClose(spoken, nameKey) -> CLOSE
                    else -> 0
                }
                if (s > best) { best = s; bestIndex = index }
            }
            if (bestIndex >= 0) {
                unusedSpoken.removeAt(bestIndex)
                matched++
                total += best
                if (best != EXACT) allExact = false
            }
        }
        // The literal People-tab rule, so anything the search box would find is never missed.
        if (matched == 0 && SearchMatcher.matches(contact.displayName, spokenLatin)) {
            return Scored(contact, 1, PREFIX, allExact = false)
        }
        if (matched == 0) return null
        return Scored(contact, matched, total, allExact)
    }
}
