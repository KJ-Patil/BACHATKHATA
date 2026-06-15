# BachatKhata (बचतखाता) — Personal Finance & Expense Tracker

BachatKhata is a secure, visually stunning personal finance management and expense tracker Android application built using traditional **Java + XML** following modern **MVVM architecture** principles and a premium **Claymorphism** design system.

---

## 🌟 Key Features

1. **Claymorphism UI Layout**: Custom styled cards, buttons, text fields, and progress rings with drop shadows, border strokes, and smooth spring animations.
2. **MVVM Architecture**: Clean separation of concerns using `ViewModel`, `LiveData`, `Repository` and clean event callbacks.
3. **Firebase Cloud Backend**:
   - **Firebase Authentication**: Email/Password registry + Google Sign-In credentials.
   - **Cloud Firestore**: Real-time listeners syncing transactions, categories, budgets, savings goals, and alerts.
4. **Offline Resilience**: Real-time connection indicators (online/offline banner warning) with offline database caching.
5. **Smart Alerts & Budgets**: Periodically monitors category thresholds (80% warn, 100% exceeded) via background `WorkManager` daily alerts.
6. **Savings Goal Tracker**: Interactive progress indicators drawing remaining values, deadlines, and automatically projecting the monthly savings needed to hit milestones.
7. **Interactive Analytics Graphs**: Beautiful PieChart and BarChart breakdowns utilizing custom `MPAndroidChart` rendering.
8. **AppLock Security**: Intercepts active app sessions on idle background time exceeding the user-configured lock timeout, forcing verification via a custom 4-digit PIN lock or biometric fingerprints.
9. **PDF & CSV Exporting**: Generates clean formatted CSV spreadsheets and multi-page Canvas-drawn PDF tables written directly to the system Downloads directory.

---

## 🛠️ Tech Stack

- **Min SDK**: 26 (Android 8.0 Oreo)
- **Target SDK**: 35 (Android 15)
- **Language**: Java Only (Strictly 0% Kotlin / 0% Jetpack Compose)
- **Architecture**: MVVM (ViewModel, LiveData, ViewBinding)
- **Backend Services**: Firebase Auth, Cloud Firestore, Firebase Storage
- **Libraries**:
  - MPAndroidChart (v3.1.0)
  - Glide (v4.16.0)
  - Lottie Animations (v6.4.0)
  - WorkManager
  - AndroidX BiometricPrompt

---

## 📦 Project Directory Layout

```
BACHATKHATA/
├── app/
│   ├── build.gradle             # Groovy build configurations
│   ├── proguard-rules.pro       # Obfuscation keep rules (Firebase, MPAndroidChart, etc.)
│   └── src/
│       └── main/
│           ├── java/com/example/bachatkhata/
│           │   ├── MainActivity.java
│           │   ├── BaseActivity.java              # AppLock lifecycle tracker
│           │   ├── SplashActivity.java
│           │   ├── LoginActivity.java
│           │   ├── PinSetupActivity.java          # Cryptographic PIN engine
│           │   ├── HomeFragment.java              # Main dashboard
│           │   ├── TransactionsFragment.java      # Searchable grouped listing
│           │   ├── BudgetFragment.java            # Budgets tracker
│           │   ├── SavingsFragment.java           # Milestone targets
│           │   ├── AnalyticsFragment.java         # Pie & Bar graphs
│           │   ├── ProfileFragment.java           # App configurations
│           │   ├── ExportActivity.java            # CSV/PDF Compiler
│           │   ├── CategoryManageActivity.java    # Default/Custom categorizer
│           │   └── ...
│           └── res/
│               ├── layout/              # Claymorphism XML bindings
│               ├── values/              # App tokens, dimensions, and styling
│               └── values-night/        # Dark Mode theme colors override
└── settings.gradle              # Repositories declaration (including JitPack)
```

---

## 🚀 Step-by-Step Setup Guide

Follow these instructions to deploy and run BachatKhata on your system:

### 1. Clone & Import
Clone the repository and open the project using **Android Studio Ladybug (2024.2) or newer**. Ensure Gradle is allowed to index and sync the dependencies.

### 2. Firebase Configuration file
Generate and add the **`google-services.json`** configuration file inside the **`app/`** module folder.

### 3. Enable Authentication Sign-In Providers
Navigate to your project in the **Firebase Console**, go to *Build -> Authentication -> Sign-in method*, and enable the **Email/Password** and **Google** sign-in providers.

### 4. Register SHA Certificates
Generate your application's SHA fingerprints (via `./gradlew signingReport` in the Android Studio terminal) and add both the **SHA-1** and **SHA-256** certificate fingerprints to the settings panel of your Firebase Android app. (SHA-1 is required for Google Sign-In, and SHA-256 is recommended for app lock biometrics/app check).

### 5. Initialize Firestore Database & Rules
Create a Cloud Firestore database in **Native Mode**. Under the *Rules* tab, paste and publish the following security rules:

```javascript
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
      match /{document=**} {
        allow read, write: if request.auth != null && request.auth.uid == userId;
      }
    }
  }
}
```

### 6. Enable Firebase Storage
In the Firebase console, navigate to *Build -> Storage* and click **Get Started** to enable cloud storage (used to upload and load profile pictures securely).

### 7. Place Lottie Assets
Download the required Lottie animation JSON files and copy them into the app's raw resources folder (**`app/src/main/res/raw/`**):
* `coins_splash.json` (for splash screens)
* `empty_state.json` (for transaction and budget search placeholders)
* `onboarding_1.json`, `onboarding_2.json`, `onboarding_3.json` (for onboarding slides)
* `confetti.json` (for savings milestone progress completions)
* `lesson_complete.json` (for budget warnings)

### 8. SMS Permissions
Grant the **SMS Read** permissions on the first launch of the application on a **physical device** (since standard emulators cannot receive actual carrier SMS broadcasts to support automatic log imports).

### 9. Test Biometrics
Test the biometric unlock screen on a physical device. If running on an emulator, configure fingerprint emulation in the emulator settings first.

### 10. Best Testing Environment
For the best visual and performance testing experience, build and run on a physical Android device targeting **minSdk 26** or newer.

---

## 🔒 Security Specifications

* **PIN Verification**: Encrypted locally via custom standard `SHA-256` hashing functions before performing user auth checks or storing remote credentials.
* **AppLock Interceptor**: Monitors active task cycles. If the app is minimized/backgrounded for over the user-selected timeout duration (saved in `SharedPreferences`), `BaseActivity` intercepts navigation and launches `PinSetupActivity` in `VERIFY` mode.
