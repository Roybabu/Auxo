# 🧾 Invision

**A whole shop in your pocket.** Bill customers, track every piece of stock, keep the books straight and print the receipt, from one Android app that works with no internet, no server and no monthly fee.

Invision is a point-of-sale and invoicing app for small Indian shops: clothes, packaged food, drinks, and restaurants and cafés. It speaks GST, counts in paise, reads barcodes, prints on Bluetooth receipt printers, and talks to your customers in English, Hindi and Bengali. It is built from scratch in Kotlin and Jetpack Compose, and every rupee that moves through it is checked by tests.

> **Version 0.4.0.** Everything below is built and passes the automated tests in CI. It has **not** yet been used on a physical phone or a real receipt printer. See [How far can you trust it?](#-how-far-can-you-trust-it) for the honest list.

---

## ✨ A day in the shop

Picture a Saturday at a small clothing and snacks shop.

**9:00 — Opening.** The owner signs in with a password (an employee signs in with theirs). Home shows yesterday's habits at a glance: today's sales, how many units are on the shelves, what is running low and what is about to expire.

**11:30 — A customer, a basket, a barcode.** The cashier scans a shirt with the phone's camera, types "biscuit" for the next item, and a cheap USB scanner adds the third. The cart refuses a quantity above stock. A 10% bill discount is shared across the lines to the exact paisa. The customer says "put it on my account", so part goes to cash, part to UPI (a QR code with the amount appears on screen) and the rest to their account, within their credit limit.

**11:45 — The phone rings.** The cashier presses *Hold*, serves someone else, and resumes the first bill later. A quotation goes out to a wedding party over WhatsApp.

**14:00 — The shelf runs low.** Stock alerts suggest what to reorder and turn it into one draft purchase order per supplier. When the goods arrive, the owner receives them against the order, with batch numbers and expiry dates for the food.

**18:00 — A return.** One shirt comes back. Invision issues a credit note, puts the shirt back into the right batch and refunds the exact amount, even when the original bill was rounded.

**21:00 — Closing.** The owner counts the cash, closes the day, checks the GST report, and lets the app back itself up to a folder. Tomorrow starts clean.

All of it works offline.

---

## 🧭 What's inside

| | |
|---|---|
| 🛒 **Billing** | Search, camera and keyboard-scanner entry; line and bill discounts; GST done properly; cash, UPI, card, on-account and split payments; hold and resume; quotations; returns and credit notes |
| 📦 **Inventory** | Garments by size and colour, packaged food, drinks and kitchen ingredients; batches with expiry; barcodes and printable labels; stock counts; CSV import; product photos |
| 🚚 **Purchasing** | Suppliers, purchase orders, goods received, reorder suggestions, wastage and write-offs |
| 👥 **Customers** | Accounts with a full running statement, credit limits, payments received, loyalty points |
| 🍽️ **Restaurant mode** | A separate menu with sections, dishes, drinks and optional recipes that use up ingredient stock |
| 📊 **Reports** | Twelve reports with a flexible period, totals and CSV export; day-end closing; a GST report |
| 🔐 **People and safety** | Super Admin and Employees with per-person permissions, idle lock, PIN or fingerprint unlock, activity log, backup and restore |
| 🖨️ **Printing** | A4/A5 PDFs, 58/76/80 mm thermal layouts, and direct Bluetooth receipt printing |
| 🌐 **Languages** | App and bills in English, Hindi and Bengali |

---

## 🛒 Billing in detail

- **Adding items:** search by name, brand, size, colour, SKU or barcode; scan with the camera; or use a USB or Bluetooth scanner (it types like a keyboard, and Enter adds the item). A 12-digit UPC-A code is found even when the scanner sends it as 13 digits. Scanning an item again raises its quantity. Quantities can't pass stock, and the cart updates live if stock changes elsewhere.
- **Discounts:** per line and on the whole bill, as ₹ or %. A bill discount is shared across lines in proportion to their value, to the paisa. Employees can be given a discount limit.
- **Customers:** optional name, mobile and GSTIN (for B2B bills), all validated, including the GSTIN check character. Bills without details print "Walk-in Customer". Typed details can be saved as a new customer from the bill.
- **Restaurant dishes** are billed alongside shop products with their own GST rate and SAC code.
- **GST** (rates as revised under GST 2.0):
  - Each product has a rate (0, 5, 18, 40% or another) and an HSN code. Garments follow the apparel rule: 5% when a piece sells for up to ₹2,500 after discount (before GST), 18% above that.
  - Prices include GST by default; this can be switched to GST added on top.
  - CGST and SGST inside the store's state, IGST when the place of supply is another state.
  - Stores not registered for GST issue a Bill of Supply with no tax.
  - Totals can be rounded to the nearest rupee, with the round-off shown as a line.
- **Payments:** cash (received, change, quick note buttons), UPI (QR with the amount when a UPI ID is set, optional reference), card (optional last four digits), "On account" for a saved customer, or any split. The sale completes only when payments add up to the total exactly.
- **Invoices** are numbered per financial year, like `INV/26-27/00001` (prefix configurable, 16 characters at most as GST rules require). Seller, buyer and product details are saved with the invoice, so later edits never change past bills.
- **Hold and resume**, **quotations**, **loyalty points**, and **sharing an invoice on WhatsApp** are built in.
- **Returns:** choose units per line (never more than is left), the refund method (including back to the customer's account) and a reason. The app issues a credit note (`CN/26-27/00001`), records the refund and puts the units back into the batches they were sold from. Returning every unit, in one go or several, refunds exactly what was paid.
- **History:** invoices and credit notes for today, 7 days, this month or all time, searchable by number, customer or phone, with a payments view that totals cash, UPI, card, refunds and net received.
- **Receipts:** 58 mm, 76 mm and 80 mm thermal layouts (32, 42 and 48 characters a line) and A5 and A4 sheets. Preview any invoice in any size, print it through Android, share it as a PDF or send it to a Bluetooth printer. Options cover footer text, customer details, HSN and GST per item, the GST summary, savings on MRP and an invoice-number barcode. Tax invoices include the amount in words and a signature block, and long invoices continue onto more pages.

---

## 📦 Inventory, purchasing and stock

- **Garments:** pick sizes (XS–3XL or custom) and colours. One variant per size and colour, each with its own barcode, SKU, prices and stock. Shared prices apply to every variant at once.
- **Packaged food and drinks:** pack sizes in g, kg, pieces, ml or L, veg or non-veg marking, batch number and expiry date.
- **Kitchen ingredients:** counted in grams, millilitres or pieces so a recipe can use part of a pack. They are never sold at the counter.
- **Batches:** food, drinks and ingredients live in batches, each with a number, expiry date, quantity and cost. Sales take the batch that expires first and never sell expired stock. A batch's units always add up to the item's stock.
- **Photos and import:** a photo per product (gallery or camera), and products can be brought in from a CSV file.
- **Stock adjustments and counts:** received, returned, damaged, used in the kitchen or a count correction, each logged with time and note. Stock can never go below zero, and a repeated tap applies a change once. Run a stock count and post the differences.
- **Suppliers and orders:** suppliers are hidden, never deleted, so history stays intact. Purchase orders (`PO/26-27/00001`) can be shared as text and received against (`GRN/26-27/00001`). A receipt that over-receives, uses a closed order, repeats a supplier bill number or refers to a deleted item is refused whole.
- **Alerts:** low stock with a suggested order, expiry within the number of days the shop chooses, and wastage write-offs valued at cost.
- **Movements:** every stock change across products in one filterable list, with CSV export.

---

## 🍽️ Restaurant mode

Switch it on and a separate menu appears: dishes and drinks in sections, with price, GST (5% by default), SAC code (996331 by default), veg or non-veg marking and an availability switch. Add a recipe (for example 120 g flour, 30 ml milk) and each serving sold takes its ingredients from stock, earliest expiry first, refusing the bill if an ingredient is short. The menu shows how many servings the current stock allows.

---

## 📊 Reports, day-end and GST

Twelve reports: sales summary by day, item sales, collections by payment method, GST by rate, HSN summary (for GSTR-1), purchases, stock movements, wastage, stock value, low stock, expiry and customer balances. Pick today, yesterday, 7 days, this month, last month, this financial year, all time or a custom range. Credit notes count against sales, so figures are net of returns. Tables scroll sideways, show a totals row and export as CSV (amounts as plain numbers, UTF-8 with a byte order mark so Excel opens it, and any text that could run as a spreadsheet formula escaped).

Employees only see the report types they are allowed to read.

**Day-end closing** records the cash counted against the day's figures. The **GST report** (under More) summarises the tax figures in one place.

---

## 🔐 People, locks and backups

- **Roles:** a Super Admin and Employees, each with their own password. Billing basics are always on for an Employee; the Super Admin switches on extras per person: returns, day-end, figures, inventory, customers, purchasing, menu, settings and the activity log. User management and backup stay with the Super Admin only.
- **Guessing is slowed down:** after 5 wrong passwords (or recovery codes) the app waits 30 seconds, doubling up to 5 minutes. The count is kept on the phone, so closing the app doesn't reset it.
- **Forgot the password?** A recovery code lets the Super Admin reset it.
- **Idle lock:** the Super Admin sets how long the app may sit untouched. It then locks, and unlocks with a PIN, a fingerprint or the password.
- **Activity log:** changes, returns and deletions, with who did them.
- **Real permission checks:** the screens hide what you can't use, but the actual refusal happens in the data layer, so a page reached by a back door still can't read or change anything.
- **Backup and restore** the whole shop to a file, and **automatic backups** to a folder you choose.

---

## 🖨️ Printing

- **PDF and Android print:** receipts and labels go through Android's print system, so any installed print service works (Wi-Fi printers, PDF, label apps).
- **Bluetooth receipt printer:** pair a 58, 76 or 80 mm ESC/POS printer in the phone's Bluetooth settings, choose it under *More → Bluetooth printer*, print a test receipt to see the width, and print any invoice from its screen. The receipt is drawn as a picture, so Hindi and Bengali print correctly. Every failure (Bluetooth off, permission missing, printer asleep, connection dropped, receipt too big to draw) is reported in plain words.
- **Labels:** an A4 sheet of 24 labels (63.5 × 33.9 mm) or a 50 × 25 mm roll, with name, variant, price, optional MRP and the barcode. "Match stock" prints one label per unit on hand.

---

## 🔎 Barcodes

- **Camera:** Google's code scanner (needs Google Play services, no camera permission). In Inventory it opens the matching product or offers to add a new one.
- **Hardware scanners** work as keyboards.
- **Checked properly:** EAN-13, EAN-8 and UPC-A are validated with their GS1 check digit. Other codes (letters, digits and `- . _ /`, up to 32 characters) are treated as Code 128.
- **Generated codes:** variants without a barcode get a unique in-store EAN-13 starting with `2`, the GS1 range reserved for in-store use, so it can never clash with a manufacturer's code.

---

## 🧮 Money you can trust

Invision is built around one rule: **the books must always add up.**

- Money is stored as whole paise in a `Money` type, never as floating point.
- A sale writes the invoice, its lines, the payments, the stock deduction, the ingredients used and the customer's account entry in **one database transaction**. If an item is short or a credit limit would be passed, **nothing** is saved and the bill says what to fix. Returns and goods receipts follow the same all-or-nothing rule.
- Every sale, return and receipt carries a one-time id, so double-tapping *Complete sale* or retrying after an error saves it once, even when two requests arrive at the same moment.
- After every scenario, a reconciliation helper in the tests checks the books: stock against movements and batches, no negative stock, payments against invoice totals, invoice totals against lines, returned units against credit notes, refunds against sales, sales on account against the ledger, no repeated invoice numbers, no orphaned rows.
- Seeded random bills (1,000 sales in every tax mode with discounts, GST slabs, inter-state and split payments) add up to the paisa, and 500 sales returned unit by unit in any order refund exactly the bill.

---

## 🧪 How far can you trust it?

Honest answer: a lot of the logic, less of the hardware.

**What has been checked.** CI builds the app, runs more than 1,600 unit, database and screen tests, runs lint (0 errors), and runs device tests on an Android 15 emulator. At the last full run all four CI jobs passed. Billing, inventory, purchasing and ledger rules show 90–100% line coverage in the coverage job.

**What has not.**
- ❌ The Bluetooth printer has never been tried on a real printer. The sending logic, failures and ESC/POS bytes are tested against a fake connection only. The chunk size and pauses are untested guesses.
- ❌ The app has never been run on a physical phone. The Bluetooth permission prompt, file pickers and camera scanning are unchecked on a real device.
- ❌ Hindi and Bengali text has not been reviewed by a native speaker.
- ❌ Opening exported CSV files in a real spreadsheet app is untested (the file contents are tested).

If you are going to use this in a real shop, test it on your own phone and printer first, and keep a backup somewhere other than the phone.

---

## 🛠️ Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.4.21 |
| UI | Jetpack Compose (BOM 2026.09.00), Material 3, adaptive navigation suite |
| Architecture | MVVM with Kotlin Coroutines and Flow, use cases, repositories, manual dependency injection |
| Storage | Room 2.8.5 (KSP 2.3.12), schema version 15 |
| Navigation | Navigation Compose 2.10.2 |
| Barcodes and QR | ZXing core 3.5.4 (barcodes, UPI QR codes), Google code scanner 16.1.0 (camera) |
| Printing and sharing | Android print framework; PDFs from `PdfDocument` shared through `FileProvider`; ESC/POS over Bluetooth |
| Build | Gradle 9.8.1 (wrapper), Android Gradle Plugin 9.4.1, Kotlin DSL, version catalog |
| Android SDK | compileSdk 37, targetSdk 37, minSdk 26 (Android 8.0) |
| Java | JDK 17 or newer to run the build (CI uses JDK 21) |
| Tests | JUnit 4, kotlinx-coroutines-test, Robolectric 4.17, Compose UI test |

AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the project does not apply the `org.jetbrains.kotlin.android` plugin. The Kotlin Gradle Plugin is pinned in the root `build.gradle.kts` so the compiler matches the Compose compiler plugin.

The app is a single activity (`FragmentActivity`, needed for the fingerprint prompt). `androidx.fragment` is pinned to 1.8.5 because the older copy that the biometric library brings in rejects the request codes the Activity Result API uses.

## 🚀 Getting started

**You need:** JDK 17 or newer; the Android SDK with Platform 37 (Android 17) and Build Tools 36.0.0 or newer; and an Android Studio release that supports Android Gradle Plugin 9.4.

```bash
./gradlew assembleDebug               # build the debug APK
./gradlew testDebugUnitTest           # run all unit, database and UI tests
./gradlew lintDebug                   # run Android lint
./gradlew connectedDebugAndroidTest   # run device tests on a connected phone or emulator
```

The debug APK is written to `app/build/outputs/apk/debug/`. Camera scanning needs Google Play services; without it the app shows a message and you can type the code or use a hardware scanner.

---

## 🧰 What the tests cover

- **Billing maths (plain JVM):** GST on inclusive and exclusive prices, the ₹2,500 apparel rule at its exact boundary, CGST/SGST/IGST splits, line and bill discounts, paisa-exact allocation, rounding, savings and tax summaries.
- **Billing rules:** payments (exact, change, split, short, over, bad input), invoice numbering and financial years, GSTIN, mobile and UPI validation, UPI payment links, Indian number grouping and amounts in words, sale and credit-note building.
- **Receipts:** thermal layouts never exceed 32, 42 or 48 characters a line; tax invoice, bill of supply and credit note content; A5/A4 columns, totals and GST summary.
- **Inventory and operations logic:** money parsing, GS1 check digits, search and filters, batch allocation, stock units, reorder suggestions, purchase order and receipt validation, ledger balances, menu and recipe validation, every report builder, CSV escaping, report periods and expiry windows.
- **ViewModels** (with in-memory fakes) for Home, Inventory, Billing, Returns, History, Suppliers, Purchasing, Stock, Menu, Reports, Customers, the printer settings and more.
- **Database** (Robolectric, Room in memory): sales save everything and deduct stock; a short item saves nothing and uses no invoice number; returns restock and over-returns change nothing; period totals; deleting a product keeps its invoices. Migration tests build version 1, 2 and 4 databases from the committed schemas, fill them with earlier data and open them with the current app; the 4 to 15 test runs every later hand-written migration and Room checks the result against the version 15 schema. Schema files for versions 6 to 14 are not committed and nothing needs them.
- **Security and sign-in:** a permission enforcement test for every guarded repository and screen route, password, PIN and recovery-code checks that never throw on odd input, and lockouts that survive a restart.
- **Bluetooth transport:** the sending logic over a fake connection (all bytes in order, 1 KB pieces, connection failures closing the socket, unexpected errors turning into a clear result, cancellation).
- **UI** (Robolectric + Compose): every main screen with sample data, receipts rendered for every paper size, a long A5 invoice across pages, and end-to-end flows through the real app and database. Screenshots go to `app/build/outputs/screenshots/`.

Robolectric runs on Android 15 (API 35); see `app/src/test/resources/robolectric.properties`.

**Device tests** (`app/src/androidTest`, on an Android 15 emulator in CI) cover what Robolectric can't: receipt PDFs written and read back with `PdfRenderer`, sharing through the FileProvider, a full sale and return through the app (including a barcode typed the way a scanner types), the stock adjustment dialog, receiving a purchase order, and adding a customer with a payment.

**Coverage** (a separate, report-only CI job using JaCoCo; figures from the last time they were read): billing rules, inventory rules, purchasing rules and customer ledger rules are at about 98–100% of lines; reports, use cases and view models are lower. **The database layer shows about 0–5%, which is not real:** its tests run inside Robolectric's own class loader, which JaCoCo can't see. Run it by hand with `./gradlew -Pcoverage createDebugUnitTestCoverageReport`.

## 🤖 Continuous integration

`.github/workflows/android-ci.yml` runs on pushes, pull requests and on demand. It builds the debug APK, runs all tests and lint, measures coverage, and runs the device tests on an emulator, then reports compiler errors, failed tests and warnings as annotations so failures can be read without opening logs. It **fails if the Room schema changes without the new schema being committed**; the new schema file is attached to the run as a gzip+base64 annotation, so it can be committed even without a local Android build. A `security` job scans every tracked file for secrets and unsafe manifest settings and builds an unsigned release variant. A quick mode skips lint for faster feedback.

---

## 🗂️ Project structure

```
app/src/main/java/com/invision/pos/
├── InvisionApplication.kt     App entry point; owns the dependency container
├── MainActivity.kt            Single activity hosting Compose
├── core/
│   ├── appearance/            Theme and accent settings
│   ├── audit/                 Activity log of changes, returns and deletions
│   ├── auth/                  Accounts, sign-in, roles and permissions, lockout, recovery code, PIN and fingerprint unlock
│   ├── backup/                Backup and restore, automatic backup to a folder
│   ├── barcode/               Barcode bar patterns (ZXing)
│   ├── dayend/                Day-end closing
│   ├── di/                    Manual dependency container (AppContainer)
│   ├── export/                CSV files in the cache and the share sheet
│   ├── format/                Money formatting
│   ├── imports/               CSV product import
│   ├── locale/                App and bill language
│   ├── label/                 Label templates, sheet layout, drawing and printing
│   ├── photos/                Product photos
│   ├── printer/               Bluetooth receipt printer (ESC/POS, pairing list, sending)
│   ├── receipt/               Receipt layouts (58/76/80 mm, A5/A4), PDF, print and share, raster for thermal printers
│   └── shop/                  Shop-wide switches (restaurant menu, suppliers) and the store logo
├── data/
│   ├── local/                 Room database, entities, DAOs, mappers, StockWriter (the one place stock changes)
│   ├── remote/                Empty placeholder; the app makes no network calls
│   ├── repository/            Repository interfaces and implementations
│   └── security/              Wrappers that check permissions before any sensitive read or write
├── domain/
│   ├── barcode/               GS1 check digits, barcode rules, in-store code generator
│   ├── billing/               Cart, GST calculator, discounts, payments, invoice and return builders, validators
│   ├── contacts/              Supplier and customer forms and validation
│   ├── customers/             Ledger balances, statements, account payment checks
│   ├── inventory/             Filters, dashboard summary, stock adjustment rules, batch allocation, reorders, item search
│   ├── menu/                  Dish and recipe forms and validation
│   ├── model/                 Product, variant, batch, stock unit, GST rate, invoice, payment, supplier, purchasing, customer, menu, settings, money
│   ├── purchasing/            Purchase order and receipt forms, numbering, order text
│   ├── reports/               Report builders, periods and CSV
│   ├── usecase/               Checkout, returns, save product, adjust stock, receive goods, write-offs, account payments, reorder drafts, summaries
│   └── validation/            Product form state and validation
├── viewmodel/                 ViewModels exposing UI state as StateFlow
└── ui/
    ├── InvisionApp.kt         Adaptive navigation (bar or rail)
    ├── navigation/            Destinations, routes, per-route access rules and NavHost
    ├── screens/               Home, Billing, Inventory, Customers, Purchasing, Stock, Menu, Reports, More
    ├── components/            Reusable composables, barcode image, scanner launcher
    ├── format/                Date and money formatting for the UI
    └── theme/                 Material 3 theme and design tokens
app/src/test/                  Unit, database (Robolectric) and UI tests
app/src/androidTest/           Device tests, run on an emulator in CI
app/schemas/                   Room schema history (versions 1–5 and 15)
```

---

## 📱 Supported platforms

Android 8.0 (API 26) and newer, phones and tablets (the navigation switches between a bottom bar and a side rail). No iOS, web or desktop build. The only environment the app has been run in is the Android 15 emulator in CI.

## 🔒 Data and privacy

- **Everything stays on the device** in a Room (SQLite) database, `invision.db`. There is no server and no sync, and the app's own code makes no network calls. Sign-in accounts live in a file in the app's private storage, with passwords kept only as salted hashes. Libraries (the Google code scanner) may add their own permissions; the merged list is checked in CI.
- **Permissions:** the manifest asks for Bluetooth (`BLUETOOTH` up to Android 11, `BLUETOOTH_CONNECT` from Android 12) for the receipt printer.
- **Not encrypted:** the database and backup files sit in storage other apps can't read on an unmodified phone, but they are not encrypted. Anyone with a rooted phone, a disk image or a copy of a backup file can read them.
- **Backups:** Android's own backup and device-to-device transfer are turned off for app data. Use the app's backup (More → Backup), and keep a copy somewhere other than the phone. Uninstalling, clearing data or losing the phone loses everything that wasn't backed up.
- **Temporary files:** receipt PDFs and CSV exports go to the app's cache only when sharing, are shared through a `FileProvider` with a read grant for the chosen app, and are cleared after a day.
- **Logging:** the app writes nothing to the system log.

## 🌐 Languages and fonts

The app and the bills can be in English, Hindi or Bengali. The app language is a per-device setting (Appearance); the bill language is a shop setting (Billing settings), so every phone prints the same bill. Product and customer names always print as entered. Receipt and PDF text in Hindi and Bengali is drawn with Noto Sans Devanagari and Noto Sans Bengali (Regular and Bold) bundled in `app/src/main/assets/fonts`, under the SIL Open Font License 1.1 (`OFL.txt` in the same folder), so it doesn't depend on the fonts a phone has.

## 🎨 Design

Material 3 with emerald accents, dark text on light surfaces (or a black theme) and rounded shapes. Shared design tokens for spacing and status colours, with reusable stat tiles, status chips, cards, text fields and state views. Every screen has loading, empty, no-results and error (with retry) states. Navigation adapts to the screen: a bottom bar on phones and a side rail on tablets.

---

## ⚠️ Known issues

- No supplier balances: purchases record goods received, not money owed.
- A bill with a total of zero (for example a 100% discount) can't be completed.
- A cash refund on a bill that was sold on account doesn't change the customer's ledger; the ledger changes only when the refund goes back to the account. Payments aren't matched to individual bills.
- The refund method isn't tied to how the original bill was paid (a UPI sale can be refunded in cash).
- The Bluetooth printer, the permission prompt, the file pickers and camera scanning haven't been tried on real hardware.
- Bluetooth LE-only printers aren't supported.
- The Hindi and Bengali texts haven't been reviewed by a native speaker.
- The database and backup files aren't encrypted.
- The release build isn't minified or signed, and is only checked to compile in CI.
- No code formatter or Kotlin static analyser (ktlint, detekt); only Android lint runs.
- Some files are very large (for example `BillingScreen.kt` and `BillingViewModel.kt`, each over 1,000 lines).
- The database layer isn't in the coverage figures, because Robolectric loads it in a way JaCoCo can't see.

## 🗺️ Roadmap

1. ✅ **Foundation:** buildable project, architecture, theme, navigation shell, CI
2. ✅ **Inventory:** garments, packaged food and drinks, barcodes, labels, dashboard
3. ✅ **Billing:** cart, discounts, GST, payments, returns, invoice history, receipts
4. ✅ **Operations:** suppliers, purchase orders and goods received, batches, expiry and wastage, stock movements and alerts, customer accounts, restaurant menu and recipes, reports and CSV export
5. ✅ **Everything since:** sign-in and roles, backup, hold and quotations, loyalty, day-end and GST report, stock counts and import, product photos, WhatsApp sharing, UPI QR, Bluetooth printing
6. 🔜 **Likely next (not committed to):** real-world testing on phones and printers, a native-speaker review of Hindi and Bengali, supplier balances, encrypted backups

## 🛡️ Security

Don't commit API keys, passwords or signing keys. `.gitignore` excludes keystores, `keystore.properties`, `secrets.properties` and `local.properties`. The `security` CI job runs `.github/scripts/security_check.py` over every tracked file (secret patterns, secret-type file names, unsafe manifest settings such as backup or cleartext traffic, workflow permissions) and builds an unsigned release APK; the `build` job lists the permissions of the merged manifest and fails on unexpected ones. Workflows run with read-only `contents` permission and use no repository secrets. Dependabot proposes monthly updates for Gradle dependencies and GitHub Actions.
