FR-36: User-Friendly Error Handling and Retry Functionality — implementation record, 20 October 2026.

The current application uses Compose presentation, Hilt ViewModels exposing StateFlow, repositories, Room caches, and Firestore data sources. FR-35's existing Room queues and WorkManager synchronization remain the financial write path. The shared ErrorBanner, existing Snackbar/dialog patterns, and global offline banner are extended for FR-36.

Implemented behaviour:

- OperationErrors maps failures to approved plain-language messages. It never displays arbitrary exception text. Network/timeouts and unknown temporary failures offer Retry; authentication, permission, missing-record, invalid-input, full-storage, and existing-active-goal cases explain what needs to change.
- Income and expense ViewModels manage loading/save/failure/retry state and guard repeated submissions before launching coroutines. Retrying a draft reuses its ID, including retries after an uncertain acknowledgement.
- The income form keeps amount, source, currency, received date, note, project/client, payment status, crypto details, and entered exchange-rate values after failure. Expense forms keep amount, date, category, classification, payment method, merchant, and note.
- Form Retry resubmits the currently entered fields. ViewModel retry actions retain the previous failed operation for list actions and other recoverable operations.
- Room emissions no longer erase unresolved save errors. Cached lists remain visible during recoverable remote failures.
- Savings create/edit/contribution/withdrawal dialogs close after confirmed success instead of closing immediately when Save is tapped. They retain their fields after failure and show the existing error banner. Goal and ledger drafts have stable IDs and duplicate-save guards.
- Savings writes continue to persist locally before upload. The opportunistic foreground upload waits at most one second; a stalled network request returns a successful local save and leaves the existing pending operation for background synchronization. Cancellation is propagated correctly through Result-based repositories.
- Retrying an already locally created goal with the same ID is idempotent and still enforces one active goal per user.
- Recurring expenses and categories use safe messages and retry the captured operation. Category creation and repeat-expense retry paths accept stable IDs, and recurring confirmation keeps its amount dialog open on failure.
- Dashboard, spending analytics, income analytics, salary, onboarding, profile settings, and transaction-history loading use safe failure messages. History's Retry now restarts its failed stream instead of targeting a flow that had already completed.
- Income estimates retain the attempted value while a save is unresolved; account changes clear the previous user's transient retry state.
- Exchange-rate failures do not expose HTTP codes, response details, or exception messages. Manual rate entry remains available.
- Authentication/settings fallback messages are sanitized while retaining the existing domain AuthException messages.

Offline save versus failed save:

Income/expense saves succeed after their Room transaction. Failed ancillary payment-default storage or worker scheduling does not falsely report a failed financial save after a committed row. Existing startup/periodic recovery can schedule the durable queue again.

A Hilt-injected, user-scoped SyncIssueStore holds transient display errors from remote listeners and synchronization. It stores no financial entries and introduces no synchronization queue. The existing offline banner can say that changes are saved on the device while also explaining a sync failure. Retry re-enqueues the existing workers with REPLACE for an explicit user retry, so it does not wait behind an earlier backoff delay. Stable IDs and FR-35's revision acknowledgement checks protect in-flight work. Normal automatic scheduling retains its previous policy.

Pending financial operations remain durable in Room. The new display-only error status is reconstructed when listeners/workers run after restart. Firestore's SDK-managed writes for other existing features remain in place.

Verification:

- Debug app APK and Android test APK builds pass.
- 613 local JVM tests pass, including 18 new tests for sanitized messages, retry policy, input/ID preservation, duplicate-tap suppression, user-scoped sync failures, savings retry, and history stream recovery.
- All three existing live ExchangeRateServiceTest checks were run separately and pass: latest rate, historical rate, and unavailable-date failure. The earlier external-service limitation did not reproduce in this run.
- All 43 Android regression tests pass: seven new FR-36 checks plus 36 existing UI, offline storage, migration, income, salary, and expense checks.
- All three real Firebase emulator stages pass, bringing Android verification to 46 passing tests overall. Twenty offline income/expense records, edits, and deletions survive a force-stop and restart; persisted WorkManager jobs upload automatically after reconnection. Lost-acknowledgement retry, no duplicate records, last-write-wins conflicts, server deletions, and authenticated-user isolation pass again.
- The normal application cold-starts successfully with no AndroidRuntime startup exception logged. Its offline banner appears with both radios disabled and disappears after reconnecting. The app remains running; Wi-Fi and mobile data are restored.

