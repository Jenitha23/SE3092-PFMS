# FR-38 foundation: reminders and notification settings

## Scope

This stage implements shared infrastructure, persistent preferences, schedule configuration, permission handling, and cancellation. It does not introduce the seven financial eligibility rules.

The existing salary confirmation workflow (`SalaryViewModel` / `SalaryReminderUseCase`) is retained as an in-app financial workflow. The existing due-recurring/subscription notification checks are extracted into `ExistingExpenseReminderSource` and use the shared delivery guard. There is no competing notification worker or second financial synchronization system.

## Settings and persistence

Settings & Profile now contains seven independent notification switches. Every type has a configurable 24-hour time; weekly summary also has a weekday selector. Daily log defaults to 20:00, weekly summary to Sunday 18:00, and other checks to 09:00, all in the existing app time zone, Asia/Colombo.

Preferences are stored in Preferences DataStore `financial_reminders`, keyed by a hash of the authenticated Firebase UID and reminder type. Each effective setting change rotates a revision token; redundant saves keep the token and existing job. All operations are local and work without connectivity. Cross-user writes are rejected. These settings are device-specific and are not uploaded to Firestore.

The two existing expense notification types default to enabled to preserve prior behavior, subject to Android permission/channel settings. The five new types default to off. Settings explicitly identifies types whose notifications are not available yet. Their saved preferences and schedules work, but their content source intentionally returns no messages at this stage. Notification switches do not remove access to salary confirmation or other financial features.

The UI uses a Hilt `ReminderSettingsViewModel`, immutable StateFlow state, loading/save/error states, guarded submissions, and safe retry text. If settings are written but WorkManager scheduling fails, the UI reports that preferences are saved and offers schedule recovery.

## Scheduling and recovery

- Reuse `ExpenseReminderWorker`, with one unique periodic WorkManager job per UID/type. Daily opportunities repeat daily; weekly summary repeats every seven days. Jobs carry UID, reminder type, revision, and the earliest permitted timestamp.
- Initial delay targets the next configured time. Domain policy rechecks today's/this ISO week's time at execution. An early job retries rather than posting early. Delayed jobs evaluate the current slot; missed days are not emitted as a backlog.
- Jobs run without a network constraint. Existing expense content refresh has a bounded timeout and falls back to Room when unavailable.
- Unchanged settings keep the same work ID. Changed settings replace that type's work. Disabled settings cancel that type's work and any currently visible notification. Account switches/sign-out cancel other users' work and notifications.
- Application startup observes authenticated settings and reconciles schedules. A six-hour maintenance job repairs interruptions between persistence and scheduling. WorkManager persists periodic requests across process death and device restarts.
- A non-exported receiver requests forced schedule repair for boot completion, clock/time-zone changes, and app replacement. It uses WorkManager and has no financial or database logic.
- The former global `ExpenseReminderPeriodicWork` is cancelled. Old unscoped jobs, including formerly enqueued one-offs, have no UID/revision inputs and finish without posting. Visiting expense screens no longer independently enqueues notification jobs or automatically requests permission.

These are deferrable reminders. WorkManager initial delays are minimum delays, not exact delivery appointments; Doze, app standby, battery restrictions, and OS scheduling can delay execution. No exact-alarm permission is requested. See Android's [work request documentation](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

## Delivery and duplicates

Before reading content and again immediately before posting, the shared delivery service checks the authenticated UID, enabled switch, current revision, time, and notification access. Final preference changes and notification posting share a process-wide injected store lock. A disabled setting or stale queued revision cannot bypass delivery guards even if cancellation/reconciliation is interrupted.

A durable per-UID/type/item calendar-slot claim is written before posting. Overlapping workers, retries, dismissed notifications, and process restarts do not repost the same item in that slot. Stable notification tags/PendingIntent identities and `setOnlyAlertOnce` add OS-level duplicate suppression. Posting failures release a claim for retry. Moving the clock backwards does not reuse an older claimed slot.

This deliberately favors at-most-once delivery: if the process dies after a claim is persisted but before Android receives the notification, that slot can be missed. DataStore and Android's notification service do not share a transaction, so exactly-once posting cannot be guaranteed. No financial entry is created or changed by reminder delivery.

## Channels and permission

Six channels cover seven independently controlled types. Due recurring and subscription review reuse the existing `pfms_expense_reminders` channel, preserving users' previous channel-blocking choices. The other five types have separate channels. Channel registration is idempotent and retains Android-managed user choices.

`POST_NOTIFICATIONS` remains declared in the manifest. On Android 13+, Settings offers an explicit Allow notifications action using the Activity Result permission contract, plus a system notification settings action. Permission state refreshes when returning to Settings. Denial blocks notification delivery only; users can still save preferences and use all financial features. Workers check both runtime/app access and the applicable channel before claiming/posting. There is no repeated permission prompt on financial screens. See Android's [notification permission](https://developer.android.com/develop/ui/compose/notifications/notification-permission) and [channel](https://developer.android.com/develop/ui/compose/notifications/channels) guidance.

## Remaining financial rules

| Type | Foundation behavior | Work reserved for the next stage |
| --- | --- | --- |
| Daily log | Switch, daily time, durable schedule; no message | Decide when a daily logging nudge is eligible and add approved content/navigation. |
| Salary day | Switch/time/schedule; in-app confirmation unchanged | Reuse salary template/use case for notification eligibility and monthly suppression. |
| Due recurring items | Existing cached due-item checks use shared scheduling/delivery | Extend/refine FR-38 eligibility without duplicating this working source. |
| Freelance invoice overdue | Switch/time/schedule; no message | Define overdue invoice eligibility from existing income data. |
| Subscription review | Existing reminder-date/review-flag checks use shared scheduling/delivery | Extend/refine the existing review rules as required by the next task. |
| Weekly summary | Switch, weekday/time, weekly schedule; no message | Compute summary eligibility/content from recorded data. |
| Goal pace Behind | Switch/time/schedule; no message | Reuse goal pace calculations and define notification eligibility/suppression. |

