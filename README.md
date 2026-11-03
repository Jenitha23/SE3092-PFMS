# Personal Finance Management System (PFMS)

**SE3092 – Platform Based Development | Assignment 01**  
**BSc (Hons) in Computer Science | Year 3, Semester 2 – 2026 | SLIIT**

## 1. Project Overview

Personal Finance Management System (PFMS) is a native Android application developed using Kotlin, Jetpack Compose, MVVM architecture, and Firebase.

The application is designed to help individuals manage irregular income, track daily expenses, understand spending behaviour, and make measurable progress toward financial goals.

PFMS was developed in response to the financial-management challenges presented in the SE3092 Assignment 01 scenario. The scenario follows Kavindu Silva, a junior software engineer who earns money from multiple sources but struggles to understand his actual income, control discretionary spending, and save toward a specific financial goal.

Unlike traditional budgeting applications that assume fixed monthly income, PFMS supports multiple income sources, currency conversion, categorized expenses, financial analytics, and savings-goal management.

The application emphasizes:

- Low-friction daily expense recording.
- Accurate tracking of irregular and multi-source income.
- Financial visibility through dashboards and analytics.
- Savings-goal progress and spending awareness.
- Firebase-based authentication and cloud persistence.
- Local caching and synchronization for offline workflows.
- Secure user-specific financial data management.

## 2. Core Features and Scenario Alignment

PFMS features are designed around the behavioural and financial problems identified in the assignment scenario.

| Scenario Problem | PFMS Solution |
|---|---|
| Income is received from multiple irregular sources | Multi-source income tracking |
| USD earnings are difficult to compare with LKR income | USD-to-LKR conversion with stored exchange-rate information |
| Outstanding freelance payments are forgotten | Expected and received freelance income tracking |
| Daily expense recording requires too much effort | Quick-add expenses, defaults, and reusable templates |
| Users cannot distinguish essential spending from discretionary purchases | Committed and Discretionary expense classifications |
| Users lack visibility into spending patterns | Financial dashboard, category charts, and spending analytics |
| Financial goals feel disconnected from daily spending | Savings-goal progress, contribution tracking, and pace analysis |
| Previous tracking attempts are abandoned | Backdated entries, helpful empty states, and gap-tolerant design |
| Users need access when connectivity is unavailable | Room local caching and synchronization infrastructure |

### 2.1 Authentication and User Management

- Email and password registration and login.
- Firebase Authentication integration.
- Google Sign-In integration.
- Persistent authentication sessions.
- Password reset functionality.
- User profile and default payment-method management.
- Account settings and account deletion.
- Biometric authentication support.

### 2.2 Income Management

- Record income from Salary, Freelance, Google AdSense, Cryptocurrency, and Other sources.
- Support LKR and USD income.
- USD-to-LKR exchange-rate conversion.
- Historical exchange-rate handling with manual fallback.
- Cryptocurrency gain and loss tracking.
- Freelance project and expected-payment tracking.
- Recurring salary templates.
- Income editing and deletion.
- Monthly income summaries and source-wise analysis.
- Estimated-versus-actual income comparison.

### 2.3 Expense Management

- Quick-add daily expenses.
- Detailed expense recording.
- Default and custom expense categories.
- Committed and Discretionary classifications.
- Cash, Card, Bank Transfer, and Digital Wallet payment methods.
- Recurring expense templates.
- Subscription management and review flags.
- Expense editing and deletion.
- Duplicate expense prevention.
- Transaction history and filtering.

### 2.4 Savings Goal Management

- Create and manage a savings goal.
- Set target amount, target date, and opening savings.
- Record contributions and withdrawals.
- View savings progress.
- Calculate remaining savings requirements.
- Analyze saving pace.
- Display projected goal completion information.
- Handle completed and overdue goals.

### 2.5 Dashboard and Financial Analytics

