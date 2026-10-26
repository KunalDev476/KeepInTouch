package com.pragmaapps.keepintouch.domain.voice

import com.pragmaapps.keepintouch.domain.model.Recurrence
import java.time.DayOfWeek

enum class DayPeriod { MORNING, AFTERNOON, EVENING, NIGHT, DAYTIME }

/** A clock time as spoken - [hour] is whatever number was said (0-23), not yet resolved to AM/PM. */
data class SpokenTime(val hour: Int, val minute: Int, val period: DayPeriod? = null) {

    /** "6 baje" alone can't tell 6am from 6pm. 12 reads as noon and 0/13-23 are already unambiguous. */
    fun isAmbiguous(fallbackPeriod: DayPeriod?): Boolean = (period ?: fallbackPeriod) == null && hour in 1..11

    /** Minutes since midnight. Call only once [isAmbiguous] is false, or it assumes the hour as-is. */
    fun minutesOfDay(fallbackPeriod: DayPeriod? = null): Int {
        val p = period ?: fallbackPeriod
        val h = when {
            hour == 0 || hour >= 13 -> hour
            p == DayPeriod.MORNING -> if (hour == 12) 0 else hour
            p == DayPeriod.AFTERNOON -> if (hour == 12 || hour >= 7) hour else hour + 12
            p == DayPeriod.EVENING -> if (hour == 12) 12 else hour + 12
            p == DayPeriod.NIGHT -> when {
                hour == 12 -> 0
                hour <= 4 -> hour
                else -> hour + 12
            }
            // "din ke 2 baje" = 2pm, "din mein 10 baje" = 10am.
            p == DayPeriod.DAYTIME -> if (hour in 1..6) hour + 12 else hour
            else -> hour
        }
        return (h % 24) * 60 + minute
    }
}

sealed class SpokenDate {
    data object Today : SpokenDate()
    data object Tomorrow : SpokenDate()
    data object DayAfterTomorrow : SpokenDate()
    data class Weekday(val day: DayOfWeek) : SpokenDate()
    data class DayOfMonth(val day: Int) : SpokenDate()
}

/** Everything one utterance said, independent of which question was being asked. */
data class ParsedCommand(
    val yes: Boolean = false,
    /** A real "no" - not the "nahi" of a self-correction like "9 baje nahi, 6 baje". */
    val no: Boolean = false,
    val cancel: Boolean = false,
    val oneTime: Boolean = false,
    val repeating: Boolean = false,
    val recurrence: Recurrence? = null,
    val weekdays: Set<DayOfWeek> = emptySet(),
    /** "hafte mein do din" - a count of days per week with no actual days named. */
    val weekdayCountWithoutNames: Boolean = false,
    val date: SpokenDate? = null,
    val times: List<SpokenTime> = emptyList(),
    /** A morning/evening word that wasn't attached to a specific time, e.g. the answer to "subah ya shaam?". */
    val period: DayPeriod? = null,
    val noTime: Boolean = false,
    val ordinal: Int? = null,
    val mentionsContact: Boolean = false,
    val mentionsTime: Boolean = false,
    val mentionsDays: Boolean = false,
    /** "galat contact hai", "maine Amit bola tha" - the contact that was picked is the wrong one. */
    val wrongContact: Boolean = false,
    /** Words left once every keyword and filler word is removed - the contact name, if one was said. */
    val nameWords: List<String> = emptyList(),
    /** Word-index ranges of each separate run of [nameWords] - one per contact when several are named. */
    val nameRuns: List<IntRange> = emptyList(),
    /** Where "dono / sab ke liye" starts - everything from here on applies to every contact named. */
    val sharedFrom: Int? = null,
    /** True when anything schedule-related was said (frequency, days, date or time). */
    val hasScheduleDetails: Boolean = false,
    val smallTalk: SmallTalk? = null,
    val honorificRequest: HonorificRequest? = null,
    /** A bare "sir" / "madam" anywhere - the answer to "how should I address you?". */
    val honorificWord: Honorific? = null,
    /** "Naam nahi batana" - the user would rather not give a name. */
    val skipName: Boolean = false
)

/** Chit-chat that isn't part of building a schedule - answered, then the pending question is repeated. */
enum class SmallTalk { HOW_ARE_YOU, THANKS, WHO_ARE_YOU, HELP, PRAISE, OFF_TOPIC, BYE, ARE_YOU_THERE, REPEAT, CALL_NOW, SORRY, FRUSTRATED }

/** "Main madam hoon", "sir mat bolo", "mujhe kuch aur bulao". */
enum class HonorificRequest { SIR, MAAM, NEUTRAL, CHANGE }

/**
 * Rule-based Hinglish understanding for voice scheduling - no model, no network. Every keyword
 * list holds both scripts, since the hi-IN recognizer writes English words in Devanagari
 * ("सैटरडे") and Hindi words sometimes in Latin, and PhoneticKey absorbs spelling variants on top.
 *
 * Self-corrections are honoured: anything directly followed by "nahi" is dropped, so
 * "roj 9 bje nhi 6 bje, din me nhi shaam me" means daily at 6pm, and "roz nahi har hafte" weekly.
 */
object VoiceCommandParser {