The Android failure fixtures use actual FirebaseFirestoreException codes and real Room tables. They verify that UNAVAILABLE retains a saved pending row, recovery clears its error and pending status without adding a second server record, and an indefinitely stalled savings upload still returns a durable local-save success.

Ordinary instrumented tests initialize test WorkManager because HiltTestApplication does not provide production's Configuration.Provider. This prevents a previously scheduled JobService from crashing UI tests. Opt-in Firebase stages retain their real worker factory to verify persisted background work. The test helper follows the existing AndroidX testing infrastructure: [WorkManagerTestInitHelper reference](https://developer.android.com/reference/androidx/work/testing/WorkManagerTestInitHelper).

Build/test reproduction:

Use Android Studio's Java with the existing Gradle wrapper:

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest
```

The local 613-test run used the existing temporary build/fr34/offline-tests.gradle init script to separate external-service tests. Those three service tests were then run using --tests com.pfms.app.data.exchange.ExchangeRateServiceTest without the init script. Local results are preserved in build/fr36/unit-results; live exchange results are in build/fr36/live-exchange-rate-tests.xml.

Android regression results are in build/fr36/android-regression-tests.txt. The separate Firebase stages use the existing OfflineFirestoreEmulatorTest with fr35Emulators=true against demo-pfms-fr35 only; setup and stage ordering are documented in FR35-offline-access-and-sync.md. Test artifacts, tools, and logs remain under ignored build directories.

Limitations:

Production Firebase and physical-device/OEM scheduling are outside emulator verification. New failure paths are exercised with controlled repository/network fixtures and local Firebase emulators, rather than by altering a production user's access rules or financial data. No production test accounts or financial records are created.

No FR-36 functional blocker remains in the completed checks. Existing local IDE changes are preserved and excluded from the focused FR-36 commits.

Modified/new project files (plus this report):

- `app/src/androidTest/java/com/pfms/app/CustomTestRunner.kt`
- `app/src/androidTest/java/com/pfms/app/FinancialErrorUiTest.kt`
- `app/src/androidTest/java/com/pfms/app/FinancialRepositoryFailureTest.kt`
- `app/src/main/java/com/pfms/app/MainActivity.kt`
- `app/src/main/java/com/pfms/app/data/error/OperationErrors.kt`
- `app/src/main/java/com/pfms/app/data/error/RepositoryResult.kt`
- `app/src/main/java/com/pfms/app/data/exchange/ExchangeRateService.kt`
- `app/src/main/java/com/pfms/app/data/local/dao/SavingsGoalDao.kt`
- `app/src/main/java/com/pfms/app/data/repository/FirestoreAdvancedExpenseRepository.kt`
- `app/src/main/java/com/pfms/app/data/repository/FirestoreUserRepository.kt`
- `app/src/main/java/com/pfms/app/data/repository/OfflineFirstExpenseRepository.kt`
- `app/src/main/java/com/pfms/app/data/repository/OfflineFirstIncomeRepository.kt`
- `app/src/main/java/com/pfms/app/data/repository/OfflineFirstSavingsGoalRepository.kt`
- `app/src/main/java/com/pfms/app/data/repository/OfflineSyncStatusRepository.kt`
- `app/src/main/java/com/pfms/app/data/repository/SalaryTemplateRepositoryImpl.kt`
- `app/src/main/java/com/pfms/app/data/sync/ExpenseSyncWorker.kt`
- `app/src/main/java/com/pfms/app/data/sync/PendingSyncCoordinatorImpl.kt`
- `app/src/main/java/com/pfms/app/data/sync/SyncIssueStore.kt`
- `app/src/main/java/com/pfms/app/domain/model/OfflineSyncState.kt`
- `app/src/main/java/com/pfms/app/domain/repository/AdvancedExpenseRepository.kt`
- `app/src/main/java/com/pfms/app/domain/repository/SyncStatusRepository.kt`
- `app/src/main/java/com/pfms/app/domain/usecase/LogoutUseCase.kt`
- `app/src/main/java/com/pfms/app/ui/component/ErrorBanner.kt`
- `app/src/main/java/com/pfms/app/ui/component/OfflineSyncBanner.kt`
- `app/src/main/java/com/pfms/app/ui/navigation/PfmsNavGraph.kt`
- `app/src/main/java/com/pfms/app/ui/screen/DashboardScreen.kt`
- `app/src/main/java/com/pfms/app/ui/screen/SettingsScreen.kt`
- `app/src/main/java/com/pfms/app/ui/screen/analytics/SpendingAnalyticsScreen.kt`
- `app/src/main/java/com/pfms/app/ui/screen/expense/AdvancedExpenseScreens.kt`
- `app/src/main/java/com/pfms/app/ui/screen/expense/ExpenseFormContent.kt`
- `app/src/main/java/com/pfms/app/ui/screen/expense/ExpenseFormScreen.kt`
- `app/src/main/java/com/pfms/app/ui/screen/expense/ExpenseListContent.kt`
- `app/src/main/java/com/pfms/app/ui/screen/expense/ExpenseListScreen.kt`
- `app/src/main/java/com/pfms/app/ui/screen/expense/ExpenseQuickAddContent.kt`
- `app/src/main/java/com/pfms/app/ui/screen/expense/QuickAddExpenseSheet.kt`
- `app/src/main/java/com/pfms/app/ui/screen/history/TransactionHistoryScreen.kt`
- `app/src/main/java/com/pfms/app/ui/screen/income/IncomeAnalyticsScreen.kt`
- `app/src/main/java/com/pfms/app/ui/screen/income/IncomeFormScreen.kt`
- `app/src/main/java/com/pfms/app/ui/screen/income/IncomeListScreen.kt`
- `app/src/main/java/com/pfms/app/ui/screen/income/IncomeOnboardingScreen.kt`
- `app/src/main/java/com/pfms/app/ui/screen/income/SalaryTemplateScreen.kt`
- `app/src/main/java/com/pfms/app/ui/screen/savings/SavingsGoalDialogs.kt`
- `app/src/main/java/com/pfms/app/ui/screen/savings/SavingsGoalScreen.kt`
- `app/src/main/java/com/pfms/app/viewmodel/AdvancedExpenseViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/AuthViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/DashboardViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/ExpenseViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/IncomeAnalyticsViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/IncomeOnboardingViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/IncomeViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/SalaryViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/SavingsGoalUiState.kt`
- `app/src/main/java/com/pfms/app/viewmodel/SavingsGoalViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/SessionViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/SettingsViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/SpendingAnalyticsViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/SyncStatusViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/TransactionHistoryViewModel.kt`
- `app/src/test/java/com/pfms/app/data/error/OperationErrorsTest.kt`
- `app/src/test/java/com/pfms/app/data/sync/SyncIssueStoreTest.kt`
- `app/src/test/java/com/pfms/app/domain/usecase/LogoutUseCaseTest.kt`
- `app/src/test/java/com/pfms/app/viewmodel/FinancialErrorHandlingTest.kt`
- `app/src/test/java/com/pfms/app/viewmodel/IncomeAnalyticsViewModelTest.kt`
- `app/src/test/java/com/pfms/app/viewmodel/SavingsGoalViewModelTest.kt`