## Verification

Builds and complete JVM suite passed: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest`. The JVM report contains **658 tests, zero failures/skips**, including 15 new schedule/ViewModel tests and all live exchange-rate tests.

Android verification uses an API 33 emulator. Final run outputs are kept in the ignored `build/fr38/` directory. Coverage includes actual channels/notifications, cached expense behavior, the seven switches/time/weekday dialogs, settings persistence/isolation, unique work replacement/cancellation, stale revisions, disabling during content loading, account changes, concurrent duplicate suppression, denied access, retryable posting failure, and old unscoped work. Existing financial, offline, migration, error, and FR-37 tests are also selected.

The opt-in restart tests use a real persistent WorkManager database, force-stop the app between two instrumentation processes, check unchanged saved preferences/work ID, and invoke boot/clock/time-zone receiver paths to verify repair and cancellation. They use isolated fixture UIDs, rather than production Firebase accounts. An additional opt-in test runs after actual Android notification permission revocation; financial form regressions also run with permission denied.

Final Android results:

- The 73-test regression selection initially had 72 passes and one test assertion failure: WorkManager pruned a replaced work row rather than retaining cancelled history. The assertion was corrected to accept removal or a finished old row; the targeted rerun passed. The replacement/cancellation implementation did not require a production change.
- Persistent WorkManager restart stages: **2 passed**, including unchanged work identity across force-stop and forced repair after simulated time, time-zone, and boot broadcasts.
- Actual notification-permission denial run: **6 passed**, including five repeated financial error/form checks. The original granted permission was restored afterwards.
- In total, **76 distinct Android checks passed** across the selection and follow-up runs, with no unresolved test failures.
- Normal app cold launch succeeded; its process remained running and its AndroidRuntime crash log was empty. `git diff --check` passed.

## Created files

```text
app/src/main/java/com/pfms/app/domain/model/ReminderSettings.kt
app/src/main/java/com/pfms/app/domain/usecase/ReminderSchedulePolicy.kt
app/src/main/java/com/pfms/app/domain/repository/ReminderSettingsRepository.kt
app/src/main/java/com/pfms/app/data/reminder/ReminderPreferencesStore.kt
app/src/main/java/com/pfms/app/data/reminder/ReminderNotifications.kt
app/src/main/java/com/pfms/app/data/reminder/ExistingExpenseReminderSource.kt
app/src/main/java/com/pfms/app/data/reminder/ReminderDelivery.kt
app/src/main/java/com/pfms/app/data/reminder/ReminderWorkScheduler.kt
app/src/main/java/com/pfms/app/data/reminder/ReminderCoordinator.kt
app/src/main/java/com/pfms/app/data/reminder/LocalReminderSettingsRepository.kt
app/src/main/java/com/pfms/app/data/reminder/ReminderStartup.kt
app/src/main/java/com/pfms/app/data/reminder/ReminderMaintenanceWorker.kt
app/src/main/java/com/pfms/app/di/ReminderModule.kt
app/src/main/java/com/pfms/app/viewmodel/ReminderSettingsViewModel.kt
app/src/main/java/com/pfms/app/ui/component/ReminderSettingsSection.kt
app/src/test/java/com/pfms/app/domain/usecase/ReminderSchedulePolicyTest.kt
app/src/test/java/com/pfms/app/viewmodel/ReminderSettingsViewModelTest.kt
app/src/androidTest/java/com/pfms/app/ReminderFoundationTest.kt
app/src/androidTest/java/com/pfms/app/ReminderSettingsUiTest.kt
app/src/androidTest/java/com/pfms/app/ReminderRestartTest.kt
app/src/androidTest/java/com/pfms/app/ReminderPermissionDeniedTest.kt
docs/FR38-reminder-foundation.md
```

## Modified files

```text
app/src/main/AndroidManifest.xml
app/src/main/java/com/pfms/app/PFMSApplication.kt
app/src/main/java/com/pfms/app/data/sync/ExpenseReminderWorker.kt
app/src/main/java/com/pfms/app/ui/navigation/PfmsNavGraph.kt
app/src/main/java/com/pfms/app/ui/screen/SettingsScreen.kt
app/src/main/java/com/pfms/app/ui/screen/expense/AdvancedExpenseScreens.kt
app/src/androidTest/java/com/pfms/app/CustomTestRunner.kt
app/src/androidTest/java/com/pfms/app/ExpenseReminderIntegrationTest.kt
```

## Limits

- Financial eligibility/content for the five new notification types is intentionally deferred. The two pre-existing rules are reused without implementing the full next stage.
- No Room schema, financial model, financial sync queue, Firestore rules, or authentication behavior is changed. Unrelated existing `.idea` changes are preserved.
- Scheduling is deferrable; exact delivery and vendor-specific physical-device background behavior are not guaranteed. Verification here uses API 33; the source retains minSdk 26 and API-appropriate permission branching, but API 26 hardware is not covered by this run.
- Receiver behavior is simulated in instrumentation; a full physical-device reboot is not part of this run. Force-stop restart persistence is tested separately.
- Preferences and delivery claims are per-device/per-UID, not cross-device synchronized. The documented claim-before-post process-death tradeoff can miss one notification opportunity while preventing duplicate delivery.
