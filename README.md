# 🧾 Invision

**A whole shop in your pocket.** Bill customers, track stock, keep the books straight and print the receipt, all from one Android app. No internet, no server, no monthly fee.

Built for small Indian shops (clothes, packaged food, drinks, cafés). It speaks GST, counts in paise, reads barcodes and works in English, Hindi and Bengali.

## What it does

- **Bill fast.** Scan with the camera or a hardware scanner, split payments across cash, UPI, card and customer accounts, hold a bill and come back to it, send quotations on WhatsApp.
- **GST done right.** CGST/SGST/IGST, HSN codes, the apparel rate rule, invoices numbered per financial year, credit notes for returns.
- **Know your stock.** Sizes and colours, batches with expiry, reorder suggestions, purchase orders, stock counts, CSV import, product photos.
- **Run a restaurant too.** A separate menu with recipes that use up ingredient stock.
- **See the numbers.** Twelve reports, day-end cash closing, a GST report, CSV export.
- **Stay in control.** Owner and employee logins with per-person permissions, idle lock with PIN or fingerprint, activity log, backup and restore.
- **Print anywhere.** PDF, Android printing, or straight to a Bluetooth receipt printer (58, 76 or 80 mm).

## One rule: the books must add up

Money is stored in whole paise, never as decimals. A sale saves the invoice, payments, stock and customer account in a single transaction, so it either all happens or none of it does. Double taps can't save a bill twice. Thousands of random bills in the tests add up to the paisa.

## Get started

**You need**
- Git
- JDK 17 or newer (Android Studio includes one)
- Android Studio recent enough for Android Gradle Plugin 9.4
- Android SDK Platform 37 and Build Tools 36.0.0 or newer (install from Android Studio's SDK Manager)
- A phone (Android 8.0+) with USB debugging, or an emulator. Camera scanning needs Google Play services.
- Internet on the first build, so Gradle can download itself and the libraries. The app itself needs none.

**Clone and run**
```bash
git clone https://github.com/Roybabu/Invision.git
cd Invision
git checkout -b my-change          # work on your own branch
```
Open the folder in Android Studio, let it sync, pick a device and press Run. If you build from the command line, create `local.properties` with `sdk.dir=/path/to/Android/sdk` (Android Studio does this for you).

**Build and test**
```bash
./gradlew assembleDebug             # APK in app/build/outputs/apk/debug/
./gradlew testDebugUnitTest         # unit, database and screen tests
./gradlew lintDebug                 # Android lint
./gradlew connectedDebugAndroidTest # device tests (phone or emulator)
```
Before you commit, run `python3 .github/scripts/security_check.py` (the same secret and manifest check CI runs). If you change the database, bump the version, add a migration and its test, and commit the new file in `app/schemas`.

Kotlin, Jetpack Compose, Material 3, Room, MVVM. Android 8.0 and newer. CI (`.github/workflows/android-ci.yml`) builds, tests, lints and runs device tests on an emulator.

## Where things live

```
app/src/main/java/com/invision/pos/
├── core/       sign-in, backup, printing, receipts, photos, DI
├── data/       Room database, repositories, permission checks
├── domain/     billing, GST, inventory, reports (plain Kotlin)
├── viewmodel/  screen state
└── ui/         Compose screens and navigation
```

## Honest status

Version 0.4.0. The logic is well tested, but the app has only run on an emulator.

- ❌ Never tried on a real Bluetooth printer or a physical phone.
- ❌ Hindi and Bengali text hasn't been checked by a native speaker.
- ❌ The database and backup files aren't encrypted. Keep backups somewhere other than the phone.
- ⚠️ No supplier balances, and a bill that totals zero can't be completed.
- ⚠️ A refund isn't tied to the original payment method, and a cash refund on an account sale doesn't change the customer's ledger.
- ⚠️ The release build isn't minified or signed.

Everything stays on the device. The app makes no network calls, and its only permission is Bluetooth (for the printer).
