# Invision
A Invoice app build from scratch 

Invision is an Android retail point-of-sale (POS) and invoicing app, built step by step.

**Status: Step 2 — inventory, barcodes and labels.** Products are stored on the device, with full inventory workflows for garments, packaged food and beverages. Billing, Customers and More are placeholders until later steps.

## Features

### Home dashboard
- Today's sales and bill count (shown once billing exists)
- Inventory KPIs: products, units in stock, stock value at cost and at selling price
- Low-stock, out-of-stock and expiring counts. Tapping one opens Inventory with that filter applied.
- "Needs attention" list of the most urgent items (expired, out of stock, expiring soon, low stock)

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
| Barcodes | ZXing core 3.5.4 (encoding), Google code scanner 16.1.0 (camera) |
| Printing | Android print framework, PDF generated with `PdfDocument` |
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
```

The debug APK is written to `app/build/outputs/apk/debug/`.

### What the tests cover

- **Domain logic (plain JVM):** money parsing, GS1 check digits, barcode rules and generation, inventory search and filters, dashboard totals and alerts, stock adjustment rules, product form validation, the garment size × colour matrix, label sheet layout and label contents.
- **ViewModels** (with an in-memory fake repository): Home, Inventory, Product detail, Product editor and Label printing, including loading, error and retry.
- **Database** (Robolectric, in-memory Room): saving and reading every field, opening-stock history, barcode/SKU conflicts, editing without losing stock, the no-negative-stock rule, barcode lookup and cascade delete.
- **UI** (Robolectric + Compose): each screen rendered with sample data, plus an end-to-end flow that adds a garment through the real app and database. These tests save screenshots to `app/build/outputs/screenshots/`.

Robolectric runs on Android 15 (API 35); see `app/src/test/resources/robolectric.properties`.

## Continuous integration

`.github/workflows/android-ci.yml` runs on every push to `main`, on pull requests, and on demand. It builds the debug APK, runs all tests and lint, and reports results as annotations on the run and pull request. Compiler errors, failed tests and compiler warnings appear there too, so failures can be read without opening the logs. The APK, test and lint reports, screenshots and the Room schema are uploaded as workflow artifacts.

## Project structure

```
app/src/main/java/com/invision/pos/
├── InvisionApplication.kt     App entry point; owns the dependency container
├── MainActivity.kt            Single activity hosting Compose
├── core/
│   ├── barcode/               Barcode bar patterns (ZXing)
│   ├── di/                    Manual dependency container (AppContainer)
│   ├── format/                Money formatting
│   └── label/                 Label templates, sheet layout, drawing and printing
├── data/
│   ├── local/                 Room database, entities, DAO and mappers
│   ├── remote/                Network/API sources (planned)
│   └── repository/            Repository interfaces and implementations
├── domain/
│   ├── barcode/               GS1 check digits, barcode rules, in-store code generator
│   ├── inventory/             Filters, dashboard summary, stock adjustment rules
│   ├── model/                 Product, variant, money, net quantity, stock movement
│   ├── usecase/               Save product, adjust stock, barcode lookup, summaries
│   └── validation/            Product form state and validation
├── viewmodel/                 ViewModels exposing UI state as StateFlow
└── ui/
    ├── InvisionApp.kt         Adaptive navigation (bar or rail)
    ├── navigation/            Destinations, routes and NavHost
    ├── screens/               Home, Inventory (list, detail, editor, labels), placeholders
    ├── components/            Reusable composables, barcode image, scanner launcher
    ├── format/                Date and money formatting for the UI
    └── theme/                 Material 3 theme and design tokens
app/schemas/                   Room schema history, for future migrations
```

Money is stored as whole minor units (paise) in `Money` to avoid floating-point rounding. Display currency currently defaults to Indian rupees (INR).

## Data and privacy

All data stays on the device in a Room database (`invision.db`). Android backup and device-to-device transfer are turned off for app data until the app has its own export or sync feature.

## Roadmap

1. **Foundation** — buildable project, architecture, theme, navigation shell, CI
2. **Inventory** — garments, packaged food and beverages, barcodes, labels, dashboard *(this step)*
3. Next steps cover billing, customers and settings.

## Security

Do not commit API keys, passwords or signing keys. `.gitignore` excludes keystores, `keystore.properties`, `secrets.properties` and `local.properties`.
