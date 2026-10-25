# Focused PFMS regression review — 2026-10-25

## Scope and source inspection

Inspected the working tree before editing. The previously implemented changes were present, including uncommitted teammate/frontend work; those changes and `.idea/gradle.xml` were preserved. This review does not replace earlier source files with patch snapshots.

Implemented paths found:

- Income: `IncomeFormScreen` → draft validation → `IncomeViewModel` → local-first income repository → Room pending write → one graph-level `IncomeSaveNavigationEffect`. A compare-and-set claim consumes completion once; late completion does not pop another screen. Stable IDs and save guards prevent repeated inserts. Dashboard/list readers observe Room.
- Savings: transaction dialogs retain drafts/errors; the controller guards immediate repeated taps. Domain ledger validation and transactional guarded DAO insert/update/delete methods enforce chronological withdrawal balances and the recorded-monthly-surplus contribution limit. Edits exclude their original row. Saved balance uses opening savings plus contributions minus withdrawals.
- Restoration: application startup starts `SyncStartup`; account/connectivity changes trigger repository refresh/retry. Goal/ledger fetches are account-checked, pending local mutations are protected, and Room observers publish restored data. Goal ViewModel checks account ownership and exposes restoration loading/error state.
- Spending detector: reactive expense repository observation invokes the domain detector with explicitly confirmed history, default 30% threshold and minimum-increase rule. UI renders returned amounts/percentage/severity, handles insufficient history/errors, and does not assign a trend to a zero baseline. Month-to-date comparisons match historical elapsed days.
- Simulator: registered route and callbacks from Savings Goal, guarded forward/back actions, domain-only calculations, amount validation, matching-input/result protection, no-goal/completed/overdue states, and read-only repository access.
- FX: one existing public-rate cache; latest cache age 0–14 days; exact-date historical quotes; manual fallback; fetched/cached/manual/saved labels and quote date; original saved conversion values survive fetch/reconnect/note-only edits.

## Confirmed issue and correction

The first full JVM run executed **855 tests, with 3 failures**. All failures were stale Mockito expectations in `OfflineFirstSavingsGoalRepositoryTest`: two additions expected raw `upsertTransaction`, and deletion expected raw `markTransactionDeleted`. Production correctly uses `insertTransactionGuarded` and `deleteTransactionGuarded`.

Updated those three expectations to verify guarded persistence happens before the remote attempt, while direct unguarded writes are not used. Added two regression tests proving surplus/withdrawal guard rejection is returned to the caller and cannot reach remote writes, and that rejecting deletion of a funding contribution cannot delete remotely. Added valid dates/opening funds to the affected fixtures. One intermediate test compilation failed because a new fixture omitted the required goal name; that fixture was corrected before the final successful run.

Only this existing test file and this report were changed by this review. No production behavior needed changing based on the completed checks.

## Build and unit results

Executed from the workspace with Android Studio's JBR as `JAVA_HOME`:

```powershell
.\gradlew.bat compileDebugKotlin testDebugUnitTest assembleDebug assembleDebugAndroidTest --rerun-tasks --console=plain
# After correcting stale tests:
.\gradlew.bat compileDebugKotlin testDebugUnitTest assembleDebug --console=plain
```

| Check | Result | Evidence |
| --- | --- | --- |
| `compileDebugKotlin` | PASS | Executed during the first full rebuild; final invocation up-to-date |
| `testDebugUnitTest` | PASS | Final XML: **857 tests, 105 suites, 0 failures/errors/skips** |
| `assembleDebug` | PASS | Debug APK rebuilt; final invocation up-to-date |
| `assembleDebugAndroidTest` | PASS | Android test APK rebuilt |

The complete unit suite includes the three existing public-provider exchange-rate tests; all three passed on this run. Deterministic cache/failure/boundary tests also passed. This does not guarantee future public-provider availability.

Existing compiler warnings remain (deprecated Compose Hilt imports, annotation target changes, always-true checks, and unstripped native debug libraries). They did not prevent compilation or packaging and were outside the requested regression fixes.

## Automated Android checks

