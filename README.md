# Invision
A Invoice app build from scratch 

Invision is an Android retail point-of-sale (POS) and invoicing app, built step by step.

**Status: Step 3 — billing.** Inventory (Step 2) plus a complete billing workflow: cart, discounts, GST, payments, returns, invoice history and printable or shareable receipts. Customers is a placeholder until a later step.

## Features

### Home dashboard
- Today's net sales (sales minus refunds) and number of bills
- Inventory KPIs: products, units in stock, stock value at cost and at selling price
- Low-stock, out-of-stock and expiring counts. Tapping one opens Inventory with that filter applied.
- "Needs attention" list of the most urgent items (expired, out of stock, expiring soon, low stock)

### Billing
- **Adding items:** search by name, brand, size, colour, SKU or barcode; scan with the camera; or use a USB/Bluetooth scanner in the search box (Enter adds the item). A 12-digit UPC-A code is found even when the scanner sends it as 13 digits with a leading 0. Scanning the same item again increases its quantity. Quantities can't go above stock, and the cart updates live if stock changes elsewhere.
- **Discounts:** per line and on the whole bill, as an amount (₹) or a percentage. A bill discount is shared across lines in proportion to their value, to the paisa.
- **Customer:** optional name, mobile number and GSTIN (for B2B bills). Bills without details print "Walk-in Customer". Mobile numbers and GSTINs (including the check character) are validated.
- **GST** (rates as revised under GST 2.0):
  - Each product has a GST rate (0, 5, 18, 40% or another rate) and an HSN code. Garments default to the apparel rule: 5% when a piece sells for up to ₹2,500 after discount (before GST), 18% above that.
  - Products added before this step have no GST rate. A GST-registered store can't bill them until a rate is set in Inventory; the cart line says so.
  - Prices include GST by default (usual for retail and MRP items); this can be switched to GST added on top.
  - CGST and SGST for sales in the store's state; IGST when the place of supply is another state (chosen per bill).
  - Stores not registered for GST issue a "Bill of Supply" with no tax.
  - Totals can be rounded to the nearest rupee, shown as a round-off line.
- **Payments:** cash (with cash received, change and quick note buttons), UPI (shows a QR code with the amount when a UPI ID is set; optional reference), card (optional last four digits), or any split of these. The sale completes only when payments add up to the total exactly.
- **Invoices:** numbered per financial year, e.g. `INV/26-27/00001` (prefix configurable, at most 16 characters as GST rules require), with date and time. Seller, buyer and product details are saved with the invoice, so later edits to products or settings don't change past bills.
- **Consistency:** the invoice, its lines, the payments and the stock deduction are saved in one database transaction. If any item no longer has enough stock, nothing is saved and the cart shows which item to fix. The same applies to returns.
- **Returns:** choose units per line (up to what is left to return), refund method and reason. This issues a credit note (`CN/26-27/00001`), records the refund and puts the units back in stock, all in one transaction. Amounts are worked out so that returning every unit, in one go or several, refunds exactly what was paid. Refunds follow the original bill's round-off: each one is what is due after the return minus what was due before, so several partial returns can't add up to more or less than the bill.
- **History:** invoices and credit notes for today, the last 7 days, this month or all time, searchable by number, customer name or phone, with sales and returns totals. A payments view lists every payment and refund with totals for cash, UPI, card, refunds and net received.
- **Receipts:** 58 mm, 76 mm (for 75–76 mm rolls) and 80 mm (for 78–80 mm rolls) thermal layouts with 32, 42 and 48 characters a line, and A5 and A4 sheets. Each invoice can be previewed in any size, printed through Android printing, or shared as a PDF (WhatsApp, email and so on). Shared PDFs are written in the app's cache and cleared after a day. Receipt options: default size, footer text, and whether to show customer details, HSN and GST per item, the GST summary, savings on MRP, and an invoice-number barcode. A4/A5 tax invoices include the amount in words and a signature block, and long invoices continue onto more pages.
- **Billing settings** (also under More): store name, address, phone, GSTIN, state, GST options, number prefixes, UPI ID and receipt options. Billing asks for the store's state and GSTIN (or "not GST-registered") before the first sale.

### Inventory
- **Garments:** pick sizes (XS–3XL or custom) and add colours. One variant is created per size and colour, each with its own barcode, SKU, prices and stock. Shared prices can be applied to every variant at once.
- **Packaged food:** net quantity (g, kg or pieces), veg or non-veg marking, batch number and expiry date. Several pack sizes can be added.
- **Beverages:** pack sizes in ml or L, with batch number and expiry date.
- Search by name, brand, size, colour, SKU or barcode. Filter by category and by stock (low, out of stock, expiring within 30 days).
- Product page with variants, barcodes, prices, stock status and stock history.
- **Stock adjustments:** received, returned, sold, damaged, or a count correction. Every change is logged with time and an optional note, and stock can never go below zero.
- Editing never overwrites stock; it changes only through adjustments. A variant that still has stock can't be removed.
- Input validation for required fields, price formats, MRP ≥ selling price, whole-number quantities, barcode check digits, and duplicate barcodes, SKUs or variants (within the product and across products).
- Loading, empty, no-results and error (with retry) states on every screen.

