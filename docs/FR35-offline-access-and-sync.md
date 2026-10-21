FR-35: Offline Data Access and Automatic Synchronization — implementation record, 20 October 2026.

The current application uses UI → Hilt ViewModel → repository → Room/Firestore data sources. Income, expenses, salary templates, savings goals, and goal transactions already had Room pending-status fields. IncomeSyncWorker coordinates income, salary, and savings synchronization; ExpenseSyncWorker owns the expense queue. This implementation extends those queues and workers.

Implemented behaviour:

- Previously cached income/expenses remain readable from Room with the network disabled. Existing savings/goal caches remain in their current repositories.
- Income and expense creation/editing/deletion first persist locally. Income no longer waits for a remote request before the save completes.
- Each financial mutation has a stable document ID, durable operation revision, updatedAt, and pending status. Queues persist across process death.
- Existing network-constrained WorkManager jobs retry failed uploads with exponential backoff starting at 30 seconds. Sign-in and connectivity transitions reschedule the workers, and 15-minute periodic recovery work covers missed scheduling.
- A global Material 3 banner shows offline mode and user-scoped pending counts. The UI receives StateFlow from SyncStatusViewModel; no Composable reads Room, calls Firestore, or starts synchronization.
- Confirmed salary income is uploaded through the income Room queue. It is no longer also submitted as a separate SDK-queued write.
- Existing salary-template/savings Firestore handling is retained; savings mutations now schedule their existing sync worker.
- Logout's unsynchronized-data check includes Room queues before checking SDK-managed pending writes.
- Database version 6 preserves cached/pending income, adds a revision column, and changes income's primary key to (userId, id). All other feature tables are retained.

Synchronization design:

Room is the durable write queue for income/expenses. Workers send versioned Firestore transactions, which atomically compare the stored version with the queued mutation and return the winner. Firestore transactions fail offline rather than creating another durable SDK write queue, so failed work remains in Room for WorkManager to retry. Firestore's read cache and the existing SDK-managed writes for other features continue to operate. See [Firestore transactions](https://firebase.google.com/docs/firestore/manage-data/transactions), [Firestore offline persistence](https://firebase.google.com/docs/firestore/manage-data/enable-offline), and [WorkManager requests and retries](https://developer.android.com/develop/background-work/background-tasks/persistent/getting-started/define-work).

Income/expense conflict policy is a deterministic total ordering:

1. Highest updatedAt wins. It is UTC epoch milliseconds from the editing device. A new local mutation advances beyond that record's previous timestamp even if the wall clock moves backwards.
2. At an equal timestamp, a deletion wins over a live row.
3. Otherwise the lexicographically highest persisted operation revision wins.

A revision is generated once per local mutation and reused across retries. Manual clock skew can affect which device's update wins; arrival order and retry count do not change the result. Records created by older versions without a revision are supported; migrated pending income gets a durable revision before upload.

Deletions use small server tombstones containing ID, owner, update time, revision, and isDeleted. Synced local tombstones are excluded from normal queries. They keep an older offline edit from resurrecting a newer deletion. A Room transaction applies an acknowledgement only when the submitted revision, update time, and pending status still match the local row. An edit made during an upload therefore remains pending. A newer authoritative server winner is applied locally instead of marking an older local value synced.

User isolation is enforced by Room queries and the income composite primary key, authenticated repository/data-source checks, user-specific document paths, and the existing Firestore security rules. Pending counts switch with the authenticated user; a worker rechecks the active user between queued income/expense operations.

Verification:

- Debug APK and Android test APK build successfully.
- 595 local unit tests pass. These include the existing income, expense, savings, authentication, calculations, worker/coordinator tests and new version-ordering/logout-queue tests.
- 18 Android Room/UI persistence tests pass: database migrations from versions 3/4/5, existing income/salary/expense flows, 20 queued offline entries, retry after a lost acknowledgement, in-flight edits, remote-winner handling, same-ID user isolation, and banner transitions.
- Three opt-in integration stages pass against real Firebase Auth/Firestore SDKs connected exclusively to the local demo-pfms-fr35 emulator project:
  - Stage 1 caches an income and expense online, disables both emulator radios, verifies cached access, creates 10 income plus 10 expense records, edits one of each, and deletes one of each. All 20 pending records are durable in Room.
  - The host force-stops the Android app while it remains offline.
  - Stage 2 starts a fresh app process while offline and verifies the saved session, edits, deletions, and queue. It enables connectivity and waits for the persisted WorkManager jobs without calling sync manually. Each server collection has 10 live records plus one deletion marker, with the expected IDs/amounts. Retrying introduces no extra records.
  - Stage 3 deliberately loses an acknowledgement after a real server commit, retries the same revision, tests newer server edits/deletions, signs in a second Firebase user, verifies Firestore denies access to the first user's path, and verifies a same-ID income for the second user cannot overwrite the first user's Room record.
- All 16 existing empty-state/expense UI regression tests pass, bringing Android verification to 37 passing tests overall.
- The normal application cold-starts successfully with database version 6. Its global offline banner appears after disabling Wi-Fi/mobile data and disappears after restoring connectivity. The application remains running, with no AndroidRuntime startup exception logged. Both radios were restored after testing.

The local Firebase test account/database are isolated from the normal application Firebase instance and financial database. No production test accounts or financial entries were created. Local emulator tools/cache/configuration and test logs are in build/fr35. The app's debug manifest allows HTTP for the local Auth emulator; release network policy is unchanged.

Reproduction uses the existing Android test runner. Start isolated Auth and Firestore emulators for demo-pfms-fr35 on ports 9199 and 8180 with the app's current security rules. Emulator configuration used:

```json
{
  "firestore": { "rules": "firestore.rules" },
  "emulators": {
    "auth": { "host": "127.0.0.1", "port": 9199 },
    "firestore": { "host": "127.0.0.1", "port": 8180 },
    "hub": { "host": "127.0.0.1", "port": 4405 },
    "logging": { "host": "127.0.0.1", "port": 4505 },
    "ui": { "enabled": false },
    "singleProjectMode": true
  }
}
```

Copy app/src/main/java/com/pfms/app/firestore.rules beside that configuration before starting the CLI. Invoke OfflineFirestoreEmulatorTest methods individually with instrumentation argument fr35Emulators=true, force-stopping com.pfms.app between stage1CreateAndModifyTwentyRecordsOffline and stage2RestartOfflineThenReconnectAutomaticallyWithoutDuplicates. The methods deliberately leave connectivity disabled between those two stages; stage2 and stage3 restore connectivity in their cleanup paths. Without the opt-in argument, these integration stages are skipped by ordinary Android test runs.

Build command used Android Studio's Java and GradleWrapperMain with :app:assembleDebug :app:assembleDebugAndroidTest :app:testDebugUnitTest --init-script build/fr34/offline-tests.gradle. That existing temporary init script excludes ExchangeRateServiceTest, whose three tests depend on external exchange-rate services. Those external endpoints are unrelated to FR-35 and were not changed or re-verified. Local unit results are in app/build/test-results/testDebugUnitTest; Android results are in build/fr35/room-ui-tests.txt, firebase-stage1.txt, firebase-stage2.txt, firebase-stage3.txt, and ui-regression-tests.txt.

No FR-35 functional blocker was found in the completed checks. Physical-device/OEM scheduling and the production Firebase backend are outside this emulator verification. Existing local IDE settings are preserved and excluded from the FR-35 commit.

Modified/new project files (plus this report):

