FR-34: Empty States and Safe Calculations — implementation record, 20 October 2026.

The current project uses Hilt ViewModels, StateFlow, and lifecycle-aware Compose collection. Income, expenses, and savings goals use Room-backed repositories with Firestore synchronisation. `OfflineFirstDashboardRepository` reads the expense repository; dashboard, spending analytics, and history combine those records with income. Domain calculators own money rules. Navigation is defined in `PfmsNavGraph`.

| Data-dependent screen | Empty-data behaviour and action |
| --- | --- |
| Dashboard | Monthly income, expenses, and net show LKR 0.00. Spending split keeps zero figures. Empty overview offers Add Income; existing expense shortcuts and recent-activity action remain available. |
| Income list | Existing zero summaries and Add Income empty card retained. Currency totals now come from the domain calculator through ViewModel state. Monthly realised income is connected to the existing card. |
| Expense list | Neutral donut, LKR 0.00 totals, and 0% split. Empty month offers Add Expense; a search/filter with no matches offers Clear filters. |
| Spending analytics | Zero-valued donut, split, discretionary share, and continuous monthly trend remain visible. Add Expense and Add Income use the existing routes. |
| Income analytics | Monthly summary, source breakdown, 0% split, and empty trend remain visible, including when optional summaries are missing. Add Income uses the existing route. An empty split stays neutral instead of showing 100% variable income. |
| Transaction history | Zero count and totals plus Add Income and Add Expense. A filtered empty result retains its separate Clear filters action. |
| Savings goal | Missing goal shows saved LKR 0.00, progress 0%, and the existing creation dialog. An empty ledger offers Add Contribution using the existing contribution dialog. |
| Recurring expenses/subscriptions | Empty template lists offer the existing add-template form. Existing zero monthly subscription total is retained. |
| Categories | Existing add-category form remains usable without records. Empty archive offers View active categories. |
| Salary template | Existing create-template form already handles a missing template; retained. |

`EmptyState` and the existing Material 3 components are reused. Composables receive state and callbacks; financial formulas live in domain calculators. Hilt bindings and persistence schemas are unchanged by FR-34.

Calculation changes:

- Shared ratio guards return zero for zero/negative denominators and non-finite inputs/results. Financial percentages retain legitimate signed values; visual progress is constrained to 0–100%.
- Zero totals, empty categories, and empty trends remain renderable. Charts do not invent positive bars for zero income.
- Unconverted USD income contributes zero LKR, consistently with the income summary. A missing conversion never substitutes USD cents for LKR cents.
- Missing and malformed dates are ignored by monthly calculations. Missing transaction dates have a readable fallback.
- Goal monthly saving uses integer ceiling division. Completion projections outside the supported calendar range return an unavailable projection instead of throwing.
- Savings inputs reject amounts outside Long minor-unit range and sub-cent values. Amount formatters handle Long.MIN_VALUE without a duplicated negative sign.

Verification:

- Debug APK and Android test APK build successfully.
- Updated debug APK installed on the emulator. Final cold launch succeeded, and the running app process had no AndroidRuntime errors in its log.
- 587 local unit tests pass, including new-user repositories/ViewModels, zero denominators, NaN/Infinity guards, empty monthly data, missing conversion/date/goal data, zero net income with existing records, and extreme goal projections.
- All 23 selected emulator tests pass: 10 FR-34 UI tests, 6 existing expense UI tests, and 7 existing offline expense integration tests. Coverage includes add actions, the goal-creation dialog, empty ledger/chart rendering, NaN/Infinity progress, filtering, expense editing, category/subscription management, offline CRUD, duplicate handling, account isolation, and database upgrades.
- The first unrestricted unit-suite run also attempted the existing `ExchangeRateServiceTest`. Two live-service tests failed: the latest-rate endpoint was unreachable and the requested historical rate was unavailable. The final local suite excluded that three-test external-service class using the temporary helper `build/fr34/offline-tests.gradle`. Production exchange-rate behaviour was not changed.
- New-user verification uses empty repository fixtures and emulator UI state snapshots; it does not create a live Firebase account or clear existing emulator financial records.