### Barcodes
- **Camera scanning:** uses Google's code scanner (provided by Google Play services, no camera permission needed). Scanning in Inventory opens the matching product, or offers to add a new one with that barcode.
- **USB and Bluetooth scanners:** these work as keyboards. Scan into the search box or a barcode field; Enter in the search box opens the product.
- **Validation:** EAN-13, EAN-8 and UPC-A are checked with their GS1 check digit. Other codes (letters, digits, `- . _ /`, up to 32 characters) are treated as Code 128.
- **Generation:** variants without a barcode get a unique in-store EAN-13 starting with `2`, the GS1 range reserved for in-store use, so it never clashes with manufacturer codes.

### Printable labels
- Choose label stock: A4 sheet with 24 labels (63.5 × 33.9 mm) or a 50 × 25 mm roll.
- Each label has the product name, brand and variant, price, optional MRP, and the barcode with its digits.
- Set copies per variant, or "Match stock" to print one per unit in stock.
- The preview uses the same drawing code as the printed PDF. Printing goes through Android's print system, so it works with any installed print service (Wi-Fi printers, PDF, label printer apps).

### Design
- Material 3 theme with emerald accents on light surfaces, dark text, and rounded shapes.
- Shared design tokens: spacing scale and status colours (success, warning, danger), plus reusable components such as stat tiles, status chips, cards, text fields and state views.
- Five-destination navigation that adapts: bottom bar on phones, side rail on tablets.

## Tech stack

| Area | Choice |
|---|---|
| Language | Kotlin 2.4.21 |
| UI | Jetpack Compose (BOM 2026.09.00), Material 3, adaptive navigation suite |
| Architecture | MVVM with Kotlin Coroutines and Flow, use cases, repository |
| Storage | Room 2.8.5 (KSP 2.3.12) |
| Navigation | Navigation Compose 2.10.2 |
| Barcodes and QR | ZXing core 3.5.4 (barcodes, UPI QR codes), Google code scanner 16.1.0 (camera) |
| Printing and sharing | Android print framework; PDFs generated with `PdfDocument`, shared through `FileProvider` |
| Build | Gradle 9.8.1 (wrapper), Android Gradle Plugin 9.4.1, Kotlin DSL, version catalog |
| Android SDK | compileSdk 37, targetSdk 37, minSdk 26 (Android 8.0) |
| Java | JDK 17 or newer to run the build (CI uses JDK 21) |
| Tests | JUnit 4, kotlinx-coroutines-test, Robolectric 4.17, Compose UI test |

AGP 9 compiles Kotlin itself ("built-in Kotlin"), so the project does not apply the `org.jetbrains.kotlin.android` plugin. The Kotlin Gradle Plugin is pinned in the root `build.gradle.kts` so the Kotlin compiler matches the Compose compiler plugin version.

## Requirements

- JDK 17 or newer
- Android SDK with Platform 37 (Android 17) and Build Tools 36.0.0 or newer
- To open in Android Studio, use a release that supports Android Gradle Plugin 9.4
- Camera scanning needs Google Play services on the device. Without it, the app shows a message and you can type the code or use a hardware scanner.

## Build and test

```bash
./gradlew assembleDebug        # build the debug APK
./gradlew testDebugUnitTest    # run all unit, database and UI tests
./gradlew lintDebug            # run Android lint
./gradlew connectedDebugAndroidTest   # run device tests on a connected phone or emulator
```

The debug APK is written to `app/build/outputs/apk/debug/`.

### What the tests cover

- **Billing maths (plain JVM):** GST on inclusive and exclusive prices, the apparel ₹2,500 rule at its exact boundary, CGST/SGST/IGST splits, line and bill discounts, paisa-exact allocation, rounding, savings, tax summaries, and that totals always add up.
- **Billing rules:** payments (exact, change, split, short, over, bad input), invoice numbering and financial years, GSTIN, mobile and UPI validation, UPI payment links, Indian number grouping and amounts in words, cart and customer checks, settings validation, sale and credit-note building (including returns in several steps adding up exactly, with and without round-off, and refunds never going negative).
- **Receipts:** thermal layouts never exceed 32, 42 or 48 characters a line; tax invoice, bill of supply and credit note content; options hiding sections; A5/A4 columns, totals, words and GST summary.
- **Inventory logic:** money parsing, GS1 check digits, barcode rules and generation (including UPC-A sent as EAN-13), search and filters, dashboard totals and alerts, stock adjustment rules, product form validation (including GST rate and HSN), the garment size × colour matrix, labels.
- **ViewModels** (with in-memory fakes): Home, Inventory, Product detail and editor, Labels, Billing (search, scanning, stock limits, discounts, customer checks, setup, cash/split/under-payment, stock sold elsewhere, place of supply), Returns, History and Billing settings.
- **Database** (Robolectric, Room in memory): sales save everything and deduct stock; a short item saves nothing and doesn't use up an invoice number; numbering per prefix and financial year; returns restock, mark lines and record refunds, and over-returns change nothing; partial returns of a rounded bill refund exactly what was paid; deleting a product keeps its invoices; period totals and payments; settings; checkout and return use cases end to end. A migration test builds a version 1 database from the committed schema, fills it with Step 2 data, and opens it with the current app.
- **UI** (Robolectric + Compose): every Inventory and Billing screen with sample data, receipts rendered for 58 mm, 76 mm, 80 mm, A5 and A4 (sale and credit note), a long A5 invoice spanning pages, and end-to-end flows through the real app and database. Screenshots are saved to `app/build/outputs/screenshots/`.

