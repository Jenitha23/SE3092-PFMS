# FR-37: Back-dated entries, monthly completeness, and welcome-back prompts

## Scope and architecture

The existing forms already supported past dates. This change keeps their Material 3 date pickers, transaction models, monthly calculators, Room cache, versioned Firestore operations, durable pending queue, and WorkManager jobs. It adds shared date validation and recording-context domain policies, a user-scoped repository, a Hilt StateFlow ViewModel, and presentation components on the dashboard and income/spending analytics screens.

Income `receivedDate` and expense `date` remain the selected transaction dates. `createdAt` is the actual recording timestamp, and `updatedAt` remains the revision/conflict timestamp. History, dashboard totals, and analytics continue grouping by the transaction date. Synchronization sends the existing dates unchanged. Historical USD income retains the existing date-specific exchange-rate lookup and manual-rate fallback. Reconfirming the same date in the income picker no longer clears its rate; changing the date triggers the existing lookup once.

No Room entity or schema changes are required. The database remains version 6; the new DAO only queries existing columns. There is no second synchronization mechanism.

## Partial Data policy

The app cannot infer completeness from entry frequency, income-only months, or days without transactions. Completeness is an explicit review of a closed month, rather than a guess about missing activity.

| Month/records | Indicator and behavior |
| --- | --- |
| Current or future month | **Partial Data**; the month is open and totals reflect recorded entries. |
| Closed month without records | **Partial Data**; explicitly says no entries are recorded. The user can review it to confirm no financial activity. |
| Closed month with valid records, not reviewed | **Partial Data**; completeness has not been confirmed. Sparse entry days do not independently imply missing transactions. |
| Invalid date/amount, or missing required realized LKR conversion | **Partial Data**, with the corresponding explanation. Review is unavailable until those details are corrected. An expected freelance invoice does not falsely require a realized-income conversion. |
| Closed, valid month explicitly reviewed | **Reviewed**; confirms either recorded income/expenses or no financial activity. |

Review requires a confirmation dialog and stores a SHA-256 signature of the month's financial fields. Adding, editing, moving, or deleting a financial record invalidates a mismatched review automatically. Sync timestamps, revisions, acknowledgements, and list ordering do not invalidate it. The repository checks both authenticated UID and current signature when saving; a stale dialog cannot approve changed records. An unassignable invalid date conservatively prevents a completeness claim because its affected month is unknown.

Review metadata persists in device-local Preferences DataStore, keyed by Firebase UID and month. Recorded totals are never estimated or replaced by this policy. Dashboard and analytics reuse the same policy and component.

## Welcome-back detection

- Use the maximum positive `createdAt` from the authenticated user's cached income and expenses, including available deletion markers. Persist the last known maximum per UID so removing a previously observed cache does not erase known logging activity.
- Convert that timestamp to a calendar date in the app's existing `Asia/Colombo` time zone. Eligibility starts at **seven calendar dates** without a new entry: six days is ineligible, exactly seven and eight or more are eligible.
- A new user, or legacy records with no reliable creation timestamp, does not trigger the prompt. Future timestamps do not produce a false inactivity claim.
- A newly recorded back-dated transaction has today's creation time, so it resets inactivity. Editing or acknowledging synchronization does not substitute `updatedAt` for creation time.
- The dashboard displays “Welcome back! Ready to record your recent finances?” with the existing Add Income and Add Expense navigation actions and a Dismiss action.
- Dismissal/action suppresses the prompt for that UID for the current app process. It survives navigation and ViewModel recreation in that session; it may appear in a later process if the user is still eligible. No streaks, penalties, or progress-loss mechanisms are introduced.
- The injected clock and pure domain policy make boundary cases testable. Observable UI state refreshes the date once a minute while subscribed.

## Verification

Validation performed on 20 October 2026:

- `:app:testDebugUnitTest`: **643 passed, zero failures/skips**, including all three live exchange-rate service tests and 27 new FR-37 unit tests.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: successful.
- Android emulator regression selection: **52 passed** (9 new FR-37 Room/UI tests plus 43 existing tests covering empty states, financial forms, errors/retries, savings, offline queues, account isolation, and migrations).
- Previous-month income calendar selection: **1 passed**. Local Firebase/WorkManager synchronization of historical entries: **1 passed**. Together with the regression selection, **54 Android checks passed**. Final logs are recorded under `build/fr37/`.
- Normal debug app cold launch: successful; process remained running and its AndroidRuntime crash log was empty.
- Domain tests cover previous-month income, previous-year expenses, historical date edits moving monthly totals, malformed/leap dates, empty/unreviewed/reviewed months, missing conversions, signature invalidation, new users, six/seven/eight-day inactivity boundaries, future clock skew, and back-dated logging activity.
- ViewModel tests cover safe load retries, reactive eligibility, session dismissal, guarded review submissions, and rejection of stale review signatures. Entry ViewModel tests cover retained historical dates/manual rates and stable IDs through retry.
- File-backed Room tests close/reopen the database with pending historical income/expenses, fail synchronization while offline, synchronize afterwards, and verify unchanged selected dates, rates, creation times, correct monthly totals, and no duplicate writes. Review persistence and authenticated-user isolation are exercised against real Room and DataStore.
- The opt-in `OfflineFirestoreEmulatorTest.stage4HistoricalDatesAndManualRateSurviveOfflineAutomaticSync` uses an isolated demo Firebase project, real Auth/Firestore Android SDKs, actual disabled emulator radios, and existing network-constrained WorkManager jobs. After reconnection, server reads verify the selected previous-month/year dates, saved rate and rate date, actual creation timestamps, and one record per stable ID.

## Changed files

Production:

```text
app/src/main/java/com/pfms/app/data/local/FinancialContextPreferences.kt
app/src/main/java/com/pfms/app/data/local/PfmsDatabase.kt
app/src/main/java/com/pfms/app/data/local/dao/FinancialActivityDao.kt
app/src/main/java/com/pfms/app/data/repository/RoomFinancialContextRepository.kt
app/src/main/java/com/pfms/app/di/FinancialContextModule.kt
app/src/main/java/com/pfms/app/domain/model/MonthlyDataStatus.kt
app/src/main/java/com/pfms/app/domain/repository/FinancialContextRepository.kt
app/src/main/java/com/pfms/app/domain/usecase/ExpenseRules.kt
app/src/main/java/com/pfms/app/domain/usecase/FinancialEntryDate.kt
app/src/main/java/com/pfms/app/domain/usecase/MonthlyDataPolicy.kt
app/src/main/java/com/pfms/app/domain/usecase/WelcomeBackEligibility.kt
app/src/main/java/com/pfms/app/ui/component/MonthlyDataNotice.kt
app/src/main/java/com/pfms/app/ui/component/WelcomeBackPrompt.kt
app/src/main/java/com/pfms/app/ui/screen/DashboardScreen.kt
app/src/main/java/com/pfms/app/ui/screen/analytics/SpendingAnalyticsScreen.kt
app/src/main/java/com/pfms/app/ui/screen/income/IncomeAnalyticsScreen.kt
app/src/main/java/com/pfms/app/ui/screen/income/IncomeFormScreen.kt
app/src/main/java/com/pfms/app/viewmodel/FinancialContextViewModel.kt
app/src/main/java/com/pfms/app/viewmodel/IncomeViewModel.kt
```

Tests and documentation:

```text
app/src/test/java/com/pfms/app/domain/usecase/RecordingContextPolicyTest.kt
app/src/test/java/com/pfms/app/viewmodel/BackdatedEntryViewModelTest.kt
app/src/test/java/com/pfms/app/viewmodel/FinancialContextViewModelTest.kt
app/src/androidTest/java/com/pfms/app/RecordingContextRoomTest.kt
app/src/androidTest/java/com/pfms/app/RecordingContextUiTest.kt
app/src/androidTest/java/com/pfms/app/OfflineFirestoreEmulatorTest.kt
docs/FR37-recording-context.md
```

## Limits

- Month reviews are local to the device and UID; they are not synchronized across installations. Another device conservatively shows an unconfirmed month until reviewed there.
- Eligibility uses known cached creation timestamps and the last locally observed maximum. It cannot infer unavailable remote history or recover an unknown legacy recording time. Device clock changes can affect the calendar boundary.
- The local Firebase integration verifies SDK/server behavior and emulator WorkManager scheduling. Production Firebase configuration/rules and physical-device vendor background restrictions are outside this run's coverage.
- Existing unrelated `.idea/deploymentTargetSelector.xml` and `.idea/gradle.xml` edits were preserved. They are not FR-37 changes.
