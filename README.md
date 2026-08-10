# KeepInTouch

A calm daily-habit app: schedule the people you want to stay close to, and the app quietly
checks them off when it detects a real, qualifying WhatsApp call with them. The Today screen is
the product; everything else supports it.

This began as a feasibility POC proving WhatsApp call detection was possible at all (see git
history / prior conversation for that phase). This is the POC's proven detection and matching
logic, carried over unchanged in substance, wrapped in a real layered app with a proper design
system, encryption at rest, and the full screen set a real product needs.

## Stack

Kotlin, Jetpack Compose + Material 3 (dynamic color with a warm teal-green brand fallback, full
dark theme), Room (encrypted via SQLCipher), Hilt, Coroutines/Flow, Compose Navigation, single
activity. `compileSdk` 36 / `targetSdk` 36 / `minSdk` 34.

## Architecture

Strict layering, enforced by what depends on what:

```
domain/          Pure Kotlin. No android.* imports, no Room, no Compose.
  model/         Immutable data classes (Contact, Schedule, ScheduleCompletion, CallEvent, PendingMatch)
  util/          Pure, unit-tested functions: NameNormalizer, NameSimilarity, QualifyingCallRule,
                 RecurrenceEngine, DateMath, PhoneMasking
  usecase/       Business logic that depends only on domain *interfaces*:
                 MatchCallToScheduleUseCase, FulfillScheduleUseCase, ProcessFinalizedCallUseCase,
                 ResolvePendingMatchUseCase, ManuallyCompleteScheduleUseCase, SkipScheduleTodayUseCase,
                 CallEventDiagnostics
  repository/    Interfaces only (ContactRepository, ScheduleRepository, CallEventRepository, ...) -
                 dependency inversion: domain defines the contract, data implements it.

data/            Android-touching implementation.
  db/            Room entities, DAOs, AppDatabase, entity<->domain mappers (Room never leaks past here)
  repository/    Repository implementations (ContentResolver, DAOs) behind the domain interfaces
  notification/  CallEpisodeProcessor, CallNotificationExtras, AvatarHasher - the actual notification parsing
  security/      PassphraseManager - Android Keystore-backed SQLCipher passphrase
  prefs/         DetectionPreferences, OnboardingPreferences (plain SharedPreferences - not secret data)

notification/    WhatsAppCallListenerService - the actual NotificationListenerService, a thin
                 @AndroidEntryPoint shell that just filters WhatsApp's two packages and delegates

di/              Hilt modules (DatabaseModule, RepositoryModule)

ui/              theme/ (design system), components/ (reusable), navigation/, screens/<name>/
                 (ViewModel + Composable per screen). No business logic in Composables - only
                 straight StateFlow reads and event callbacks.
```

MVVM, unidirectional data flow, immutable UI state via `StateFlow`. The matching engine
(`MatchCallToScheduleUseCase`, `ProcessFinalizedCallUseCase`) depends only on repository
*interfaces*, so it has zero Android/Room dependency and is fully unit-testable in isolation -
see `app/src/test`.

## Security model

- **No `INTERNET` permission, no networking library, anywhere in the dependency graph.** Nothing
  this app does can leave the device.
- **The Room database is encrypted at rest** via SQLCipher (`net.zetetic:sqlcipher-android`),
  wired in as Room's `openHelperFactory`.
- **The SQLCipher passphrase** is a random 256-bit value generated once on first run and
  encrypted with an AES-256 key that lives in the **Android Keystore** and never leaves it
  (`PassphraseManager`, plain `java.security`/`javax.crypto`, not a third-party library). Only the
  *ciphertext* is persisted in ordinary `SharedPreferences`; decrypting it requires the
  Keystore-resident key, which the OS refuses to export.
  - We deliberately did **not** use `androidx.security:security-crypto`
    (`EncryptedSharedPreferences`/`MasterKey`) for this: Google deprecated that library in April
    2025 with no direct official replacement, and this is the one piece of the app that's
    genuinely security-critical, so it seemed wrong to build it on an unmaintained dependency.
    (This was an explicit, discussed decision - see conversation history - not an oversight.)