    private val YES = Vocabulary(
        "haan", "han", "ha", "haa", "haanji", "haan ji", "ji haan", "yes", "yeah", "yep", "yup", "ok", "okay",
        "theek hai", "theek", "thik", "sahi", "sahi hai", "bilkul", "zaroor", "sure", "kar do", "kardo",
        "save karo", "save kar do", "chalega", "हां", "हाँ", "हा", "जी हां", "जी हाँ", "ठीक", "ठीक है", "सही",
        "बिल्कुल", "ज़रूर", "जरूर", "ओके", "यस", "कर दो", "चलेगा"
    )
    private val NO = Vocabulary(
        "nahi", "nahin", "nhi", "nai", "nahi nahi", "no", "nope", "mat", "galat", "wrong",
        "नहीं", "नही", "नो", "मत", "गलत", "ग़लत"
    )
    /** "na" is also the tag in "bana do na" - only a "no" when it opens the answer or is the whole answer. */
    private val NA = Vocabulary("na", "naa", "nah", "ना")
    private val CANCEL = Vocabulary(
        "cancel", "cancel karo", "rehne do", "rahne do", "band karo", "band kar do", "stop", "exit", "chhodo",
        "chhod do", "quit", "कैंसल", "कैंसिल", "रहने दो", "बंद करो", "बंद कर दो", "स्टॉप", "छोड़ो", "छोड़ दो"
    )
    private val ONE_TIME = Vocabulary(
        "ek baar", "ek bar", "ekbar", "ek hi baar", "sirf ek baar", "bas ek baar", "ek dafa", "ek hi dafa",
        "ek daffa", "sirf aaj", "one time", "onetime", "once", "single", "एक बार", "एक ही बार", "सिर्फ एक बार",
        "बस एक बार", "एक दफा", "एक दफ़ा", "एक ही दफा", "सिर्फ आज", "वन टाइम", "वनटाइम", "वन्स"
    )
    private val REPEAT = Vocabulary(
        "repeat", "repeating", "repeated", "recurring", "baar baar", "regular", "regularly", "hamesha",
        "रिपीट", "रिपीटिंग", "बार बार", "रेगुलर", "हमेशा"
    )
    private val DAILY = Vocabulary(
        "roz", "roj", "rozana", "rojana", "rozaana", "rojaana", "roznaa", "rozanaa", "har roz", "har roj",
        "har din", "har ek din", "din mein ek baar", "din me ek baar", "pratidin", "nitya", "daily", "dailly",
        "every day", "everyday",
        "रोज़", "रोज", "रोज़ाना", "रोजाना", "रोज़ाना", "रोज़ना", "हर रोज़", "हर रोज", "हर दिन", "हर एक दिन",
        "दिन में एक बार", "प्रतिदिन", "नित्य", "डेली", "एवरीडे"
    )
    private val WEEKLY = Vocabulary(
        "weekly", "every week", "har hafte", "har haftey", "har hafta", "har week", "hafte me ek baar",
        "hafte mein ek baar", "week me ek baar", "saptahik", "har saptah",
        "हर हफ्ते", "हर हफ़्ते", "हर हफ्ता", "हर सप्ताह", "हफ्ते में एक बार", "हफ़्ते में एक बार", "साप्ताहिक",
        "वीकली", "हर वीक"
    )
    private val BIWEEKLY = Vocabulary(
        "biweekly", "bi weekly", "fortnightly", "do hafte", "2 hafte", "15 din", "pandrah din", "pakhwada",
        "pakhwade", "every two weeks", "every 2 weeks", "alternate week", "har dusre hafte",
        "दो हफ्ते", "दो हफ़्ते", "2 हफ्ते", "2 हफ़्ते", "15 दिन", "पंद्रह दिन", "पखवाड़ा", "पखवाड़े",
        "हर दूसरे हफ्ते", "बायवीकली"
    )
    private val MONTHLY = Vocabulary(
        "monthly", "every month", "har mahine", "har mahina", "har maheena", "har mahinay", "har month",
        "mahine me ek baar", "mahine mein ek baar", "masik", "har maah",
        "हर महीने", "हर महीना", "हर माह", "महीने में एक बार", "मासिक", "मंथली", "हर मंथ"
    )
    private val WEEK_WORD = Vocabulary(
        "hafte", "haftey", "hafta", "hapta", "week", "saptah", "हफ्ते", "हफ़्ते", "हफ्ता", "हफ़्ता", "सप्ताह", "वीक"
    )
    private val MONTH_WORD = Vocabulary("mahine", "mahina", "maheena", "mahinay", "month", "months", "महीने", "महीना", "माह")
    private val DAY_COUNT_UNIT = Vocabulary("din", "baar", "bar", "dafa", "days", "times", "दिन", "बार", "दफा")

    private val DAYS: Map<DayOfWeek, Vocabulary> = mapOf(
        DayOfWeek.MONDAY to Vocabulary("monday", "somvar", "somvaar", "सोमवार", "मंडे", "मनडे"),
        DayOfWeek.TUESDAY to Vocabulary("tuesday", "mangalvar", "mangalvaar", "मंगलवार", "ट्यूसडे", "टयूजडे"),
        DayOfWeek.WEDNESDAY to Vocabulary("wednesday", "budhvar", "budhvaar", "बुधवार", "वेडनेसडे", "वेनसडे", "वेन्सडे"),
        DayOfWeek.THURSDAY to Vocabulary(
            "thursday", "guruvar", "guruvaar", "veervar", "brihaspativar", "गुरुवार", "वीरवार", "बृहस्पतिवार", "थर्सडे"
        ),
        DayOfWeek.FRIDAY to Vocabulary("friday", "shukravar", "shukravaar", "शुक्रवार", "फ्राइडे", "फ्रायडे"),
        DayOfWeek.SATURDAY to Vocabulary("saturday", "shanivar", "shanivaar", "शनिवार", "सैटरडे", "सेटरडे"),
        DayOfWeek.SUNDAY to Vocabulary(
            "sunday", "ravivar", "ravivaar", "itvar", "itvaar", "etvar", "रविवार", "इतवार", "संडे"
        )
    )
    private val WEEKEND = Vocabulary("weekend", "weekends", "वीकेंड", "वीकेंड्स")
    private val WORKING_DAYS = Vocabulary("weekdays", "working days", "वीकडेज़", "वीकडेज")
    private val RANGE_JOINER = Vocabulary("se", "to", "till", "tak", "until", "से", "तक", "टू")

