package com.pragmaapps.keepintouch.domain.voice

import com.pragmaapps.keepintouch.domain.model.Contact
import com.pragmaapps.keepintouch.domain.model.Recurrence
import com.pragmaapps.keepintouch.domain.model.Schedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.random.Random

/** One schedule the conversation wants created - one per spoken weekday for "Saturday aur Sunday". */
data class ScheduleRequest(
    val contact: Contact,
    val recurrence: Recurrence,
    val nextDueDate: Long,
    val preferredTimeMinutes: Int?,
    val comfortableTimeEndMinutes: Int?
)

enum class SaveOutcome { CREATED, NAME_COLLISION, FAILED }

/** What the voice flow needs from the rest of the app - an interface so the flow stays unit-testable. */
interface VoiceScheduleBackend {
    suspend fun contacts(): List<Contact>
    suspend fun activeSchedulesFor(contact: Contact): List<Schedule>
    suspend fun save(request: ScheduleRequest): SaveOutcome
    /** True when a timed schedule's reminder couldn't actually be shown (POST_NOTIFICATIONS missing). */
    fun notificationsBlocked(): Boolean

    /** Whether the one-time "what should I call you?" introduction already happened. */
    fun introDone(): Boolean = true
    fun markIntroDone() {}
    fun honorific(): Honorific = Honorific.NEUTRAL
    fun setHonorific(honorific: Honorific) {}
}

/** Read-only description of the schedule being built, for the summary and success cards. */
data class ScheduleSummary(
    val contactName: String,
    val hasWhatsApp: Boolean,
    val recurrence: Recurrence,
    val weekdays: List<DayOfWeek>,
    val oneTimeDate: Long?,
    val startMinutes: Int?,
    val endMinutes: Int?,
    val scheduleCount: Int
)

sealed class ChatItem {
    abstract val id: Long

    data class UserText(override val id: Long, val text: String) : ChatItem()
    data class AssistantText(override val id: Long, val prompt: VoicePrompt) : ChatItem()
    data class ContactConfirm(override val id: Long, val contact: Contact) : ChatItem()
    /** An exact name match, picked without asking - shown with a "Wrong contact?" way out. */
    data class ContactSelected(override val id: Long, val contact: Contact) : ChatItem()
    data class ContactChoices(override val id: Long, val contacts: List<Contact>) : ChatItem()
    data class Summary(override val id: Long, val summary: ScheduleSummary) : ChatItem()
    data class Success(override val id: Long, val summary: ScheduleSummary) : ChatItem()
}

/** A tap-able fallback answer. [spokenEquivalent] is fed through the same parser as speech. */
data class QuickReply(val label: String, val spokenEquivalent: String)

/** What the bottom of the screen offers besides the mic. */
sealed class InputOptions {
    data object None : InputOptions()
    data object YesNo : InputOptions()
    data class Chips(val options: List<QuickReply>) : InputOptions()
    data object ContactSearch : InputOptions()
    data object GrantContacts : InputOptions()
    data object GrantNotifications : InputOptions()
}

/**
 * The flow's answer to one turn: lines to play in order, then whether to listen for a reply.
 * [endSession] = stop the mic entirely afterwards (timeout, mic/contacts problem) rather than
 * going back to waiting for the wake word.
 */
data class Reply(val prompts: List<VoicePrompt>, val listen: Boolean, val endSession: Boolean = false)

/**
 * The voice scheduling conversation as a plain state machine - no Android, no audio. The
 * ViewModel feeds it what the user said/tapped and plays back the [Reply]; everything about
 * which question comes next lives here.
 *
 * Slots are filled opportunistically: "Jarvis, Rahul ke liye har Saturday aur Sunday shaam 6 baje
 * schedule banao" fills contact, days and time in one go, and the flow only asks for what's still
 * missing. A single exact name match is picked straight away (with a "wrong contact" way out);
 * anything less certain is confirmed first.
 *
 * Several contacts in one breath ("Amar sir ke liye shaam 6 baje aur Mummy ke liye 8 baje, dono
 * rozana") are queued and handled one after another - "dono"/"sab" details apply to each.
 */