- **Phone numbers are masked in the UI** (`PhoneMasking` - only the last 2 digits are ever shown)
  everywhere a number is displayed (Contact Detail).
- **Contacts access is read-only.** The app never writes to the address book.
- Release builds enable R8 (`isMinifyEnabled = true`, `isShrinkResources = true`).
- `android:allowBackup="false"` - the encrypted DB should not be swept into Android's own
  auto-backup/device-transfer mechanisms, even encrypted.

## The qualifying-call rule ("done")

Carried over unchanged from the POC. A call fulfills a schedule only if **all** hold:

1. The episode reached **ONGOING** (`android.callType == 2`) - answered/connected.
2. Inferred duration ≥ the configured minimum (Settings → Detection; **5000 ms by default**),
   computed as `endTime − ongoing "when"` (the notification's own `Notification.when`, captured
   the moment ONGOING was first observed); if unavailable, falls back to
   `removed elapsedRealtime − ongoing-posted elapsedRealtime`.
3. Direction is allowed per Settings (**INCOMING and OUTGOING both count by default**, either can
   be excluded) - `UNKNOWN` direction never qualifies regardless of settings.
4. It is **not** a group call.
5. If it's a video call, it qualifies the same as voice **unless** "video calls count the same as
   voice" is turned off in Settings.

Missed / rejected / short / never-connected / group calls are logged in Diagnostics but never
fulfill anything.

### "Stable internet" cannot be verified - by design, not by omission

A `CallStyle` notification carries no network-quality, codec, jitter, or reconnection signal of
any kind. The only proxy available is *"the ONGOING state lasted past the threshold without the
notification being torn down and reappearing."* That is what "qualified" means here. It is
explicitly **not** proof of a stable connection - a call could have 6 seconds of choppy,
reconnecting audio and still pass; a perfectly clean 4.9-second call would fail. This is
documented, not silently assumed away, and is called out again on the Diagnostics screen.

### Outgoing-call detection

Direction is inferred purely from what was observed for a notification `key`: `INCOMING` if
`callType == 1` (ringing) was ever seen; **tentatively `OUTGOING`** if the very first state ever
observed for a brand-new key is `callType == 2` (ONGOING) with no preceding INCOMING state;
`UNKNOWN` otherwise. During POC testing on a real device, **outgoing calls were confirmed to
produce a detectable notification** - both directions are enabled by default in Settings as a
result. Settings' Diagnostics section still keeps a live outgoing-detection status line, since
this can vary by WhatsApp version/OS - if it ever reads "0 observed" on a device where outgoing
calls were actually placed, that's a real finding worth re-examining, not just historical trivia.

### Cellular (regular phone) call detection - added, unverified

`WhatsAppCallListenerService` also watches whatever package `TelecomManager.getDefaultDialerPackage()`
resolves as the device's current default dialer (a plain, unprotected query - confirmed against
AOSP source that the no-arg overload needs no permission; a different, permission-gated overload
exists and is not the one used here), applying the exact same CallStyle heuristic already used for
WhatsApp. This does **not** add a new permission or violate the notification-listener-only
architecture - it's the same mechanism, just no longer hardcoded to two package names.

What's genuinely uncertain: a regular phone call is primarily rendered by the OS's own in-call
screen via the Telecom/InCallService framework, not necessarily via a notification at all. Whether
a given device's default dialer *also* posts a CallStyle-compatible notification (many show a
"return to call" chip while you're in another app, which might or might not qualify) is unverified
and will vary by OEM. Settings' Diagnostics section has a live "Cellular call detection" status
line reporting the resolved dialer package and how many matching notifications have actually been
observed - place a real cellular call and check it rather than assuming this works.

### The `isGroupCall` extra is unverified, and instrumented rather than assumed

There is no publicly documented `Notification` extra literally named `isGroupCall` (unlike
`android.callType`/`android.callIsVideo`, which are real documented `CallStyle` constants).
`CallNotificationExtras.GROUP_CALL_CANDIDATE_KEYS` probes several plausible candidate keys
(`isGroupCall`, `android.isGroupCall`, `android.isGroupConversation`) and records which (if any)
fired, per call, in `rawJson` - visible on the Diagnostics screen along with an explicit note that
this is unverified.

