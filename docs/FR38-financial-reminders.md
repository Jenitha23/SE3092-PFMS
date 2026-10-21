# FR-38: all seven financial reminder types

This completes the financial rules on top of the existing FR-38 notification foundation. The same Hilt worker, WorkManager scheduler, DataStore preferences/claims, channels, permission handling, and Settings controls are reused. No second notification or financial synchronization system is introduced.

## Implemented behavior

| Reminder | Eligibility and content | Duplicate policy | Destination |
| --- | --- | --- | --- |
| Daily log | Neutral prompt at/after the selected daily time, including users with no financial records. | Once per configured calendar day. | Dashboard, with existing Add Income/Add Expense actions. |
| Salary day | Reuses `SalaryReminderUseCase`: enabled valid salary template, payday clamped to the last day of a short month, and this month not confirmed. A delayed check can notify after payday while confirmation is outstanding. | Once per template/calendar month. Confirmation suppresses further eligibility. | Income list, which supports the existing salary confirmation workflow. |
| Due recurring items | Reuses `RecurringExpenseCalculator.isDue` for active, non-cancelled templates whose next due date has arrived. Each due item has its own notification. | Once per template/due date. Confirmation advances the next due date, ending eligibility for the old occurrence. | Recurring expenses, or Subscriptions for subscription payments. |
| Freelance invoice pending over 14 days | `Freelance` records with `Expected` or legacy `Pending` status, a valid selected invoice date, and age strictly greater than 14 calendar days. Received/paid/cancelled/other statuses, deleted records, invalid dates, and future dates are excluded. | Once per invoice/pending date; another day passing, notes, sync timestamps, or amount edits do not re-alert an unchanged pending invoice. | Income list and its existing invoice payment actions. |
| Subscription review | Reuses `RecurringExpenseCalculator.isReviewDue`: active, non-cancelled subscription with a reached review date, or explicitly marked `REVIEW`. Clearing the review or deactivating the subscription removes eligibility. | Once per subscription/review date and review flag. | Subscriptions. |
| Weekly summary | The seven completed calendar days before the configured weekly reminder day. Shows recorded realized income, expenses, and signed net savings. Uses existing historical LKR conversion and realized-income/crypto rules. | Once per configured weekly opportunity. No catch-up notification for every missed week. | Spending analytics. |
| Goal pace Behind | Reuses `CalculateGoalProgressUseCase` and `CalculateGoalPaceUseCase`, including the existing trailing-three-completed-month benchmark. Only a valid, active, incomplete goal classified Behind is eligible. | Once per observed Behind episode. Observed On Track/completion/inactivity re-arms a later Behind episode; changing the goal target amount/date also starts a new episode. Daily fluctuations while still Behind do not. | Savings goal and its existing pace display. |

All clocks/calendar boundaries use the existing `Asia/Colombo` app time zone. Each switch independently controls its reminder type. Subscription payment-due and review reminders are separate actions: when both are enabled and eligible, both can appear, each with its own occurrence claim.

## Invoice date policy

The existing income model has no separate invoice-issued-date field. For `Expected` freelance records, `Income.receivedDate` is the selected invoice date. It is the authoritative date for invoice ageing, rather than `createdAt` (logging activity) or `updatedAt` (edits/sync). The existing form now labels that selected date **Invoice Date** while an expected freelance invoice is being entered. Back-dated invoices keep their selected dates through the existing offline/sync implementation.

Exactly 14 calendar days is not overdue; 15 days is. An unknown/invalid invoice date is not replaced with a guessed creation or synchronization date. The existing UI uses Expected/Received and deletion; imported legacy Pending/Paid/Cancelled statuses are handled conservatively without adding a new invoice cancellation workflow.

## Integration and architecture

- `FinancialReminderUseCases` owns eligibility, reminder text, weekly financial calculations, and the goal assessment. It reuses existing salary, recurrence, income, progress, and pace calculations. Composables do not calculate eligibility.
- `CachedFinancialReminderRepository` reads the authenticated user's existing Room records in a consistent transaction. It avoids transient empty emissions from presentation-oriented Flow APIs. New DAO methods are read-only queries; the Room schema remains version 6 with no entity/migration changes.
- Data remains local-first. The prior bounded recurring/subscription refresh and Room fallback are retained. Other rules use the same cached income, expenses, salary template, active goal, and goal ledger used by the financial features. No network request is required to calculate a cached reminder.
- `FinancialReminderContentSource` replaces the foundation's two-type adapter and maps domain destinations to existing navigation routes. All seven types are now available; the foundation's unavailable labels are removed. Existing switches, times, weekdays, and defaults are preserved.
- The existing worker still rejects wrong users, disabled preferences, stale setting revisions, missing permission/channel access, and early execution. The source revalidates cached eligibility before delivery, so an invoice paid or an occurrence confirmed during loading is skipped. Visible notifications no longer represented by eligible candidates are cleared on successful checks.
- The existing DataStore claim mechanism supports financial occurrences alongside daily/weekly slots. Claims, goal episode state, settings, and work survive process restarts. Posting failure releases its claim for retry. Overlapping workers share the same injected lock/ledger.
- Notification intents use the existing `MainActivity`, carry the expected UID, and only accept allowlisted financial routes. Authentication and app unlock are still required. A reminder belonging to a different signed-in account is consumed without navigation; old-account workers also fail their delivery guard.
- Notifications use the existing channels and permission flow, immutable PendingIntents, stable identities, private lock-screen visibility, and expanded text for summaries. Notification failures remain inside background work and do not interrupt financial writes.

