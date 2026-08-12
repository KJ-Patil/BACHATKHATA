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

## 2. Firestore disk cache stays ON (`ANDROID_FEATURES.md` §4.3)

The spec asks for `MemoryCacheSettings`, so no plaintext copy of the financial data
sits on disk. That instruction exists to back up §1's encryption-at-rest model —
and since §1 is deliberately not implemented here (see above), pinning the cache
to memory would buy nothing while **removing offline-first entirely**: this app is
Firestore-direct with no Room mirror, so an in-memory cache means a blank app the
moment the network drops.

The data on disk is still covered by Android full-disk encryption and the app
sandbox, which is the same protection §1 relies on. Turning the cache off would be
a strict regression in exchange for no security gain.

Revisit alongside §1 if the app ever moves onto a local Room store.

---

## 3. Tech stack — Java + XML Views, not Kotlin + Compose

`ANDROID_FEATURES.md` §1–§2 map the web app to **Kotlin + Jetpack Compose + Room**.
This project is instead **Java + XML Views + Firestore-direct**, following MVVM.

This is an existing, deliberate divergence (the whole codebase is built this way).
The spec's §5 business logic is still ported faithfully as pure Java in
`domain/` — only the platform layer (UI, storage, DI) differs. Related to §1 above:
the absence of Room is exactly why the spec's Room-based encryption model doesn't
apply.

---

## 4. Base-currency storage — migration assumption (`ANDROID_FEATURES.md` §5.11)

The spec's invariant is now implemented: **every persisted amount is INR**, with
`CurrencyManager.toBaseAmount` on every input edge, `fromBaseAmount` on every
editable field, and `formatAmount` converting on the way out (with a
`formatAmount(value, false)` overload for figures derived from a value *as typed* —
the Add-Loan preview, the What-If projection, the Bill Splitter, the gold
valuation).

**Before this change the app stored amounts as typed and displayed them with the
active symbol**, so switching INR → USD relabelled ₹5,000 as `$5,000.00` without
converting. That is now fixed, but it leaves one question the spec does not answer:
what to do with rows written under the old behaviour.

**The assumption taken: all existing stored amounts are treated as INR.**

- For **INR users this is exactly right and a no-op** — the rate is 1.0, so nothing
  moves. That covers the overwhelming majority of installs.
- For a user who entered amounts while a non-INR currency was active, those rows
  were never really INR, and they will now read as though they were. There is no
  way to recover the truth: the per-row `currency` field records what was *active*,
  not a reliable unit, and nothing recorded the rate at entry time.

A per-row backfill using the stored `currency` field is possible if the need
arises, but it would need a rate as of each transaction's date, which was never
captured. Flag the reinterpretation in release notes rather than guessing.

Related: the CSV and XLSX exports now emit **one** currency — the active one — and
their `Currency` column names it, instead of pairing a base-currency figure with
the per-row entry currency.
