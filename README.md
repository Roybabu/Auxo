# Invision
An invoicing app built from scratch.

Invision is an Android retail point-of-sale (POS) and invoicing app, built step by step.

**Status: Step 4 — operations, plus the additions listed under "Added since Step 4" below (version 0.4.0).** Inventory (Step 2) and billing (Step 3), plus suppliers, purchase orders and goods received, stock batches with expiry and wastage, stock movement history and alerts, customer accounts with sales on account and payments, a restaurant menu with optional recipe-based ingredient use, and filterable reports with CSV export.

## Features

### Home dashboard
- Today's net sales (sales minus refunds) and number of bills
- Inventory KPIs: products, units in stock, stock value at cost and at selling price
- Low-stock, out-of-stock and expiring counts. Tapping one opens Inventory with that filter applied.
- "Needs attention" list of the most urgent items (expired, out of stock, expiring soon, low stock), with a link to Stock alerts
- Quick actions: new bill, add product, receive stock, customers

### Billing
- **Adding items:** search by name, brand, size, colour, SKU or barcode; scan with the camera; or use a USB/Bluetooth scanner in the search box (Enter adds the item). A 12-digit UPC-A code is found even when the scanner sends it as 13 digits with a leading 0. Scanning the same item again increases its quantity. Quantities can't go above stock, and the cart updates live if stock changes elsewhere.
- **Discounts:** per line and on the whole bill, as an amount (₹) or a percentage. A bill discount is shared across lines in proportion to their value, to the paisa.
- **Customer:** optional name, mobile number and GSTIN (for B2B bills). Bills without details print "Walk-in Customer". Mobile numbers and GSTINs (including the check character) are validated. A saved customer can be found by name or mobile and put on the bill, and typed details can be saved as a new customer from the bill.
- **Restaurant dishes:** dishes from the restaurant menu are found by the same search (or picked by section) and billed alongside shop products, with their own GST rate and SAC code. Dishes taken off the menu can't be added.
- **GST** (rates as revised under GST 2.0):
  - Each product has a GST rate (0, 5, 18, 40% or another rate) and an HSN code. Garments default to the apparel rule: 5% when a piece sells for up to ₹2,500 after discount (before GST), 18% above that.
  - Products added before this step have no GST rate. A GST-registered store can't bill them until a rate is set in Inventory; the cart line says so.
  - Prices include GST by default (usual for retail and MRP items); this can be switched to GST added on top.
  - CGST and SGST for sales in the store's state; IGST when the place of supply is another state (chosen per bill).
  - Stores not registered for GST issue a "Bill of Supply" with no tax.
  - Totals can be rounded to the nearest rupee, shown as a round-off line.