    private val TODAY = Vocabulary("aaj", "aj", "today", "आज")
    private val TOMORROW = Vocabulary("kal", "tomorrow", "कल")
    private val DAY_AFTER = Vocabulary("parso", "parson", "day after tomorrow", "परसों", "परसो")
    private val DATE_WORD = Vocabulary("tarikh", "tareekh", "date", "तारीख", "तारीख़", "डेट")

    private val PERIODS: Map<DayPeriod, Vocabulary> = mapOf(
        DayPeriod.MORNING to Vocabulary("subah", "subha", "savere", "morning", "am", "a.m", "सुबह", "सवेरे", "मॉर्निंग", "एएम"),
        DayPeriod.AFTERNOON to Vocabulary("dopahar", "dopehar", "afternoon", "noon", "दोपहर", "आफ्टरनून"),
        DayPeriod.EVENING to Vocabulary("shaam", "sham", "evening", "pm", "p.m", "शाम", "इवनिंग", "पीएम"),
        DayPeriod.NIGHT to Vocabulary("raat", "rat", "night", "रात", "नाइट"),
        // Only as a phrase - a bare "din" is far more often "har din" / "do din".
        DayPeriod.DAYTIME to Vocabulary("din me", "din mein", "din ke", "din ko", "daytime", "दिन में", "दिन के", "दिन को")
    )
    private val BAJE = Vocabulary("baje", "bje", "baj", "o'clock", "oclock", "बजे", "बज")
    private val SAADHE = Vocabulary("saadhe", "sadhe", "साढ़े", "साढे")
    private val SAVA = Vocabulary("sava", "सवा")
    private val PAUNE = Vocabulary("paune", "pone", "पौने")
    private val DEDH = Vocabulary("dedh", "डेढ़", "डेढ")
    private val DHAI = Vocabulary("dhai", "ढाई")

    private val NUMBER_WORDS: List<Pair<Vocabulary, Int>> = listOf(
        Vocabulary("ek", "one", "एक") to 1,
        Vocabulary("do", "two", "दो") to 2,
        Vocabulary("teen", "three", "तीन") to 3,
        Vocabulary("char", "chaar", "four", "चार") to 4,
        Vocabulary("paanch", "panch", "five", "पांच", "पाँच") to 5,
        Vocabulary("chhe", "chhah", "che", "six", "छह", "छः", "छे") to 6,
        Vocabulary("saat", "seven", "सात") to 7,
        Vocabulary("aath", "eight", "आठ") to 8,
        Vocabulary("nau", "nine", "नौ") to 9,
        Vocabulary("das", "ten", "दस") to 10,
        Vocabulary("gyarah", "eleven", "ग्यारह") to 11,
        Vocabulary("barah", "twelve", "बारह") to 12
    )

    private val ORDINALS: List<Pair<Vocabulary, Int>> = listOf(
        Vocabulary("pehla", "pahla", "pehle", "pehli", "first", "1st", "पहला", "पहले", "पहली", "फर्स्ट") to 1,
        Vocabulary("doosra", "dusra", "doosre", "dusri", "second", "2nd", "दूसरा", "दूसरे", "दूसरी", "सेकंड") to 2,
        Vocabulary("teesra", "tisra", "teesre", "third", "3rd", "तीसरा", "तीसरे", "तीसरी", "थर्ड") to 3,
        Vocabulary("chautha", "chauthe", "fourth", "4th", "चौथा", "चौथे", "चौथी") to 4,
        Vocabulary("paanchva", "panchva", "fifth", "5th", "पांचवा", "पांचवां", "पाँचवाँ") to 5
    )

    private val NO_TIME = Vocabulary(
        "koi time nahi", "time nahi", "no time", "no fixed time", "anytime", "any time", "kabhi bhi", "jab bhi",
        "koi bhi time", "fixed nahi", "nahi chahiye", "skip", "कोई टाइम नहीं", "टाइम नहीं", "कभी भी", "जब भी",
        "कोई भी टाइम", "नहीं चाहिए"
    )
    private val CONTACT_WORDS = Vocabulary("contact", "naam", "name", "person", "कॉन्टैक्ट", "कांटेक्ट", "नाम")
    private val TIME_WORDS = Vocabulary("time", "samay", "waqt", "timing", "टाइम", "समय", "वक्त", "वक़्त", "टाइमिंग")
    private val DAYS_WORDS = Vocabulary("din", "day", "days", "date", "repeat", "frequency", "दिन", "डे", "तारीख", "रिपीट")
    private val WRONG_CONTACT = Vocabulary(
        "galat contact", "wrong contact", "contact galat", "galat banda", "galat insaan", "galat naam",
        "ye nahi", "yeh nahi", "ye wala nahi", "dusra contact", "गलत कॉन्टैक्ट", "ग़लत कॉन्टैक्ट",
        "कॉन्टैक्ट गलत", "गलत नाम", "ये नहीं", "यह नहीं", "ये वाला नहीं"
    )
    private val SAID_EARLIER = Vocabulary("bola tha", "boli thi", "kaha tha", "kahi thi", "बोला था", "बोली थी", "कहा था")
    private val SHARED = Vocabulary(
        "dono", "donon", "dono ke liye", "both", "sab", "sabhi", "sabke liye", "all", "दोनों", "दोनो", "सब", "सभी", "सबके"
    )

