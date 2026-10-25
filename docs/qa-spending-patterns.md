# Smart spending patterns frontend

## Existing calculation and observation flow

`SpendingAnalyticsContent` links to `Routes.SpendingPatterns` through `PfmsNavGraph`.
The screen observes `SpendingPatternsViewModel`, which uses
`ObserveSpendingPatternsUseCase` and `DetectSpendingPatternsUseCase`.
`ExpenseRepository` is bound to `OfflineFirstExpenseRepository`; its user-scoped Room
observation updates the detector for additions, edits, deletions, and synchronized changes.
Network failure does not prevent reading cached Room expenses.

The existing detector is unchanged. It requires at least two explicitly confirmed
historical months from the previous three, uses a default 30% increase threshold and
LKR 1,000 minimum increase, and returns NOTICEABLE/HIGH severity. Zero-baseline
categories are excluded. Unfinished months compare historical spending through the
same day of month and exclude future current-month expenses. Missing history is not
automatically treated as a reliably recorded zero-spend month.

## Frontend changes

- Category cards display returned category, current amount, historical average,
  difference, percentage, severity, comparison-month count, and comparable-day context.
- Severity uses text labels and distinct theme colors; the UI never derives severity
  or calculates an independent baseline/percentage.
- Amounts stack on narrow screens or with larger font settings, using the shared
  finance amount component to keep full currency values visible.
- Loading clears old results while comparison inputs/retries change. Errors use the
  existing safe error presenter and Retry action, which restarts a failed stream.
- Missing result, insufficient history, and no detected increase have separate copy.
  An empty result is not described as proof of a steady trend. The UI explains that
  categories without positive historical baselines are unrated; the domain does not
  supply per-category details for excluded categories, so none are invented.
- Resume refreshes the date and observation; expired cached state resets after the
  subscription timeout. Confirmed months outside the valid window are removed.
- No domain algorithm, navigation destination, repository binding, database schema,
  dependency, or unrelated screen was changed.

## Files

- `app/src/main/java/com/pfms/app/ui/screen/analytics/SpendingPatternsScreen.kt`
- `app/src/main/java/com/pfms/app/viewmodel/SpendingPatternsViewModel.kt`
- `app/src/main/java/com/pfms/app/viewmodel/SecondaryFinancePresentation.kt`
- `app/src/test/java/com/pfms/app/viewmodel/SpendingPatternsViewModelTest.kt` (new)
- `app/src/androidTest/java/com/pfms/app/SpendingPatternsUiTest.kt` (new)
- `app/src/androidTest/java/com/pfms/app/SpendingPatternsOfflineTest.kt` (new)

## Executed checks (2026-10-25)

- Debug APK and Android test APK builds succeeded.
- 20 JVM tests passed: 9 new `SpendingPatternsViewModelTest`, 5 existing
  `InnovationUseCasesTest`, and 6 existing `HistoryAndSecondaryRedesignStateTest`.
- Emulator run: `OK (7 tests)`. This includes four new UI tests, one real Room/offline
  CRUD and database-reopen test, and the two existing `HistoryAndSecondaryUiTest`
  methods for Spending Analytics links and history-month/simulator controls.
- Coverage includes multiple categories, returned severity and amounts, default
  threshold behavior, no increase, insufficient/zero-baseline history, partial-month
  data, errors/retry, recalculation loading, calendar changes, and narrow dark layouts
  with enlarged text and full currency values.
- `git diff --check` passed.

The initial offline test did not run because JUnit rejected an inferred Boolean return
type. Its signature was corrected to Unit; the final seven-test run passed in full.
Offline transport failures were simulated against isolated real Room databases. Live
Firestore synchronization and physical device network toggling were not exercised.

## Manual check

1. Open Spending Analytics, then Smart spending patterns. Check Back returns to
   Spending Analytics and Explore savings impact opens the savings simulator.
2. Confirm only months with adequately recorded history. Fewer than two should
   show the insufficient-history message.
3. With valid history and an increase meeting domain rules, verify category amounts,
   percentage and severity against the stored results. Add/edit/delete an expense
   and return to the screen; the result should update without restarting the app.
4. Disable network access with expenses cached in Room. Confirm patterns still load;
   network reconnection must not be required to calculate them.
5. Check a zero-baseline category is not assigned a percentage/severity. For a partial
   month, verify the comparable-day explanation and the displayed as-of date.
6. Check small screens, larger text and both themes. After a recoverable observation
   failure, Retry should replace the error with newly observed results.
