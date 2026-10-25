# Finance feature navigation review

## Routes and wiring inspected

The current `PfmsNavGraph` registers each audited route exactly once:

| Route constant | Route | Arguments |
| --- | --- | --- |
| SpendingAnalytics | `spending_analytics` | None |
| SpendingPatterns | `spending_patterns` | None |
| SavingsGoal | `savings_goal` | None |
| SavingsSimulator | `savings_simulator` | None |
| AddIncome | `add_income` | None |
| EditIncome | `edit_income/{incomeId}` | String `incomeId`; existing route builder URI-encodes it |
| IncomeList | `income_list` | None |
| Dashboard | `dashboard` | None |

No missing registration, duplicate registration, incorrect route string, or missing
feature argument was found. `Routes.kt` needed no change.

`SpendingAnalyticsScreen` forwards `onOpenSpendingPatterns` to its Smart spending
patterns link. `SavingsGoalScreen` forwards `onOpenSavingsSimulator` through its
content to Explore What-if. Both callback chains were already present. Spending
Patterns also retains its Explore savings impact link to the simulator.

## Confirmed issues and minimal corrections

- Simulator Back previously called a plain pop. Opening it from Spending Patterns
  therefore returned to Patterns, conflicting with the explicit Simulator → Savings
  Goal requirement. Toolbar and Android Back now share the same navigation action:
  pop to an existing Savings Goal entry, or replace the simulator with Savings Goal.
- Missing-goal navigation uses the same action. Existing Goal entries are reused even
  if other feature screens sit between Goal and simulator; no duplicate goal is added.
- Previously, callbacks from an outgoing destination could still pop/navigate again.
  Feature actions now require the originating back-stack entry to remain current.
  Rapid repeated callbacks after the first action are ignored. Forward navigation
  also retains the existing single-top option.
- Patterns Back returns to the previous entry. If Patterns is itself the first entry,
  it opens Spending Analytics safely rather than leaving an empty navigation stack.

No destination was removed, and the existing NavHost/Hilt architecture is retained.

## Income save fix preserved

The graph still has one `IncomeSaveNavigationEffect` owner and no additional
`saveSuccessful` navigation observers. `consumeSaveSuccess()` atomically acknowledges
the persistent state before navigating, so recomposition or simultaneous collectors
cannot pop twice. Add/Edit Income return to their caller; an isolated form falls back
to Income List. A completion arriving on another destination is consumed without
redirecting that screen. No income persistence or success-consumption code was changed
for this navigation review.

## Files modified for this task

- `app/src/main/java/com/pfms/app/ui/navigation/PfmsNavGraph.kt`
- `app/src/main/java/com/pfms/app/ui/navigation/FinanceFeatureNavigation.kt` (new scoped actions/Back handler)
- `app/src/androidTest/java/com/pfms/app/FinanceFeatureNavigationTest.kt` (new)
- `app/src/androidTest/java/com/pfms/app/SavingsSimulatorUiTest.kt` (navigation harness uses production actions)
- `app/src/test/java/com/pfms/app/ui/navigation/NavigationRoutesTest.kt`
- `docs/qa-finance-navigation.md` (this report)
- `docs/qa-savings-simulator.md` (updated Back behavior)

`Routes.kt`, Spending Analytics/Patterns screen callbacks, Savings Goal/Simulator
screen callbacks, and the income completion implementation were inspected and retained.

## Executed checks (2026-10-25)

- Debug APK and Android test APK builds succeeded.
- 15 JVM tests passed: 5 `NavigationRoutesTest`, 4 `IncomeNavigationCutoverTest`,
  and 6 `IncomeSaveCompletionTest`.
- Emulator `FinanceFeatureNavigationTest`: `OK (8 tests)`. Uses actual feature content,
  the production navigation actions, and a real NavHost. Covers repeat open/close,
  toolbar/Android Back, stale callbacks, root fallback, missing data, simulator opened
  from Patterns, and reuse of an existing Goal entry below other feature destinations.
- Emulator `IncomeSaveNavigationTest`: `OK (7 tests)`. Covers online/offline creation,
  caller return/fallback, edit success, repeated success collectors, recomposition,
  late completion elsewhere, and persistence across reopen. Tests use isolated Room
  and simulated remote transport; no live-account income was inserted.
- Updated simulator navigation regressions: `OK (2 tests)` for Goal → simulator →
  Back → reopen and the missing-goal action, using the production navigation helpers.
- `git diff --check` passed. Registration audit found one declaration per listed route.

Direct signed-in emulator checks also passed: Insights → Spending Analytics → Patterns;
Patterns toolbar Back → Analytics; reopening Patterns; Patterns → simulator empty
state; Android Back → the existing Savings Goal entry. A salary reminder was dismissed
with Not Yet without confirming income. The account displayed No Active Savings Goal,
so the populated Goal → simulator flow was exercised in isolated UI tests instead.
Live-account income saves were not performed; online/offline save verification used
the isolated persistence tests above. No live financial records were changed.

## Manual live-account checklist

1. Open Spending Analytics → Smart spending patterns. Use toolbar Back, reopen,
   then use Android Back. Both should return to the caller.
2. Open Goal → Explore What-if. Use both Back actions and reopen repeatedly. Each
   return should show the same Goal entry without an extra simulator beneath it.
3. Open simulator through Patterns → Explore savings impact. Both Back actions
   should now show Savings Goal. Check missing/unavailable-goal navigation too.
4. Tap navigation links/Back rapidly; the first action should take effect once.
5. Save income from Income List and Dashboard, online and offline. Verify one record,
   correct return destination, visible updated data, and no blank screen. Reopen the
   form to verify an old save-success state cannot navigate again.

Automated UI checks use isolated NavHosts, the production feature navigation actions,
and actual screen components. Income tests use real isolated Room persistence with
simulated authentication/remote transport, not live-account financial records.