class VoiceConversation(
    private val backend: VoiceScheduleBackend,
    private val now: () -> Long = System::currentTimeMillis,
    private val zone: () -> ZoneId = ZoneId::systemDefault,
    private val random: Random = Random.Default
) {
    enum class Step {
        WAIT_WAKE, ASK_NAME, ASK_HONORIFIC, ASK_CONTACT, PICK_CONTACT, CONFIRM_CONTACT, CONFIRM_EXTRA, ASK_TYPE,
        ASK_FREQUENCY, ASK_WEEKDAYS, ASK_DATE, ASK_TIME, ASK_AM_PM, CONFIRM_SUMMARY, EDIT_SUMMARY, ASK_ANOTHER
    }

    private data class Draft(
        val contact: Contact? = null,
        val oneTime: Boolean? = null,
        val recurrence: Recurrence? = null,
        val weekdays: Set<DayOfWeek> = emptySet(),
        val daysUnclear: Boolean = false,
        val date: LocalDate? = null,
        val timeDecided: Boolean = false,
        val startMinutes: Int? = null,
        val endMinutes: Int? = null,
        /** Times said without morning/evening - waiting on the "subah ya shaam?" answer. */
        val pendingTimes: List<SpokenTime> = emptyList()
    )

    var step: Step = Step.WAIT_WAKE
        private set
    var items: List<ChatItem> = emptyList()
        private set
    var input: InputOptions = InputOptions.None
        private set

    private var draft = Draft()
    private var candidates: List<Contact> = emptyList()
    /** The other contacts' parts of a multi-contact command, still to be handled. */
    private val pendingCommands = ArrayDeque<String>()
    /** After "wrong contact", even an exact-looking match gets confirmed. */
    private var forceConfirm = false
    private var contactMisses = 0
    private var noNameTurns = 0
    private var misses = 0
    private var silentTurns = 0
    private var nameMisses = 0
    private var nextId = 0L
    /** True while working through a multi-contact command, for the "all done" line at the end. */
    private var inMultiRun = false
    /** The last question asked, repeated after small talk or a honorific correction. */
    private var lastQuestion: VoicePrompt? = null
    private var lastGreeting: VoicePrompt? = null
    private var stepBeforeHonorific: Step? = null

    val inConversation: Boolean get() = step != Step.WAIT_WAKE

    // region Entry points

    /** The wake word was heard. [remainder] is anything said after it in the same breath. */
    suspend fun onWake(remainder: String): Reply {
        resetDraft()
        silentTurns = 0
        cachedContacts = backend.contacts()
        if (cachedContacts.isEmpty()) {
            input = InputOptions.GrantContacts
            return say(VoicePrompt.CONTACTS_PERMISSION_REQUIRED, listen = false).copy(endSession = true)
        }
        input = InputOptions.None
        // A command in the same breath goes straight to work - no greeting, no introduction yet.
        if (remainder.isNotBlank()) {
            step = Step.ASK_CONTACT
            return onSpeech(listOf(remainder))
        }
        if (!backend.introDone()) {
            step = Step.ASK_NAME
            nameMisses = 0
            return say(VoicePrompt.INTRO_ASK_NAME)
        }
        step = Step.ASK_CONTACT
        return say(greeting())
    }

    /** A random greeting for how this user likes to be addressed, never the same one twice in a row. */
    private fun greeting(): VoicePrompt {
        val pool = when (backend.honorific()) {
            Honorific.SIR -> listOf(VoicePrompt.WAKE_GREET_SIR_1, VoicePrompt.WAKE_GREET_SIR_2, VoicePrompt.WAKE_GREET_SIR_3)
            Honorific.MAAM -> listOf(VoicePrompt.WAKE_GREET_MAAM_1, VoicePrompt.WAKE_GREET_MAAM_2, VoicePrompt.WAKE_GREET_MAAM_3)
            Honorific.NEUTRAL -> listOf(VoicePrompt.WAKE_GREET_NEUTRAL_1, VoicePrompt.WAKE_GREET_NEUTRAL_2, VoicePrompt.WAKE_GREET_NEUTRAL_3)
        }
        return pick(pool.filter { it != lastGreeting }.ifEmpty { pool }).also { lastGreeting = it }
    }

    private fun pick(options: List<VoicePrompt>): VoicePrompt = options[random.nextInt(options.size)]

    /** Contacts became readable (permission granted / synced) - pick up where [onWake] left off. */
    suspend fun onContactsAvailable(): Reply = onWake("")

    /**
     * [hypotheses] = the recognizer's alternatives, most likely first. [shownAs] replaces what the
     * chat bubble shows - a tapped "Every 2 weeks" chip is parsed as "biweekly" but shown as tapped.
     */
    suspend fun onSpeech(hypotheses: List<String>, shownAs: String? = null): Reply {
        val usable = hypotheses.map { it.trim() }.filter { it.isNotEmpty() }
        if (usable.isEmpty()) return onSilence()
        silentTurns = 0
        val utterances = usable.map { Utterance(it) }
        val chosen = pickHypothesis(utterances)
        addItem { ChatItem.UserText(it, shownAs ?: chosen.raw) }
        return handle(chosen)
    }

    /** Recognizer heard nothing during a turn that expected an answer. */
    fun onSilence(): Reply {
        silentTurns++
        if (silentTurns >= 2) {
            silentTurns = 0
            endConversation()
            return say(VoicePrompt.LISTENING_TIMEOUT, listen = false).copy(endSession = true)
        }
        return say(VoicePrompt.NO_SPEECH)
    }

    /** The wake word never came within the listening window. */
    fun onWakeTimeout(): Reply {
        endConversation()
        return say(VoicePrompt.LISTENING_TIMEOUT, listen = false).copy(endSession = true)
    }

    suspend fun onQuickReply(reply: QuickReply): Reply = onSpeech(listOf(reply.spokenEquivalent), shownAs = reply.label)

    suspend fun onYesNoTapped(yes: Boolean): Reply {
        addItem { ChatItem.UserText(it, if (yes) "Yes" else "No") }
        return handleYesNo(yes)
    }

    suspend fun onContactTapped(contact: Contact): Reply {
        addItem { ChatItem.UserText(it, contact.displayName) }
        return chooseContact(contact)
    }

    /** The "Wrong contact?" link on an auto-picked contact. */
    suspend fun onWrongContactTapped(): Reply {
        addItem { ChatItem.UserText(it, "Wrong contact") }
        return wrongContact(emptyList())
    }

    /** Typed into the search box offered after two failed attempts at hearing a name. */
    suspend fun onContactTyped(query: String): Reply {
        if (query.isBlank()) return Reply(emptyList(), listen = true)
        addItem { ChatItem.UserText(it, query.trim()) }
        return searchContacts(PhoneticKey.tokenize(query))
    }

    /** The mic is gone (no permission) - say why, and leave the UI to offer the Settings button. */
    fun onMicUnavailable(permissionDenied: Boolean): Reply {
        endConversation()
        return say(if (permissionDenied) VoicePrompt.MIC_PERMISSION_REQUIRED else VoicePrompt.MIC_ERROR, listen = false)
            .copy(endSession = true)
    }

    // endregion

    private fun pickHypothesis(utterances: List<Utterance>): Utterance {
        val useful = utterances.firstOrNull { u ->
            val parsed = VoiceCommandParser.parse(u, bareNumbersAreTimes = expectsTime())
            when (step) {
                Step.ASK_CONTACT -> parsed.nameWords.isNotEmpty() &&
                    VoiceContactMatcher.match(parsed.nameWords, cachedContacts).isNotEmpty()
                Step.PICK_CONTACT -> parsed.ordinal != null || VoiceContactMatcher.match(parsed.nameWords, candidates).isNotEmpty()
                Step.CONFIRM_CONTACT, Step.CONFIRM_EXTRA, Step.CONFIRM_SUMMARY -> parsed.yes || parsed.no || parsed.hasScheduleDetails
                Step.ASK_ANOTHER -> parsed.yes || parsed.no ||
                    VoiceContactMatcher.match(parsed.nameWords, cachedContacts).isNotEmpty()
                Step.ASK_TYPE -> parsed.oneTime || parsed.repeating
                Step.ASK_FREQUENCY -> parsed.recurrence != null
                Step.ASK_WEEKDAYS -> parsed.weekdays.isNotEmpty()
                Step.ASK_DATE -> parsed.date != null
                Step.ASK_TIME -> parsed.times.isNotEmpty() || parsed.noTime || parsed.no
                Step.ASK_AM_PM -> parsed.period != null || parsed.times.any { it.period != null }
                else -> false
            }
        }
        return useful ?: utterances.first()
    }

    private var cachedContacts: List<Contact> = emptyList()

    /** Whether any schedule saved in this run has a call time - those need notification permission. */
    private var anyTimedSaved = false

    private val CONTACT_STEPS = setOf(Step.WAIT_WAKE, Step.ASK_CONTACT, Step.PICK_CONTACT, Step.CONFIRM_CONTACT, Step.ASK_ANOTHER)
    private val YES_NO_STEPS = setOf(Step.CONFIRM_CONTACT, Step.CONFIRM_EXTRA, Step.CONFIRM_SUMMARY, Step.ASK_ANOTHER)

    /** Lines that ask something - the one repeated after a small-talk detour. */
    private val QUESTIONS = setOf(
        VoicePrompt.CONTACT_NO_NAME, VoicePrompt.CONTACT_CONFIRM, VoicePrompt.CONTACT_MULTIPLE,
        VoicePrompt.CONTACT_NOT_FOUND, VoicePrompt.CONTACT_NOT_FOUND_2, VoicePrompt.CONTACT_HAS_SCHEDULE,
        VoicePrompt.WRONG_CONTACT_ACK, VoicePrompt.ASK_TYPE, VoicePrompt.ASK_FREQUENCY, VoicePrompt.ASK_WEEKDAYS,
        VoicePrompt.WEEKDAYS_UNCLEAR, VoicePrompt.ASK_DATE, VoicePrompt.DATE_IN_PAST, VoicePrompt.ASK_TIME,
        VoicePrompt.TIME_AM_PM, VoicePrompt.TIME_IN_PAST, VoicePrompt.TIME_WINDOW_INVALID, VoicePrompt.SUMMARY_CONFIRM,
        VoicePrompt.EDIT_SUMMARY, VoicePrompt.ASK_ANOTHER
    )
    private val HOW_ARE_YOU = listOf(
        VoicePrompt.SMALLTALK_HOW_ARE_YOU_1, VoicePrompt.SMALLTALK_HOW_ARE_YOU_2, VoicePrompt.SMALLTALK_HOW_ARE_YOU_3,
        VoicePrompt.SMALLTALK_HOW_ARE_YOU_4, VoicePrompt.SMALLTALK_HOW_ARE_YOU_5, VoicePrompt.SMALLTALK_HOW_ARE_YOU_6
    )
    private val WHO_ARE_YOU = listOf(VoicePrompt.SMALLTALK_WHO_ARE_YOU_1, VoicePrompt.SMALLTALK_WHO_ARE_YOU_2)
    private val HELP = listOf(VoicePrompt.SMALLTALK_HELP_1, VoicePrompt.SMALLTALK_HELP_2)
    private val PRAISE = listOf(VoicePrompt.SMALLTALK_PRAISE_1, VoicePrompt.SMALLTALK_PRAISE_2)
    private val OFF_TOPIC = listOf(
        VoicePrompt.SMALLTALK_OFF_TOPIC_1, VoicePrompt.SMALLTALK_OFF_TOPIC_2, VoicePrompt.SMALLTALK_OFF_TOPIC_3
    )

    private fun expectsTime() = step == Step.ASK_TIME || step == Step.ASK_AM_PM || step == Step.EDIT_SUMMARY

    private suspend fun handle(utterance: Utterance): Reply {
        val parsed = VoiceCommandParser.parse(utterance, bareNumbersAreTimes = expectsTime())
        when (step) {
            Step.ASK_NAME -> return handleName(utterance, parsed)
            Step.ASK_HONORIFIC -> return handleHonorificAnswer(parsed)
            else -> Unit
        }
        if (parsed.cancel) {
            endConversation()
            return say(VoicePrompt.CANCELLED, listen = false)
        }
        parsed.honorificRequest?.let { return handleHonorificRequest(it) }
        if (isSmallTalk(parsed)) return handleSmallTalk(parsed.smallTalk!!)
        // "Ye galat contact hai, maine Amit bola tha" - at any point after a contact was picked.
        if (parsed.wrongContact && draft.contact != null && step !in CONTACT_STEPS) {
            return wrongContact(parsed.nameWords)
        }

        return when (step) {
            // Handled above, before cancel/small talk.
            Step.WAIT_WAKE, Step.ASK_NAME, Step.ASK_HONORIFIC -> Reply(emptyList(), listen = false)
            Step.ASK_CONTACT -> startCommand(utterance, parsed)
            Step.PICK_CONTACT -> {
                val byOrdinal = parsed.ordinal?.let { candidates.getOrNull(it - 1) }
                val byName = VoiceContactMatcher.match(parsed.nameWords, candidates)
                when {
                    byOrdinal != null -> { mergeSlots(parsed); chooseContact(byOrdinal) }
                    byName.size == 1 -> { mergeSlots(parsed); chooseContact(byName.single()) }
                    byName.size > 1 -> showChoices(byName)
                    parsed.no -> retryFromScratch()
                    parsed.nameWords.isNotEmpty() -> searchContacts(parsed.nameWords)
                    else -> miss()
                }
            }
            Step.CONFIRM_CONTACT -> when {
                // "Nahi, Rahul Verma" - the right name came with the no.
                (parsed.no || parsed.wrongContact) && parsed.nameWords.isNotEmpty() -> wrongContact(parsed.nameWords)
                parsed.no || parsed.wrongContact -> handleYesNo(false)
                // "Haan, shaam ke 6 baje rojana" - keep everything said after the yes.
                parsed.yes -> { mergeSlots(parsed); handleYesNo(true) }
                parsed.hasScheduleDetails -> { mergeSlots(parsed); handleYesNo(true) }
                else -> say(VoicePrompt.YES_NO_UNCLEAR)
            }
            Step.CONFIRM_EXTRA -> when {
                parsed.no -> handleYesNo(false)
                parsed.yes || parsed.hasScheduleDetails -> { mergeSlots(parsed); handleYesNo(true) }
                else -> say(VoicePrompt.YES_NO_UNCLEAR)
            }
            Step.ASK_ANOTHER -> when {
                // "Haan, Amar sir ke liye roz shaam 6 baje" - straight into the next schedule.
                VoiceContactMatcher.match(parsed.nameWords, cachedContacts).isNotEmpty() -> {
                    resetDraft()
                    startCommand(utterance, parsed)
                }
                parsed.no -> handleYesNo(false)
                parsed.yes -> handleYesNo(true)
                else -> say(VoicePrompt.YES_NO_UNCLEAR)
            }
            Step.CONFIRM_SUMMARY -> when {
                // A direct correction ("nahi, 7 baje" / "haan par 7 baje") skips "what do you want to change?".
                parsed.hasScheduleDetails -> applyEdit(parsed)
                parsed.no -> handleYesNo(false)
                parsed.yes -> handleYesNo(true)
                else -> say(VoicePrompt.YES_NO_UNCLEAR)
            }
            Step.EDIT_SUMMARY -> applyEdit(parsed)
            Step.ASK_TYPE -> if (parsed.oneTime || parsed.repeating || parsed.date != null) {
                mergeSlots(parsed)
                advance()
            } else miss()
            Step.ASK_FREQUENCY -> if (parsed.recurrence != null || parsed.weekdays.isNotEmpty() || parsed.oneTime) {
                mergeSlots(parsed)
                advance()
            } else miss()
            Step.ASK_WEEKDAYS -> when {
                parsed.weekdays.isNotEmpty() -> { mergeSlots(parsed); advance() }
                parsed.weekdayCountWithoutNames -> say(VoicePrompt.WEEKDAYS_UNCLEAR)
                else -> miss()
            }
            Step.ASK_DATE -> when {
                parsed.date != null -> { mergeSlots(parsed); advance() }
                else -> miss()
            }
            Step.ASK_TIME -> when {
                parsed.times.isNotEmpty() -> applyTimes(parsed.times, parsed.period)
                parsed.noTime || parsed.no -> {
                    draft = draft.copy(timeDecided = true, startMinutes = null, endMinutes = null, pendingTimes = emptyList())
                    advance()
                }
                else -> miss()
            }
            Step.ASK_AM_PM -> {
                val period = parsed.period ?: parsed.times.firstNotNullOfOrNull { it.period }
                if (period != null) applyTimes(draft.pendingTimes, period) else miss()
            }
        }
    }

    private suspend fun handleYesNo(yes: Boolean): Reply = when (step) {
        Step.CONFIRM_CONTACT -> {
            val contact = candidates.singleOrNull()
            if (yes && contact != null) contactConfirmed(contact) else retryFromScratch()
        }
        Step.CONFIRM_EXTRA -> when {
            yes -> advance()
            // Skip just this contact and carry on with the rest of a multi-contact command.
            pendingCommands.isNotEmpty() -> continueWithNextCommand(spoken(listOf(VoicePrompt.CONTACT_SKIPPED)))
            else -> {
                endConversation()
                say(VoicePrompt.CANCELLED, listen = false)
            }
        }
        Step.CONFIRM_SUMMARY -> if (yes) save() else {
            step = Step.EDIT_SUMMARY
            misses = 0
            input = InputOptions.Chips(
                listOf(QuickReply("Contact", "contact"), QuickReply("Days", "din"), QuickReply("Time", "time"))
            )
            say(VoicePrompt.EDIT_SUMMARY)
        }
        Step.ASK_ANOTHER -> if (yes) {
            resetDraft()
            step = Step.ASK_CONTACT
            say(VoicePrompt.WAKE_ACK)
        } else {
            endConversation()
            say(VoicePrompt.GOODBYE, listen = false)
        }
        else -> say(VoicePrompt.NOT_UNDERSTOOD)
    }

    // region Introduction, honorific and small talk

    /**
     * First conversation: "what should I call you?". The answer only picks sir/ma'am/neither (see
     * [HonorificGuesser]) - the name itself isn't kept. A schedule command instead of a name skips
     * the introduction for now and goes straight to work.
     */
    private suspend fun handleName(utterance: Utterance, parsed: ParsedCommand): Reply {
        val stated = parsed.honorificRequest.toHonorific() ?: parsed.honorificWord
        return when {
            parsed.hasScheduleDetails -> {
                step = Step.ASK_CONTACT
                startCommand(utterance, parsed)
            }
            parsed.skipName || parsed.cancel || parsed.no -> finishIntro(null)
            stated != null -> finishIntro(stated)
            parsed.nameWords.isNotEmpty() -> finishIntro(HonorificGuesser.guess(parsed.nameWords))
            ++nameMisses < 2 -> say(VoicePrompt.INTRO_NAME_NOT_CAUGHT)
            else -> finishIntro(null)
        }
    }

    /** [honorific] null = no name given - carry on without sir/ma'am. */
    private fun finishIntro(honorific: Honorific?): Reply {
        backend.markIntroDone()
        honorific?.let { backend.setHonorific(it) }
        step = Step.ASK_CONTACT
        return say(
            when (honorific) {
                Honorific.SIR -> VoicePrompt.INTRO_WELCOME_SIR
                Honorific.MAAM -> VoicePrompt.INTRO_WELCOME_MAAM
                Honorific.NEUTRAL -> VoicePrompt.INTRO_WELCOME_NEUTRAL
                null -> VoicePrompt.INTRO_NAME_SKIPPED
            }
        )
    }

    private fun HonorificRequest?.toHonorific(): Honorific? = when (this) {
        HonorificRequest.SIR -> Honorific.SIR
        HonorificRequest.MAAM -> Honorific.MAAM
        HonorificRequest.NEUTRAL -> Honorific.NEUTRAL
        HonorificRequest.CHANGE, null -> null
    }

    /** "Main madam hoon" / "sir mat bolo" at any point: apologise, remember, carry on where we were. */
    private fun handleHonorificRequest(request: HonorificRequest): Reply {
        val honorific = request.toHonorific()
        if (honorific == null) {
            stepBeforeHonorific = step
            step = Step.ASK_HONORIFIC
            return say(VoicePrompt.HONORIFIC_CHANGE_ACK)
        }
        return applyHonorific(honorific)
    }

    /** The answer to "how would you like to be addressed?" - anything unclear means neither. */
    private fun handleHonorificAnswer(parsed: ParsedCommand): Reply {
        step = stepBeforeHonorific ?: Step.ASK_CONTACT
        stepBeforeHonorific = null
        val honorific = parsed.honorificRequest.toHonorific() ?: parsed.honorificWord ?: Honorific.NEUTRAL
        return applyHonorific(honorific)
    }

    private fun applyHonorific(honorific: Honorific): Reply {
        backend.setHonorific(honorific)
        backend.markIntroDone()
        val fix = when (honorific) {
            Honorific.SIR -> VoicePrompt.HONORIFIC_FIX_SIR
            Honorific.MAAM -> VoicePrompt.HONORIFIC_FIX_MAAM
            Honorific.NEUTRAL -> VoicePrompt.HONORIFIC_FIX_NEUTRAL
        }
        return say(listOf(fix, pendingQuestion()))
    }

    /**
     * Small talk only counts when nothing else was said - "Rahul ko har Sunday call karo" is a
     * command, not "call karo", and a "haan" at a yes/no question is an answer.
     */
    private fun isSmallTalk(parsed: ParsedCommand): Boolean {
        val talk = parsed.smallTalk ?: return false
        if (parsed.hasScheduleDetails || parsed.wrongContact) return false
        if (VoiceContactMatcher.match(parsed.nameWords, cachedContacts).isNotEmpty()) return false
        if (step in YES_NO_STEPS && (parsed.yes || (parsed.no && talk != SmallTalk.REPEAT && talk != SmallTalk.FRUSTRATED))) {
            return false
        }
        return true
    }

    private fun handleSmallTalk(talk: SmallTalk): Reply {
        // The how-are-you / are-you-there clips end with "which contact?", so they only fit before one is picked.
        val beforeContact = step == Step.ASK_CONTACT
        return when (talk) {
            SmallTalk.HOW_ARE_YOU -> if (beforeContact) say(pick(HOW_ARE_YOU)) else repeatQuestion()
            SmallTalk.ARE_YOU_THERE -> if (beforeContact) say(VoicePrompt.SMALLTALK_ARE_YOU_THERE) else repeatQuestion()
            SmallTalk.THANKS -> if (step == Step.ASK_ANOTHER) {
                endConversation()
                say(pick(listOf(VoicePrompt.SMALLTALK_THANKS_1, VoicePrompt.SMALLTALK_THANKS_2)), listen = false)
            } else {
                say(listOf(VoicePrompt.SMALLTALK_THANKS_3, pendingQuestion()))
            }
            SmallTalk.WHO_ARE_YOU -> say(listOf(pick(WHO_ARE_YOU), pendingQuestion()))
            SmallTalk.HELP -> say(listOf(pick(HELP), if (beforeContact) VoicePrompt.EXAMPLE_HINT else pendingQuestion()))
            SmallTalk.PRAISE -> say(listOf(pick(PRAISE), pendingQuestion()))
            SmallTalk.OFF_TOPIC -> say(listOf(pick(OFF_TOPIC), pendingQuestion()))
            SmallTalk.BYE -> {
                endConversation()
                say(pick(listOf(VoicePrompt.SMALLTALK_BYE_1, VoicePrompt.SMALLTALK_BYE_2)), listen = false)
            }
            SmallTalk.REPEAT -> repeatQuestion()
            SmallTalk.CALL_NOW -> say(listOf(VoicePrompt.SMALLTALK_CALL_NOW, pendingQuestion()))
            SmallTalk.SORRY -> say(VoicePrompt.SMALLTALK_USER_SORRY)
            SmallTalk.FRUSTRATED ->
                say(listOf(VoicePrompt.SMALLTALK_FRUSTRATED, if (beforeContact) VoicePrompt.EXAMPLE_HINT else pendingQuestion()))
        }
    }

    private fun repeatQuestion(): Reply = say(listOf(VoicePrompt.SMALLTALK_REPEAT, pendingQuestion()))

    /** What to ask again after a detour - the question that's actually waiting for an answer. */
    private fun pendingQuestion(): VoicePrompt = when (step) {
        Step.ASK_NAME -> VoicePrompt.INTRO_ASK_NAME
        Step.ASK_CONTACT, Step.WAIT_WAKE -> VoicePrompt.CONTACT_NO_NAME
        else -> lastQuestion ?: VoicePrompt.CONTACT_NO_NAME
    }

    // endregion

    // region Contact

    /**
     * A fresh command at the "which contact?" stage. Several contacts in it get split apart: the
     * first is handled now, the rest wait in [pendingCommands].
     */
    private suspend fun startCommand(utterance: Utterance, parsed: ParsedCommand): Reply {
        if (cachedContacts.isEmpty()) cachedContacts = backend.contacts()
        val parts = splitByContact(utterance, parsed)
        if (parts.size <= 1) {
            mergeSlots(parsed)
            return searchContacts(parsed.nameWords)
        }
        parts.drop(1).asReversed().forEach { pendingCommands.addFirst(it) }
        inMultiRun = true
        val lead = spoken(listOf(VoicePrompt.MULTI_DETECTED))
        val reply = processCommand(parts.first())
        return reply.copy(prompts = lead + reply.prompts)
    }

    private suspend fun processCommand(text: String): Reply {
        val parsed = VoiceCommandParser.parse(Utterance(text))
        mergeSlots(parsed)
        return searchContacts(parsed.nameWords)
    }

    /**
     * "Amar sir ke liye shaam ke 6 baje aur Mummy ke liye shaam ke 8 baje, dono rozana" ->
     * ["Amar sir ke liye shaam ke 6 baje aur dono rozana", "Mummy ke liye shaam ke 8 baje dono rozana"].
     * Splits only at name runs that actually match a contact, so leftover words ("honge") never
     * start a phantom schedule. Words before the first name, and from "dono"/"sab" on, go to every part.
     */
    private fun splitByContact(utterance: Utterance, parsed: ParsedCommand): List<String> {
        val runs = parsed.nameRuns.filter { run ->
            VoiceContactMatcher.match(run.map { utterance.words[it] }, cachedContacts).isNotEmpty()
        }
        if (runs.size <= 1) return listOf(utterance.raw)
        val words = utterance.words
        val sharedFrom = parsed.sharedFrom?.takeIf { it > runs.last().first } ?: words.size
        val shared = words.subList(0, runs.first().first) + words.subList(sharedFrom, words.size)
        return runs.mapIndexed { index, run ->
            val end = runs.getOrNull(index + 1)?.first ?: sharedFrom
            (words.subList(run.first, end) + shared).joinToString(" ")
        }
    }

    /** Moves on to the next queued contact, after [lead] lines (e.g. "saved") already said. */
    private suspend fun continueWithNextCommand(lead: List<VoicePrompt>): Reply {
        val next = pendingCommands.removeFirst()
        resetDraft()
        step = Step.ASK_CONTACT
        val announced = lead + spoken(listOf(VoicePrompt.MULTI_NEXT))
        val reply = processCommand(next)
        return reply.copy(prompts = announced + reply.prompts)
    }

    /** The picked contact is wrong - keep what was said about the schedule, find the right person. */
    private suspend fun wrongContact(nameWords: List<String>): Reply {
        forceConfirm = true
        draft = draft.copy(contact = null)
        candidates = emptyList()
        if (nameWords.isEmpty()) {
            step = Step.ASK_CONTACT
            input = InputOptions.None
            return say(VoicePrompt.WRONG_CONTACT_ACK)
        }
        return searchContacts(nameWords)
    }

    private suspend fun searchContacts(nameWords: List<String>): Reply {
        step = Step.ASK_CONTACT
        if (cachedContacts.isEmpty()) cachedContacts = backend.contacts()
        if (nameWords.isEmpty()) {
            input = InputOptions.None
            // Twice without a name - show how a request sounds.
            return if (++noNameTurns >= 2) say(listOf(VoicePrompt.CONTACT_NO_NAME, VoicePrompt.EXAMPLE_HINT))
            else say(VoicePrompt.CONTACT_NO_NAME)
        }
        noNameTurns = 0
        val result = VoiceContactMatcher.search(nameWords, cachedContacts)
        val matches = result.contacts
        return when {
            result.exact && !forceConfirm -> {
                addItem { ChatItem.ContactSelected(it, matches.single()) }
                contactConfirmed(matches.single())
            }
            matches.isEmpty() -> {
                contactMisses++
                if (contactMisses >= 2) {
                    input = InputOptions.ContactSearch
                    say(VoicePrompt.CONTACT_NOT_FOUND_2)
                } else {
                    say(VoicePrompt.CONTACT_NOT_FOUND)
                }
            }
            matches.size == 1 -> {
                candidates = matches
                step = Step.CONFIRM_CONTACT
                input = InputOptions.YesNo
                addItem { ChatItem.ContactConfirm(it, matches.single()) }
                say(VoicePrompt.CONTACT_CONFIRM)
            }
            else -> showChoices(matches)
        }
    }

    private fun showChoices(matches: List<Contact>): Reply {
        candidates = matches
        step = Step.PICK_CONTACT
        input = InputOptions.None
        misses = 0
        addItem { ChatItem.ContactChoices(it, matches) }
        return say(VoicePrompt.CONTACT_MULTIPLE)
    }

    /** Picking from the list (by tap, "doosra" or the full name) is itself the confirmation. */
    private suspend fun chooseContact(contact: Contact): Reply = contactConfirmed(contact)

    private suspend fun contactConfirmed(contact: Contact): Reply {
        draft = draft.copy(contact = contact)
        candidates = listOf(contact)
        // Informational only - calls are matched by name, so a number-less contact still works.
        val lead = spoken(if (contact.primaryNumber == null) listOf(VoicePrompt.CONTACT_NO_PHONE) else emptyList())
        if (backend.activeSchedulesFor(contact).isNotEmpty()) {
            step = Step.CONFIRM_EXTRA
            input = InputOptions.YesNo
            return say(VoicePrompt.CONTACT_HAS_SCHEDULE).let { it.copy(prompts = lead + it.prompts) }
        }
        val next = advance()
        return next.copy(prompts = lead + next.prompts)
    }

    private fun retryFromScratch(): Reply {
        resetDraft()
        forceConfirm = true
        step = Step.ASK_CONTACT
        return say(VoicePrompt.CONTACT_RETRY)
    }

    // endregion

    // region Slots

    private fun mergeSlots(parsed: ParsedCommand) {
        var d = draft
        if (parsed.recurrence != null) d = d.copy(recurrence = parsed.recurrence, oneTime = false)
        else if (parsed.oneTime) d = d.copy(oneTime = true, recurrence = null)
        else if (parsed.repeating && d.oneTime == null) d = d.copy(oneTime = false)

        if (parsed.weekdays.isNotEmpty()) d = d.copy(weekdays = parsed.weekdays, daysUnclear = false)
        else if (parsed.weekdayCountWithoutNames) d = d.copy(daysUnclear = true)

        // A specific day with no repeat words ("kal", "Saturday ko") reads as a one-time call.
        val date = parsed.date?.let { resolveDate(it) }
        if (date != null && d.oneTime != false && d.recurrence == null) d = d.copy(date = date, oneTime = true)
        draft = d

        if (parsed.times.isNotEmpty()) storeTimes(parsed.times, parsed.period)
        else if (parsed.noTime) draft = draft.copy(timeDecided = true, startMinutes = null, endMinutes = null, pendingTimes = emptyList())
    }

    /** Null for a "25 tarikh" that's already gone this month - see [advance]'s DATE_IN_PAST. */
    private var dateInPast = false

    private fun resolveDate(spoken: SpokenDate): LocalDate? {
        val today = today()
        dateInPast = false
        return when (spoken) {
            SpokenDate.Today -> today
            SpokenDate.Tomorrow -> today.plusDays(1)
            SpokenDate.DayAfterTomorrow -> today.plusDays(2)
            is SpokenDate.Weekday -> nextOccurrence(spoken.day, includeToday = true)
            is SpokenDate.DayOfMonth -> {
                val candidate = runCatching { today.withDayOfMonth(spoken.day) }.getOrNull()
                if (candidate == null || candidate.isBefore(today)) { dateInPast = true; null } else candidate
            }
        }
    }

    /** Stores spoken times, or parks them in [Draft.pendingTimes] when AM/PM is still unknown. */
    private fun storeTimes(times: List<SpokenTime>, period: DayPeriod?): VoicePrompt? {
        val relevant = times.take(2)
        if (relevant.any { it.isAmbiguous(period) }) {
            draft = draft.copy(pendingTimes = relevant, timeDecided = false)
            return null
        }
        val start = relevant[0].minutesOfDay(period)
        var end = relevant.getOrNull(1)?.minutesOfDay(period ?: relevant[0].period)
        if (end != null && end <= start) {
            // "11 se 1" - the second number most likely crossed into the afternoon.
            val shifted = end + 12 * 60
            end = if (relevant[1].hour < 12 && relevant[1].period == null && shifted < 24 * 60 && shifted > start) shifted else null
            if (end == null) {
                draft = draft.copy(pendingTimes = emptyList(), timeDecided = false, startMinutes = null, endMinutes = null)
                return VoicePrompt.TIME_WINDOW_INVALID
            }
        }
        draft = draft.copy(startMinutes = start, endMinutes = end, timeDecided = true, pendingTimes = emptyList())
        return null
    }

    private fun applyTimes(times: List<SpokenTime>, period: DayPeriod?): Reply {
        if (times.isEmpty()) return miss()
        val problem = storeTimes(times, period)
        if (problem != null) {
            step = Step.ASK_TIME
            return say(problem)
        }
        return advance()
    }

    private suspend fun applyEdit(parsed: ParsedCommand): Reply {
        return when {
            parsed.mentionsContact && parsed.times.isEmpty() && parsed.recurrence == null -> {
                val keep = draft.copy(contact = null)
                draft = keep
                step = Step.ASK_CONTACT
                input = InputOptions.None
                if (parsed.nameWords.isNotEmpty()) searchContacts(parsed.nameWords) else say(VoicePrompt.CONTACT_NO_NAME)
            }
            parsed.times.isNotEmpty() || parsed.recurrence != null || parsed.weekdays.isNotEmpty() || parsed.oneTime -> {
                if (parsed.recurrence != null || parsed.oneTime) {
                    draft = draft.copy(recurrence = null, oneTime = null, weekdays = emptySet(), date = null)
                }
                mergeSlots(parsed)
                val lead = spoken(listOf(VoicePrompt.EDIT_DONE))
                advance().let { it.copy(prompts = lead + it.prompts) }
            }
            parsed.mentionsTime -> {
                draft = draft.copy(timeDecided = false, startMinutes = null, endMinutes = null, pendingTimes = emptyList())
                advance()
            }
            parsed.mentionsDays -> {
                draft = draft.copy(oneTime = null, recurrence = null, weekdays = emptySet(), date = null)
                advance()
            }
            else -> miss()
        }
    }

    // endregion

    /** Asks for the next missing piece, or shows the summary once nothing is missing. */
    private fun advance(): Reply {
        misses = 0
        input = InputOptions.None
        val d = draft

        if (d.contact == null) {
            step = Step.ASK_CONTACT
            return say(VoicePrompt.CONTACT_NO_NAME)
        }
        if (dateInPast) {
            dateInPast = false
            step = Step.ASK_DATE
            return say(VoicePrompt.DATE_IN_PAST)
        }
        val oneTime = d.oneTime ?: when {
            d.recurrence != null || d.weekdays.size >= 2 -> false
            d.date != null -> true
            else -> null
        }
        if (oneTime == null) {
            step = Step.ASK_TYPE
            return say(VoicePrompt.ASK_TYPE)
        }
        draft = d.copy(oneTime = oneTime)

        if (!oneTime) {
            val recurrence = d.recurrence ?: if (d.weekdays.isNotEmpty() || d.daysUnclear) Recurrence.WEEKLY else null
            if (recurrence == null) {
                step = Step.ASK_FREQUENCY
                return say(VoicePrompt.ASK_FREQUENCY)
            }
            draft = draft.copy(recurrence = recurrence)
            if ((recurrence == Recurrence.WEEKLY || recurrence == Recurrence.BIWEEKLY) && d.weekdays.isEmpty()) {
                step = Step.ASK_WEEKDAYS
                return say(if (d.daysUnclear) VoicePrompt.WEEKDAYS_UNCLEAR else VoicePrompt.ASK_WEEKDAYS)
            }
        } else {
            val date = d.date ?: d.weekdays.singleOrNull()?.let { nextOccurrence(it, includeToday = true) }
            if (date == null) {
                step = Step.ASK_DATE
                return say(VoicePrompt.ASK_DATE)
            }
            draft = draft.copy(date = date)
        }

        if (draft.pendingTimes.isNotEmpty()) {
            step = Step.ASK_AM_PM
            return say(VoicePrompt.TIME_AM_PM)
        }
        if (!draft.timeDecided) {
            step = Step.ASK_TIME
            return say(VoicePrompt.ASK_TIME)
        }
        val start = draft.startMinutes
        if (oneTime && draft.date == today() && start != null && start <= minutesNow()) {
            draft = draft.copy(timeDecided = false, startMinutes = null, endMinutes = null)
            step = Step.ASK_TIME
            return say(VoicePrompt.TIME_IN_PAST)
        }

        step = Step.CONFIRM_SUMMARY
        input = InputOptions.YesNo
        addItem { ChatItem.Summary(it, summary(buildRequests().size)) }
        return say(VoicePrompt.SUMMARY_CONFIRM)
    }

    // region Saving

    private fun buildRequests(): List<ScheduleRequest> {
        val d = draft
        val contact = d.contact ?: return emptyList()
        val start = d.startMinutes
        val end = d.endMinutes
        if (d.oneTime == true) {
            val date = d.date ?: return emptyList()
            return listOf(ScheduleRequest(contact, Recurrence.ONE_TIME, date.toEpochMillis(), start, end))
        }
        val recurrence = d.recurrence ?: return emptyList()
        return when (recurrence) {
            Recurrence.WEEKLY, Recurrence.BIWEEKLY -> d.weekdays.sorted().map { day ->
                ScheduleRequest(contact, recurrence, firstDue(day, start).toEpochMillis(), start, end)
            }
            // Daily starts today while today's call can still happen; otherwise tomorrow.
            Recurrence.DAILY -> {
                val firstDay = if (start == null || start > minutesNow()) today() else today().plusDays(1)
                listOf(ScheduleRequest(contact, recurrence, firstDay.toEpochMillis(), start, end))
            }
            Recurrence.MONTHLY -> listOf(ScheduleRequest(contact, recurrence, today().plusMonths(1).toEpochMillis(), start, end))
            Recurrence.ONE_TIME -> emptyList()
        }
    }

    /** This week's occurrence if it's still ahead (today counts until the call time passes), else next week's. */
    private fun firstDue(day: DayOfWeek, startMinutes: Int?): LocalDate {
        val includeToday = startMinutes == null || startMinutes > minutesNow()
        return nextOccurrence(day, includeToday)
    }

    private suspend fun save(): Reply {
        val contact = draft.contact ?: return advance()
        val existing = backend.activeSchedulesFor(contact)
        val requests = buildRequests()
        val fresh = requests.filterNot { request -> existing.any { it.duplicates(request) } }

        if (fresh.isEmpty()) {
            if (pendingCommands.isNotEmpty()) return continueWithNextCommand(spoken(listOf(VoicePrompt.DUPLICATE_SCHEDULE)))
            step = Step.ASK_ANOTHER
            input = InputOptions.YesNo
            return say(listOf(VoicePrompt.DUPLICATE_SCHEDULE, VoicePrompt.ASK_ANOTHER))
        }

        val outcomes = fresh.map { request -> runCatching { backend.save(request) }.getOrDefault(SaveOutcome.FAILED) }
        val created = outcomes.count { it == SaveOutcome.CREATED }
        val prompts = mutableListOf<VoicePrompt>()
        when {
            created == 0 && outcomes.all { it == SaveOutcome.NAME_COLLISION } -> {
                if (pendingCommands.isNotEmpty()) return continueWithNextCommand(spoken(listOf(VoicePrompt.NAME_CLASH)))
                endConversation()
                return say(VoicePrompt.NAME_CLASH, listen = false)
            }
            created == 0 -> {
                // Stay on the summary - "yes" again retries the save.
                input = InputOptions.YesNo
                return say(VoicePrompt.SAVE_FAILED)
            }
            created < fresh.size -> prompts += VoicePrompt.PARTIAL_SAVE_FAILED
            // The last contact of a multi-contact request wraps the whole request up.
            inMultiRun && pendingCommands.isEmpty() -> prompts += VoicePrompt.MULTI_ALL_DONE
            created == 1 -> prompts += VoicePrompt.SAVED_ONE
            else -> prompts += VoicePrompt.SAVED_MANY
        }
        if (pendingCommands.isEmpty()) inMultiRun = false
        addItem { ChatItem.Success(it, summary(created)) }
        if (draft.startMinutes != null) anyTimedSaved = true

        if (pendingCommands.isNotEmpty()) return continueWithNextCommand(spoken(prompts))

        if (anyTimedSaved && backend.notificationsBlocked()) {
            prompts += VoicePrompt.NOTIFICATION_PERMISSION_REQUIRED
            prompts += VoicePrompt.ASK_ANOTHER
            step = Step.ASK_ANOTHER
            input = InputOptions.GrantNotifications
            return say(prompts)
        }
        prompts += VoicePrompt.ASK_ANOTHER
        step = Step.ASK_ANOTHER
        input = InputOptions.YesNo
        return say(prompts)
    }

    private fun Schedule.duplicates(request: ScheduleRequest): Boolean {
        if (recurrence != request.recurrence) return false
        if (preferredTimeMinutes != request.preferredTimeMinutes || comfortableTimeEndMinutes != request.comfortableTimeEndMinutes) return false
        return when (recurrence) {
            Recurrence.WEEKLY, Recurrence.BIWEEKLY -> dayOf(nextDueDate) == dayOf(request.nextDueDate)
            Recurrence.ONE_TIME -> dayStart(nextDueDate) == dayStart(request.nextDueDate)
            Recurrence.DAILY, Recurrence.MONTHLY -> true
        }
    }

    private fun summary(count: Int): ScheduleSummary {
        val d = draft
        val contact = d.contact
        return ScheduleSummary(
            contactName = contact?.displayName.orEmpty(),
            hasWhatsApp = contact?.hasWhatsApp == true,
            recurrence = if (d.oneTime == true) Recurrence.ONE_TIME else d.recurrence ?: Recurrence.WEEKLY,
            weekdays = if (d.oneTime == true) emptyList() else d.weekdays.sorted(),
            oneTimeDate = if (d.oneTime == true) d.date?.toEpochMillis() else null,
            startMinutes = d.startMinutes,
            endMinutes = d.endMinutes,
            scheduleCount = count
        )
    }

    // endregion

    // region Helpers

    private fun miss(): Reply {
        misses++
        if (misses < 2) return say(VoicePrompt.NOT_UNDERSTOOD)
        input = fallbackOptions()
        return say(VoicePrompt.NOT_UNDERSTOOD_2)
    }

    private fun fallbackOptions(): InputOptions = when (step) {
        Step.ASK_TYPE -> InputOptions.Chips(listOf(QuickReply("Repeating", "repeat"), QuickReply("One time", "one time")))
        Step.ASK_FREQUENCY -> InputOptions.Chips(
            listOf(
                QuickReply("Daily", "daily"), QuickReply("Weekly", "weekly"),
                QuickReply("Every 2 weeks", "biweekly"), QuickReply("Monthly", "monthly")
            )
        )
        Step.ASK_WEEKDAYS -> InputOptions.Chips(
            DayOfWeek.entries.map { QuickReply(it.shortLabel(), it.name.lowercase()) } + QuickReply("Weekend", "weekend")
        )
        Step.ASK_DATE -> InputOptions.Chips(
            listOf(QuickReply("Today", "today"), QuickReply("Tomorrow", "tomorrow"), QuickReply("Day after", "day after tomorrow"))
        )
        Step.ASK_TIME -> InputOptions.Chips(listOf(QuickReply("No fixed time", "no time")))
        Step.ASK_AM_PM -> InputOptions.Chips(listOf(QuickReply("Morning", "morning"), QuickReply("Evening", "evening")))
        Step.EDIT_SUMMARY -> InputOptions.Chips(
            listOf(QuickReply("Contact", "contact"), QuickReply("Days", "din"), QuickReply("Time", "time"))
        )
        Step.ASK_CONTACT -> InputOptions.ContactSearch
        else -> InputOptions.None
    }

    private fun endConversation() {
        pendingCommands.clear()
        inMultiRun = false
        anyTimedSaved = false
        lastQuestion = null
        stepBeforeHonorific = null
        step = Step.WAIT_WAKE
        input = if (input == InputOptions.GrantContacts) input else InputOptions.None
        resetDraft()
    }

    private fun resetDraft() {
        draft = Draft()
        forceConfirm = false
        candidates = emptyList()
        contactMisses = 0
        noNameTurns = 0
        misses = 0
        dateInPast = false
    }

    private fun say(prompt: VoicePrompt, listen: Boolean = true): Reply = say(listOf(prompt), listen)

    private fun say(prompts: List<VoicePrompt>, listen: Boolean = true): Reply {
        prompts.lastOrNull { it in QUESTIONS }?.let { lastQuestion = it }
        return Reply(spoken(prompts), listen = listen && step != Step.WAIT_WAKE)
    }

    /** Adds each line to the chat as it's queued, so the bubble shows up alongside the audio. */
    private fun spoken(prompts: List<VoicePrompt>): List<VoicePrompt> {
        prompts.forEach { prompt -> addItem { ChatItem.AssistantText(it, prompt) } }
        return prompts
    }

    private inline fun addItem(build: (Long) -> ChatItem) {
        items = items + build(nextId++)
    }

    private fun today(): LocalDate = Instant.ofEpochMilli(now()).atZone(zone()).toLocalDate()

    private fun minutesNow(): Int = Instant.ofEpochMilli(now()).atZone(zone()).toLocalTime().let { it.hour * 60 + it.minute }

    private fun nextOccurrence(day: DayOfWeek, includeToday: Boolean): LocalDate {
        val today = today()
        var diff = (day.value - today.dayOfWeek.value + 7) % 7
        if (diff == 0 && !includeToday) diff = 7
        return today.plusDays(diff.toLong())
    }

    private fun LocalDate.toEpochMillis(): Long = atStartOfDay(zone()).toInstant().toEpochMilli()
    private fun dayOf(epochMillis: Long): DayOfWeek = Instant.ofEpochMilli(epochMillis).atZone(zone()).dayOfWeek
    private fun dayStart(epochMillis: Long): LocalDate = Instant.ofEpochMilli(epochMillis).atZone(zone()).toLocalDate()

    // endregion
}

fun DayOfWeek.shortLabel(): String = name.take(3).lowercase().replaceFirstChar { it.uppercase() }
