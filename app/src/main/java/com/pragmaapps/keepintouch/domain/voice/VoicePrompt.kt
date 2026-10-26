package com.pragmaapps.keepintouch.domain.voice

/**
 * Every line the assistant can say. Each one is a fixed, pre-recorded clip (res/raw/voice_*.mp3,
 * mapped in data/voice/VoicePromptAudio) spoken in Hinglish - [englishText] is what the chat bubble
 * shows, since the UI stays English. No clip ever says a contact's name or the assistant's own
 * name, so nothing personal leaves the phone and the wake word can change without re-recording.
 */
enum class VoicePrompt(val englishText: String) {
    WAKE_ACK("Hi! Which contact would you like to create a schedule for?"),
    NO_SPEECH("I didn't hear anything. Could you say that again?"),
    LISTENING_TIMEOUT("I'll stop listening for now. Tap the mic whenever you're ready."),

    CONTACT_NO_NAME("Which contact is this schedule for? Please tell me their name."),
    CONTACT_CONFIRM("Is this the contact you want to create the schedule for?"),
    CONTACT_MULTIPLE("I found a few matching contacts. Which one do you mean?"),
    CONTACT_NOT_FOUND("I couldn't find that contact. Please say the name again."),
    CONTACT_NOT_FOUND_2("I still couldn't find it. Try typing the name in the search box below."),
    CONTACT_RETRY("Okay. Please tell me the contact's name and the schedule again."),
    CONTACT_HAS_SCHEDULE("This contact already has a schedule. Do you still want to add another one?"),
    CONTACT_NO_PHONE("This contact doesn't have a phone number saved."),
    NAME_CLASH("Another contact with the same name already has a schedule. Please rename this contact or create the schedule from the app."),
    CONTACTS_PERMISSION_REQUIRED("I need access to your contacts to find people. Please allow it."),

    ASK_TYPE("Should this schedule repeat, or is it a one-time call?"),
    ASK_FREQUENCY("How often? Daily, weekly, every two weeks, or monthly?"),
    ASK_WEEKDAYS("Which day of the week should the call be on?"),
    WEEKDAYS_UNCLEAR("Which days exactly? For example, Saturday and Sunday."),
    ASK_DATE("Which day should the call be? Today, tomorrow, or another day?"),
    DATE_IN_PAST("That date has already passed. Please pick an upcoming day."),

    ASK_TIME("Do you want a fixed time, a time window like 10 to 11, or no fixed time?"),
    TIME_AM_PM("Morning or evening?"),
    TIME_IN_PAST("That time has already passed today. Please pick a later time."),
    TIME_WINDOW_INVALID("The window ends before it starts. Please say the time window again."),

    SUMMARY_CONFIRM("Here's your schedule. Shall I save it?"),
    EDIT_SUMMARY("What would you like to change: the contact, the days, or the time?"),
    DUPLICATE_SCHEDULE("This exact schedule already exists for this contact."),
    SAVED_ONE("Done! Your schedule has been created."),
    SAVED_MANY("Done! Your schedules have been created."),
    PARTIAL_SAVE_FAILED("Some schedules were saved, but a few couldn't be. Please check them in the app."),
    SAVE_FAILED("Sorry, something went wrong while saving. Please try again."),
    NOTIFICATION_PERMISSION_REQUIRED("Please allow notifications so I can remind you before the call."),
    ASK_ANOTHER("Would you like to create a schedule for someone else too?"),
    CANCELLED("Okay, I've cancelled it."),
    GOODBYE("Alright, talk to you later!"),

    NOT_UNDERSTOOD("Sorry, I didn't catch that. Could you say it again?"),
    NOT_UNDERSTOOD_2("Sorry, I still didn't get that. You can also pick one of the options below."),
    YES_NO_UNCLEAR("Please answer with a yes or a no."),
    MIC_ERROR("The microphone stopped working. Please try again in a moment."),
    MIC_PERMISSION_REQUIRED("I need microphone access to hear you. Please allow it."),

    // First conversation ever - who am I talking to?
    INTRO_ASK_NAME("Hello, I'm your voice assistant for call schedules. What should I call you?"),
    INTRO_NAME_NOT_CAUGHT("Sorry, I didn't catch your name. Please say it again."),
    INTRO_NAME_SKIPPED("No problem. Who would you like to schedule a call with?"),
    INTRO_WELCOME_SIR("Nice to meet you, sir. I can create call schedules. Which contact shall we start with?"),
    INTRO_WELCOME_MAAM("Nice to meet you, ma'am. I can create call schedules. Which contact shall we start with?"),
    INTRO_WELCOME_NEUTRAL("Nice to meet you. I can create call schedules. Which contact shall we start with?"),