- `app/src/androidTest/java/com/pfms/app/DatabaseVersionFourUpgradeTest.kt`
- `app/src/androidTest/java/com/pfms/app/MigrationFiveSixTest.kt`
- `app/src/androidTest/java/com/pfms/app/OfflineExpenseIntegrationTest.kt`
- `app/src/androidTest/java/com/pfms/app/OfflineFirestoreEmulatorTest.kt`
- `app/src/androidTest/java/com/pfms/app/OfflineQueueRoomTest.kt`
- `app/src/androidTest/java/com/pfms/app/OfflineSyncBannerTest.kt`
- `app/src/debug/AndroidManifest.xml`
- `app/src/main/java/com/pfms/app/MainActivity.kt`
- `app/src/main/java/com/pfms/app/PFMSApplication.kt`
- `app/src/main/java/com/pfms/app/data/local/PfmsDatabase.kt`
- `app/src/main/java/com/pfms/app/data/local/dao/ExpenseDao.kt`
- `app/src/main/java/com/pfms/app/data/local/dao/IncomeDao.kt`
- `app/src/main/java/com/pfms/app/data/local/dao/PendingChangesDao.kt`
- `app/src/main/java/com/pfms/app/data/local/entity/IncomeEntity.kt`
- `app/src/main/java/com/pfms/app/data/local/migration/DatabaseMigrations.kt`
- `app/src/main/java/com/pfms/app/data/remote/firestore/ExpenseRemoteDataSource.kt`
- `app/src/main/java/com/pfms/app/data/remote/firestore/FirestoreExpenseRemoteDataSource.kt`
- `app/src/main/java/com/pfms/app/data/remote/firestore/FirestoreIncomeRemoteDataSource.kt`
- `app/src/main/java/com/pfms/app/data/remote/firestore/FirestoreSyncVersion.kt`
- `app/src/main/java/com/pfms/app/data/remote/firestore/IncomeRemoteDataSource.kt`
- `app/src/main/java/com/pfms/app/data/remote/firestore/RemoteRecords.kt`
- `app/src/main/java/com/pfms/app/data/repository/FirestoreLocalDataRepository.kt`
- `app/src/main/java/com/pfms/app/data/repository/OfflineFirstExpenseRepository.kt`
- `app/src/main/java/com/pfms/app/data/repository/OfflineFirstIncomeRepository.kt`
- `app/src/main/java/com/pfms/app/data/repository/OfflineFirstSavingsGoalRepository.kt`
- `app/src/main/java/com/pfms/app/data/repository/OfflineSyncStatusRepository.kt`
- `app/src/main/java/com/pfms/app/data/repository/SalaryTemplateRepositoryImpl.kt`
- `app/src/main/java/com/pfms/app/data/sync/AndroidConnectivityObserver.kt`
- `app/src/main/java/com/pfms/app/data/sync/ExpenseSyncWorker.kt`
- `app/src/main/java/com/pfms/app/data/sync/IncomeSyncWorker.kt`
- `app/src/main/java/com/pfms/app/data/sync/PendingSyncCoordinatorImpl.kt`
- `app/src/main/java/com/pfms/app/data/sync/SyncStartup.kt`
- `app/src/main/java/com/pfms/app/data/sync/SyncVersion.kt`
- `app/src/main/java/com/pfms/app/di/DatabaseModule.kt`
- `app/src/main/java/com/pfms/app/di/SyncModule.kt`
- `app/src/main/java/com/pfms/app/domain/model/OfflineSyncState.kt`
- `app/src/main/java/com/pfms/app/domain/repository/ConnectivityObserver.kt`
- `app/src/main/java/com/pfms/app/domain/repository/SyncStatusRepository.kt`
- `app/src/main/java/com/pfms/app/ui/component/OfflineSyncBanner.kt`
- `app/src/main/java/com/pfms/app/viewmodel/SyncStatusViewModel.kt`
- `app/src/test/java/com/pfms/app/data/repository/ExpenseRemoteTransactionTest.kt`
- `app/src/test/java/com/pfms/app/data/repository/LocalPendingLogoutTest.kt`
- `app/src/test/java/com/pfms/app/data/repository/OfflineFirstExpenseRepositoryTest.kt`
- `app/src/test/java/com/pfms/app/data/repository/OfflineFirstIncomeRepositoryTest.kt`
- `app/src/test/java/com/pfms/app/data/sync/SyncVersionTest.kt`