- Monthly income and expense summaries.
- Net savings or surplus/deficit calculations.
- Latest financial transactions.
- Expense category analysis.
- Committed-versus-Discretionary spending comparison.
- Income and expense trends.
- Spending insights and financial summaries.
- Savings-goal progress indicators.

### 2.6 Offline Support and Synchronization

- Room-based local financial data caching.
- Offline transaction recording workflows.
- Cloud Firestore synchronization.
- Background synchronization using WorkManager.
- Synchronization status handling.
- Network-error recovery mechanisms.

Some operations that depend directly on Firestore transactions may require an active network connection.

## 3. Technology Stack

| Component | Technology |
|---|---|
| Programming Language | Kotlin |
| UI Framework | Jetpack Compose |
| UI Design System | Material Design 3 |
| Architecture | MVVM with domain/use-case and repository layers |
| State Management | StateFlow |
| Asynchronous Programming | Kotlin Coroutines |
| Dependency Injection | Hilt |
| Authentication | Firebase Authentication |
| Cloud Database | Cloud Firestore |
| Local Database | Room |
| Background Tasks | WorkManager |
| Navigation | Navigation Compose |
| Build System | Gradle Kotlin DSL |
| Build Tool | Gradle Wrapper |
| Minimum Android SDK | API 26 (Android 8.0) |
| Target Android SDK | API 37 |
| Application ID | `com.pfms.app` |

## 4. System Architecture

PFMS follows the Model–View–ViewModel (MVVM) architectural pattern.

The application separates presentation, business logic, data operations, and persistence responsibilities.

### 4.1 Architecture Overview

```text
                   Jetpack Compose UI
             (Screens and User Interactions)
                           |
                           v
                    ViewModels
             (StateFlow and UI Events)
                           |
                           v
                Domain Models / Use Cases
              (Financial Business Logic)
                           |
                           v
                 Repository Interfaces
                           |
                           v
              Repository Implementations
                      /          \
                     v            v
               Room Database   Cloud Firestore
                (Local Data)    (Cloud Data)
                      \          /
                       \        /
                 Sync Coordination
                    WorkManager
```

### 4.2 Presentation Layer

The presentation layer uses Jetpack Compose to build reactive Android interfaces.

ViewModels manage screen state and user interactions through StateFlow.

The presentation layer is responsible for displaying application data and sending user actions to the appropriate ViewModel.

### 4.3 Domain Layer

The domain layer contains business models, repository contracts, and reusable financial calculations.

Examples include:

- Income calculations.
- Currency conversion.
- Expense classification.
- Monthly financial totals.
- Savings-goal progress.
- Recurring expense calculations.

Centralizing these rules helps prevent inconsistent calculations between screens.

### 4.4 Data Layer

The data layer manages local and remote financial records.

Important implementation components include:

| Component | Responsibility |
|---|---|
| `FirestoreAdvancedExpenseRepository.kt` | Advanced expense categories, recurring templates, and subscription operations |
| `OfflineFirstIncomeRepository.kt` | Income persistence and synchronization |
| `OfflineFirstExpenseRepository.kt` | Expense persistence and synchronization |
| `OfflineFirstSavingsGoalRepository.kt` | Savings-goal data management |
| `ExchangeRateService.kt` | Currency exchange-rate retrieval |
| `PfmsDatabase.kt` | Room database configuration |
| `IncomeSyncWorker.kt` | Background income synchronization |
| `ExpenseSyncWorker.kt` | Background expense synchronization |

### 4.5 Dependency Injection

Hilt is used to manage dependencies across application components.

This improves modularity, maintainability, and testability while avoiding unnecessary manual dependency construction inside Composable functions.

## 5. Firebase Configuration

PFMS uses Firebase Authentication and Cloud Firestore.

A correctly configured Firebase project is required to build and use the application's cloud-based functionality.

### 5.1 Create or Configure a Firebase Project

