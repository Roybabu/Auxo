<p align="center"><img src="app/src/main/res/drawable-nodpi/auxo_logo_horizontal.png" alt="Auxo: Built to grow." width="420"></p>

<p align="center">
<img src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84" alt="Android 8.0+">
<img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF" alt="Kotlin and Jetpack Compose">
<img src="https://img.shields.io/badge/data-stays%20on%20device-informational" alt="Data stays on the device">
<img src="https://img.shields.io/badge/languages-English%20%C2%B7%20Hindi%20%C2%B7%20Bengali-blue" alt="English, Hindi, Bengali">
</p>

**Auxo** is a whole shop in your pocket. Bill customers, track stock, keep the books straight and print the receipt, all from one Android app. It works offline, with no server and no monthly fee. The only time it uses the internet is to check for and download app updates. The name comes from the Greek *auxō*, "to increase or grow".

Built for small Indian shops (clothes, packaged food, drinks, cafés). It speaks GST, counts in paise, reads barcodes and works in English, Hindi and Bengali.

## What it does

- **Bill fast.** Scan with the camera or a hardware scanner, split payments across cash, UPI, card and customer accounts, hold a bill and come back to it, send quotations on WhatsApp.
- **GST done right.** CGST/SGST/IGST, HSN codes, the apparel rate rule, invoices numbered per financial year, credit notes for returns.
- **Know your stock.** Sizes and colours, batches with expiry, reorder suggestions, purchase orders, stock counts, CSV import, product photos.
- **Run a restaurant too.** Dishes sit beside your other products, with or without a stock count.
- **See the numbers.** Twelve reports, day-end cash closing, a GST report, CSV export.
- **Stay in control.** Owner and employee logins with per-person permissions, idle lock with PIN or fingerprint, activity log, backup and restore.
- **Print anywhere.** PDF, Android printing, or straight to a Bluetooth receipt printer (58, 76 or 80 mm).

## Screenshots

<!-- Add real screenshots to docs/screenshots/ and list them here as a three-column table, for example:
| New bill | Held bills | Inventory |
|---|---|---|
| <img src="docs/screenshots/new-bill.png" width="240"> | <img src="docs/screenshots/held-bills.png" width="240"> | <img src="docs/screenshots/inventory.png" width="240"> |
-->
Screenshots are coming. The app has so far run only on an emulator (see Honest status below).

## Brand assets

<img src="docs/branding/auxo-icon-transparent.png" alt="The Auxo app icon" width="72" align="left">
The approved artwork is in `design/auxo/` (originals: `docs/branding/auxo-logo-reference.png`; the app icon is `docs/branding/auxo-icon-transparent.png`): the full logo, the symbol alone, a vertical logo, a version for dark backgrounds and a grey one. The launcher icon and the sign-in logo are cut from that same artwork by `tools/extract_auxo_logo.py`, nothing is redrawn.
<br clear="left">

## One rule: the books must add up

Money is stored in whole paise, never as decimals. A sale saves the invoice, payments, stock and customer account in a single transaction, so it either all happens or none of it does. Double taps can't save a bill twice. Thousands of random bills in the tests add up to the paisa.

## Get started

**You need**
- Git
- JDK 17 or newer (Android Studio includes one)
- Android Studio recent enough for Android Gradle Plugin 9.4
- Android SDK Platform 37 and Build Tools 36.0.0 or newer (install from Android Studio's SDK Manager)
- A phone (Android 8.0+) with USB debugging, or an emulator. Camera scanning needs Google Play services.
- Internet on the first build, so Gradle can download itself and the libraries. The app works offline; it uses the internet only to look for updates (see Privacy and permissions).

**Clone and run**
```bash
git clone https://github.com/Roybabu/Auxo.git
cd Auxo
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

## Tech stack

| Layer | Used |
|---|---|
| Language | Kotlin |
| UI | Jetpack Compose, Material 3, Navigation Compose |
| Architecture | MVVM, with plain-Kotlin domain logic |
| Database | Room (SQLite) locked with SQLCipher, with exported schemas and migration tests |
| Barcodes | Camera and hardware scanners, ZXing |
| Printing | PDF, Android printing, Bluetooth receipt printers |
| Languages | English, Hindi, Bengali |
| CI | GitHub Actions: build, unit tests, lint, security check, emulator tests |

## Where things live

```
app/src/main/java/com/auxo/pos/
├── core/       sign-in, backup, printing, receipts, photos, DI
├── data/       Room database, repositories, permission checks
├── domain/     billing, GST, inventory, reports (plain Kotlin)
├── viewmodel/  screen state
└── ui/         Compose screens and navigation
```

## Database upgrades

No release has been published, so no customer database exists below version 15. Upgrades are guaranteed only from version 15 (the current version is 23). Versions 6 to 14 were development steps: their migrations are in `AuxoDatabase.kt` and are covered only by the 4 to 15 chain test, and `app/schemas` has no exported files for them. A test phone still holding a version 6 to 14 database should be uninstalled and reinstalled rather than updated. Once the first real release is out, every new version needs a migration, its test and its exported schema.

## Honest status

Version 0.4.0. The logic is well tested, but the app has only run on an emulator.

- ❌ Never tried on a real Bluetooth printer or a physical phone.
- ❌ Hindi and Bengali text hasn't been checked by a native speaker.
- ⚠️ The database file on the phone is locked with SQLCipher (AES-256), its key kept in the Android Keystore. This is new and has not yet been tried on a real phone, including converting an existing unlocked database. Backups are locked only if the owner sets a passphrase; without one, a backup carries the database key and is as readable as the data. Keep backups somewhere other than the phone.
- ⚠️ No supplier balances, and a bill that totals zero can't be completed.
- ⚠️ A refund isn't tied to the original payment method, and a cash refund on an account sale doesn't change the customer's ledger.
- ⚠️ The release build isn't minified or signed. Turning on minification (R8) is planned for after release signing is set up, because it has to be tested on a signed release on a real phone: sign in, sale, receipt print, barcode scan, backup and restore, fingerprint. Until then the release file is larger and its code is easy to read.

## Privacy and permissions

Your shop's data (products, bills, customers, settings) stays on the device. The app has no server and no account, and it sends none of that data anywhere. The only thing it does over the network is update itself:

- **Update check.** When the app opens, and when you tap Check for updates, it asks GitHub (`api.github.com`, the project's latest release) whether a newer version exists. The request carries no shop data. If one exists it offers to download it. Downloading and installing only happen when you tap them.
- **Install permission.** To install a downloaded update, Android asks you to allow Auxo to install apps. This is the `REQUEST_INSTALL_PACKAGES` permission, used only for updates.

Permissions the app declares:

| Permission | Why |
| --- | --- |
| Internet | Check for and download app updates (above). Nothing else. |
| Install unknown apps | Install a downloaded update, after you allow it. |
| Bluetooth (connect) | Print receipts on a paired Bluetooth printer. No scanning and no location. |
| Storage (Android 9 and older only) | Save backups in Documents/Auxo, where they survive uninstalling the app. |

Things that happen outside the app's own network use: sending a customer a reminder opens WhatsApp or the messages app, which then does its own sending, and the barcode scanner is Google Play services' scanner, which Google may download once.
