# Financial management fixes and verification

## Implemented behavior

- Edit Income initializes the saved USD rate. Note-only edits preserve the saved rate, rate date, and LKR equivalent; amount edits use that historical rate. A changed date requires an appropriate rate. Invalid freelance payment statuses are rejected on update.
- Signed-in users without an income estimate complete an income setup screen. The estimate is stored in minor units in their Firebase profile and read by Income Analytics. A completion marker prevents Analytics Reset from restarting onboarding.
- Salary confirmation is connected after authentication and unlock. The repository assigns ownership to new salary templates. A Room transaction checks monthly confirmation again before creating its deterministic salary income ID.
- Dashboard Quick Add opens with the amount focused. Categories are direct chips. Opening, choosing a category, and saving require three control taps, excluding typing and scrolling. Last-used payment method is stored per account across restarts.
- Ordinary expenses use the shared Room database, version 4, with a 3-to-4 migration. Inserts, edits, and deletion tombstones are stored locally before background synchronization. WorkManager retries when connected. Server snapshots preserve pending local changes.
- Each expense mutation has a revision so acknowledgement of an older upload cannot mark a newer edit synced. Firebase insert transactions use a per-user fingerprint guard to check identical submissions within 60 seconds. Rejected offline submissions remain locally available for explicit Save Anyway or Cancel, including after restarting.
- Recurring and subscription reminders fall back to cached Room templates when server refresh fails. Cancelled and paused items are skipped. Daily WorkManager execution is approximate, not an exact-time alarm. Recurring payment confirmation still requires an online Firestore transaction.

## Automated verification

Verified on 2026-10-17: build succeeded, 332 JVM tests passed, and all 22 targeted emulator tests passed. Device tests used `emulator-5554`. The emulator test log is generated at `app/build/emulator-completion-tests.log`; JVM reports are under `app/build/reports/tests/testDebugUnitTest`.

Build and JVM tests:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-21.0.10'
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest --offline
```

Device test classes:

- `IncomePersistenceIntegrationTest`: offline income CRUD, USD field persistence, salary ownership, concurrent monthly confirmation, restart and synchronization.
- `OfflineExpenseIntegrationTest`: offline expense CRUD, database reopen, payment defaults, retries, pending-write reconciliation, revision acknowledgement, duplicate conflicts, account isolation and migration.
- `ExpenseReminderIntegrationTest`: actual Android notifications from cached due/review templates when server refresh fails; exclusion of paused, cancelled and future items; stable notification IDs.
- `IncomeQuickAddUiTest`: focused Quick Add amount, direct category selection, save callback, and onboarding estimate entry.
- `ExpenseRoomIntegrationTest` and `ExpenseScreenIntegrationTest`: category, subscription, Room and expense-screen regressions.

Firebase transaction unit tests use mocked SDK transactions. Device persistence tests use real Room and an in-memory remote implementation. These tests do not prove production Firebase permissions or real multi-device synchronization.

## Remaining live-project acceptance checks

Use a dedicated test account and Firebase Console:

1. Sign in without an estimate, enter LKR 150,000 during setup, and check Income Analytics. The profile should contain `estimatedMonthlyIncomeMinor = 15000000` and `incomeOnboardingComplete = true`.
2. Add USD 100 at a historical rate of 305.42. Edit only its note, then its amount to USD 200. Confirm the original rate/date remain and the resulting LKR equivalent becomes 61,084.00.
3. Confirm a due salary, restart, and verify exactly one salary income document for that template/month. The confirmation must stay hidden for the confirmed month.
4. Save a card expense, restart, and open Dashboard Quick Add. Confirm the displayed payment default is Card. Enter an amount, select a category chip and save.
5. Disable both Wi-Fi and mobile data. Add and edit expenses, restart, and verify the data remains. Delete an expense offline. Reconnect and verify Firestore contains the surviving edits and the deletion completes.
6. On two devices signed into the same test account, submit matching expense fields within 60 seconds. Verify only one uploads automatically; the other requires duplicate confirmation. Save Anyway should allow the second record; Cancel should remove the unresolved local submission.
7. Allow notifications. Create a due recurring template and a subscription whose review date is today. Leave and reopen their screens to enqueue a check. Verify notification text and destinations. Repeat without network after caching the templates. Paused/cancelled items must not produce new reminders.
8. Confirm a due recurring occurrence twice, including after restarting. Verify one expense with its deterministic occurrence ID and one due-date advancement. This check needs internet.

Cloud collections remain under `users/{uid}`. Expense duplicate guards use `expenseDuplicateGuards`; ordinary transactions remain in `expenses`. The repository's existing Firestore rules permit the authenticated owner to access these paths; deployed rules must be checked against the live project.