    private val SMALL_TALK: List<Pair<SmallTalk, Vocabulary>> = listOf(
        SmallTalk.FRUSTRATED to Vocabulary(
            "samajh nahi aata", "kuch samajh nahi aata", "kya bakwas", "bakwas", "bekaar", "bekar", "pagal", "useless",
            "stupid", "dimag kharab", "समझ नहीं आता", "बकवास", "बेकार", "पागल", "दिमाग खराब"
        ),
        SmallTalk.REPEAT to Vocabulary(
            "phir se bolo", "fir se bolo", "phir se boliye", "dobara bolo", "dobara boliye", "kya kaha", "kya bola",
            "samajh nahi aaya", "repeat karo", "say again", "फिर से बोलो", "फिर से बोलिए", "दोबारा बोलो", "क्या कहा",
            "क्या बोला", "समझ नहीं आया"
        ),
        SmallTalk.CALL_NOW to Vocabulary(
            "call karo", "call lagao", "call laga do", "phone lagao", "phone laga do", "phone karo", "abhi call",
            "call kar do", "dial karo", "कॉल करो", "कॉल लगाओ", "कॉल लगा दो", "फोन लगाओ", "फ़ोन लगाओ", "फोन करो", "अभी कॉल"
        ),
        SmallTalk.SORRY to Vocabulary("sorry", "maaf karna", "maaf kijiye", "galti ho gayi", "सॉरी", "माफ़ करना", "माफ करना", "गलती हो गई"),
        SmallTalk.HOW_ARE_YOU to Vocabulary(
            "kaise ho", "kaise hain", "kaise hai", "kaisa hai", "kya haal", "kya haal hai", "how are you", "kaise ho aap",
            "कैसे हो", "कैसे हैं", "कैसा है", "क्या हाल", "हाउ आर यू"
        ),
        SmallTalk.WHO_ARE_YOU to Vocabulary("kaun ho", "tum kaun", "aap kaun", "who are you", "कौन हो", "आप कौन", "तुम कौन"),
        SmallTalk.HELP to Vocabulary(
            "kya kar sakte", "kya karte ho", "help", "madad", "kaise use", "kaise bolun", "kya bolun", "what can you do",
            "क्या कर सकते", "क्या करते हो", "हेल्प", "मदद", "कैसे बोलूं", "क्या बोलूं"
        ),
        SmallTalk.THANKS to Vocabulary(
            "thank you", "thanks", "thank u", "shukriya", "dhanyavad", "dhanyawad", "थैंक यू", "थैंक्स", "शुक्रिया", "धन्यवाद"
        ),
        SmallTalk.PRAISE to Vocabulary(
            "wah", "waah", "badhiya", "shabash", "great", "awesome", "nice", "very good", "good job", "zabardast", "mast",
            "bahut achhe", "वाह", "बढ़िया", "शाबाश", "ज़बरदस्त", "जबरदस्त", "बहुत अच्छे"
        ),
        SmallTalk.BYE to Vocabulary(
            "bye", "bye bye", "goodbye", "good bye", "alvida", "baad mein", "chalo baad", "tata", "good night",
            "बाय", "अलविदा", "बाद में", "टाटा", "गुड बाय", "गुड नाइट"
        ),
        SmallTalk.OFF_TOPIC to Vocabulary(
            "mausam", "weather", "gaana", "gana", "song", "music", "joke", "chutkula", "news", "khabar", "cricket", "score",
            "मौसम", "गाना", "जोक", "चुटकुला", "न्यूज़", "न्यूज", "खबर", "क्रिकेट"
        ),
        SmallTalk.ARE_YOU_THERE to Vocabulary(
            "sun rahe ho", "sun rahe hain", "suno", "ho kya", "are you there", "hello", "hallo", "सुन रहे हो", "सुन रहे हैं",
            "सुनो", "हेलो", "हैलो"
        )
    )

    // Deliberately not in KEYWORD_VOCABULARIES: "sir" is also part of contact names ("Amar Sir").
    private val HONORIFIC_NEUTRAL = Vocabulary(
        "sir mat bolo", "madam mat bolo", "maam mat bolo", "sir madam mat bolo", "sir mat kaho", "madam mat kaho",
        "naam se bulao", "naam se bulaiye", "dont call me sir", "dont call me madam", "kuch mat bolo",
        "सर मत बोलो", "मैडम मत बोलो", "मैम मत बोलो", "सर मैडम मत बोलो", "नाम से बुलाओ", "नाम से बुलाइए"
    )
    private val HONORIFIC_MAAM = Vocabulary(
        "main madam hoon", "main maam hoon", "main mam hoon", "madam bolo", "maam bolo", "madam boliye", "maam boliye",
        "madam kaho", "mujhe madam", "mujhe maam", "sir nahi madam", "sir nahi maam", "sir nahin madam",
        "main ladki hoon", "main aurat hoon", "मैं मैडम हूं", "मैं मैडम हूँ", "मैं मैम हूं", "मैडम बोलो", "मैम बोलो",
        "मैडम बोलिए", "मुझे मैडम", "सर नहीं मैडम", "सर नहीं मैम", "मैं लड़की हूं", "मैं लड़की हूँ"
    )
    private val HONORIFIC_SIR = Vocabulary(
        "main sir hoon", "sir bolo", "sir boliye", "sir kaho", "mujhe sir", "madam nahi sir", "maam nahi sir",
        "main ladka hoon", "main aadmi hoon", "मैं सर हूं", "मैं सर हूँ", "सर बोलो", "सर बोलिए", "मुझे सर", "मैडम नहीं सर",
        "मैम नहीं सर", "मैं लड़का हूं", "मैं लड़का हूँ"
    )
    private val HONORIFIC_CHANGE = Vocabulary(
        "kuch aur bulao", "kuch aur bulaiye", "mujhe kaise bulate ho", "bulane ka tarika badlo", "kuch aur bolo mujhe",
        "मुझे कुछ और बुलाओ", "कुछ और बुलाओ", "कुछ और बुलाइए"
    )
    private val SIR_WORD = Vocabulary("sir", "सर")
    private val MAAM_WORD = Vocabulary("madam", "maam", "mam", "mem", "ma am", "मैडम", "मैम")
    private val SKIP_NAME = Vocabulary(
        "nahi batana", "nahi bataunga", "nahi bataungi", "naam nahi", "skip", "skip karo", "chhodo", "rehne do",
        "नहीं बताना", "नहीं बताऊंगा", "नहीं बताऊंगी", "नाम नहीं", "स्किप", "छोड़ो", "रहने दो"
    )