Used existing `emulator-5554`, installing debug/test APKs with `install -r` without clearing production data. Tests use dedicated Room databases, preferences and test auth/remote transports. No manual workflow test or real-user financial write was performed.

The first income instrumentation process stalled after the dashboard save scenario. Its diagnostic screenshot showed the harness dashboard and updated total, not a blank destination. A window dump contained an ANR window, but `dumpsys activity lastanr` did not supply an app ANR report; a root cause was not established. That process was stopped and returned `Process crashed`; it is not counted as a passing run. The same scenario passed independently (**1/1**), then the full income navigation class passed in a fresh process. The isolated rerun is not an additional distinct scenario in the totals below.

Final batch outputs are saved in ignored `app/build/pfms-regression-android.txt`.

| Android suite | Passed |
| --- | ---: |
| IncomeSaveNavigationTest | 7 |
| IncomePersistenceIntegrationTest | 2 |
| IncomeFormUiTest | 3 |
| IncomeExchangeRateIntegrationTest | 2 |
| SavingsRestorationTest | 5 |
| SavingsWithdrawalPersistenceTest | 5 |
| SavingsWithdrawalUiTest | 3 |
| SavingsContributionFlowUiTest | 6 |
| SpendingPatternsOfflineTest | 1 |
| SpendingPatternsUiTest | 4 |
| SavingsSimulatorReadOnlyTest | 1 |
| SavingsSimulatorUiTest | 7 |
| FinanceFeatureNavigationTest | 8 |
| Total distinct tests in the completed batches | **54** |

## Feature verification matrix

PASS below means the named automated check passed; it does not imply a manual production-backend test.

| Feature or bug | Verification performed | Result | Files changed by this review | Remaining issues |
| --- | --- | --- | --- | --- |
| Kotlin compilation | Full `compileDebugKotlin` rebuild | PASS | None | Existing warnings |
| Complete JVM suite | 857 tests across 105 suites, no skips | PASS | `OfflineFirstSavingsGoalRepositoryTest.kt` | Public-provider tests depend on network availability |
| Debug/app-test packaging | `assembleDebug`, `assembleDebugAndroidTest` | PASS | None | Build success is separate from test success |
| Online income creation | Form/VM/repository tests; online simulated transport and one remote row | PASS | None | Real Firebase SDK/rules path not exercised |
| Offline income creation and blank-screen fix | Real form, Room, dashboard readers and NavHost; one acknowledged completion | PASS | None | Initial stalled instrumentation process is disclosed above |
| Save navigation, repeated collection/recomposition | Income navigation and completion tests | PASS | None | No universal crash-free guarantee beyond exercised workflows |
| Income visibility after restart | Disk Room reopen plus recreated ViewModels/cache consumers | PASS | None | Actual OS force-stop/relaunch is not tested here |
| Withdrawal validation/errors/correction | UI example, full ledger validator, real Room rejection and corrected save | PASS | `OfflineFirstSavingsGoalRepositoryTest.kt` | None identified in exercised scenarios |
| Recorded-monthly-surplus contribution validation | Domain/VM tests and six real-dialog/Room tests, including offline and rapid taps | PASS | `OfflineFirstSavingsGoalRepositoryTest.kt` | Real server-side behavior not exercised |
| Contribution/withdrawal editing and deletion | Guarded replacement/deletion, balance refresh and rejected funding deletion | PASS | `OfflineFirstSavingsGoalRepositoryTest.kt` | None identified in exercised scenarios |
| Goal restoration/login/reconnect/account scoping | Startup/session unit tests and five Room tests with simulated auth/remote; duplicate/pending protection | PASS | None | Real Firebase logout/login remains unverified |
| Category patterns, percentages and severity | Domain/VM tests, four UI tests and real Room observation | PASS | None | Requires two confirmed historical months; zero baselines intentionally unrated |
| Detector empty/insufficient/partial-month/offline/CRUD states | Domain/VM/UI tests and offline Room add/edit/delete/reopen | PASS | None | None identified in exercised scenarios |
| Simulator entry/back/input/empty/completed/overdue | Seven simulator UI tests, domain/VM tests, navigation helpers | PASS | None | None identified in exercised scenarios |
| Simulator never mutates records | Repeated scenarios/retries with real Room and remote-write counters | PASS | None | None identified in exercised scenarios |
| FX cache warning/date, freshness and historical matching | Service/state tests, two form/Room integrations, public-provider tests | PASS | None | Expiry boundary is a deterministic automated test, not a manual aged-device check |
| FX manual fallback and saved conversion preservation | Invalid/corrected manual input, offline save, cache/db recreation, reconnect/no-op edit | PASS | None | Actual device process death not exercised |
| Feature destinations/back stack/repeated taps | Eight feature-navigation tests plus income/simulator navigation tests | PASS | None | No production UI route changes required |
| Real Firebase Auth/Firestore end-to-end flows | Checked isolated emulator availability; required services absent | BLOCKED | None | No services on 9199/8180; use QA backend/checklist below |
| Manual phone/emulator workflows and radio toggling | No manual workflow execution | NOT TESTED | None | Follow checklist below on a QA account |

