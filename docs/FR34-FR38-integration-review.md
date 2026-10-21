# FR-34–FR-38 integration and regression review

Review date: 20 October 2026. Branch: `feature/resilience-gap-tolerance-reminders`. The review includes the latest committed FR-34–FR-38 code and the uncommitted Settings/time-picker/Expenses UI fixes already present when this review began. Production Firebase and the developer's financial records are not used as test fixtures.

## Integration fixes

1. Removed the production Room builder's destructive migration fallback. Database version remains **6** with the existing explicit `1→4`, `2→4`, `3→4`, `4→5`, and `5→6` paths. No financial table/entity is removed or reset.
2. Expense scheduling exceptions after a successful Room commit no longer report a failed save. Creation, editing, and deletion retain their durable pending record; the existing startup/connectivity/periodic recovery mechanisms can repair scheduling.
3. Salary, savings-goal, and goal-ledger acknowledgements now run in Room transactions and compare the submitted pending entity with the current entity. An acknowledgement for an older upload cannot clear a newer edit or remove a recreated record. Salary deletion removes only the submitted template ID, preserving a replacement template for the same user. Queue processing rechecks both the active user and the pending entity before each upload, so an old queue snapshot cannot resend an entry already changed and acknowledged during an earlier upload.
4. A stale cached salary snapshot can no longer roll back a newer confirmed month or salary amount. Salary synchronization propagates coroutine cancellation instead of consuming it as an ordinary network failure.
5. Income list, dashboard, income analytics, and savings pace now agree on mixed-case legacy source/status/crypto values. Legacy pending freelance remains unrealized and appears in pending totals. Savings monthly expenses use the same date parser as the dashboard, including supported legacy month-only dates; invalid dates remain excluded.
6. Fixed both reminder weekday-label lint errors by reading the observable configuration locale. The notification-permission request now has an explicit API-33 guard.
7. Authentication form integration tests now use isolated ViewModel/repository state instead of depending on the emulator's existing signed-in account. They do not sign out the developer or send reset emails. Added a separate opt-in production-auth-repository test against local Firebase Auth/Firestore to verify successful registration, profile/default initialization, logout, friendly wrong-password failure, login, and empty new-user financial collections.
8. Fixed a connectivity-callback race found during real offline testing. The previous callback reread `activeNetwork`/capabilities during `onLost`, when Android can still expose the lost validated network. It could leave the observable indicator online until another callback or process restart. Loss now emits offline directly, capability changes use the supplied capabilities, and late callbacks for an older network cannot override its replacement. Registration/initial-state delivery is ordered with callback updates. Three JVM regressions model the stale manager snapshot and network replacement explicitly. The emulator radio commands also wait for shell completion instead of closing their output pipes immediately.

The new tests use real Room DAOs/repositories for in-flight goal/ledger/salary changes, stale deletion acknowledgements, replacement salary templates, stale salary snapshots, account changes, stale queue snapshots, and expense scheduling failure. Remote callbacks in these ten tests are controlled fixtures; they are not claims about a production Firebase run. Four new JVM tests verify cross-feature calculation agreement for historical, empty, unconverted, mixed-case, and legacy pending data, including the separate currency/crypto breakdown.

## Architecture and configuration review

- Production bindings follow UI → StateFlow ViewModel → repository → Room/Firestore. Existing Hilt singleton bindings are retained. Both debug and release compilation exercise Hilt generation and assisted worker factories.
- `PFMSApplication` implements `Configuration.Provider`, supplies `HiltWorkerFactory`, and starts the existing sync/reminder coordinators. The manifest removes the default WorkManager initializer for this configuration.
- Income and expenses retain the Room pending queue, stable document IDs, mutation revisions, guarded acknowledgements, server tombstones, and deterministic `(updatedAt, deleted, revision)` ordering. Versioned Firestore transactions fail offline and leave Room pending; no second SDK write queue was introduced for these records.
- The existing income worker owns income/salary/savings synchronization, while the expense worker owns expenses. Unique work names, per-repository serialization for income/expenses, and periodic recovery are retained. Recovery work is intentional; it is not a second financial write system.
- The single reminder scheduler creates one unique job per enabled type and UID, cancels legacy reminder work, and removes old-user work/notifications. Delivery rechecks authentication, settings revision, permission/channel access, time, financial eligibility, and durable duplicate claims. All seven financial handlers use this infrastructure.
- Notification channels, `POST_NOTIFICATIONS`, `RECEIVE_BOOT_COMPLETED`, Internet/network-state permissions, private notification visibility, immutable PendingIntents, route allowlisting, and expected-UID navigation checks were reviewed. No exact-alarm permission or exact-delivery promise is introduced.
- Existing Room migrations, Firestore user paths/security rules, local preferences keyed by UID, and Firebase modules are retained. The isolated Firebase emulators use the same checked-in security rules. No production rules were deployed.
- No Gradle dependency upgrades, duplicate workers, schema version changes, or unrelated financial feature rewrites were introduced. Existing Hilt/dependency/resource/style warnings remain separate from compilation or lint errors.