## Matching model: call → schedule

The notification's caller name (from the `Person` list, falling back to the notification title)
is matched only against **name snapshots** - never phone numbers, because the notification never
exposes one.

| Case | Condition | Result |
|---|---|---|
| (a) | Exactly one active schedule's snapshot matches (raw-equal, normalized-equal, or a unique fuzzy match ≥ 0.85 similarity) | Auto-attach. Confidence = `EXACT` / `NORMALIZED` / `FUZZY` |
| (b) | Zero schedules match, but the name matches a synced Contact with no active schedule | Confirm-screen prompt: "create a schedule for this contact?" Nothing fulfilled yet |
| (c) | The normalized name matches 2+ active schedules (duplicate display names) | Confirm screen: "which of these was this call with?" |
| (d) | A fuzzy match exists but isn't unique/confident enough (similarity in `[0.6, 0.85)`, or candidates tie) | Confirm screen, same as (c) |

`0.85`/`0.6` (`NameSimilarity.AUTO_MATCH_THRESHOLD`/`MIN_CANDIDATE_THRESHOLD`) are judgment calls,
not platform-specified values - documented so they can be tuned from real data.

Matching (attaching `matchedScheduleId`/`matchConfidence`, for Diagnostics' "why matched / why
not") runs for **every** finalized call. **Acting** on a match - auto-fulfilling or raising a
Confirm prompt - only happens for **qualifying** calls.

### Avatar learning

An avatar is only ever "learned" from a WhatsApp call notification's `largeIcon` (SHA-256 over a
normalized 32×32 ARGB bitmap), on a confident fulfillment - auto-match or Confirm-screen
resolution alike (`FulfillScheduleUseCase`). It's compared only to other learned
notification-avatar hashes, **never** to the address-book photo, and pre-highlights (not
auto-selects) a Confirm-screen candidate on a future duplicate-name match.

## Recurrence rules

- **ONE_TIME**: due once; fulfilling sets `active = false`. No further occurrences (resuming it
  from Contact Detail makes it due again, on purpose - a deliberate, simple interpretation).
- **DAILY**: fulfilling advances `nextDueDate` to the day *after the completion date* (not the
  stale due date) - so a late fulfillment doesn't leave the schedule perpetually overdue.
