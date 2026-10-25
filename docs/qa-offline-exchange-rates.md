# Offline USD/LKR integration

## Source inspection

The existing currency patch was already in the current source. `ExchangeRateService` uses the existing `ExchangeRateCache`, a device-wide SharedPreferences cache of public rates. Latest offline quotes are accepted only at ages 0 through 14 calendar days in Asia/Colombo. Historical cache reads already require the requested date exactly. Future transactions require a manual estimate. These policies and the cache implementation remain unchanged.

`IncomeViewModel` retrieves quotes on IO, associates them with the transaction date, cancels obsolete requests, and calculates conversions through `CurrencyConverter`. `IncomeFormViewModel` owns the draft and preview. Compose forwards actions and renders state. Add/edit routes seed an existing USD conversion before rendering an edit form. Room stores USD minor units, the rate string, quote date, and converted LKR minor units. `OfflineFirstIncomeRepository` commits locally before synchronization; Firestore mapping preserves those fields.

Confirmed gaps were an implicit date display, inferred saved-rate provenance, no guaranteed cached advisory in presentation, missing inline manual errors, account changes retaining the working quote, and preview preservation ignoring a changed quote date. The service also accepted a historical HTTP response with a different quote date and could return invalid/nonpositive undated quotes as successful results.

## Changes

- `app/src/main/java/com/pfms/app/data/exchange/ExchangeRateService.kt`: validate positive dated responses, reject mismatched historical responses before caching, close HTTP responses, and allow an internal injected HTTP client for deterministic tests. Production Hilt construction, providers, fallback order, and the single existing cache remain intact.
- `app/src/main/java/com/pfms/app/viewmodel/IncomeViewModel.kt`: expose explicit saved-quote provenance, validate returned dates, strengthen the cached advisory, clear stale advisory on manual entry, and clear working rate/draft state on account changes. Existing save completion/navigation handling is preserved.
- `app/src/main/java/com/pfms/app/viewmodel/IncomeFormViewModel.kt`: separate rate detail/date/advisory, label fetched/cached/manual/saved states explicitly, and show correctable inline positive-rate validation.
- `app/src/main/java/com/pfms/app/domain/usecase/IncomeEntryAmount.kt`: preserve an original conversion in the preview only if its quote date also matches, consistent with existing update persistence logic.
- `app/src/main/java/com/pfms/app/ui/screen/income/IncomeFormScreen.kt`: forward saved-rate provenance.
- `app/src/main/java/com/pfms/app/ui/screen/income/IncomeFormFields.kt`: render **Exchange rate date** and inline manual validation using existing components.
- `app/src/main/java/com/pfms/app/ui/navigation/PfmsNavGraph.kt`: wire the saved-rate flag in add and edit routes. No navigation restructuring for this task.
- New `ExchangeRateOfflineServiceTest`, `IncomeExchangeRateStateTest`, and `IncomeExchangeRateIntegrationTest` tests.

No migration, dependency, repository rewrite, new production cache, or persisted provenance field was added. Old saved records are labeled **Saved rate**, since the existing model does not persist whether they originally used fetched, cached, or manual quotes. Looking up a new rate never writes transactions. Changing an edit form's rate and explicitly saving may change its conversion; merely opening it, reconnecting, or saving a note preserves its original conversion.

## Automated verification

2026-10-25: focused Gradle JVM tests and debug/app-test APK builds completed successfully.

| Suite | Passed |
| --- | ---: |
| ExchangeRateOfflineServiceTest | 7 |
| IncomeExchangeRateStateTest | 7 |
| IncomeEntryAmountTest | 4 |
| IncomeFormViewModelTest | 5 |
| IncomeCompletionTest | 8 |
| IncomeSaveCompletionTest | 6 |
| BackdatedEntryViewModelTest | 2 |
| FinancialErrorHandlingTest | 8 |
| Total | 47 |

HTTP uses intercepted responses/errors, not live providers. Cases include online fetch/cache, cache recreation, day-14 acceptance/day-15 rejection, future/missing cache rejection, exact historical dates, mismatched HTTP dates, malformed quotes, reconnect/refetch, distinct labels, invalid manual values/correction, saved conversion preservation, account changes, and canceled requests not replacing cleared/manual state.

Android checks use the existing Pixel 7 Pro emulator, isolated test Room databases/preferences and test authentication/remote sources. No real user financial records are created. The new two integration tests exercise the real form, ViewModel, service, cache, converter, and Room repository: offline cached USD save, duplicate-tap prevention, database/cache consumer recreation, account scoping, reconnect without rewriting records, expired cache manual fallback, correction of invalid manual input, and offline manual saving. Consumer recreation verifies persisted fields; an actual OS process death remains a manual check.

Emulator results: `IncomeExchangeRateIntegrationTest` **2/2**, existing `IncomeFormUiTest` **3/3**, `IncomePersistenceIntegrationTest` **2/2**, and `IncomeSaveNavigationTest` **7/7** passed. An initial combined regression run had one Espresso window-focus failure in `saveWithoutAPreviousScreenFallsBackToIncomeList`; all seven navigation tests passed when rerun in a fresh instrumentation process. No production fix was made for that test-environment failure.

## Manual verification on a QA account

1. Online: Income → Add Income → USD. Enter an amount and wait for the quote. Verify **Fetched rate**, **Exchange rate date**, the rate, and the LKR preview. Save and record the four values (USD amount, rate, date, LKR amount).
2. Disable both Wi-Fi and mobile data. Start another USD income dated today. After network attempts finish, verify **Cached rate**, the original quote date, and an advisory that it may not reflect the current rate. Save once; verify one list entry and the updated dashboard.
3. Force-stop and reopen while offline. Verify the saved USD amount, rate/date, and LKR conversion match. Edit its note only, save, and verify the conversion is unchanged.
4. On an isolated test installation without a cached quote, repeat offline. Verify **Manual rate** and an editable rate field. Try empty, `abc` (paste if the numeric keyboard cannot type letters), `0`, and negative input. Verify a visible error, open form, and no new income entry. Enter a positive decimal rate and retry; verify preview and saved conversion agree.
5. The day-14/day-15 boundary is covered deterministically by the service tests. For a manual expired-cache scenario, use a dedicated test build/fixture with an expired quote; do not alter a normal user's clock or clear their financial database.
6. Backdate an entry offline. A latest quote for another date must not be used. If the exact historical date was previously fetched/cached, verify that quote/date; otherwise enter a manual rate for the selected date.
7. Reconnect and tap **Refresh rate** on a new USD draft. Verify **Fetched rate** and its returned date. Reopen previously saved records and confirm their conversions are unchanged. Reopening an existing record should show **Saved rate**, without an automatic fetch.
8. While a lookup is running, switch currency/date or choose **Enter manually**. Verify the obsolete response does not replace the new selection/input. Save with the network disconnected mid-session; local saving must still succeed once a usable quote/manual rate exists.
9. Sign out/in and switch QA accounts. Draft quotes should reset; income must remain account-scoped. Public exchange-rate cache reuse across accounts is intentional and contains no user records.

Live provider availability, physical-device network transitions, real Firebase login/synchronization, and actual force-stop/relaunch should still be verified with this checklist. Automated HTTP tests intentionally do not depend on public provider uptime. Offline fallback occurs after the existing network attempts/timeouts; it is not an immediate connectivity listener. Reconnection requires a refresh/new lookup; it does not silently replace a working quote or historical conversion.