## Files changed during this review

```text
.gitignore
app/src/androidTest/java/com/pfms/app/AuthIntegrationTest.kt
app/src/androidTest/java/com/pfms/app/ExpenseReminderIntegrationTest.kt
app/src/androidTest/java/com/pfms/app/ExpenseScreenIntegrationTest.kt
app/src/androidTest/java/com/pfms/app/FinalReviewIntegrationTest.kt (new)
app/src/androidTest/java/com/pfms/app/OfflineFirestoreEmulatorTest.kt
app/src/main/java/com/pfms/app/data/local/dao/GoalTransactionDao.kt
app/src/main/java/com/pfms/app/data/local/dao/SalaryTemplateDao.kt
app/src/main/java/com/pfms/app/data/local/dao/SavingsGoalDao.kt
app/src/main/java/com/pfms/app/data/repository/OfflineFirstExpenseRepository.kt
app/src/main/java/com/pfms/app/data/repository/OfflineFirstSavingsGoalRepository.kt
app/src/main/java/com/pfms/app/data/repository/SalaryTemplateRepositoryImpl.kt
app/src/main/java/com/pfms/app/data/sync/AndroidConnectivityObserver.kt
app/src/main/java/com/pfms/app/di/DatabaseModule.kt
app/src/main/java/com/pfms/app/domain/usecase/IncomeSummaryCalculator.kt
app/src/main/java/com/pfms/app/domain/usecase/analytics/GetMonthlyIncomeSummaryUseCase.kt
app/src/main/java/com/pfms/app/domain/usecase/savings/CalculateMonthlyNetSavingsUseCase.kt
app/src/main/java/com/pfms/app/ui/component/ReminderSettingsSection.kt
app/src/test/java/com/pfms/app/data/repository/OfflineFirstSavingsGoalRepositoryTest.kt
app/src/test/java/com/pfms/app/data/repository/SalaryTemplateRepositoryImplTest.kt
app/src/test/java/com/pfms/app/data/sync/SavingsGoalPendingSyncIntegrationTest.kt
app/src/test/java/com/pfms/app/data/sync/AndroidConnectivityObserverTest.kt (new)
app/src/test/java/com/pfms/app/domain/usecase/CrossFeatureFinancialCalculationTest.kt (new)
docs/FR34-FR38-integration-review.md (new)
```

These earlier, uncommitted UI fixes were retained and included in the reviewed builds, without being edited again during the review:

```text
app/src/androidTest/java/com/pfms/app/ReminderSettingsUiTest.kt
app/src/main/java/com/pfms/app/ui/screen/SettingsScreen.kt
app/src/main/java/com/pfms/app/ui/screen/expense/ExpenseListScreen.kt
app/src/main/java/com/pfms/app/ui/theme/Theme.kt
docs/FR38-financial-reminders.md
```

The pre-existing `.idea/deploymentTargetSelector.xml` and `.idea/gradle.xml` changes are untouched. Test logs, scripts, and screenshots are kept in ignored `build/final-review/`.

## Build and verification record

Android Studio's installed JDK and the project Gradle wrapper were used:

```text
.\gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:testDebugUnitTest :app:lintDebug --console=plain
```

This final command passed again after all production fixes (`final-validation.log`). It produced the debug application/test APKs and `app/build/outputs/apk/release/app-release-unsigned.apk`. **686 JVM tests passed, zero failures/errors/skips**, including all three network-dependent exchange-rate tests. Lint completed with **0 errors and 72 warnings**. The release artifact is unsigned; compilation is not a release-signing or store-publication verification. Generated Kotlin build diagnostics are ignored in `.gitignore` alongside the existing Gradle/build artifacts.