- **Payments:** cash (with cash received, change and quick note buttons), UPI (shows a QR code with the amount when a UPI ID is set; optional reference), card (optional last four digits), "On account" for a saved customer (all or part of the bill goes on their account, within their credit limit), or any split of these. The sale completes only when payments add up to the total exactly.
- **Invoices:** numbered per financial year, e.g. `INV/26-27/00001` (prefix configurable, at most 16 characters as GST rules require), with date and time. Seller, buyer and product details are saved with the invoice, so later edits to products or settings don't change past bills.
- **Consistency:** the invoice, its lines, the payments, the stock deduction (from the batches that expire first, never from expired stock), the ingredients used by dishes, and the amount put on the customer's account are saved in one database transaction. If any item or ingredient is short, or the credit limit would be passed, nothing is saved and the bill says what to fix. Each bill carries a one-time id, so tapping "Complete sale" twice or retrying after an error can't save it twice. The same all-or-nothing rule applies to returns.
- **Returns:** choose units per line (up to what is left to return), refund method (including back to the customer's account for sales on account) and reason. This issues a credit note (`CN/26-27/00001`), records the refund and puts the units back in stock, into the batches they were sold from, all in one transaction. Amounts are worked out so that returning every unit, in one go or several, refunds exactly what was paid. Refunds follow the original bill's round-off: each one is what is due after the return minus what was due before, so several partial returns can't add up to more or less than the bill.
- **History:** invoices and credit notes for today, the last 7 days, this month or all time, searchable by number, customer name or phone, with sales and returns totals. A payments view lists every payment and refund, including payments received against customer accounts, with totals for cash, UPI, card, refunds and net received.
- **Receipts:** 58 mm, 76 mm (for 75–76 mm rolls) and 80 mm (for 78–80 mm rolls) thermal layouts with 32, 42 and 48 characters a line, and A5 and A4 sheets. Each invoice can be previewed in any size, printed through Android printing, or shared as a PDF (WhatsApp, email and so on). Shared PDFs are written in the app's cache and cleared after a day. Receipt options: default size, footer text, and whether to show customer details, HSN and GST per item, the GST summary, savings on MRP, and an invoice-number barcode. A4/A5 tax invoices include the amount in words and a signature block, and long invoices continue onto more pages.
- **Billing settings** (also under More): store name, address, phone, GSTIN, state, GST options, number prefixes, UPI ID and receipt options. Billing asks for the store's state and GSTIN (or "not GST-registered") before the first sale.

### Inventory
- **Garments:** pick sizes (XS–3XL or custom) and add colours. One variant is created per size and colour, each with its own barcode, SKU, prices and stock. Shared prices can be applied to every variant at once.
- **Packaged food:** net quantity (g, kg or pieces), veg or non-veg marking, batch number and expiry date. Several pack sizes can be added.
- **Beverages:** pack sizes in ml or L, with batch number and expiry date.
- **Kitchen ingredients:** counted in grams, millilitres or pieces (typed in kg, L or pieces) so a recipe can use part of a pack; priced at cost per kg, L or piece. Ingredients aren't sold at the counter.
- **Batches:** food, beverages and ingredients are kept in batches, each with its own batch number, expiry date, quantity and cost. Opening stock and every receipt become batches; the product page lists them with expired and expiring ones marked. Sales take the batch that expires first and never sell expired stock. A batch's units always add up to the item's stock.
- **Purchasing details:** reorder level, reorder quantity and preferred supplier per product.
- Search by name, brand, size, colour, SKU or barcode. Filter by category and by stock (low, out of stock, expiring within 30 days).
- Product page with variants, batches, barcodes, prices, stock status and stock history.
- **Stock adjustments:** received (into a new or existing batch), returned, sold, damaged, used in the kitchen, or a count correction. Every change is logged with time and an optional note, and stock can never go below zero. A repeated tap applies the change once.
- Editing never overwrites stock; it changes only through adjustments, receipts, sales and write-offs. A variant that still has stock can't be removed.
- Input validation for required fields, price formats, MRP ≥ selling price, whole-number quantities, barcode check digits, and duplicate barcodes, SKUs or variants (within the product and across products).
- Loading, empty, no-results and error (with retry) states on every screen.

### Suppliers and purchasing
- **Suppliers:** name, contact person, phone (landlines allowed), email, GSTIN with state check, address and notes. Suppliers are hidden rather than deleted, so past orders and receipts keep them.
- **Purchase orders** (`PO/26-27/00001`): pick a supplier, add items by search or barcode (low items get a suggested quantity), set quantities in pieces, kg or L, unit costs, a needed-by date and a note. Drafts can be edited; "Mark as ordered" sends them. An order can be shared as text (WhatsApp, SMS, email) or cancelled. The order page shows what has arrived and the value still due.
- **Goods received** (`GRN/26-27/00001`): receive against an order (quantities still due are filled in; set 0 for what didn't arrive) or without one. Food and ingredients take a batch number and expiry date per line. Receiving adds stock and batches, updates cost prices and moves the order to "Partly received" or "Received", all in one transaction.
- **Guards:** more than is still due on an order line, an order that is closed or cancelled, an item deleted in the meantime, or a supplier bill number already received from that supplier refuses the whole receipt and changes nothing. Each receipt screen has a one-time id, so saving twice receives once.

### Stock alerts, expiry and wastage
- **Low stock:** items out of stock or at their reorder level, with a suggested order (the reorder quantity, or enough to reach twice the reorder level). Chosen items become draft purchase orders, one per supplier; items without a preferred supplier go to a supplier picked on the page, or are left out and counted.
- **Expiry:** batches that have expired or expire within 7 or 30 days, with days left and value at cost.
- **Wastage:** write off part or all of a batch as expired, damaged or spoiled, with a note. Write-offs are stock movements valued at cost and appear in the wastage report.

### Stock movements
- Every stock change across products in one list: opening stock, received, returned, sold, used in the kitchen, damaged, expired, spoiled and count corrections, with the bill or receipt number and batch.
- Filters for period, kind of change and category, and search by item, batch or bill number. Totals for stock in, stock out and wastage at cost. Export as CSV.

### Customers
- Customer list with what each one owes, total due across accounts, and an "owe money" filter. Search by name, mobile or GSTIN.
- **Customer details:** name, mobile number (one customer per number), email, GSTIN, state, address, credit limit and notes. A new customer can start with an opening balance.
- **Account:** every entry (opening balance, sale on account, return, payment received) with the running balance, plus the customer's bills. The statement can be exported as CSV.
- **Payments received:** cash, UPI (with reference) or card (last four digits), never more than the balance due. A repeated tap records the payment once.

### Restaurant menu
- A menu separate from shop products: dishes and drinks in sections, with price, GST rate (5% by default) and SAC code (996331 by default), veg or non-veg marking, and an on/off switch for availability.
- **Recipes (optional):** list the ingredients a serving uses (for example 120 g flour, 30 ml milk). With "Take ingredients out of stock" on, each serving sold takes them from stock (earliest expiry first, never expired) as "Used in kitchen" movements; a bill is refused if an ingredient is short. The menu shows how many servings current stock allows.
- Returning a dish refunds it but doesn't put ingredients back.

### Reports
- Sales summary by day, item sales (products, dishes or both), collections by payment method (including account payments), GST by rate, HSN summary (for GSTR-1), purchases (by supplier), stock movements, wastage, stock value, low stock, expiry and customer balances.
- Periods: today, yesterday, last 7 days, this month, last month, this financial year, all, or a custom range. Report-specific filters for category, supplier, products or dishes, expiry window and outstanding balances. Credit notes count against sales, so figures are net of returns.
- Shown as a table that scrolls sideways, with a totals row, and exported as CSV (amounts as plain numbers, dates as yyyy-MM-dd, UTF-8 with a byte order mark for Excel, and text that could run as a spreadsheet formula escaped). Exports are written to the app's cache and shared through the share sheet; they are cleared after a day.

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

### Added since Step 4
These were built after the sections above were written. Most of them have unit and screen tests, but none has been run on a physical phone.
- **Sign-in and roles:** a Super Admin and Employees, each with a password. The Super Admin chooses which extras an Employee gets (returns, day-end, figures, inventory, customers, purchasing, menu, settings, activity log); billing basics are always on. Wrong passwords and wrong recovery codes are counted and slow guessing down (30 seconds, doubling to 5 minutes after 5 tries); the counts are kept on the phone, so closing the app does not reset them. A recovery code lets the Super Admin reset a forgotten password. The app locks after an idle time the Super Admin sets, and can be unlocked with a PIN or the fingerprint. An activity log records changes, returns and deletions.
- **Billing:** hold and resume bills, quotations, discounts with an Employee limit, loyalty points, a UPI payment QR on receipts, sharing an invoice on WhatsApp, and a restaurant mode that can be switched on or off.
- **Stock:** expiry warnings with a number of days the shop chooses, reorder drafts per supplier, stock counts, product import from a CSV file, and a photo on each product (gallery or camera).
- **Day-end and GST:** day-end closing with the cash counted, and a GST report.
- **Backup:** backup and restore of the whole shop to a file, and automatic backups to a folder the person chooses. Only the Super Admin can use them.
- **Bluetooth receipt printer:** pick a paired 58, 76 or 80 mm ESC/POS printer under More, print a test receipt, and print any invoice from its screen. This has never been tried on a real printer; see "Printing and barcode limits".

### Design
- Material 3 theme with emerald accents on light surfaces, dark text, and rounded shapes.
- Shared design tokens: spacing scale and status colours (success, warning, danger), plus reusable components such as stat tiles, status chips, cards, text fields and state views.
- Five-destination navigation that adapts: bottom bar on phones, side rail on tablets. Stock and purchasing pages are reached from the Inventory menu and the More tab; the restaurant menu and reports from More.

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
- **Step 4 domain (plain JVM):** batch allocation (earliest expiry first, returns back into the batches sold from), stock units and costs per kg or L, expired and sellable stock, reorder suggestions and draft orders per supplier, item search, purchase order and goods receipt validation (quantities, costs, over-receipts, duplicates, bill numbers, batch and expiry), purchase numbers and order text, ledger balances and running statements, account payment checks, customer and supplier validation, menu and recipe validation, every report builder, CSV escaping and formats, report periods and expiry windows.
- **ViewModels** (with in-memory fakes): Home, Inventory, Product detail and editor, Labels, Billing (search, scanning, stock limits, discounts, customer checks, setup, cash/split/under-payment, stock sold elsewhere, place of supply, dishes, linking a saved customer, sales on account, saving a typed customer), Returns, History, Billing settings, Suppliers, Purchase orders, Receiving goods, Stock alerts and write-offs, Stock movements, Menu and dish editor, Reports, Customers, Customer account and payments.
- **Database** (Robolectric, Room in memory): sales save everything and deduct stock; a short item saves nothing and doesn't use up an invoice number; numbering per prefix and financial year; returns restock, mark lines and record refunds, and over-returns change nothing; partial returns of a rounded bill refund exactly what was paid; deleting a product keeps its invoices; period totals and payments; settings; checkout and return use cases end to end. Migration tests build version 1, 2 and 4 databases from the committed schemas, fill them with earlier data, and open them with the current app (existing food stock becomes batches; the 4 to 15 test runs every later hand-written migration and Room checks the result against the version 15 schema). Schema files for versions 6 to 14 are not committed and are not needed by any test or CI check.
- **Stock and ledger correctness** (Robolectric, Room in memory): after every operation each item's stock equals the sum of its movements and, for batched items, the sum of its batches. Covered: sales across batches, expired stock refused, write-offs (more than a batch holds changes nothing; a repeated request writes off once), returns into the right batches, repeated stock changes, ingredient stock in grams; purchase order numbering and draft rules, partial and full receipts, over-receipts, duplicate supplier bills with and without an order, closed and cancelled orders, deleted items rolling back a receipt; opening balances, one customer per mobile number, sales on account and the credit limit, payments above the balance refused and repeated payments counted once, refunds to the account; dishes using their recipes, ingredient shortages and expired ingredients refusing the bill, dish returns, recipe edits and deletions.
- **Data integrity** (Robolectric + plain JVM): a reconciliation helper checks the books after each scenario: stock against movements and batches, no negative stock, payments against invoice totals, invoice totals against lines, returned units against credit notes, refunds against sales, sales on account against the ledger, no repeated invoice numbers, no orphaned rows. Rollback tests show that a short line, a credit limit, an invoice that doesn't balance, a return for another sale's line, a return with one bad line and an over-received purchase line each leave nothing behind (no invoice, payment, stock movement, ledger entry, number or request id used). Repeated sale, return and receipt requests save once, also when sent at the same moment. Seeded random bills (1,000 sales in every tax mode with line and bill discounts, GST slabs, inter-state and split payments) add up to the paisa, and 500 sales returned unit by unit in any order refund exactly the bill.
- **UI** (Robolectric + Compose): every Inventory and Billing screen and the Step 4 pages (customers, account payment, suppliers, purchase order editor and detail, receiving goods, stock alerts, write-off form, stock movements, menu, dish editor, reports and More) with sample data, receipts rendered for 58 mm, 76 mm, 80 mm, A5 and A4 (sale and credit note), a long A5 invoice spanning pages, and end-to-end flows through the real app and database. Screenshots are saved to `app/build/outputs/screenshots/`.

Robolectric runs on Android 15 (API 35); see `app/src/test/resources/robolectric.properties`.

**Device tests** (`app/src/androidTest`, run on an Android 15 emulator in CI) cover what Robolectric can't:
- Receipt PDFs for every paper size written with `PdfDocument` and read back with `PdfRenderer` (page count, page size, printed content), and long A5/A4 invoices continuing onto more pages.
- Sharing: the PDF reaches the receiving app as a readable `content://` file through the FileProvider.
- A full sale and return through the app: an item added from search and another by sending its barcode digits and Enter as key presses (the way a USB or Bluetooth scanner types), cash payment, the system share sheet and print dialog opening, and a return; stock, invoice, payment and refund are checked in the database at each step.
- The stock adjustment dialog (text fields in a dialog never report idle under Robolectric).
- Receiving a purchase order through the app (order list, order page, receive screen with a supplier bill number), checking stock, cost price, order status and the receipt in the database.
- Adding a customer with an opening balance and recording a payment against the account.

**Coverage.** A separate CI job ("Unit test coverage", reported only, it never fails the build) runs the unit tests with JaCoCo and prints line and branch coverage per package. At the last run (397 unit tests): billing rules 98.5% of lines, inventory rules 98.6%, purchasing rules 98.3%, customer ledger rules 100%, reports 92.6%, use cases 89.6%, view models 82.0%. **The database layer (`data/repository`, `data/local`) shows about 0-5%, which is not real:** its tests run inside Robolectric's own class loader, which JaCoCo does not see. Those classes are exercised by the Room tests listed above (and the reconciliation checks), but their coverage cannot be measured with this setup. Compose screens are likewise tested under Robolectric and are not part of the figures. Run it by hand with `./gradlew -Pcoverage createDebugUnitTestCoverageReport` (the report is in `app/build/reports/coverage/`).

Known limits in billing and ledgers:
- Suppliers have no balance or payments: purchase receipts record stock and cost, but what is owed to a supplier is not tracked.
- A bill that comes to nothing (for example a 100% discount) can't be completed, because every payment line must be more than zero.
- A refund on a bill that was put on a customer's account can be paid out in cash; the ledger only changes when the refund is sent back to the account. Payments are not matched to individual bills.
- Refunds can't be traced to the payment methods used on the original sale (a UPI sale can be refunded in cash).

Not covered by automated tests:
- Camera scanning (needs Google Play services and a camera).
- Output on a real printer: the print dialog is opened with the receipt, but nothing is printed. The Bluetooth receipt printer is tested only against a fake connection (the sending logic, failures and the ESC/POS bytes), never on a real printer.
- A physical phone: the device tests run on an emulator.
- Opening exported CSV files in a spreadsheet app: the file contents are tested, and the share sheet path is the same one receipts use.

## Continuous integration

`.github/workflows/android-ci.yml` runs on every push to `main`, on pull requests, and on demand from the Actions tab. It builds the debug APK, runs all tests and lint, measures unit test coverage in a separate job, and reports results as annotations on the run and pull request. Compiler errors, failed tests and compiler warnings appear there too, so failures can be read without opening the logs. CI also fails if the Room schema changes without the new schema being committed in `app/schemas`; the new schema file is then attached to the run as a gzip+base64 annotation, so it can be committed even without a local Android build. The APK, test and lint reports, screenshots and the Room schema are uploaded as workflow artifacts. A `security` job scans the repository and builds an unsigned release variant. A further job runs the device tests on an Android 15 emulator and reports their results the same way.

## Project structure

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
│   └── repository/            Repository interfaces and implementations
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
    ├── navigation/            Destinations, routes and NavHost
    ├── screens/               Home, Billing (cart, payment, invoice, history, returns, settings), Inventory, Customers,
    │                          Purchasing (suppliers, orders, receiving), Stock (alerts, movements), Menu, Reports, More
    ├── components/            Reusable composables, barcode image, scanner launcher
    ├── format/                Date and money formatting for the UI
    └── theme/                 Material 3 theme and design tokens
app/src/test/                  Unit, database (Robolectric) and UI tests
app/src/androidTest/           Device tests, run on an emulator in CI
app/schemas/                   Room schema history, for future migrations
```

Money is stored as whole minor units (paise) in `Money` to avoid floating-point rounding. Display currency currently defaults to Indian rupees (INR).

## Supported platforms

Android 8.0 (API 26) and newer, phones and tablets (the navigation switches between bar and rail). Nothing else is built: no iOS, web or desktop target. The only environment where the app has been run is the Android 15 emulator in CI; it has not been tried on a physical phone.

## Data and privacy

- **Persistence:** all data stays on the device in a Room (SQLite) database, `invision.db`, schema version 15. There is no server or sync, and the app's own code makes no network calls. Sign-in accounts are local, in a file in the app's private storage (passwords are stored only as salted hashes). The manifest asks for Bluetooth permission (`BLUETOOTH` up to Android 11, `BLUETOOTH_CONNECT` from Android 12) for the receipt printer. Libraries (the Google code scanner) may add their own, such as network access; the merged list is checked in CI but its exact contents were not read from a log.
- **Not encrypted:** the database sits in the app's private storage, which other apps cannot read on an unmodified device, but it is not encrypted. Anyone with a rooted device or a full-disk image can read it.
- **Backup:** Android's own backup and device-to-device transfer are turned off for app data. The app has its own backup and restore (More, Backup) and can back up automatically to a chosen folder. Backup files are not encrypted. Uninstalling the app, clearing its data or losing the phone loses everything that has not been backed up to somewhere else.
- **Temporary files:** receipt PDFs and CSV exports are written to the app's cache only when sharing, are shared through a `FileProvider` with a read grant for the chosen app, and are cleared after a day.
- **Logging:** the app writes nothing to the system log.

## Printing and barcode limits

- Receipts and labels can be opened in Android's print dialog as PDFs. Receipts can also be sent straight to a paired Bluetooth thermal printer (classic Bluetooth serial, ESC/POS, printed as a picture so Hindi and Bengali text works). Bluetooth LE-only printers are not supported. The chunk size and pauses used when sending are untested guesses, and a printer that needs a different connection method may not work. Output on a real printer has not been checked. Labels still use Android printing only.
- Camera scanning uses Google's code scanner and needs Google Play services. Without it, type the code or use a hardware scanner that types as a keyboard. Camera scanning has not been tested automatically.
- Generated barcodes follow the GS1 check-digit rules; scan quality of printed labels depends on the printer and paper and has not been tested.

## Known issues

- No supplier balances: purchases record goods received, not money owed.
- A bill with a total of zero cannot be completed.
- A cash refund on a bill that was sold on account does not change the customer's ledger.
- The refund method is not tied to how the original bill was paid.
- Database layer code is not in the coverage figures, because Robolectric loads it in a way JaCoCo cannot see.
- No code formatter or Kotlin static analyser (ktlint, detekt) is set up; only Android lint runs.
- The release build is not minified or signed, and has only been checked to compile in CI.
- The Bluetooth printer has never been tried on a real printer, and the permission prompt and file pickers have not been checked on a physical phone.
- The Hindi and Bengali texts have not been reviewed by a native speaker.
- The database and backup files are not encrypted.
- Some files are very large (for example `BillingScreen.kt` and `BillingViewModel.kt`, each over 1,000 lines).

## Languages and fonts

The app and the bills can be in English, Hindi or Bengali. The app language is a per-device setting (Appearance); the bill language is a shop setting (Billing settings) so every phone prints the same bill. Product and customer names are always printed as entered. Receipt and PDF text in Hindi and Bengali is drawn with Noto Sans Devanagari and Noto Sans Bengali (Regular and Bold) bundled in `app/src/main/assets/fonts`, licensed under the SIL Open Font License 1.1 (`OFL.txt` in the same folder), so it does not depend on the fonts a phone has.

## Roadmap

1. **Foundation** — buildable project, architecture, theme, navigation shell, CI
2. **Inventory** — garments, packaged food and beverages, barcodes, labels, dashboard
3. **Billing** — cart, discounts, GST, payments, returns, invoice history, receipts
4. **Operations** — suppliers, purchase orders and goods received, batches, expiry and wastage, stock movements and alerts, customer accounts, restaurant menu and recipes, reports and CSV export *(this step)*
5. Next step to be defined.

## Security

Do not commit API keys, passwords or signing keys. `.gitignore` excludes keystores, `keystore.properties`, `secrets.properties` and `local.properties`. The `security` CI job runs `.github/scripts/security_check.py` over every tracked file (secret patterns, secret-type file names, unsafe manifest settings such as backup or cleartext traffic, workflow permissions) and builds an unsigned release APK; the `build` job lists the permissions of the merged manifest and fails on unexpected ones. Workflows run with read-only `contents` permission and use no repository secrets. Dependabot proposes monthly updates for Gradle dependencies and GitHub Actions.
