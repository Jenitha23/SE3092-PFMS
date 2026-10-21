# Personal Finance Management System (PFMS)

**SE3092 – Platform Based Development | Assignment 01**  
**BSc (Hons) | Year 3, Semester 2 (2026) | SLIIT**

## 1. Project Overview

Personal Finance Management System (**PFMS**) is a native Android application designed to help users understand and manage their finances when income comes from multiple, sometimes irregular sources. It addresses the scenario described in the assignment: income from salary, freelance projects, Google AdSense, and cryptocurrency can be difficult to reconcile, while frequent expenses make savings progress hard to see.

PFMS brings income, expenses, financial summaries, and savings goals into one application. Its design emphasizes low-friction expense entry, clear distinctions between committed and discretionary spending, and visibility into progress toward a specific savings target. The application includes support for LKR and USD income, expense categorization, savings-goal tracking, and financial analytics. Authentication is handled through Firebase Authentication; Cloud Firestore stores user data, with Room-based local caching and synchronization components to support offline-first workflows.

### Technology stack

| Area | Technology |
| --- | --- |
| Language | Kotlin |
| UI | Jetpack Compose, Material Design 3 |
| Architecture | MVVM with domain/use-case and repository layers |
| UI state and asynchronous operations | StateFlow and Kotlin Coroutines |
| Dependency injection | Hilt |
| Authentication | Firebase Authentication (email/password and Google Sign-In integration) |
| Cloud persistence | Cloud Firestore |
| Local persistence | Room |
| Background synchronization | WorkManager |
| Navigation | Navigation Compose |
| Build system | Gradle Kotlin DSL and Gradle Wrapper |
| Android | Minimum SDK 26; target SDK 37; application ID `com.pfms.app` |

## 2. Firebase Configuration Setup

PFMS requires a Firebase project configured for its Android application.