    HONORIFIC_FIX_MAAM("My apologies, ma'am. Hello again. I'll remember that."),
    HONORIFIC_FIX_SIR("My apologies, sir. Hello again. I'll remember that."),
    HONORIFIC_FIX_NEUTRAL("Of course. I'll remember your preference and address you accordingly."),
    HONORIFIC_CHANGE_ACK("Of course. Tell me how you'd like to be addressed, and I'll remember."),

    // Greeting when the wake word is said on its own.
    WAKE_GREET_SIR_1("Hello, sir. Who would you like to schedule a call with?"),
    WAKE_GREET_SIR_2("Yes, sir. Which contact would you like to schedule a call with?"),
    WAKE_GREET_SIR_3("Welcome back, sir. Which contact shall we create a schedule for?"),
    WAKE_GREET_MAAM_1("Hello, ma'am. Who would you like to schedule a call with?"),
    WAKE_GREET_MAAM_2("Yes, ma'am. Which contact would you like to schedule a call with?"),
    WAKE_GREET_MAAM_3("Welcome back, ma'am. Which contact shall we create a schedule for?"),
    WAKE_GREET_NEUTRAL_1("Hello. Who would you like to schedule a call with?"),
    WAKE_GREET_NEUTRAL_2("Which contact would you like to schedule a call with?"),
    WAKE_GREET_NEUTRAL_3("Welcome back. Which contact shall we create a schedule for?"),

    // Small talk. The how-are-you and are-you-there lines end by asking for a contact, so they're
    // only used before one is picked - see VoiceConversation.handleSmallTalk.
    SMALLTALK_HOW_ARE_YOU_1("I'm doing great, thanks for asking. Who shall we create a schedule for?"),
    SMALLTALK_HOW_ARE_YOU_2("All good, thank you. Who would you like to schedule a call with?"),
    SMALLTALK_HOW_ARE_YOU_3("I'm very well, thank you. Who would you like a call reminder for?"),
    SMALLTALK_HOW_ARE_YOU_4("I'm well, thank you. Which contact shall we start scheduling with?"),
    SMALLTALK_HOW_ARE_YOU_5("I'm doing very well, thanks. Who shall I schedule a call with?"),
    SMALLTALK_HOW_ARE_YOU_6("I'm great, thanks for asking. Which contact would you like to create a schedule for?"),
    SMALLTALK_THANKS_1("You're welcome. I'm glad I could help."),
    SMALLTALK_THANKS_2("My pleasure. Call me again whenever you need help."),
    SMALLTALK_THANKS_3("You're welcome. I'm here to help."),
    SMALLTALK_WHO_ARE_YOU_1("I'm your assistant in this app. I create call schedules to help you stay connected."),
    SMALLTALK_WHO_ARE_YOU_2("I'm your in-app assistant. I create call schedules to help you keep in touch with loved ones."),
    SMALLTALK_HELP_1("Tell me who you want to call and how often. I'll create the schedule."),
    SMALLTALK_HELP_2("Give me the contact and how often you'd like to call. I'll handle the schedule."),
    SMALLTALK_PRAISE_1("Thank you. I'm glad I could be useful."),
    SMALLTALK_PRAISE_2("Thank you. That's kind of you to say."),
    SMALLTALK_OFF_TOPIC_1("For now, I can only help with creating call schedules."),
    SMALLTALK_OFF_TOPIC_2("I can't help with that yet. I can create call schedules for you."),
    SMALLTALK_OFF_TOPIC_3("My help is currently limited to call schedules. I'm happy to help with those."),
    SMALLTALK_BYE_1("All right. Call me again whenever you need me."),
    SMALLTALK_BYE_2("Talk to you later. Take care."),
    SMALLTALK_ARE_YOU_THERE("Yes, I'm here. Who shall we create a schedule for?"),
    SMALLTALK_REPEAT("Of course. I'll repeat that for you."),
    SMALLTALK_CALL_NOW("I can't place calls, but I can create a call schedule."),
    SMALLTALK_USER_SORRY("No problem. Take your time."),
    SMALLTALK_FRUSTRATED("Sorry, I haven't understood you correctly. Please tell me again."),

    // Flow.
    MULTI_DETECTED("Got it. I'll create the contacts' schedules one at a time."),
    MULTI_NEXT("Let's move on to the next contact's schedule."),
    MULTI_ALL_DONE("All the schedules you requested have been created."),
    CONTACT_SKIPPED("Okay, let's skip this contact and move on."),
    WRONG_CONTACT_ACK("Sorry, the wrong contact was selected. Please tell me the correct name."),
    EDIT_DONE("Okay, I've updated the details."),
    EXAMPLE_HINT("For example, say, \"Schedule a call with Mum every Sunday evening.\"")
}
