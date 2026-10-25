# What-if savings simulator frontend

## Existing flow and preserved calculation

`SavingsGoalScreen` forwards `onOpenSavingsSimulator` to its redesigned content.
The active goal's Savings pace panel has an Explore What-if link. `PfmsNavGraph`
connects that link to `Routes.SavingsSimulator`. Toolbar and Android Back return to
Savings Goal, reusing its existing entry when available. The same simulator destination
is also accessible from spending patterns; its Back destination remains Savings Goal.

`SavingsSimulatorViewModel` observes the authenticated account's active goal and
ledger through `SavingsGoalRepository`. `CalculateSavingsSimulationUseCase` alone
calculates actual saved funds, potential extra savings, projected balance/progress,
shortfall, and optional completion date. Its algorithm was not changed.

Potential extra savings include the hypothetical spending reduction. The projected
balance additionally includes other planned monthly contributions. Both inputs are
scenario values in memory, never real contributions. Current saved funds still come
from opening savings plus contributions minus withdrawals in the actual ledger.

## Completed frontend work

- Blank input means zero. Exact existing currency parsing rejects negatives,
  unrepresentable values, text and fractional minor units. Errors preserve the draft.
- Forecasts are hidden immediately when inputs are invalid or when the displayed
  calculation does not yet match the current input amounts. Corrections recalculate.
- Independent dirty flags keep untouched input fields synchronized. Changing goals
  clears errors/draft overrides from the previous goal.
- Forecasts clearly distinguish actual recorded savings from hypothetical values.
  Full amounts use existing finance components and a vertically scrollable layout.
- Loading, missing active goal, failed loading, calculation error, and results are
  separate states. Retry restarts read observation; resume refreshes the calculation
  date. Startup restoration is observed so missing local data during restoration
  does not prematurely appear as a confirmed empty goal.
- An estimated completion row appears only for a non-completed scenario when the
  domain supplies a date. A missing date is explained, never fabricated.
- Completed goals show no future extra savings. Overdue goals explain that deadline
  projections add no future savings; the domain may still return a future completion
  estimate based on the hypothetical monthly pace.
- Open Savings Goals replaces the simulator destination. If the previous destination
  is already Savings Goal, single-top navigation reuses it instead of adding a duplicate.
- No domain, repository persistence, database schema or dependencies changed.

## Files changed for this task

- `app/src/main/java/com/pfms/app/ui/screen/savings/SavingsSimulatorScreen.kt`
- `app/src/main/java/com/pfms/app/viewmodel/SavingsSimulatorViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/SecondaryFinancePresentation.kt` (simulator controller)
- `app/src/main/java/com/pfms/app/ui/navigation/PfmsNavGraph.kt` (simulator's missing-goal navigation only)
- `app/src/test/java/com/pfms/app/viewmodel/SavingsSimulatorViewModelTest.kt` (new)
- `app/src/androidTest/java/com/pfms/app/SavingsSimulatorUiTest.kt` (new)
- `app/src/androidTest/java/com/pfms/app/SavingsSimulatorReadOnlyTest.kt` (new)

`SavingsGoalScreen.kt` and its callback chain were inspected and already wired correctly;
they required no additional change for this task.

## Executed checks (2026-10-25)

- Debug APK and Android test APK builds succeeded.
- 24 JVM tests passed: 13 new `SavingsSimulatorViewModelTest`, 5 existing
  `InnovationUseCasesTest`, and 6 existing `HistoryAndSecondaryRedesignStateTest`.
- Emulator `SavingsSimulatorReadOnlyTest`: `OK (1 test)`. The real simulator ViewModel
  and offline-first repository read an isolated Room goal and contribution/withdrawal
  ledger. Repeated changes and retry left the goal entity, ledger entities, and pending
  count unchanged. The supplied remote transport recorded zero reads and zero writes.
- Emulator `SavingsSimulatorUiTest`: `OK (7 tests)`. Covers valid input updates,
  actual/hypothetical labels, invalid input/correction/blank-zero, missing goal, errors,
  completed and overdue goals, completion date visibility, narrow dark layouts, and
  Goal → simulator → Back → Goal → reopen navigation in a Compose NavHost.
- Existing simulator UI regressions in `HistoryAndSecondaryUiTest`: `OK (2 tests)`.
- `git diff --check` passed.

These are targeted checks, not a full project test run. Navigation wiring in the real
`PfmsNavGraph` was inspected; UI navigation tests use an isolated NavHost without live
authentication. Live Firebase services and physical network toggling were not exercised.

## Manual verification

1. With an active goal, open Savings Goal → Savings pace → Explore What-if.
2. Try a monthly spending reduction and an optional planned monthly contribution.
   Check the hypothetical forecast updates while actual recorded savings stays fixed.
3. Try blank/zero, negative, non-numeric, excessive and fractional-cent input. Invalid
   input must show an inline error and hide the old forecast; correcting it restores results.
4. Use Back, then reopen. Check no duplicate goal destination is created by Open Savings
   Goals if the active goal disappears while the simulator is open.
5. Check an already-funded goal, overdue goal, zero monthly pace and unavailable date.
6. With no active goal, confirm the simulator explains the prerequisite and opens Goals.
7. Compare the goal/ledger before and after several scenarios, including while offline.
   No goal update, contribution, withdrawal or pending financial write should be created
   by the simulator. Normal application synchronization remains separate.
