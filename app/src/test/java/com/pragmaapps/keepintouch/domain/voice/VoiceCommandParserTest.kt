package com.pragmaapps.keepintouch.domain.voice

import com.pragmaapps.keepintouch.domain.model.Recurrence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek

class VoiceCommandParserTest {

    private fun parse(text: String, bare: Boolean = false) = VoiceCommandParser.parse(Utterance(text), bare)

    @Test
    fun `full first command fills every slot at once`() {
        val p = parse("Rahul ke liye har Saturday aur Sunday shaam 6 baje schedule banao")
        assertEquals(listOf("Rahul"), p.nameWords)
        assertEquals(Recurrence.WEEKLY, p.recurrence)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), p.weekdays)
        assertEquals(listOf(SpokenTime(6, 0, DayPeriod.EVENING)), p.times)
        assertEquals(18 * 60, p.times.single().minutesOfDay())
    }

    @Test
    fun `devanagari command fills the same slots`() {
        val p = parse("राहुल के लिए हर शनिवार और रविवार शाम 6 बजे शेड्यूल बनाओ")
        assertEquals(listOf("राहुल"), p.nameWords)
        assertEquals(Recurrence.WEEKLY, p.recurrence)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), p.weekdays)
        assertEquals(18 * 60, p.times.single().minutesOfDay())
    }

    @Test
    fun `frequency words`() {
        assertEquals(Recurrence.DAILY, parse("roz").recurrence)
        assertEquals(Recurrence.DAILY, parse("हर दिन").recurrence)
        assertEquals(Recurrence.WEEKLY, parse("har hafte").recurrence)
        assertEquals(Recurrence.BIWEEKLY, parse("do hafte mein ek baar").recurrence)
        assertEquals(Recurrence.MONTHLY, parse("har mahine").recurrence)
        assertFalse(parse("do hafte mein ek baar").oneTime)
    }

    @Test
    fun `two days a week without naming them is flagged`() {
        val p = parse("hafte mein do din")
        assertEquals(Recurrence.WEEKLY, p.recurrence)
        assertTrue(p.weekdayCountWithoutNames)
        assertTrue(p.weekdays.isEmpty())
    }

    @Test
    fun `day ranges expand`() {
        assertEquals(DayOfWeek.entries.take(5).toSet(), parse("monday se friday").weekdays)
        assertEquals(setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY), parse("weekend pe").weekdays)
    }

    @Test
    fun `one time and dates`() {
        assertTrue(parse("ek baar").oneTime)
        assertEquals(SpokenDate.Tomorrow, parse("kal").date)
        assertEquals(SpokenDate.DayAfterTomorrow, parse("परसों").date)
        assertEquals(SpokenDate.DayOfMonth(25), parse("25 tarikh ko").date)
        assertEquals(SpokenDate.Weekday(DayOfWeek.FRIDAY), parse("friday ko").date)
    }

    @Test
    fun `time windows and modifiers`() {
        val window = parse("subah 10 se 11 ke beech")
        assertEquals(listOf(600, 660), window.times.map { it.minutesOfDay() })

        assertEquals(18 * 60 + 30, parse("saadhe 6 baje shaam").times.single().minutesOfDay())
        assertEquals(19 * 60 + 30, parse("7:30 pm").times.single().minutesOfDay())
        assertEquals(9 * 60 + 15, parse("सुबह सवा 9 बजे").times.single().minutesOfDay())
    }

    @Test
    fun `a bare hour is ambiguous until morning or evening is said`() {
        val p = parse("6 baje")
        assertTrue(p.times.single().isAmbiguous(null))
        assertEquals(DayPeriod.EVENING, parse("shaam").period)
    }

    @Test
    fun `numbers that are counts or dates are not times`() {
        assertTrue(parse("15 din mein ek baar").times.isEmpty())
        assertTrue(parse("25 tarikh").times.isEmpty())
        assertTrue(parse("6").times.isEmpty())
        assertEquals(1, parse("6", bare = true).times.size)
    }

    @Test
    fun `yes no and cancel`() {
        assertTrue(parse("haan").yes)
        assertTrue(parse("हाँ जी").yes)
        assertTrue(parse("nahi").no)
        assertFalse(parse("theek nahi hai").yes)
        assertTrue(parse("rehne do").cancel)
        assertTrue(parse("koi time nahi").noTime)
    }

    @Test
    fun `ordinals pick from a list`() {
        assertEquals(2, parse("doosra wala").ordinal)
        assertEquals(1, parse("पहला").ordinal)
        assertNull(parse("Rahul Sharma").ordinal)
    }

    @Test
    fun `daily and other frequency variants`() {
        for (text in listOf("rojana", "rozana", "rozaana", "roj", "har roj", "रोजाना", "रोज़ाना", "हर रोज", "pratidin", "daily")) {
            assertEquals(text, Recurrence.DAILY, parse(text).recurrence)
        }
        for (text in listOf("har hafte", "har haftey", "हर हफ्ते", "weekly")) assertEquals(text, Recurrence.WEEKLY, parse(text).recurrence)
        for (text in listOf("har mahine", "har maheena", "हर महीने", "monthly")) assertEquals(text, Recurrence.MONTHLY, parse(text).recurrence)
        for (text in listOf("pakhwada", "15 din mein ek baar", "पंद्रह दिन")) assertEquals(text, Recurrence.BIWEEKLY, parse(text).recurrence)
        for (text in listOf("ek baar", "ek dafa", "एक बार", "sirf ek baar")) assertTrue(text, parse(text).oneTime)
    }

    @Test
    fun `period before the number with ke in between`() {
        assertEquals(18 * 60, parse("shaam ke 6 bje").times.single().minutesOfDay())
        assertEquals(14 * 60, parse("din ke 2 baje").times.single().minutesOfDay())
        assertEquals(10 * 60, parse("din mein 10 baje").times.single().minutesOfDay())
    }

    @Test
    fun `self corrections`() {
        val p = parse("roj 9 bje nhi 6 bje din me nhi shaam me")
        assertEquals(Recurrence.DAILY, p.recurrence)
        assertEquals(1, p.times.size)
        assertEquals(18 * 60, p.times.single().minutesOfDay(p.period))
        assertFalse(p.no)

        assertEquals(Recurrence.WEEKLY, parse("roz nahi har hafte").recurrence)
        assertEquals(setOf(DayOfWeek.SUNDAY), parse("saturday nahi sunday").weekdays)
        assertEquals(SpokenDate.Tomorrow, parse("aaj nahi kal").date)
    }

    @Test
    fun `tag na is not a no`() {
        assertTrue(parse("haan bana do na").yes)
        assertTrue(parse("na").no)
        assertTrue(parse("na yaar").no)
    }

    @Test
    fun `several contact names come out as separate runs`() {
        val u = Utterance("amar sir ke liye shaam ke 6 bje aur mummy ke liye 8 bje dono rojana")
        val p = VoiceCommandParser.parse(u)
        assertEquals(listOf(listOf("amar", "sir"), listOf("mummy")), p.nameRuns.map { run -> run.map { u.words[it] } })
        assertEquals(u.words.indexOf("dono"), p.sharedFrom)
    }

    @Test
    fun `word numbers are not times without a marker`() {
        assertTrue(parse("shaam 6 baje bana do", bare = true).times.size == 1)
    }
}
