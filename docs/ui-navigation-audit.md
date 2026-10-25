# UI and navigation audit — 25 October 2026

## Changes

- Home's Live Ledger Activity renders the four newest entries for its selected month. The count still describes the full month; **See all** opens Transaction History.
- History uses the shared finance typography, full monetary values, category icons, compact cards, and a scrollable filter dialog. Incremental loading and transaction-to-editor navigation are retained.
- Switching between income and expense filters clears incompatible filters in the ViewModel.
- Spending Analytics, Spending Patterns, and What-if Savings now use the same finance components and responsive reading width.
- Splash, password reset, app lock, income onboarding, privacy information, biometric onboarding, and salary confirmation now use the current typography, colors, branding, or cards as appropriate.
- Salary amount validation and simulator draft parsing now live in ViewModels using domain amount rules. Clearing a simulator input resets its value to zero; invalid input hides the stale projection.

## Where to find each screen

| Screen | Entry point |
| --- | --- |
| Transaction History | Home → Live Ledger Activity → See all; Spending Analytics → See full history |
| Income Analytics | Insights tab from Home, Income, Expenses, or Goals; Income's analytics button |
| Spending Analytics | Expenses → Spending Analytics card or tools menu; Insights → Spending Analytics card |
| Spending Patterns | Spending Analytics → Smart spending patterns |
| What-if Savings | Spending Patterns → Explore savings impact; an active Savings Goal → Explore What-if |
| Add/Edit Income | Income → Add Income; income record actions; income entries in History |
| Recurring Salary | Income → calendar icon, labelled Recurring salary |
| Add/Edit Expense | Expenses → Add; expense record actions; expense entries in History |
| Quick Add Expense | Home → Quick Add; Expenses → floating quick-add button or tools menu |
| Categories | Expenses → tools → Categories |
| Recurring Expenses | Expenses → tools → Recurring |
| Subscriptions | Expenses → tools → Subscriptions |
| Recurring/subscription editor | Recurring Expenses or Subscriptions → New; existing template → Edit |
| Savings Goals | Goals tab; Home's savings goal card |
| Goal and contribution dialogs | Savings Goals → Create/Edit Goal, Contribute, Withdraw, or ledger actions |
| Settings and Privacy Policy | Home → account avatar → Settings → Privacy Policy |
| Login, signup, password reset | Session gate; login/signup links; Login → Forgot password |
| Income onboarding | First authenticated session without a completed income estimate |
| App lock and salary confirmation | Existing session-lock and salary-reminder gates |

Bottom-tab Insights consistently opens Income Analytics. Spending Analytics is a secondary destination with explicit entry links. Back returns to its caller; an empty What-if screen provides a route to Savings Goals.

## Verification

- Debug app and instrumentation APK builds passed.
- 52 targeted JVM tests passed, including dashboard calculations, history filtering, presentation state, spending/savings rules, and error recovery.
- 23 distinct emulator UI checks passed across runs: history, dashboard, spending, patterns, simulator, empty states, onboarding, password-reset validation, and Income Analytics regression checks.
- Screenshots were reviewed in normal light mode and in a 320 × 640 dp viewport with 1.3× text in dark mode.
- Live navigation verified Home → History → Back, Home → Insights → Spending Analytics → Patterns → What-if → Back, the Expenses → Spending Analytics link, Categories, recurring/subscription editors and Cancel, Add Income and Cancel, Recurring Salary and Cancel, Goals, Settings, and Privacy Policy.
- Live checks did not save or modify financial entries. Optional salary reminders were dismissed with Not Yet.

One cold launch during validation raised an internal Cloud Firestore 26.6.0 `EventManager.addQueryListener` null-reference error. Subsequent cold launches and the live route checks succeeded. Its cause remains unconfirmed; the UI changes do not claim to resolve it. Firebase dependency versions were left unchanged.

Unused legacy compatibility composables are not registered as screens; they were retained for existing callers/tests. This audit covers reachable screens and the listed flows, not every possible authentication, network, device, or notification state.
