# Savings goal restoration investigation

The existing restoration patch is integrated: `PFMSApplication.onCreate()` starts
`SyncStartup`, which combines authenticated UID with connectivity and pulls savings
data on login and reconnection. Savings synchronization in `PendingSyncCoordinatorImpl`
uploads pending changes; it does not download remote goals. Logout deliberately clears
Room under the existing cache policy, making the startup pull necessary.

## Confirmed gaps and changes

- `refreshRemoteSavingsData()` ignored failed ledger reads and returned success. A
  goal could therefore appear with an incomplete balance. It now fetches all ledgers
  before merging, propagates failures, and publishes a new goal after its ledger.
- Startup previously logged a failed pull and waited for another connectivity/session
  event. Retryable failures now retry with exponential delay (1 second, up to 30 seconds),
  cancelling on sign-out, account switch, or going offline. Non-retryable errors stop.
- Restoration status now reaches the existing savings screen through the repository
  and ViewModel. With no cached goal, restoration shows loading; failures show the
  existing error/Retry flow. Cached goals remain readable during reconnect.
- Remote records must match the requested UID/goal. A changed session cannot publish
  an in-flight old-session fetch. ViewModel state hides another account's cached goal.
- Room remains authoritative. ID-based upserts avoid duplicate ledger entries; pending
  local edits and deletions win. Balances still use opening savings + contributions -
  withdrawals through the existing domain calculations. No schema/navigation changes.

The historical report's exact production failure cannot be established without that
account's logs and documents. The partial-failure path above is reproduced by tests.
Firestore rules and documents have not been read from or changed in the live project.

## Files for this fix

Production:

- `app/src/main/java/com/pfms/app/data/repository/OfflineFirstSavingsGoalRepository.kt`
- `app/src/main/java/com/pfms/app/data/sync/SyncStartup.kt`
- `app/src/main/java/com/pfms/app/domain/repository/SavingsGoalRepository.kt`
- `app/src/main/java/com/pfms/app/domain/model/SavingsRestorationState.kt` (new)
- `app/src/main/java/com/pfms/app/viewmodel/SavingsGoalViewModel.kt`

Tests:

- `app/src/test/java/com/pfms/app/data/FakeSavingsGoalRepository.kt`
- `app/src/test/java/com/pfms/app/data/repository/OfflineFirstSavingsGoalRepositoryTest.kt`
- `app/src/test/java/com/pfms/app/data/sync/SavingsRestorationStartupTest.kt` (new)
- `app/src/test/java/com/pfms/app/viewmodel/SavingsGoalViewModelTest.kt`
- `app/src/androidTest/java/com/pfms/app/SavingsRestorationTest.kt` (new)
- `app/src/androidTest/java/com/pfms/app/SavingsGoalRedesignUiTest.kt`

## Executed automated checks (2026-10-25)

- Debug APK and Android test APK build: successful.
- Targeted JVM tests: 80 passed, zero failures/errors (48 savings ViewModel,
  17 Firestore remote source, 8 pending sync integration, 4 repository restoration,
  3 startup authentication/connectivity/retry tests).
- Emulator `SavingsRestorationTest`: `OK (5 tests)`. Uses isolated real Room databases
  and simulated transport/authentication. Covers logout-cache-clear/login restoration,
  observed data, offline reopen, reconnection, duplicate prevention, ledger-fetch failure,
  session changes during fetch, and pending local edits/deletions.
- Emulator `SavingsGoalRedesignUiTest#restorationTransitionsFromLoadingToErrorThenPopulatedWithoutReopening`:
  `OK (1 test)`. Verifies the existing Compose layout renders loading, retryable error,
  and restored balance without reopening.
- `git diff --check`: passed.

These tests do not authenticate against live Firebase or validate deployed rules.

## Manual live Firebase verification

1. Sign in as account A. Create a goal and record an affordable contribution and
   withdrawal. Note the opening amount, balance, progress, and ledger IDs/count.
2. Wait for pending writes to finish. Confirm documents under
   `users/{uid}/savingsGoals/{goalId}` and `users/{uid}/goalContributions/{transactionId}`.
   Both models must contain the same `userId`; transactions must reference `goalId`.
3. Log out normally, then sign in to A. Open Goals immediately. Verify that loading
   resolves to the same goal, complete ledger, balance, and progress without reopening.
4. Disable Wi-Fi/mobile data after restoration. Navigate away/back and force-stop/reopen
   while still authenticated. Cached goal/ledger must remain available. Offline login
   after logout cannot restore a deliberately cleared local cache without network access.
5. Reconnect. Verify restored data stays correct and ledger count/IDs do not change.
6. Switch to B. A's goal, amounts, and ledger must never appear. Switch back to A and
   verify restoration again. Repeat reconnect/login to check idempotence.
7. If live restoration fails, inspect Logcat tag `PfmsSavingsRestore` and the displayed
   error. Verify deployed read permissions for both subcollections, UID ownership,
   active goal status, field names/types, and matching transaction goal IDs. The local
   mapper/schema changes are not a substitute for repairing malformed remote documents
   or deployed security rules. Do not clear app storage to hide a restoration failure.
