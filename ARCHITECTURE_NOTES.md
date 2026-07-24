# Architecture Notes — Deliberate Deviations

This file records design decisions where **Bachat Khata (Android)** intentionally
differs from `ANDROID_FEATURES.md`. These are considered choices, not oversights —
please read this before "fixing" them.

---

## 1. Encryption at rest (`ANDROID_FEATURES.md` §4) — not implemented, on purpose

### What the spec asks for
The spec describes encrypting every sensitive financial value on-device with a
**PIN-derived key** (PBKDF2 + AES-GCM), unlocked into memory only after the user
enters their PIN, while the **cloud copy stays plaintext** as a recovery safety net.

That model was written for the web app, which stores financial data in the
browser's `localStorage` — a store the app fully controls and can encrypt before
writing to.

### Why it doesn't fit this app
This Android app is **Firestore-direct**. Transactions, budgets, goals, ledger
entries and so on are read from and written to Firestore. Firestore keeps an
offline copy in a **SQLite file the app does not control**, and the Firebase
Android SDK exposes **no hook to encrypt that file** with our own key.

So there is no app-controlled local plaintext store of financial data to attach a
PIN-derived key to. The spec's technique has nothing to bind to here.

### What already protects the data
| Threat | Protection already in place |
|---|---|
| Credentials on disk (PIN hash, SMS gateway key) | **Already AES-GCM encrypted** via `EncryptedSharedPreferences` (`SharedPreferencesManager`) |
| The offline financial cache | Android **full-disk encryption** (mandatory well below this app's `minSdk 26`) + **app sandboxing** — unreadable by other apps or over USB |
| A stolen or unlocked phone | The existing **PIN / biometric app lock** (`BaseActivity` auto-lock + `PinSetupActivity`) gates the whole UI |

### Why we did NOT force the spec's model in anyway
Two routes exist, and both are worse than doing nothing:

1. **Field-level encryption** (encrypt each field before every Firestore write).
   This makes the **cloud copy ciphertext**, which directly contradicts the
   spec's own §4 safety net: "a forgotten PIN never loses data, it re-syncs from
   the cloud." With encrypted fields, a forgotten PIN would mean **permanently
   unrecoverable data**. It also touches every model and every read/write path.

2. **Full Room migration** (move financial data into a local Room store, then
   encrypt that). This is a multi-day rewrite of the data layer across ~40
   screens, to hand-build a local store whose main new benefit is mostly already
   delivered by the OS's full-disk encryption.

### Decision
**Keep the current design.** The sensitive credentials are already encrypted, the
offline cache is protected at the OS level, and the "stolen phone" threat is
handled by the existing app lock — which at-rest encryption would not stop anyway,
since the app decrypts once unlocked. The effort-to-benefit of the two heavier
options is poor, and one of them removes a recoverability guarantee the design
deliberately chose.

If the product later moves off Firestore-direct onto a local Room store (a much
larger change), revisit encryption at rest at that boundary.

---

## 2. Tech stack — Java + XML Views, not Kotlin + Compose

`ANDROID_FEATURES.md` §1–§2 map the web app to **Kotlin + Jetpack Compose + Room**.
This project is instead **Java + XML Views + Firestore-direct**, following MVVM.

This is an existing, deliberate divergence (the whole codebase is built this way).
The spec's §5 business logic is still ported faithfully as pure Java in
`domain/` — only the platform layer (UI, storage, DI) differs. Related to §1 above:
the absence of Room is exactly why the spec's Room-based encryption model doesn't
apply.