## Weekly amounts and invalid data

Only records with selected transaction dates inside the weekly interval are included. Expected freelance invoices are not realized income. Crypto losses subtract from income; foreign income uses its saved historical LKR amount without live exchange-rate lookup. Goal allocations are not subtracted from general net savings a second time.

An empty week explicitly says no income/expenses were recorded and displays recorded zero totals. Missing dates, invalid amounts, or unavailable saved conversions produce a Partial Data explanation, rather than estimated amounts. Arbitrary-precision accumulation avoids overflow in weekly totals. Goal alerts are suppressed for known invalid/missing required details or extreme values that could overflow the existing Long-based pace calculator; no unreliable Behind conclusion is posted.

## Verification

Full build command: `:app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --console=plain` using Android Studio's JDK.

- **679 JVM tests passed, zero failures/skips**, including 21 new financial-rule/navigation tests and existing live exchange-rate tests.
- Tests cover all seven rules; salary early/due/confirmed/disabled and February day-31 clamping; multiple recurring items; confirmation advancement; invoice age 14 versus 15 days; selected invoice date versus logging/sync timestamps; paid/cancelled/invalid/future invoices; subscription date/flags/activity; empty and populated weekly totals; foreign income, crypto losses, negative savings, and overflow; Behind versus On Track; completed/inactive goals; and safe navigation/account checks.
- Android API 33 emulator selection includes the existing financial, offline, error, FR-37, reminder infrastructure, Settings, and migration checks plus 11 new all-seven-rule integration checks. They use real Room/DataStore and the production snapshot/domain/content/delivery classes, with captured notification calls for financial-rule assertions. Existing notification integration tests exercise actual Android channels and posting/cancellation.
- Restart tests use a persistent WorkManager database and a force-stop between processes. They now also persist an invoice occurrence claim and Behind episode, checking that neither is reset on restart. Existing boot/time/time-zone repair and cancellation checks remain included.
- Actual notification-permission denial and financial-form regressions are run separately, then the original permission is restored.

Final API 33 emulator results:

- The 84-check selection passed 83 checks initially. One existing OS notification test observed only the first notification before Android's asynchronous posting finished. The test now waits for the expected service state with a bounded timeout; all three actual-notification tests passed on rerun. No production change was needed for that test timing issue.
- All 11 new financial-reminder integration checks passed, including independent switches, two-date invoice/goal suppression, paid-during-load revalidation, multiple due items, completed goal suppression, weekly totals, account isolation, safe destinations, and successful offline financial writes after notification failure.
- Both persistent WorkManager/restart stages passed, including the invoice occurrence and Behind episode surviving a force-stop, plus boot/time/time-zone repair and cancellation.
- The actual permission-denial run passed all six checks. It checks all seven types are suppressed and preferences remain writable, alongside five repeated financial-form/error regressions. Original notification permission was restored.
- **87 distinct Android checks passed**, with no unresolved failures. Logs are under the ignored `build/fr38-complete/` directory.
- Normal debug app cold launch succeeded; its process remained running and its AndroidRuntime crash log was empty. `git diff --check` passed.

## Created files

```text
app/src/main/java/com/pfms/app/domain/model/FinancialReminder.kt
app/src/main/java/com/pfms/app/domain/repository/FinancialReminderRepository.kt
app/src/main/java/com/pfms/app/domain/usecase/FinancialReminderUseCases.kt
app/src/main/java/com/pfms/app/data/repository/CachedFinancialReminderRepository.kt
app/src/main/java/com/pfms/app/data/reminder/FinancialReminderContentSource.kt
app/src/test/java/com/pfms/app/domain/usecase/FinancialReminderUseCasesTest.kt
app/src/androidTest/java/com/pfms/app/FinancialReminderTestSupport.kt
app/src/androidTest/java/com/pfms/app/FinancialReminderIntegrationTest.kt
docs/FR38-financial-reminders.md
```

## Modified or replaced files

