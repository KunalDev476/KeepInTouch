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
    MIC_PERMISSION_REQUIRED("I need microphone access to hear you. Please allow it.")
}
