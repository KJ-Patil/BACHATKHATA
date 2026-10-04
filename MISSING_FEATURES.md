# Missing Features Roadmap — Bachat Khata (Android)

Prioritized backlog of pending features and technical enhancements for the Android app.
Updated: 2026-10-04

---

## Prioritization Matrix Overview

| Priority | Feature | Category | Effort | Status / Impact |
|:---:|---|---|:---:|---|
| **P0 (Critical)** | **PIN Lock — Complete 4-Mode Lifecycle** | Security / Blocker | Medium (~3–5h) | ✅ **Completed** (Setup, Verify, Change, Forgot recovery) |
| **P1 (High)** | **Theme Toggle Button in Home Header** | Quick Win / UX | Small (~1–2h) | High visual impact, fully backed by existing dark mode engine, low effort. |
| **P2 (Medium)** | **Modern AndroidX SplashScreen API** | Startup / Polish | Small (~1–2h) | Eliminates artificial 2s lag & blank cold-start frames; standard Android 12+ API. |
| **P3 (Planned)** | **UI & End-to-End Integration Tests** | Quality / Stability | Large (~2–4d) | Prevents regressions across sync/offline flows; builds upon stabilized UI. |

---

## Priority 0 (Critical) — PIN Lock: All 4 Modes (§6.2b) — ✅ COMPLETED

### Target Specifications
The PIN lock screen (`PinSetupActivity.java`) handles **four distinct modes** passed via Intent extra (`"pin_mode"` / `"mode"`):

| Mode | Flow & Behavior | Status |
|---|---|:---:|
| **`setup`** | First-time user: enter 4 digits → confirm digits → hash (v2 salted) & store locally + Firestore → route to Home | ✅ Implemented |
| **`verify`** | Returning user: enter 4 digits → compare hash → success opens app. Mismatch = error shake + vibration. "Switch Account" option. | ✅ Implemented |
| **`change`** | Authenticated user from Settings: enter current PIN → verify → enter new PIN → confirm → store updated hash | ✅ Implemented |
| **`forgot`** | Recovery flow: re-authenticate identity via Firebase (Google Sign-In, Password re-auth, or Phone OTP) → clear old PIN hash → route to `setup` | ✅ Implemented |

### Implementation Checklist
- [x] Add `"CHANGE"` and `"FORGOT"` state handling to `PinSetupActivity.java`.
- [x] For `change` mode: add an initial verification step for the active PIN before allowing input of a new PIN. Wire entry point from `ProfileFragment.java` / Settings.
- [x] For `forgot` mode: invoke Firebase re-authentication (`reauthenticate` with Credential for Google or Email/Password, or Phone OTP) before clearing the stored PIN hash and resetting to the setup state.
- [x] Add "Switch Account" button on the `verify` screen to safely call `FirebaseAuth.signOut()`, purge local cache mirrors, and return to `LoginActivity`.
- [x] Ensure edge-to-edge and system Back behavior are preserved (Back in verify sends task to background, not dismiss; Back in change/setup steps backward cleanly).

**Estimated Effort**: Medium (~3–5 hours)

---

## Priority 1 (High) — Theme Toggle Button in Home Header (§9, §11.1)

### Why this is Priority 1
High visible value with minimal development effort and near-zero risk. Bachat Khata's dark mode subsystem is already built and working (`values-night/themes.xml` and `SharedPreferencesManager.applyThemeMode()`), but the toggle is buried in Profile & Settings. Surfacing this directly in the Home header provides instant delight and accessibility.

### Target Specifications
- A quick-action **sun/moon icon button** positioned in the Home screen header bar beside the notification bell.
- Smooth cross-fade micro-animation (rotation + alpha scale) when switching between light and dark modes.
- Accessible content description toggling dynamically: `"Switch to dark mode"` / `"Switch to light mode"`.

### Current State
- Dark theme exists and works across the app.
- No quick-access icon exists in `fragment_home.xml`.