    /** Small words allowed between a value and the "nahi" that corrects it ("din me nhi", "9 bje nhi"). */
    private val CONNECTOR = Vocabulary("me", "mein", "ko", "ke", "ki", "pe", "par", "wala", "waala", "wali", "में", "को", "के", "की", "पे", "पर", "वाला")

    /** Filler that is never part of a contact's name ("Rahul ke liye schedule bana do"). */
    private val FILLER = Vocabulary(
        "ke", "ki", "ka", "ko", "kya", "liye", "lie", "le", "se", "me", "mein", "main", "mai", "mujhe", "muje",
        "mera", "meri", "mere", "hum", "humko", "aap", "ap", "please", "plz", "pls", "schedule", "shedule",
        "schedul", "schdule", "banana", "banao", "bana", "banado", "banaiye", "banaye", "bnana", "bna", "bnao",
        "bnado", "create", "make", "set", "setup", "add", "karna", "karni", "karne", "karo", "kar", "kardo", "call",
        "calls", "phone", "baat", "hai", "hain", "h", "ho", "hoga", "hogi", "honge", "hon", "tha", "thi", "naya",
        "new", "reminder", "remind", "yaad", "dilana", "bhi", "hi", "ji", "contact", "wala", "wali", "waala", "wale",
        "and", "aur", "ya", "or", "with", "for", "the", "a", "an", "to", "of", "is", "isko", "use", "unko", "usko",
        "unse", "usse", "inse", "chahta", "chahti", "chahiye", "chahte", "hu", "hoon", "hun", "rakhna", "rakho",
        "rakh", "rakhdo", "dena", "de", "dijiye", "i", "want", "need", "my", "baje", "beech", "bich", "between",
        "around", "lagbhag", "har", "every", "pe", "par", "on", "at", "din", "time", "timing", "jo", "kre", "kare",
        "karein", "karen", "wo", "woh", "ye", "yeh", "iske", "uske", "inke", "unke", "sath", "saath", "maine",
        "mene", "bola", "boli", "kaha", "lagao", "laga", "lagado", "dalo", "daal", "bulao", "bulaiye", "bula",
        "kaho", "kahiye", "kahkar", "bolo", "boliye", "name", "am", "its",
        "के", "की", "का", "को", "क्या", "लिए", "लिये", "से", "में", "मैं", "मुझे", "मेरा", "मेरी", "मेरे", "आप",
        "शेड्यूल", "शेड्यूल्ड", "बनाना", "बनाओ", "बना", "बनादो", "बनाइए", "बनाइये", "बनाएं", "बनाने", "करना",
        "करनी", "करने", "करो", "कर", "कॉल", "फोन", "फ़ोन", "बात", "है", "हैं", "हो", "होगा", "होगी", "होंगे",
        "था", "थी", "नया", "रिमाइंडर", "याद", "भी", "ही", "जी", "वाला", "वाले", "और", "या", "इसको", "उसको",
        "उनको", "इनको", "उनसे", "चाहता", "चाहती", "चाहिए", "हूं", "हूँ", "रखना", "रखो", "रख", "देना", "दे",
        "दीजिए", "बीच", "लगभग", "हर", "पे", "पर", "जो", "करे", "करें", "वो", "ये", "यह", "इसके", "उसके", "साथ",
        "मैंने", "मेने", "बोला", "कहा", "लगा", "लगाओ", "बुलाओ", "बुलाइए", "कहो", "कहिए", "बोलो", "बोलिए"
    )

    private val KEYWORD_VOCABULARIES: List<Vocabulary> by lazy {
        listOf(
            YES, NO, NA, CANCEL, ONE_TIME, REPEAT, DAILY, WEEKLY, BIWEEKLY, MONTHLY, WEEK_WORD, MONTH_WORD,
            DAY_COUNT_UNIT, WEEKEND, WORKING_DAYS, TODAY, TOMORROW, DAY_AFTER, DATE_WORD, BAJE, SAADHE, SAVA, PAUNE,
            DEDH, DHAI, NO_TIME, CONTACT_WORDS, TIME_WORDS, DAYS_WORDS, WRONG_CONTACT, SAID_EARLIER, SHARED, SKIP_NAME, FILLER
        ) + SMALL_TALK.map { it.second } + DAYS.values + PERIODS.values + NUMBER_WORDS.map { it.first } + ORDINALS.map { it.first }
    }

    private val CLOCK_REGEX = Regex("^(\\d{1,2})[:.](\\d{2})$")
    private val COMPACT_AM_PM_REGEX = Regex("^(\\d{1,2})(?:[:.](\\d{2}))?(am|pm)$")

    private data class Hit<T>(val range: IntRange, val value: T)

    /** Per-utterance working state: which "nahi"s were corrections rather than real answers. */
    private class Scan(val utterance: Utterance) {
        val noIndexes: Set<Int> = NO.findAll(utterance).flatMap { it.toList() }.toSet() +
            NA.findAll(utterance).map { it.first }.filter { it == 0 || utterance.size <= 2 }
        val correctiveNo = mutableSetOf<Int>()