Earlier attempts are retained in the logs: the first lint analysis stopped with an internal FIR analyzer exception; the next completed analysis found the two reminder locale errors, which were fixed before the successful final run. Nothing was suppressed or baselined to obtain the passing lint result.

The final Android test APK was rebuilt after adding all-seven-type Android notification coverage. Its test build and lint check also passed. Android's automatically generated group summary is excluded when counting the application's user-scoped notification tags.

**106 distinct ordinary API-33 emulator checks passed in the final run**, with no failed classes (`android-final-validation-summary.log`). This includes authentication form validation, empty states/charts, income/expense forms and CRUD, friendly errors/input retention/retries, ten new concurrent-save tests, savings data, migrations, offline queues, account isolation, historical-date context, monthly-review/welcome-back presentation, reminder settings, seven financial eligibility rules, actual Android channels/posting/cancellation, and durable deduplication. Together with the five Firebase stages, two persistent-reminder stages, and one permission-denial check, **114 distinct Android checks passed**. The staged tests passed on the connectivity-fixed implementation; the final ordinary run also includes the subsequent exact-ID salary-deletion regression and DAO fix.

Initial broad runs were not clean: UI automation stalled while Espresso waited for a Choreographer frame, and one savings-dialog test timed out. These runs were interrupted and are retained in the logs. The emulator was rebooted without clearing data, classes were run in fresh processes, and a redundant competing scroll in the expense-category test was replaced with an assertion that the edit form was already visible. The complete final UI class checks passed. The original black-screen symptom from the earlier task was not reproduced as a production-app crash in this review.

The added actual-notification test posts all seven types through `AndroidReminderNotifier` and the shared guarded delivery path, verifies one stable notification per type after repeated attempts, and verifies that disabling all types cancels them and prevents further posting. Its eligibility input is controlled; separate Room/source/domain integrations verify the real financial eligibility rules.

All five real Firebase emulator stages passed after fixing the connectivity callback and waiting for radio commands to complete. The initial stage-1 offline transition had timed out before the offline records were created; stages 2/3 consequently had incomplete fixtures and failed as well. These initial results are preserved in `firebase-stage*-initial.log` and `persistent-stages-summary.log`; the complete passing rerun is in `persistent-stages-fixed-summary.log`.

The passing stages use real Firebase Auth/Firestore Android SDKs, the application's current rules, file-backed Room, actual Wi-Fi/mobile-data changes, and persisted WorkManager jobs against **demo-pfms-fr35** on local ports 9199/8180:

1. Cache financial entries online, disconnect, view the cache, create ten income and ten expense records, edit one of each, and delete one of each. The queue remains durable.
2. Force-stop/restart while still offline, verify records and queue, then reconnect and wait for automatic workers. Server collections contain the expected ten live records plus one tombstone each. Repeated sync does not add records.
3. Lose an acknowledgement after an actual server commit, retry, verify deterministic newer-edit/deletion wins, sign in another user, verify Firestore denies the first user's path, and verify same-ID Room isolation.
4. Add previous-month USD income and a previous-year expense offline; verify automatic sync preserves selected dates, saved historical/manual conversion, logging timestamps, and stable IDs.
5. Use the production auth repository with local emulators to register a user, initialize profile/categories, verify empty financial collections, log out, reject a wrong password with a friendly error, and log in successfully.

Both persistent reminder stages also passed: preferences, occurrence claims, goal episode state, and unique work survived a process stop; boot/time/time-zone repair and cancellation worked. These stages use persistent WorkManager storage with controlled authenticated-user/worker dependencies, not a claim that physical-device boot scheduling was exercised.

Notification-permission denial was tested by revoking the real API-33 permission. The dedicated test passed with all seven types suppressed and preferences still writable. All five financial-error/form tests also passed with permission denied. The original granted permission and enabled Wi-Fi/mobile-data settings were restored afterward. Repeated financial tests are not counted as additional distinct cases.

The instrumentation classes were invoked using the installed SDK's `adb`, with each ordinary class in a fresh application process:

```text
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb shell am instrument -w -e class com.pfms.app.<TestClass> -e timeout_msec 60000 com.pfms.app.test/com.pfms.app.CustomTestRunner
```

Opt-in Firebase and reminder-restart tests were run in ordered stages with their required runner arguments. The scripts and complete arguments are retained under `build/final-review/`; ordinary tests exclude those stages and the separate permission-denial test. Test applications use a named Firebase instance and isolated fixtures rather than altering the developer's production financial records. Local Firebase emulator services were stopped after verification.