Build/test commands (PowerShell; Android Studio's Java avoids the machine's invalid JAVA_HOME):

```powershell
& 'C:\Program Files\Android\Android Studio\jbr\bin\java.exe' -classpath gradle/wrapper/gradle-wrapper.jar org.gradle.wrapper.GradleWrapperMain :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest --init-script build/fr34/offline-tests.gradle
```

The temporary init script contains:

```groovy
allprojects {
    tasks.withType(org.gradle.api.tasks.testing.Test).configureEach {
        exclude '**/ExchangeRateServiceTest.class'
    }
}
```

The selected emulator tests ran through `com.pfms.app.test/com.pfms.app.CustomTestRunner`, with class filter `com.pfms.app.EmptyStatesUiTest,com.pfms.app.ExpenseScreenIntegrationTest,com.pfms.app.OfflineExpenseIntegrationTest`. The full emulator result is saved in `build/fr34/emulator-test-results.txt`; local unit XML reports are in `app/build/test-results/testDebugUnitTest/`.

Files changed for FR-34 (paths under `app/src/main/java/com/pfms/app/` unless stated otherwise):

| Area | Files |
| --- | --- |
| Repository | `data/repository/OfflineFirstDashboardRepository.kt` |
| Shared calculation rules | `domain/usecase/SafeCalculations.kt` (new), `domain/usecase/IncomeSummaryCalculator.kt`, `domain/usecase/dashboard/MonthlyFinanceCalculator.kt` |
| Income calculations | `domain/usecase/analytics/IncomeRealizedCalculator.kt`, `GetRegularVsVariableIncomeUseCase.kt`, `GetIncomeEstimationComparisonUseCase.kt` in the same directory |
| Savings calculations | `domain/usecase/savings/CalculateGoalProgressUseCase.kt`, `CalculateGoalPaceUseCase.kt`, `CalculateDiscretionaryImpactUseCase.kt` in the same directory |
| ViewModels | `viewmodel/IncomeViewModel.kt`, `viewmodel/IncomeAnalyticsViewModel.kt` |
| Shared presentation | `ui/component/DashboardCharts.kt`, `ui/component/TransactionRow.kt` |
| Navigation/dashboard | `ui/navigation/PfmsNavGraph.kt`, `ui/screen/DashboardScreen.kt` |
| Analytics/history | `ui/screen/analytics/SpendingAnalyticsScreen.kt`, `ui/screen/income/IncomeAnalyticsScreen.kt`, `ui/screen/history/TransactionHistoryScreen.kt` |
| Income/expenses | `ui/screen/income/IncomeListScreen.kt`, `ui/screen/expense/ExpenseListScreen.kt`, `ui/screen/expense/AdvancedExpenseScreens.kt` |
| Savings presentation | `ui/screen/savings/SavingsGoalScreen.kt`, `SavingsGoalComponents.kt`, `SavingsGoalUiFormatter.kt` in the same directory |
| New unit tests | `app/src/test/java/com/pfms/app/domain/usecase/EmptyDataSafetyTest.kt`, `app/src/test/java/com/pfms/app/viewmodel/EmptyUserViewModelTest.kt` |
| Updated unit tests | `app/src/test/java/com/pfms/app/viewmodel/IncomeAnalyticsViewModelTest.kt`, `DashboardViewModelTest.kt` in the same directory; `app/src/test/java/com/pfms/app/utils/MainDispatcherRule.kt` (awaits tracked ViewModel cancellation before resetting Main) |
| New emulator tests | `app/src/androidTest/java/com/pfms/app/EmptyStatesUiTest.kt` |
| Documentation | `docs/FR34-empty-states-and-safe-calculations.md` |

The database-version-5 startup repair and IDE settings changes were already present before this task. FR-35 is outside this implementation.