```text
app/src/main/java/com/pfms/app/MainActivity.kt
app/src/main/java/com/pfms/app/data/local/dao/IncomeDao.kt
app/src/main/java/com/pfms/app/data/local/dao/ExpenseDao.kt
app/src/main/java/com/pfms/app/data/reminder/ExistingExpenseReminderSource.kt (replaced by FinancialReminderContentSource.kt)
app/src/main/java/com/pfms/app/data/reminder/ReminderDelivery.kt
app/src/main/java/com/pfms/app/data/reminder/ReminderNotifications.kt
app/src/main/java/com/pfms/app/data/reminder/ReminderPreferencesStore.kt
app/src/main/java/com/pfms/app/di/ReminderModule.kt
app/src/main/java/com/pfms/app/domain/model/ReminderSettings.kt
app/src/main/java/com/pfms/app/ui/component/ReminderSettingsSection.kt
app/src/main/java/com/pfms/app/ui/navigation/Routes.kt
app/src/main/java/com/pfms/app/ui/navigation/PfmsNavGraph.kt
app/src/main/java/com/pfms/app/ui/screen/income/IncomeFormScreen.kt
app/src/test/java/com/pfms/app/domain/usecase/ReminderSchedulePolicyTest.kt
app/src/test/java/com/pfms/app/ui/navigation/ReminderDestinationTest.kt
app/src/test/java/com/pfms/app/ui/navigation/SavingsGoalNavigationAndDashboardTest.kt
app/src/androidTest/java/com/pfms/app/ExpenseReminderIntegrationTest.kt
app/src/androidTest/java/com/pfms/app/ReminderRestartTest.kt
app/src/androidTest/java/com/pfms/app/ReminderPermissionDeniedTest.kt
docs/FR38-reminder-foundation.md
```

## Limits

- WorkManager is deferrable, so delivery can occur after the selected time. No exact-alarm mechanism is introduced. Platform battery/Doze behavior and vendor restrictions remain applicable.
- Rules use the user's available cached financial records. Remote edits become visible after the existing repositories reconcile them. Summaries describe recorded amounts, not a claim of complete financial history.
- Settings, claims, and observed goal episodes remain per-device/per-UID. A recovery and return to Behind entirely between scheduled checks cannot be observed retrospectively.
- The foundation's claim-before-post tradeoff remains: process death between durable claim and Android posting can miss one alert while preventing duplicates. Financial entries are unaffected.
- Runtime verification uses API 33. API 26 compatibility is retained through minSdk/API-compatible calls, but API 26 hardware, physical-device reboot/vendor scheduling, production Firebase changes from other devices, and end-to-end notification taps on a signed-in physical device are not covered by this run. Intent destinations, allowlisting, expected-user gating, cached data, and actual notification posting are tested separately.
- No seven-type handler remains intentionally unimplemented. Unrelated existing `.idea` edits are preserved.

## Settings and emulator follow-up (20 October 2026)

- Defined Material 3's newer surface-container colors in the existing light/dark brand palettes. Previously, components using these roles inherited Material defaults with a pink/lilac tint even though the older `surface` role was white. Profile, Preferences, Security, Account, and Financial Reminders cards now explicitly use the app surface; the reminder heading uses emerald.
- Replaced the reminder `TimeInput` inside `AlertDialog` with a scrollable, keyboard-free Material 3 clock dialog. Opening it clears existing text-field focus and hides the keyboard. Its AM/PM selection and disabled-reminder explanation are explicit, and selected colors use emerald. Every reminder uses this same picker; Cancel leaves the saved schedule unchanged.
- The emulator's saved daily time was **04:28**, while the attempted afternoon test was around **16:28**. That stored value represents the next morning, not an afternoon scheduling failure. The new picker makes the distinction explicit. The selected hour is still persisted as minutes since midnight through the existing ViewModel/repository and scheduler.
- The originally reported black overlay could not be reproduced as an app exception: recent crash logs had no matching PFMS fatal exception. The automatic keyboard/focus transition in that picker has been removed; repeated open/save/cancel tests and a normal signed-in run completed without a black screen. This is not a claim that unrelated emulator graphics-driver failures have been repaired.
- Removed only the Expenses toolbar's duplicate Quick Add button. The bottom floating Quick Add action, full-form Add button, and existing tools-menu action remain available.

Verification: debug app/test APK builds passed; all **679 JVM tests** passed. **39 distinct API 33 emulator checks** passed (six reminder Settings checks, 13 scheduling/preference guards, 11 financial-rule integrations, three real Android notification tests, and six expense-screen checks). An initial clock test needed an explicit minute-field selection after choosing the hour; the corrected six-test Settings suite passed on rerun. Screenshots and logs are in ignored `build/fr38-complete/ui-fix-*` and `fr38-*-after.png` files.

A normal, signed-in application test selected **4:45 PM** using the new picker and pressed Home. Without forcing a job or advancing the emulator clock, `ExpenseReminderWorker` ran at **16:45:00** and Android displayed one **Daily finance reminder**. Tapping it opened the dashboard. Daily duplicate protection still means that changing the time after this delivered test does not create another daily reminder on the same date. The original **04:28 AM**, enabled preference was restored afterward; financial records were not edited. The final Settings suite passed again after the AM/PM colors were set to emerald, the Expenses screenshot confirmed the toolbar button was removed and the floating action remained, and the normal app stayed running without a new PFMS fatal exception.