        /** True (and records the "nahi") when the value ending just before [endExclusive] is taken back. */
        fun negated(endExclusive: Int): Boolean {
            var i = endExclusive
            var skipped = 0
            while (i < utterance.size && skipped < 2 && CONNECTOR.matchesWord(utterance, i)) { i++; skipped++ }
            if (i < utterance.size && i in noIndexes) {
                correctiveNo += i
                return true
            }
            return false
        }
    }

    /**
     * [bareNumbersAreTimes] is set while the assistant has just asked for a time, so a plain
     * "6" or "6 se 7" counts - in any other context a stray number is more likely "15 din" or a
     * date than a clock time, so a time then needs "baje", AM/PM or a morning/evening word.
     */
    fun parse(utterance: Utterance, bareNumbersAreTimes: Boolean = false): ParsedCommand {
        if (utterance.isEmpty()) return ParsedCommand()
        val scan = Scan(utterance)

        val recurrenceHits = nonOverlapping(
            hits(utterance, DAILY, Recurrence.DAILY) + hits(utterance, BIWEEKLY, Recurrence.BIWEEKLY) +
                hits(utterance, MONTHLY, Recurrence.MONTHLY) + hits(utterance, WEEKLY, Recurrence.WEEKLY)
        )
        val recurrence = recurrenceHits.filterNot { scan.negated(it.range.last + 1) }.maxByOrNull { it.range.first }?.value
        val recurrenceIndexes = recurrenceHits.flatMap { it.range.toList() }.toSet()

        val weekdays = parseWeekdays(utterance, scan)
        val dayCount = weekdayCountWithoutNames(utterance)
        // Named weekdays imply weekly when there's a repeat hint ("har Saturday") or more than one
        // day (a one-time call can't be on two days) - a lone "Saturday ko" stays ambiguous.
        val effectiveRecurrence = recurrence ?: when {
            dayCount -> Recurrence.WEEKLY
            weekdays.isNotEmpty() && !mentionsRelativeDay(utterance) &&
                (weekdays.size >= 2 || hasRepeatHint(utterance)) -> Recurrence.WEEKLY
            else -> null
        }

        val (times, loosePeriod) = parseTimes(utterance, scan, bareNumbersAreTimes, recurrenceIndexes)
        val date = parseDate(utterance, scan, weekdays)
        val oneTimeSaid = ONE_TIME.findAll(utterance).any { !scan.negated(it.last + 1) }

        val no = (scan.noIndexes - scan.correctiveNo).isNotEmpty()
        val (nameWords, nameRuns) = nameWords(utterance)
        val wrongContact = WRONG_CONTACT.occursIn(utterance) ||
            (SAID_EARLIER.occursIn(utterance) && nameWords.isNotEmpty()) ||
            (CONTACT_WORDS.occursIn(utterance) && no)

        return ParsedCommand(
            yes = YES.occursIn(utterance) && !no,
            no = no,
            cancel = CANCEL.occursIn(utterance),
            oneTime = effectiveRecurrence == null && (oneTimeSaid ||
                date is SpokenDate.Today || date is SpokenDate.Tomorrow || date is SpokenDate.DayAfterTomorrow ||
                date is SpokenDate.DayOfMonth),
            repeating = effectiveRecurrence != null || REPEAT.occursIn(utterance),
            recurrence = effectiveRecurrence,
            weekdays = weekdays,
            weekdayCountWithoutNames = dayCount && weekdays.isEmpty(),
            date = date,
            times = times,
            period = loosePeriod,
            noTime = NO_TIME.occursIn(utterance),
            ordinal = ORDINALS.firstOrNull { (vocab, _) -> vocab.occursIn(utterance) }?.second
                ?: utterance.words.singleOrNull()?.toIntOrNull()?.takeIf { it in 1..9 },
            mentionsContact = CONTACT_WORDS.occursIn(utterance),
            mentionsTime = TIME_WORDS.occursIn(utterance) || times.isNotEmpty(),
            mentionsDays = DAYS_WORDS.occursIn(utterance) || weekdays.isNotEmpty() || recurrence != null,
            wrongContact = wrongContact,
            nameWords = nameWords,
            nameRuns = nameRuns,
            sharedFrom = SHARED.findAll(utterance).firstOrNull()?.first,
            hasScheduleDetails = effectiveRecurrence != null || weekdays.isNotEmpty() || date != null ||
                times.isNotEmpty() || oneTimeSaid || dayCount || loosePeriod != null || NO_TIME.occursIn(utterance),
            smallTalk = SMALL_TALK.firstOrNull { (_, vocab) -> vocab.occursIn(utterance) }?.first,
            honorificRequest = when {
                HONORIFIC_NEUTRAL.occursIn(utterance) -> HonorificRequest.NEUTRAL
                HONORIFIC_MAAM.occursIn(utterance) -> HonorificRequest.MAAM
                HONORIFIC_SIR.occursIn(utterance) -> HonorificRequest.SIR
                HONORIFIC_CHANGE.occursIn(utterance) -> HonorificRequest.CHANGE
                else -> null
            },
            honorificWord = when {
                MAAM_WORD.occursIn(utterance) -> Honorific.MAAM
                SIR_WORD.occursIn(utterance) -> Honorific.SIR
                else -> null
            },
            skipName = SKIP_NAME.occursIn(utterance)
        )
    }

    private fun <T> hits(utterance: Utterance, vocab: Vocabulary, value: T): List<Hit<T>> =
        vocab.findAll(utterance).map { Hit(it, value) }

