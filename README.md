<p align="center">
<picture>
<source media="(prefers-color-scheme: dark)" srcset="design/auxo/auxo-logo-horizontal-dark.png">
<img src="design/auxo/auxo-logo-horizontal.png" alt="Auxo: Built to grow." width="420">
</picture>
</p>

<p align="center">
<img src="https://img.shields.io/badge/platform-Android%208.0%2B-3DDC84" alt="Android 8.0+">
<img src="https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF" alt="Kotlin and Jetpack Compose">
<img src="https://img.shields.io/badge/license-GPL--3.0-blue" alt="GPL-3.0 license">
<img src="https://img.shields.io/badge/data-stays%20on%20device-informational" alt="Data stays on the device">
<img src="https://img.shields.io/badge/languages-English%20%C2%B7%20Hindi%20%C2%B7%20Bengali-blue" alt="English, Hindi, Bengali">
</p>

<p align="center"><strong><a href="https://github.com/Roybabu/Auxo/releases/latest">Download the latest APK</a></strong> (Android 8.0 or newer)</p>

**Auxo** is a whole shop in your pocket. Bill customers, track stock, keep the books straight and print the receipt, all from one Android app. It works offline, with no server and no monthly fee. The only time it uses the internet is to check for and download app updates. The name comes from the Greek *auxō*, "to increase or grow".

Built for small Indian shops (clothes, packaged food, drinks, cafés). It speaks GST, counts in paise, reads barcodes and works in English, Hindi and Bengali.

## What it does

- **Bill fast.** Scan with the camera or a hardware scanner, split payments across cash, UPI, card and customer accounts, hold a bill and come back to it, send quotations on WhatsApp.
- **GST done right.** CGST/SGST/IGST, HSN codes, the apparel rate rule, invoices numbered per financial year, credit notes for returns.
- **Know your stock.** Sizes and colours, batches with expiry, reorder suggestions, purchase orders, stock counts, CSV import, product photos.
- **Run a restaurant too.** Dishes sit beside your other products, with or without a stock count.
- **See the numbers.** Twelve reports, day-end cash closing, a GST report, CSV export.
- **Stay in control.** Owner and employee logins with per-person permissions, idle lock with PIN or fingerprint, recovery code, activity log.
- **Never lose the books.** Every backup is locked with your own passphrase and saved on the phone, by hand or automatically (every 15 minutes up to every 7 days, or after a number of invoices). Restore on a new phone from the sign-in page. See [Backups](#backups).
- **Print anywhere.** PDF, Android printing, or straight to a Bluetooth receipt printer (58, 76 or 80 mm).

## Screenshots

| Sign in | Home | New bill |
|---|---|---|
| <img src="docs/screenshots/sign-in.jpg" width="240" alt="Sign in"> | <img src="docs/screenshots/home.jpg" width="240" alt="Home"> | <img src="docs/screenshots/new-bill.jpg" width="240" alt="New bill"> |

| Inventory | Add product | Customers |
|---|---|---|
| <img src="docs/screenshots/inventory.jpg" width="240" alt="Inventory"> | <img src="docs/screenshots/add-product.jpg" width="240" alt="Add product"> | <img src="docs/screenshots/customers.jpg" width="240" alt="Customers"> |

| Invoices and payments | Day end | Backup and restore |
|---|---|---|
| <img src="docs/screenshots/invoices.jpg" width="240" alt="Invoices and payments"> | <img src="docs/screenshots/day-end.jpg" width="240" alt="Day end"> | <img src="docs/screenshots/backup.jpg" width="240" alt="Backup and restore"> |

Taken on a real Android phone.

## One rule: the books must add up

Money is stored in whole paise, never as decimals. A sale saves the invoice, payments, stock and customer account in a single transaction, so it either all happens or none of it does. Double taps can't save a bill twice. Thousands of random bills in the tests add up to the paisa.

## Install

Download `Auxo-vX.Y.Z.apk` from the [latest release](https://github.com/Roybabu/Auxo/releases/latest) on your phone (Android 8.0 or newer) and open it. Android will ask you to allow installs from your browser or file manager the first time. Later versions are offered inside the app (More → Check for updates) and install over the old one. Google is introducing developer verification for apps on certified phones, so Android may show an extra warning for apps from outside Google Play. The app is signed with the project's own key; the privacy policy is in [docs/PRIVACY.md](docs/PRIVACY.md).

## Backups

- **Always locked.** The first backup asks you to choose a passphrase (at least 10 characters; short and common ones are refused). Every backup is encrypted with it and saved as an `.auxobak` file. Auxo cannot recover the passphrase, so keep it safe. Unlocked `.zip` backups are not made and cannot be restored.
- **Where.** Backups are saved in the Auxo folder inside Documents, which survives uninstalling the app. Keep another copy off the phone.
- **Automatic backup.** In Backup, choose how often: every 15 or 30 minutes, 1, 2, 3, 6 or 12 hours, every day, every week, or after a number of invoices, and how many backups to keep. The first backup runs straight away. Android will not wake an app more often than every 15 minutes, so timing is approximate.
- **Restore on a new phone.** On the sign-in page, choose the folder holding your backups, pick one, enter its passphrase, check the shop name shown, confirm, then set a new username and password. Restoring replaces everything on the phone, and sign-in details are never part of a backup.

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

## Where things live

```
app/src/main/java/com/auxo/pos/
├── core/       sign-in, backup, printing, receipts, photos, DI
├── data/       Room database, repositories, permission checks
├── domain/     billing, GST, inventory, reports (plain Kotlin)
├── viewmodel/  screen state
└── ui/         Compose screens and navigation
```

## Crash and problem reports

If the app crashes, it saves a short report on the phone and offers, the next time you open it, to share it. **More → Send a diagnostic report** makes one on demand. Nothing is sent unless you choose to. A report holds the app and phone version, the error and where it happened, and the names of the last screens opened. It holds no customer, sales or shop details, and database keys, email addresses and long numbers are removed. You can share the file with the developer, or use **Report on GitHub**, which opens a new issue on this repository: anything you paste there is public.

## Privacy and permissions

The full policy is in [docs/PRIVACY.md](docs/PRIVACY.md).

Your shop's data (products, bills, customers, settings) stays on the device. The app has no server and no account, and it sends none of that data anywhere. The only thing it does over the network is update itself:

- **Update check.** When the app opens, and when you tap Check for updates, it asks GitHub (`api.github.com`, the project's latest release) whether a newer version exists. The request carries no shop data. If one exists it offers to download it. Downloading and installing only happen when you tap them.
- **Install permission.** To install a downloaded update, Android asks you to allow Auxo to install apps. This is the `REQUEST_INSTALL_PACKAGES` permission, used only for updates.

Permissions the app declares:

| Permission | Why |
| --- | --- |
| Internet | Check for and download app updates (above). Nothing else. |
| Install unknown apps | Install a downloaded update, after you allow it. |
| Bluetooth (connect) | Print receipts on a paired Bluetooth printer. No scanning and no location. |
| Storage (Android 9 and older only) | Save backups, including automatic ones, in Documents/Auxo, where they survive uninstalling the app. |

Things that happen outside the app's own network use: sending a customer a reminder opens WhatsApp or the messages app, which then does its own sending, and the barcode scanner is Google Play services' scanner, which Google may download once.

## License

Auxo is free software under the [GNU General Public License v3.0](LICENSE) (GPL-3.0-or-later). You can use, study, change and share it. If you distribute a modified version, you must share its source code under the same license.

The name **Auxo**, its logo and its artwork are not covered by that license. Forks may reuse the code but not the name or the logo.
