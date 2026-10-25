# Offline income save: blank screen regression

## Investigation

The save path is `IncomeFormScreen` -> `IncomeFormViewModel.submit` ->
`IncomeViewModel.addIncome` -> `OfflineFirstIncomeRepository.addIncome` ->
`IncomeDao.upsertIncome`. The repository writes a user-scoped Room row with
`PENDING_INSERT`, then schedules network-constrained sync. The ViewModel sets
`saveSuccessful` only after the repository returns. Room observation updates both
the income state and `DashboardViewModel`; neither requires restarting the app.

The production graph had a root save-success effect and separate effects inside
Add Income and Edit Income. They collected the same shared StateFlow. Each effect
could hold a `true` snapshot, clear the flag, and pop independently. Clearing the
flag did not invalidate the other collector's captured value. Two pops could skip
the intended caller; with only Home underneath the form, they removed the final
destination and left `NavHost` empty.

Before fixing the behavior, two regression tests with a real Compose NavHost,
income form, ViewModel and disk Room failed. The Home -> Add Income case produced
`currentDestination == null` after the successful local save. The Income List ->
Add Income case also failed its expected-return-destination assertion. This is a
navigation failure after persistence, rather than a failed offline write. The
same race can affect online saves because both paths report local success.

## Fix

- `PfmsNavGraph` installs one `IncomeSaveNavigationEffect`; form destinations no
  longer independently handle save completion.
- `IncomeViewModel.consumeSaveSuccess` atomically acknowledges live state. Even
  multiple collectors holding an old `true` snapshot cannot claim it twice.
- Navigation only pops Add/Edit Income. A completion arriving on another screen
  is acknowledged without navigating. If the form has no previous destination,
  Income List is the fallback.
- Add/update ignore additional submissions while saving or while a completed
  save awaits consumption. Existing stable IDs for failed-save retries remain.

The existing acknowledged-UI-state convention is retained. A separate replaying
flow/channel is unnecessary: completion remains available until consumed, even
when the UI is not collecting, and cannot trigger navigation after consumption.
Room schema, repository persistence, Firestore code and WorkManager are unchanged.

## Executed verification

- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: successful.
- 53 targeted JVM tests: passed, zero failures/errors. Suites:
  `IncomeSaveCompletionTest`, `IncomeCompletionTest`, `FinancialErrorHandlingTest`,
  `BackdatedEntryViewModelTest`, `OfflineFirstIncomeRepositoryTest`,
  `IncomeStreamViewModelTest`, `IncomeFormViewModelTest`, and
  `IncomeNavigationCutoverTest`.
- Pixel 7 Pro emulator: `IncomeSaveNavigationTest` (7) and
  `IncomePersistenceIntegrationTest` (2), **OK (9 tests)**.
- Coverage includes saving from Home and Income List, competing collectors,
  recomposition and collector remounting, returning from edit, safe fallback,
  late completion on another screen, repeated taps, retry IDs, dashboard and
  income reader updates, pending offline rows, restored sync, historical USD
  fields, and database/ViewModel recreation without replaying success.

Instrumentation uses real Room, Compose navigation and ViewModels with isolated
test databases. Auth and remote services are controlled fakes: offline writes
are tested with the fake server rejecting writes, and online/reconnection sync
with it accepting writes. No production financial records are inserted by these
tests. Database reopening verifies disk durability; it does not exercise a full
OS process restart through the real authentication/session gate.

## Remaining release-device check

Using a QA account, sign in, disable Wi-Fi and mobile data, and save a valid LKR
income from Income -> Add Income. Confirm one return to Income, one new record,
and the updated current-month dashboard. Repeat from Home -> Add Income and
edit an entry. Rotate or background/resume to check that navigation does not
repeat. Force-stop and reopen while offline; verify the same single record.
Reconnect and verify that Firestore receives that record once, then create one
income online. Real Firebase synchronization and the complete signed-in process
restart are manual checks; this run does not claim live-cloud validation.