### Implementation Checklist
- [ ] Add a `MaterialButton` (icon-only style) or `ImageButton` to the header toolbar in `fragment_home.xml` adjacent to the notification bell.
- [ ] Add vector drawables for light (`ic_sun_24`) and dark (`ic_moon_24`).
- [ ] Wire click listener in `HomeFragment.java` to toggle between `MODE_NIGHT_NO` and `MODE_NIGHT_YES` via `SharedPreferencesManager`.
- [ ] Animate transition with `ObjectAnimator` (rotation: 0° → 180°, alpha: 1.0 → 0.0 → 1.0) or `AnimatedVectorDrawable`.
- [ ] Keep Firestore user preferences (`themeMode`) synchronized in the background.

**Estimated Effort**: Small (~1–2 hours)

---

## Priority 2 (Medium) — Modern AndroidX SplashScreen API (§6.1)

### Why this is Priority 2
Crucial for first impressions and app responsiveness. Currently, `SplashActivity.java` incurs a hardcoded 2000ms delay and inflates a custom layout, which can cause a blank white window during early OS cold starts and needlessly slows down daily app launches.

### Target Specifications
- Utilize Google's standard `androidx.core:core-splashscreen` library.
- Eliminate the arbitrary 2-second sleep; hold the splash screen strictly while checking Firebase Auth status and retrieving the cached or remote PIN profile.
- Seamless transition to `MainActivity`, `PinSetupActivity`, or `LoginActivity` the instant state is resolved.

### Current State
- `SplashActivity.java` relies on `Handler.postDelayed(..., 2000)` and `ActivitySplashBinding`.

### Implementation Checklist
- [ ] Add `implementation "androidx.core:core-splashscreen:1.0.1"` (or latest stable) to `app/build.gradle`.
- [ ] Call `SplashScreen.installSplashScreen(this)` in `SplashActivity.onCreate()` before `super.onCreate()`.
- [ ] Define splash screen theme in `values/themes.xml` (and `values-night/themes.xml`) using `Theme.SplashScreen` with `windowSplashScreenAnimatedIcon` and `postSplashScreenTheme`.
- [ ] Replace `postDelayed` with `splashScreen.setKeepOnScreenCondition(() -> !isAuthResolved)`.
- [ ] Retire redundant custom layout `activity_splash.xml` once the platform API handles the presentation.

**Estimated Effort**: Small (~1–2 hours)

---

## Priority 3 (Planned) — UI & End-to-End Integration Tests (§15)

### Why this is Priority 3
Foundational for long-term maintainability and release readiness, but requires larger time investment and benefits from having UI screens (such as all PIN modes and header buttons) stabilized first. 

### Target Specifications
- Comprehensive automated regression protection spanning critical money flows, offline data mutations, and security lockouts.

### Current State
- 11 unit tests for core domain calculation logic (`LoanMathTest`, `CalendarUtilTest`, `AnomalyRadarTest`, etc.).
- No UI tests, no Espresso tests, and only a placeholder instrumented test.

### Implementation Checklist
- [ ] Configure Espresso & AndroidX Test dependencies in `app/build.gradle`.
- [ ] **P3.1 Core Flow UI Tests**:
  - Login & Onboarding journey.
  - PIN setup, verify, and invalid entry lockout / error shake.
  - Add / edit transaction and ledger entry calculation.
- [ ] **P3.2 Integration & Sync Tests**:
  - Firestore offline cache sync round-trip (create offline → reconnect → assert sync).
  - Cloud backup creation and JSON/Drive restore verification.
  - Multi-currency conversion input/display fidelity (base-currency INR rules).
- [ ] **P3.3 Configuration Tests**:
  - Dark mode rendering across primary fragments.
  - Airplane mode bypass checks (verifying app fails closed to local PIN cache).

**Estimated Effort**: Large (~2–4 days)