## Requirement status and practical limits

| Requirement | Verified result |
| --- | --- |
| FR-34 | Empty/new-user forms and screens render useful zero/empty states; charts and zero-denominator calculations pass their regression checks. Cross-feature realized totals agree for the additional legacy/historical fixtures. |
| FR-35 | Real SDK offline cache, ten income plus ten expense creations, edits/deletions, offline process restart, automatic WorkManager synchronization, lost-acknowledgement retry, stable IDs, LWW conflicts, and account isolation pass against local Firebase. The connectivity-state regression was fixed. |
| FR-36 | Form input/date/category retention, friendly errors, recoverable retry, duplicate-save protection, and pending-versus-failed status pass the form/repository checks. Notification permission denial does not block those financial forms. |
| FR-37 | Historical dates survive offline storage/synchronization; monthly calculations, explicit month-review/Partial Data policy, new-user exclusion, six/seven/eight-day welcome-back boundaries, logging time versus transaction date, and dismissal pass their existing checks. No-entry days alone are not treated as evidence of missing transactions. |
| FR-38 | Seven independent switches, financial eligibility rules, channels, permission denial, unique scheduling, guarded delivery, actual Android notification posting/cancellation, persisted deduplication, and process-restart repair pass. Actual posting uses controlled content; separate tests exercise the financial rules. |

These statuses describe the tested API-33 emulator and local Firebase environment. They are not a blanket production or physical-device certification.

Remaining manual checks:

- Production Firebase configuration/rules, real network interruptions, multi-device writes, and device clock differences. Local emulator SDK/security tests do not exercise the deployed backend.
- Real Google sign-in account selection, biometric authentication, and password-reset email delivery. Automated authentication checks cover form validation and email/password registration/login/logout against local Firebase.
- Physical API-26 and current Android devices, OEM battery restrictions/Doze, actual device restart, and waiting through real calendar reminder schedules. WorkManager can deliver after the configured time; physical timing is not certified by controlled worker/restart tests.
- Notification navigation when launched from a terminated app with real user sessions, accessibility/dark-theme behavior across devices, and release signing/install/distribution. Debug navigation/routing checks and unsigned release compilation provide narrower evidence.
- Existing advanced subscription/template configuration screens and their Firestore SDK offline acknowledgement behavior need production interaction testing. The review preserves that existing implementation rather than claiming every configuration form received a full end-to-end offline test.

Reminder preferences/month-review markers remain device-local and user-scoped as designed. The existing claim-before-notification policy prevents repeat delivery but retains a small crash window between saving a claim and Android posting the notification. Neither this behavior nor cross-device preference synchronization was redesigned in this regression review.

## Normal-application smoke checks

The ordinary debug application was also launched with the existing signed-in account, without creating, editing, or deleting its financial records. Dashboard, Income, Expenses, Goals, Spending Analytics, and Settings rendered. White/green Settings styling was visually checked. An initial coordinate-based navigation script remained on Goals for two intended destinations; those snapshots are not evidence for Analytics/Settings. The destinations were subsequently opened correctly and separately verified (`insights-verified.*`, `settings-verified.*`).

With real emulator radios disabled, cached Expenses remained visible with the offline banner. Re-enabling Wi-Fi/mobile data removed the banner. Artifacts are `expenses-offline.*` and `expenses-reconnected.*`. The earlier file called `home-offline` actually contains Expenses and is not counted as a separate Dashboard offline check. Instrumented empty-state, financial-rule, and repository tests supply the broader controlled-data coverage described above.

After the final test run, the final APK cold-launched successfully through the ordinary `PFMSApplication`/Hilt setup. The UI hierarchy contained 84 nodes, including Monthly Overview and Partial Data. Startup income/expense workers reported success and no fatal AndroidRuntime exception appeared for that process (`final-normal-launch-logcat.log`). Multiple startup/recovery requests are visible, including serialized appended expense work; the existing unique-work policies and repository serialization are retained. This is not evidence that only one worker invocation ever occurs. The emulator was left on the normal dashboard with Wi-Fi/mobile data enabled and notification permission restored to granted. The original account/data was retained.

No unresolved compilation error or failing final automated check remains. The lint warnings and unverified/manual scenarios above remain explicit limitations.