- **WEEKLY**: same idea, +7 days from the completion date.
- **"Skip today"** (Today's swipe-left): advances `nextDueDate` by exactly one day, records no
  completion, gives no streak credit - applies uniformly to every recurrence type.
- **"Mark done"** (Today's swipe-right): records a completion exactly like a detected call, but
  with `callEventId = null` (nothing was detected) and `matchConfidence = CONFIRMED_BY_USER`. This
  is why `ScheduleCompletion.callEventId` is nullable - the spec's own data model implies every
  completion traces to a call, but an explicit manual-completion feature was also required, and
  those two requirements only reconcile with a nullable FK.
- **Streak** (People/Contact Detail) is currently just "how many completions exist for this
  schedule" - there's no separate tracking of *missed* occurrences yet, so it can't distinguish
  "5 in a row" from "5 total, with gaps." A stricter streak would need the app to actively record
  a miss when a due date passes unfulfilled, which nothing currently does.

## Known limitations

- No phone number is ever available from the notification - matching is name-only, with all the
  fragility that implies (see Matching model above).
- Network/call "stability" is fundamentally undetectable from a notification (see above).
- Duplicate contact names always need human confirmation - never silently guessed.
- The `isGroupCall` signal is unverified (see above) - group-call exclusion may not fire on every
  WhatsApp/OS version until confirmed against a real group call.
- `hiltViewModel()` (from `androidx.hilt.navigation.compose`) shows a deprecation warning
  (superseded by a package requiring Room/Lifecycle versions that need `compileSdk 37`). Left
  as-is deliberately: fixing it would violate this project's `compileSdk 36` requirement.
  `rememberSwipeToDismissBoxState`'s `confirmValueChange` parameter is also deprecated with no
  simple replacement (the suggested fix is a nontrivial dynamic-anchors rewrite). Both still work
  correctly; both are cosmetic warnings, not left silently - see the code for the exact lines.

## Room migrations

- `exportSchema = true`, with schema JSON committed at `app/schemas/.../{1,2}.json`.
- `DatabaseMigrationTest` (instrumented) covers both the v1 schema shape and the real v1 -> v2
  migration.
- **v1 -> v2** (first real schema change): `call_events.notifKey`'s unique index was dropped.
  Real devices were observed reusing the same WhatsApp notification key across separate,
  sequential calls (e.g. a missed call, then a later fresh ring) rather than minting a new key
  per call. The unique constraint meant the second call silently got merged into - and its
  processing blocked by - the first (already-finalized) row's `endTime != null` guard, so every
  call after the first one on a reused key was never detected. `Migrations.MIGRATION_1_2` drops
  and recreates the index as non-unique; `CallEventDao.findByNotifKey` now only ever matches the
  *in-flight* (unfinalized) episode for a key, so a finalized row is never mistaken for a
  continuation, and multiple historical rows can legitimately share a notifKey over time.
- **Policy for v2+**: every schema change must come with (a) a bumped `version`, (b) a real
  `Migration` object, (c) a `MigrationTestHelper` test exercising that exact migration, and (d)
  the newly exported schema JSON committed alongside it. Do not use
  `fallbackToDestructiveMigration()`.

## Tests

- `app/src/test` (pure JVM, no Android/Robolectric needed - the domain layer has no Android
  dependency): `NameNormalizerTest`, `NameSimilarityTest`, `QualifyingCallRuleTest`,
  `RecurrenceEngineTest`, `PhoneMaskingTest`, `MatchCallToScheduleUseCaseTest` (against in-memory
  fake repositories in `fakes/`). Run: `./gradlew :app:testDebugUnitTest`.
- `app/src/androidTest` (instrumented, needs a device/emulator): `DatabaseMigrationTest` (schema
  smoke test, see above) and `DatabaseSmokeTest` (real DAO round-trips against an in-memory Room
  instance, including the nullable-`callEventId` manual-completion path). These compile and
  package cleanly (`./gradlew :app:assembleDebugAndroidTest`) but have **not been executed** in
  this session - no device/emulator was connected. Run with a device attached:
  `./gradlew :app:connectedDebugAndroidTest`.

## Building

Requires a JDK (Android Studio's bundled JBR works: point `JAVA_HOME` at
`Android Studio.app/Contents/jbr/Contents/Home` on macOS).

```
./gradlew :app:assembleDebug
```

Build-tooling notes:
- AGP 9's "built-in Kotlin" support rejects KSP's (Room's/Hilt's annotation processor) legacy
  `kotlin.sourceSets` registration by default. `gradle.properties` sets
  `android.disallowKotlinSourceSets=false` - AGP's own documented escape hatch for this
  transitional incompatibility.
- `androidx.hilt:hilt-navigation-compose` is pinned to **1.3.0**, not the latest 1.4.0: 1.4.0
  transitively pulls in `lifecycle 2.11.0`, which requires `compileSdk 37`, conflicting with this
  project's explicit `compileSdk 36` requirement.

## Manual test script

Prerequisites: a second phone (or WhatsApp Web/linked device) to place/receive calls, and at
least one saved contact who has WhatsApp.

1. **Onboarding.** Fresh install → value-prop page → Contacts step (grant; confirm auto-advance
   to the next page without tapping Next) → Notification-access step (Open Settings, enable the
   app, return via back/recents; confirm it reflects granted without restarting the app) →
   "Get started" lands on Today.
2. **People: schedule someone.** People tab → All contacts → tap **+** on a WhatsApp contact →
   Weekly, due today → Create. Confirm it now appears under People → Scheduled, and on Today.
3. **Qualifying incoming call.** Have the other phone call this contact's WhatsApp number. Answer,
   stay connected **> 5s** (or your configured minimum), hang up. Expect: Today's row check-off
   animates within a second or two; Settings → Diagnostics shows the event `QUALIFIED`,
   `direction=INCOMING`; Contact Detail shows one timeline entry and streak = 1.
4. **Qualifying outgoing call.** From the test device, call that contact yourself, stay connected
   past the threshold, hang up. Confirm it fulfills identically to an incoming call, and
   Diagnostics' outgoing-detection line increments.
5. **Missed/rejected call (must NOT fulfill).** Let it ring out or reject it. Confirm Diagnostics
   shows `qualified=false`, reason "Never reached ONGOING," and no completion anywhere.
5b. **Call right after a missed call (regression check for the v1→v2 fix).** Immediately after
   step 5, have the same contact call again and let this one qualify (answer, stay connected past
   the threshold). Confirm it fulfills normally and shows up as its own row in Diagnostics/Contact
   Detail timeline - both the missed call *and* the qualifying one should be visible, not just one
   swallowing the other. (This is the exact scenario that used to silently stop detecting anything
   further once a notification key got reused - see "Room migrations.")
6. **Short call (must NOT fulfill).** Answer and hang up within ~2-3 seconds. Confirm
   `qualified=false` with a duration-below-threshold reason.
6b. **Cellular call (unverified - see "Cellular call detection").** Place or receive a regular
   phone call (not WhatsApp), staying connected past the threshold. Check Settings → Diagnostics'
   "Cellular call detection" status line: if it now shows an observed count, the device's dialer
   does post a compatible notification and the call should qualify/fulfill like any other; if it
   still reads "unconfirmed," that's the answer to the open question on this device, not a bug.
7. **Contact without WhatsApp (negative case).** Schedule a non-WhatsApp contact from People (the
   **+** and row tap both still work for them, greyed with a "can't auto-detect" hint). Confirm
   the Create sheet shows the explicit warning, and no call can ever mark it done.
8. **Caller not in contacts.** Have someone outside your address book call you. Confirm
   Diagnostics logs a qualifying event with no matched schedule and nothing appears on Confirm
   (case: `NoMatch`).
9. **Duplicate display names.** Create two contacts sharing an exact display name (e.g. two
   "Alex"es), schedule both. Have one call and qualify. Confirm it does **not** auto-fulfill
   either; it appears on Confirm (reached via Today's banner) asking which Alex; resolving
   fulfills only the chosen one with `CONFIRMED_BY_USER`.
10. **Emoji/decorated names.** Confirm a call from "Priya ❤️" still matches a schedule snapshot of
    "Priya" as `NORMALIZED`, not fuzzy/no-match.
11. **WhatsApp Business.** Repeat step 3 with a `com.whatsapp.w4b` call if available - captured
    identically.
12. **Group call (must NOT fulfill a 1:1 schedule).** Join/start a WhatsApp group call including a
    scheduled contact, stay connected past the threshold. Confirm Diagnostics flags
    `isGroupCall=true` (or explicitly shows the candidate keys as unverified if none fired) and no
    1:1 schedule is fulfilled.
13. **Video call.** Repeat step 3 as a video call with the default Settings (video counts). Confirm
    it fulfills identically. Then turn off "video calls count the same as voice" in Settings and
    repeat - confirm it now logs but does not fulfill.
14. **Manual mark-done / skip (Today).** Swipe a due row right - confirm it completes instantly
    with `CONFIRMED_BY_USER` and no call event. On a different due row, swipe left - confirm
    `nextDueDate` moves forward one day with no completion recorded.
15. **Pause/resume/delete (Contact Detail).** From People → Scheduled, tap a person → pause the
    schedule (confirm it disappears from Today) → resume it (confirm it reappears if due) →
    delete it (confirm dialog, then it's gone from People).
16. **Data export/clear (Settings).** Export data → pick a location via the system file picker →
    confirm the JSON file contains contacts/schedules/completions/call events. Clear all data
    (confirm dialog) → confirm People/Today/Contact history are all empty afterward.
17. **Crash-proofing.** Force-stop and relaunch mid-call, or toggle notification access off/on
    mid-session. Confirm no crash, and previously finalized events remain intact in Diagnostics.
18. **Recurrence advance.** After a Weekly schedule is fulfilled, confirm `nextDueDate` moved
    forward 7 days from the completion date (not the old due date) and it stays active. Repeat
    with a One-time schedule and confirm it becomes inactive instead.
