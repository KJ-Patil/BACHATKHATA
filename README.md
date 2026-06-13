# BachatKhata (बचतखाता) — Personal Finance & Expense Tracker

BachatKhata is a secure, visually stunning personal finance management and expense tracker Android application built using traditional **Java + XML** following modern **MVVM architecture** principles and a premium **Claymorphism** design system.

---

## 🌟 Key Features

1. **Claymorphism UI Layout**: Custom styled cards, buttons, text fields, and progress rings with drop shadows, border strokes, and smooth spring animations.
2. **MVVM Architecture**: Clean separation of concerns using `ViewModel`, `LiveData`, `Repository` and clean event callbacks.
3. **Firebase Cloud Backend**:
   - **Firebase Authentication**: Email/Password registry + Google Sign-In credentials.
   - **Cloud Firestore**: Real-time listeners syncing transactions, categories, budgets, and savings goals.
4. **Offline Resilience**: Real-time connection indicators (online/offline banner warning) with offline database caching.
5. **Smart Alerts & Budgets**: Periodically monitors category thresholds (80% warn, 100% exceeded) via background `WorkManager` daily alerts.
6. **Savings Goal Tracker**: Interactive progress indicators drawing remaining values, deadlines, and automatically projecting the monthly savings needed to hit milestones.
7. **Interactive Analytics Graphs**: Beautiful PieChart and BarChart breakdowns utilizing custom `MPAndroidChart` rendering.
8. **AppLock Security**: Intercepts active app sessions on idle background time exceeding 60 seconds, forcing verification via a custom 4-digit PIN lock or biometric fingerprints.
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

## 🚀 Step-by-Step Deployment Guide

### Prerequisites
1. **Android Studio**: Android Studio Jellyfish (or newer)
2. **Java JDK**: JDK 17 (configured in Gradle setting options)
3. **Active Firebase Console Account**

### Setup Firebase Project
1. Go to the [Firebase Console](https://console.firebase.google.com/).
2. Click **Add Project** and name it `BachatKhata`.
3. Disable Google Analytics (optional, for rapid deployment).
4. Register a new **Android App** inside your project:
   - **Android Package Name**: `com.example.bachatkhata`
   - **Signing Certificate SHA-1**: Generate yours using Gradle command `./gradlew signingReport` (Required for Google Sign-In).
5. Download the configuration file: **`google-services.json`**
6. Copy this file into your project's **`app/`** directory.

### Enable Firebase Services
1. **Authentication**: Go to *Build -> Authentication -> Get Started*. Enable **Email/Password** and **Google** sign-in providers.
2. **Cloud Firestore**: Go to *Build -> Firestore Database -> Create Database*. Select your preferred region and start in **test mode** (or configure secure read/write rules for `users/{uid}`).

### Build and Launch
1. Open the project in Android Studio.
2. Let Gradle sync complete and index.
3. Build and compile the debug application target:
   ```powershell
   ./gradlew assembleDebug
   ```
4. Run the application on your target physical device or emulator.

---

## 🔒 Security Specifications

- **PIN Verification**: Encrypted locally via custom standard `SHA-256` hashing functions before performing user auth checks or storing remote credentials.
- **AppLock Interceptor**: Monitors active task cycles. If the app is minimized/backgrounded for over 60 seconds, `BaseActivity` intercepts navigation and launches `PinSetupActivity` in `VERIFY` mode.