## Remaining end-to-end verification

No isolated Firebase Auth/Firestore services were listening on the configured local test ports **9199/8180**. Real Firebase session restoration/sync, device radio changes, and actual OS process death/relaunch were not exercised by the isolated transports or database reopening tests. Do not treat those automated tests as a manual production-backend signoff.

Use a QA account and the configured backend for the following exact manual checks:

1. **Income and restart:** online, Income → Add Income → save a small identifiable entry. Verify one row and dashboard update. Turn off both radios; save another valid income. Verify return to the caller, no blank screen and one new row. Force-stop/reopen offline and confirm both saved entries remain visible. Reconnect and confirm one remote document per entry and no duplication. Reopen Add Income and return without saving; the old completion must not navigate again.
2. **Savings validation and edits:** create a goal with sufficient opening funds. Record legitimate income/expenses for the selected month, then a contribution within the resulting surplus. Attempt one above the remaining recorded surplus; verify a clear error, open dialog, unchanged balance and no ledger document. Correct and retry. Attempt a withdrawal above the displayed available amount; verify the same protections. Edit and delete a contribution/withdrawal and check the balance exactly once. Deleting funding required by an existing withdrawal should be rejected.
3. **Restoration and accounts:** wait until the QA goal and ledger documents are synchronized. Log out normally, then log in with the same account online. Confirm goal, ledger, balance and progress restore. Disable radios and reopen; cached Room data remains readable. Reconnect and check no duplicate ledger IDs. Switch to a second QA account and verify the first account's data is absent; switch back. Do not force logout with pending writes when testing successful cloud restoration.
4. **Detector:** record sufficiently complete history for two of the previous three months, then confirm those months in Spending Analytics → Smart spending patterns. Use one category with a significant domain-eligible increase and another with no increase; verify amounts/severity. Add, edit and delete a current expense and watch results update without reopening. Offline, repeat using locally available data. Unconfirm history and verify the insufficient-history message.
5. **Simulator/navigation:** Savings Goal → What-if Savings; enter a nonnegative decimal reduction, then negative/non-numeric input, correct it, and try zero/blank. Check no-goal, completed and overdue QA fixtures. Record ledger/balance before and after input changes; they must not change. Verify toolbar and Android Back return to Savings Goal, reopen repeatedly, and rapidly tap the opening link. Spending Analytics → Patterns → Back must return correctly.
6. **FX:** online USD draft: record fetched quote/date/preview, then save. Offline new draft: verify cached label/date/warning and save. Backdate to an uncached day: manual input is required; a quote from another day cannot be substituted. Try invalid/zero/negative manual values, correct to a positive decimal, and save. Reconnect/Refresh rate on a new draft; previously saved conversions must remain unchanged, including after note-only edits and force-stop/reopen. Missing/expired-cache manual testing requires an isolated installation/fixture; do not clear normal financial data or change the user's device clock. Day-14/day-15 matching is covered by deterministic tests.