Robolectric runs on Android 15 (API 35); see `app/src/test/resources/robolectric.properties`.

**Device tests** (`app/src/androidTest`, run on an Android 15 emulator in CI) cover what Robolectric can't:
- Receipt PDFs for every paper size written with `PdfDocument` and read back with `PdfRenderer` (page count, page size, printed content), and long A5/A4 invoices continuing onto more pages.
- Sharing: the PDF reaches the receiving app as a readable `content://` file through the FileProvider.
- A full sale and return through the app: an item added from search and another by sending its barcode digits and Enter as key presses (the way a USB or Bluetooth scanner types), cash payment, the system share sheet and print dialog opening, and a return; stock, invoice, payment and refund are checked in the database at each step.
- The stock adjustment dialog (text fields in a dialog never report idle under Robolectric).

Not covered by automated tests:
- Camera scanning (needs Google Play services and a camera).
- Output on a real printer: the print dialog is opened with the receipt, but nothing is printed.
- A physical phone: the device tests run on an emulator.

## Continuous integration

`.github/workflows/android-ci.yml` runs on every push to `main`, on pull requests, and on demand. It builds the debug APK, runs all tests and lint, and reports results as annotations on the run and pull request. Compiler errors, failed tests and compiler warnings appear there too, so failures can be read without opening the logs. CI also fails if the Room schema changes without the new schema being committed in `app/schemas`; the new schema file is then attached to the run as a gzip+base64 annotation, so it can be committed even without a local Android build. The APK, test and lint reports, screenshots and the Room schema are uploaded as workflow artifacts. A second job runs the device tests on an Android 15 emulator and reports their results the same way.

## Project structure

```
app/src/main/java/com/invision/pos/
├── InvisionApplication.kt     App entry point; owns the dependency container
├── MainActivity.kt            Single activity hosting Compose
├── core/
│   ├── barcode/               Barcode bar patterns (ZXing)
│   ├── di/                    Manual dependency container (AppContainer)
│   ├── format/                Money formatting
│   ├── label/                 Label templates, sheet layout, drawing and printing
│   └── receipt/               Receipt layouts (58/76/80 mm, A5/A4), PDF, print and share
├── data/
│   ├── local/                 Room database, entities, DAO and mappers
│   ├── remote/                Network/API sources (planned)
│   └── repository/            Repository interfaces and implementations
├── domain/
│   ├── barcode/               GS1 check digits, barcode rules, in-store code generator
│   ├── billing/               Cart, GST calculator, discounts, payments, invoice and return builders, validators
│   ├── inventory/             Filters, dashboard summary, stock adjustment rules
│   ├── model/                 Product, variant, GST rate, invoice, payment, settings, money
│   ├── usecase/               Checkout, returns, save product, adjust stock, summaries
│   └── validation/            Product form state and validation
├── viewmodel/                 ViewModels exposing UI state as StateFlow
└── ui/
    ├── InvisionApp.kt         Adaptive navigation (bar or rail)
    ├── navigation/            Destinations, routes and NavHost
    ├── screens/               Home, Billing (cart, payment, invoice, history, returns, settings), Inventory, More
    ├── components/            Reusable composables, barcode image, scanner launcher
    ├── format/                Date and money formatting for the UI
    └── theme/                 Material 3 theme and design tokens
app/src/test/                  Unit, database (Robolectric) and UI tests
app/src/androidTest/           Device tests, run on an emulator in CI
app/schemas/                   Room schema history, for future migrations
```

Money is stored as whole minor units (paise) in `Money` to avoid floating-point rounding. Display currency currently defaults to Indian rupees (INR).

## Data and privacy

All data stays on the device in a Room database (`invision.db`, schema version 2). Receipt PDFs are written to the app's cache only when sharing, and cleared after a day. Android backup and device-to-device transfer are turned off for app data until the app has its own export or sync feature.

## Roadmap

1. **Foundation** — buildable project, architecture, theme, navigation shell, CI
2. **Inventory** — garments, packaged food and beverages, barcodes, labels, dashboard
3. **Billing** — cart, discounts, GST, payments, returns, invoice history, receipts *(this step)*
4. Next steps cover customers and reports.

## Security

Do not commit API keys, passwords or signing keys. `.gitignore` excludes keystores, `keystore.properties`, `secrets.properties` and `local.properties`.