    /** Where two phrases overlap ("do hafte mein ek baar" vs "hafte mein ek baar"), the one starting first wins. */
    private fun <T> nonOverlapping(hits: List<Hit<T>>): List<Hit<T>> {
        val kept = mutableListOf<Hit<T>>()
        for (hit in hits.sortedWith(compareBy<Hit<T>> { it.range.first }.thenByDescending { it.range.last })) {
            if (kept.none { it.range.last >= hit.range.first }) kept += hit
        }
        return kept
    }

    /** "har Saturday" / "Saturday aur Sunday" repeat; "Saturday ko" with nothing else is ambiguous - left to the flow. */
    private fun hasRepeatHint(utterance: Utterance): Boolean =
        utterance.keys.contains(PhoneticKey.of("har")) || utterance.keys.contains(PhoneticKey.of("हर")) ||
            REPEAT.occursIn(utterance) || WEEK_WORD.occursIn(utterance)

    private fun mentionsRelativeDay(utterance: Utterance): Boolean =
        TODAY.occursIn(utterance) || TOMORROW.occursIn(utterance) || DAY_AFTER.occursIn(utterance)

    private fun parseWeekdays(utterance: Utterance, scan: Scan): Set<DayOfWeek> {
        val result = linkedSetOf<DayOfWeek>()
        WEEKEND.findAll(utterance).filterNot { scan.negated(it.last + 1) }.forEach { _ ->
            result += setOf(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
        }
        WORKING_DAYS.findAll(utterance).filterNot { scan.negated(it.last + 1) }.forEach { _ ->
            result += DayOfWeek.entries.take(5)
        }

        // Position of every named day, so "Monday se Friday" can expand into a range.
        val positions = mutableListOf<Pair<Int, DayOfWeek>>()
        for ((day, vocab) in DAYS) {
            vocab.findAll(utterance).filterNot { scan.negated(it.last + 1) }.forEach { positions += it.first to day }
        }
        positions.sortBy { it.first }
        result += positions.map { it.second }

        for (i in 0 until positions.size - 1) {
            val (startIndex, startDay) = positions[i]
            val (endIndex, endDay) = positions[i + 1]
            val between = (startIndex + 1 until endIndex)
            if (between.count() in 1..2 && between.any { RANGE_JOINER.matchesWord(utterance, it) }) {
                var d = startDay
                while (d != endDay) { result += d; d = d.plus(1) }
                result += endDay
            }
        }
        return result
    }

    /** "hafte mein do din" / "week mein 3 baar" - two or more days a week, none named. */
    private fun weekdayCountWithoutNames(utterance: Utterance): Boolean {
        if (!WEEK_WORD.occursIn(utterance)) return false
        for (i in 0 until utterance.size - 1) {
            val count = numberAt(utterance, i) ?: continue
            if (count in 2..6 && DAY_COUNT_UNIT.matchesWord(utterance, i + 1)) return true
        }
        return false
    }

    /** The last date said wins, minus any taken back ("aaj nahi, kal"). */
    private fun parseDate(utterance: Utterance, scan: Scan, weekdays: Set<DayOfWeek>): SpokenDate? {
        val dateHits = mutableListOf<Hit<SpokenDate>>()
        dateHits += hits(utterance, DAY_AFTER, SpokenDate.DayAfterTomorrow)
        dateHits += hits(utterance, TODAY, SpokenDate.Today)
        dateHits += hits(utterance, TOMORROW, SpokenDate.Tomorrow)
        for (i in 0 until utterance.size - 1) {
            val n = utterance.words[i].toIntOrNull() ?: continue
            if (n in 1..31 && DATE_WORD.matchesWord(utterance, i + 1)) dateHits += Hit(i..i + 1, SpokenDate.DayOfMonth(n))
        }
        val date = nonOverlapping(dateHits).filterNot { scan.negated(it.range.last + 1) }.maxByOrNull { it.range.first }?.value
        return date ?: weekdays.singleOrNull()?.let { SpokenDate.Weekday(it) }
    }

    private fun numberAt(utterance: Utterance, index: Int): Int? {
        utterance.words.getOrNull(index)?.toIntOrNull()?.let { return it }
        return NUMBER_WORDS.firstOrNull { (vocab, _) -> vocab.matchesWord(utterance, index) }?.second
    }

    /** Returns every clock time said (corrections removed), plus a period word that wasn't attached to one. */
    private fun parseTimes(
        utterance: Utterance,
        scan: Scan,
        bareNumbersAreTimes: Boolean,
        recurrenceIndexes: Set<Int>
    ): Pair<List<SpokenTime>, DayPeriod?> {
        // "din mein ek baar" is a frequency, not "during the day".
        val periodHits = nonOverlapping(
            PERIODS.flatMap { (period, vocab) -> hits(utterance, vocab, period) }
        ).filter { hit -> hit.range.none { it in recurrenceIndexes } }
        val livePeriods = periodHits.filterNot { scan.negated(it.range.last + 1) }
        val usedPeriods = mutableSetOf<Hit<DayPeriod>>()

        fun periodEndingAt(index: Int): Hit<DayPeriod>? {
            livePeriods.firstOrNull { it.range.last == index }?.let { return it }
            // "shaam ke 6 baje" - skip one small connector word.
            if (index >= 0 && CONNECTOR.matchesWord(utterance, index)) return livePeriods.firstOrNull { it.range.last == index - 1 }
            return null
        }
        fun periodStartingAt(index: Int): Hit<DayPeriod>? = livePeriods.firstOrNull { it.range.first == index }

        val times = mutableListOf<SpokenTime>()
        var i = 0
        while (i < utterance.size) {
            val word = utterance.lowerWords[i]
            var hour: Int? = null
            var minute = 0
            var consumed = 1
            var explicitPeriod: DayPeriod? = null
            var numberWasWord = false

            val clock = CLOCK_REGEX.matchEntire(word)
            val compact = COMPACT_AM_PM_REGEX.matchEntire(word)
            when {
                compact != null -> {
                    hour = compact.groupValues[1].toInt()
                    minute = compact.groupValues[2].toIntOrNull() ?: 0
                    explicitPeriod = if (compact.groupValues[3] == "am") DayPeriod.MORNING else DayPeriod.EVENING
                }
                clock != null -> {
                    hour = clock.groupValues[1].toInt()
                    minute = clock.groupValues[2].toInt()
                }
                DEDH.matchesWord(utterance, i) -> { hour = 1; minute = 30; consumed = 1; numberWasWord = true }
                DHAI.matchesWord(utterance, i) -> { hour = 2; minute = 30; consumed = 1; numberWasWord = true }
                SAADHE.matchesWord(utterance, i) || SAVA.matchesWord(utterance, i) || PAUNE.matchesWord(utterance, i) -> {
                    val n = numberAt(utterance, i + 1)
                    if (n != null) {
                        consumed = 2
                        numberWasWord = true
                        when {
                            SAADHE.matchesWord(utterance, i) -> { hour = n; minute = 30 }
                            SAVA.matchesWord(utterance, i) -> { hour = n; minute = 15 }
                            else -> { hour = if (n == 1) 12 else n - 1; minute = 45 }
                        }
                    }
                }
                else -> {
                    val n = numberAt(utterance, i)
                    if (n != null && n in 0..23) {
                        hour = n
                        numberWasWord = utterance.words[i].toIntOrNull() == null
                    }
                }
            }

            if (hour == null || hour > 23 || minute > 59) { i++; continue }
            val next = i + consumed

            // A number that's really a count/date/frequency ("15 din", "2 hafte", "25 tarikh", "ek baar").
            if (DAY_COUNT_UNIT.matchesWord(utterance, next) || WEEK_WORD.matchesWord(utterance, next) ||
                DATE_WORD.matchesWord(utterance, next) || MONTH_WORD.matchesWord(utterance, next) || i in recurrenceIndexes
            ) { i = next; continue }

            var end = next
            val hasBaje = BAJE.matchesWord(utterance, end)
            if (hasBaje) end++
            if (explicitPeriod == null) {
                periodStartingAt(end)?.let { hit -> explicitPeriod = hit.value; usedPeriods += hit; end = hit.range.last + 1 }
            }
            if (explicitPeriod == null) {
                periodEndingAt(i - 1)?.let { hit -> explicitPeriod = hit.value; usedPeriods += hit }
            }

            val hasMarker = compact != null || clock != null || hasBaje || explicitPeriod != null
            val partOfRange = isRangePartner(utterance, i, next, times, livePeriods.map { it.range })
            val accept = when {
                // Word numbers ("bana do", "ek baar") are everywhere in Hindi - only a time with
                // "baje", a saadhe/sava/paune, or when the whole answer is just that number.
                numberWasWord -> hasBaje || consumed == 2 || (bareNumbersAreTimes && utterance.size <= 2)
                hasMarker || partOfRange -> true
                else -> bareNumbersAreTimes
            }
            if (accept && !scan.negated(end)) {
                times += SpokenTime(hour, minute, explicitPeriod)
            }
            i = if (accept) end else next
        }

        // The last period word nobody claimed - "din me nhi, shaam me" leaves "shaam".
        val loosePeriod = livePeriods.filterNot { it in usedPeriods }.maxByOrNull { it.range.first }?.value
        // One period word for a whole window ("shaam 6 se 7") applies to both ends.
        val sharedPeriod = times.firstNotNullOfOrNull { it.period }
        val resolved = if (times.size == 2 && sharedPeriod != null) times.map { it.copy(period = it.period ?: sharedPeriod) } else times
        return resolved to loosePeriod
    }

    private val BETWEEN = Vocabulary("beech", "bich", "between", "बीच")

    /** "6 se 7 baje" - the first number has no marker of its own but pairs with one that does. */
    private fun isRangePartner(
        utterance: Utterance,
        index: Int,
        next: Int,
        earlier: List<SpokenTime>,
        periodRanges: List<IntRange>
    ): Boolean {
        if (RANGE_JOINER.matchesWord(utterance, next) && numberAt(utterance, next + 1) != null) {
            val after = next + 2
            return BAJE.matchesWord(utterance, after) || periodRanges.any { it.first == after || it.last == index - 1 } ||
                BETWEEN.occursIn(utterance) || CLOCK_REGEX.matches(utterance.lowerWords.getOrElse(next + 1) { "" })
        }
        val prev = index - 1
        return earlier.isNotEmpty() && RANGE_JOINER.matchesWord(utterance, prev)
    }

    private fun nameWords(utterance: Utterance): Pair<List<String>, List<IntRange>> {
        val covered = BooleanArray(utterance.size)
        for (vocab in KEYWORD_VOCABULARIES) {
            for (range in vocab.findAll(utterance)) range.forEach { covered[it] = true }
        }
        val isName = BooleanArray(utterance.size) { i ->
            val word = utterance.words[i]
            !covered[i] && word.any { it.isLetter() } && !CLOCK_REGEX.matches(word.lowercase()) &&
                !COMPACT_AM_PM_REGEX.matches(word.lowercase())
        }
        val runs = mutableListOf<IntRange>()
        var start = -1
        for (i in 0..utterance.size) {
            if (i < utterance.size && isName[i]) {
                if (start < 0) start = i
            } else if (start >= 0) {
                runs += start until i
                start = -1
            }
        }
        return utterance.words.filterIndexed { i, _ -> isName[i] } to runs
    }
}