1. Open [Firebase Console](https://console.firebase.google.com/).
2. Create a Firebase project or select an existing authorized project.
3. Register an Android application with package name:

   `com.pfms.app`

4. Download the Firebase Android configuration file.
5. Place the file at:

   ```text
   PFMS/app/google-services.json
   ```

### 5.2 Firebase Authentication

In Firebase Console:

1. Open **Authentication → Sign-in method**.
2. Enable **Email/Password** authentication.
3. Enable **Google** as a sign-in provider if Google Sign-In is required.
4. Configure the relevant OAuth settings.
5. Register the SHA-1 fingerprints for the development and release signing certificates.

SHA-256 fingerprints can also be registered where required.

If Firebase configuration changes affect the Android OAuth configuration, download the updated `google-services.json` and rebuild the application.

### 5.3 Cloud Firestore

Create a Cloud Firestore database and configure the required security rules.

PFMS stores user-specific financial records under authenticated user identifiers.

Conceptual collection structure:

```text
users/
  {uid}/
    income/
    expenses/
    categories/
    incomeSources/
    savingsGoals/
    goalContributions/
    recurringTemplates/
    expenseDuplicateGuards/
```

Additional profile information and application-specific metadata may be stored in the user's document or related collections.

### 5.4 Firestore Security Rules

The authoritative Firebase CLI rules file is located at:

```text
PFMS/firestore.rules
```

The root `firebase.json` references this file.

Firestore Security Rules are intended to:

- Require Firebase Authentication.
- Restrict user financial records by authenticated UID.
- Validate supported document fields and data types.
- Restrict invalid financial values.
- Protect records against unauthorized cross-user access.

Security Rules must be deployed to the Firebase project before testing cloud operations.

To deploy using Firebase CLI:

```bash
firebase login
firebase use <YOUR_FIREBASE_PROJECT_ID>
firebase deploy --only firestore:rules
```

Verify that the deployed rules match the intended source file.

Security should be tested using separate authenticated users and unauthenticated requests.

### 5.5 Firebase Configuration and Git Security

The Firebase Android configuration file is excluded from Git tracking.

The project `.gitignore` should include:

```gitignore
# Firebase Android configuration
/app/google-services.json

# Release signing files
*.jks
*.keystore
```

Developers cloning the repository must obtain an authorized Firebase Android configuration and place it inside the `app/` directory.

The repository must not contain Firebase service-account private keys, release signing keystores, passwords, or other confidential credentials.

Firebase client configuration is not a substitute for proper authentication, security rules, and API-key restrictions.

## 6. Build and Installation Instructions

### 6.1 Prerequisites

- Android Studio.
- Android SDK.
- JDK 17 or the compatible Gradle JDK configured for the project.
- Internet access for initial dependency downloads.
- Android emulator or physical device running API 26 or above.
- Valid `app/google-services.json`.
- Access to a properly configured Firebase project.

Use the included Gradle Wrapper to build the project.

### 6.2 Build with Android Studio

1. Clone or download the repository.
2. Open the `PFMS` project directory in Android Studio.
3. Confirm that the Firebase configuration is present.
4. Allow Gradle synchronization to complete.
5. Select an Android emulator or physical Android device.
6. Choose the `app` run configuration.
7. Click **Run**.

### 6.3 Generate a Debug APK

From the project root, execute:

**Windows:**

```powershell
.\gradlew.bat :app:assembleDebug
```

**macOS / Linux:**

```bash
./gradlew :app:assembleDebug
```

The generated APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

### 6.4 Generate a Signed Release APK

A signed release APK can be generated through Android Studio.

1. Select **Build → Generate Signed Bundle / APK**.
2. Select **APK**.
3. Choose an existing release keystore or create a new one.
4. Enter the required signing credentials.
5. Select the `release` build variant.
6. Complete the signing wizard.

The release APK will be generated in the selected output directory, commonly under:

```text
app/release/
```

or:

```text
app/build/outputs/apk/release/
```

The release signing certificate's SHA-1 fingerprint must be registered in Firebase for applicable Google Sign-In configurations.

**Important:** Release signing keys and passwords must remain private. Preserve the original signing keystore for future updates.

### 6.5 Install on Android

The APK can be installed on a compatible Android emulator or physical Android device.

For a connected device, the debug variant can also be installed using:

**Windows:**

```powershell
.\gradlew.bat :app:installDebug
```

**macOS / Linux:**

```bash
./gradlew :app:installDebug
```

A debug APK and a release APK signed with different keys cannot normally be installed over one another using the same application ID.

Synchronize any pending local financial records before uninstalling an existing application build.

## 7. Testing and Verification

PFMS includes automated test source files and supports manual end-to-end testing through Android Studio.

### 7.1 Unit Testing

Run JVM unit tests with:

**Windows:**

```powershell
.\gradlew.bat :app:testDebugUnitTest
```

**macOS / Linux:**

```bash
./gradlew :app:testDebugUnitTest
```

Unit testing covers applicable business logic, validation, financial calculations, and ViewModel behaviour.

### 7.2 Android Instrumentation Testing

Start an Android emulator or connect a physical device.

**Windows:**

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

**macOS / Linux:**

```bash
./gradlew :app:connectedDebugAndroidTest
```

Instrumentation testing supports verification of Android components and applicable UI or integration behaviour.

### 7.3 Manual End-to-End Testing

The recommended end-to-end verification scenarios are:

| Test Area | Expected Behaviour |
|---|---|
| Registration | User account and profile are created |
| Authentication | Email/password and configured Google Sign-In work |
| Session persistence | User remains authenticated after restart |
| Income management | Income records are saved and reflected in summaries |
| Currency conversion | USD income produces a stored LKR equivalent |
| Expense management | Expenses can be added, edited, and deleted |
| Subscription management | Recurring templates can be created, edited, and confirmed |
| Savings goals | Contributions and withdrawals update goal progress |
| Dashboard | Financial totals match stored transactions |
| Offline entry | Supported transactions remain accessible locally |
| Synchronization | Pending records synchronize after reconnection |
| Security | Unauthorized cross-user access is denied |
| Empty states | Screens handle missing financial data without crashing |
| App restart | Existing records and session state remain consistent |

The actual results of these checks should be recorded during testing rather than inferred solely from the presence of source code.

### 7.4 Android Compatibility

The application is configured with:

- Minimum SDK: API 26.
- Target SDK: API 37.

Installation and core operations should be verified on a compatible Android 8.0 device or emulator and a newer Android version.

## 8. Financial Data Integrity

PFMS uses integer minor units for financial amounts to reduce floating-point precision problems.

Financial calculations are designed to support:

- Consistent monthly income and expense totals.
- Stored currency-conversion values.
- Committed and Discretionary spending analysis.
- Accurate savings-goal contribution calculations.
- Reusable business logic across application screens.

Reporting uses the Sri Lankan financial context, with LKR as the primary reporting currency.

## 9. UI and User Experience Design

PFMS uses Jetpack Compose and Material Design 3 to provide a consistent Android interface.

Important design considerations include:

- Quick access to frequently used financial actions.
- Clear financial summaries.
- Readable currency and transaction information.
- Expense category identification.
- Savings-goal progress visualization.
- Plain-language error and status messages.
- Useful empty states.
- Support for responsive and accessible layouts.

The design focuses on encouraging consistent financial tracking rather than overwhelming users with unnecessary steps.

### 9.1 Annotated Wireframes (Figma)

The PFMS wireframes document scenario-specific UI decisions, including quick expense entry, clear monthly financial summaries, and savings-goal visibility for Kavindu Silva.

**[View PFMS Annotated Wireframes in Figma](https://www.figma.com/design/R4siWhfR2cSzbYr7G82Qo4/PFMS-Wiremframes?node-id=0-1&p=f)**



---

**Personal Finance Management System (PFMS)**  
*Helping users understand income, control spending, and make progress toward financial goals.*