1. Open the [Firebase Console](https://console.firebase.google.com/) and create or select a Firebase project.
2. Add an **Android app** with package name **`com.pfms.app`**. This must match `applicationId` in `app/build.gradle.kts`.
3. Download the Android configuration file **`google-services.json`** from Firebase project settings and place it at:

   ```text
   PFMS/app/google-services.json
   ```

   If working from the provided project archive, this file may already exist. Replace it only when intentionally connecting to a different Firebase project.
4. In **Authentication → Sign-in method**, enable **Email/Password**. To use Google Sign-In, also enable **Google**, configure the required OAuth credentials, and register the SHA-1/SHA-256 fingerprints for the signing certificate used on your device or build. Re-download `google-services.json` if Firebase configuration changes require it.
5. In **Firestore Database**, create a Cloud Firestore database in the desired region. Do not leave the database with unrestricted test rules.
6. Review the project's **root-level** `firestore.rules` and publish appropriate security rules in Firebase Console. Rules must restrict each user's financial records to their authenticated UID and validate record fields.
7. Verify registration, login, saving an income/expense record, reading it back, and accessing that record after an app restart.

### Firestore rules in this repository

The Firebase CLI configuration in the repository root (`firebase.json`) references:

```json
{
  "firestore": {
    "rules": "firestore.rules"
  }
}
```

Therefore **`PFMS/firestore.rules`** is the rules file for Firebase CLI deployment from the project root. A separate file at `app/src/main/java/com/pfms/app/firestore.rules` is also present in the supplied source archive; it is **not** the path referenced by `firebase.json`. Review both files and keep one authoritative ruleset. The contents of a local rules file are **not proof** that those rules have been deployed to the live Firebase project.

If Firebase CLI is installed and you are authorized to deploy rules for the selected project, you can deploy the root rules from the project directory:

```bash
firebase login
firebase use <YOUR_FIREBASE_PROJECT_ID>
firebase deploy --only firestore:rules
```

**Security:** Do not commit service-account private keys, signing keystores, passwords, or other secrets. Treat Firebase client configuration separately from administrative credentials. Verify security rules with both an authenticated owner account and another user account before the demo.

## 3. Build Instructions

### Prerequisites

- Android Studio with a compatible Android SDK installed.
- **JDK 17** configured for the Gradle build.
- Internet connection for the initial Gradle dependency download and Firebase setup.
- Android emulator or physical Android device running **Android 8.0 (API 26) or newer**.
- Valid Firebase Android configuration at `app/google-services.json`.

The repository includes the Gradle Wrapper configured for **Gradle 9.6.0**. Use the wrapper instead of relying on a separately installed Gradle version.

### Option A — Android Studio

1. Extract or clone the project.
2. Open the **`PFMS`** directory (the directory containing `settings.gradle.kts`) in Android Studio.
3. Allow **Gradle Sync** to finish. Install any missing Android SDK components requested by Android Studio.
4. Confirm `app/google-services.json` is present and corresponds to the correct Firebase Android app.
5. Select an emulator or a connected Android device.
6. Select the **`app`** run configuration and click **Run**.

### Option B — Command line

Open a terminal **inside the `PFMS` project root**.

**Windows PowerShell:**

```powershell
.\gradlew.bat clean assembleDebug
```

**macOS / Linux:**

```bash
./gradlew clean assembleDebug
```

The generated debug APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

For a connected device, install the debug variant with:

**Windows PowerShell:**

```powershell
.\gradlew.bat installDebug
```

**macOS / Linux:**

```bash
./gradlew installDebug
```

To run local unit tests:

**Windows PowerShell:**

```powershell
.\gradlew.bat testDebugUnitTest
```

**macOS / Linux:**

```bash
./gradlew testDebugUnitTest
```

> A successful build does not by itself confirm that all Firebase flows, offline synchronization, or device interactions work. Test core operations using the configured live Firebase project before submission. The assignment accepts a debug or release-signed APK connected to live Firebase.

## 4. Architectural Summary

PFMS follows a layered **Model–View–ViewModel (MVVM)** approach, separating UI concerns, application state, business rules, and data access.

```text
                    Jetpack Compose Screens
                 (presentation and user actions)
                              |
                              v
                     ViewModels + StateFlow
                  (UI state and event handling)
                              |
                              v
                   Domain Models / Use Cases
                 (shared financial calculations)
                              |
                              v
                     Repository Interfaces
                              |
                              v
                  Repository Implementations
                         /          \
                        v            v
                Room Database     Cloud Firestore
                 (local cache)      (cloud data)
                         \          /
                          \        /
                    Sync and reconciliation
                        (WorkManager)
```

### Layer responsibilities

- **Presentation (`ui/`, `viewmodel/`)** — Jetpack Compose renders screens; ViewModels expose UI state and handle user actions. Navigation Compose connects screens.
- **Domain (`domain/`)** — Holds domain models, repository contracts, and reusable business rules, including income, expense, and savings-related calculations.
- **Data (`data/`)** — Contains Firebase and Room data sources, repository implementations, mapping, and synchronization logic.
- **Dependency injection (`di/`)** — Hilt modules provide repositories, data sources, and other dependencies without constructing them inside Composables.
- **Background work** — WorkManager-based workers support queued synchronization activities when connectivity returns.

### Firebase and offline-first flow

1. **Firebase Authentication** establishes the signed-in user's identity.
2. Financial data is organized per authenticated user in **Cloud Firestore**; Firestore Security Rules are responsible for enforcing authorization and permitted document shapes.
3. **Room** provides local financial records for responsive reads and offline operation.
4. Offline-first repositories coordinate local updates and cloud synchronization; background workers support synchronization after temporary network unavailability.
5. ViewModels expose observable **StateFlow** values so Compose screens can react to changes without directly querying Firebase or Room.

This separation helps keep financial calculations consistent, makes features easier to test, and supports future changes to storage or UI implementation.

---
