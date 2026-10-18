package com.auxo.pos.domain.imports

import com.auxo.pos.domain.model.DietType
import com.auxo.pos.domain.model.GstRate
import com.auxo.pos.domain.model.Product
import com.auxo.pos.domain.model.ProductCategory
import com.auxo.pos.domain.model.QuantityUnit
import com.auxo.pos.domain.model.StockUnit
import com.auxo.pos.domain.model.parseRatePercent
import com.auxo.pos.domain.validation.FieldError
import com.auxo.pos.domain.validation.FormField
import com.auxo.pos.domain.validation.GstChoice
import com.auxo.pos.domain.validation.ProductForm
import com.auxo.pos.domain.validation.ProductFormValidator
import com.auxo.pos.domain.validation.ProductValidation
import com.auxo.pos.domain.validation.VariantField
import com.auxo.pos.domain.validation.VariantForm
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.ResolverStyle

/** A column of the product import file. [key] is the header written in the template. */
enum class ImportColumn(val key: String, val required: Boolean = false, vararg aliases: String) {
    NAME("name", true, "product", "productname", "item", "itemname", "title"),
    CATEGORY("category", false, "type", "producttype", "group"),
    BRAND("brand", false, "company", "make", "manufacturer"),
    DESCRIPTION("description", false, "details", "notes"),
    MATERIAL("material", false, "fabric"),
    DIET("diet", false, "diettype", "vegnonveg", "veg"),
    GST_RATE("gst_rate", false, "gst", "gstpercent", "gstpct", "tax", "taxrate"),
    HSN("hsn", false, "hsncode", "hsnsac"),
    SIZE("size", false),
    COLOUR("colour", false, "color"),
    NET_QUANTITY("net_quantity", false, "netqty", "netweight", "weight", "volume", "packsize"),
    UNIT("unit", false, "netunit", "packunit"),
    SELLING_PRICE("selling_price", false, "price", "sellingprice", "sp", "rate", "saleprice"),
    MRP("mrp", false, "maxretailprice", "listprice"),
    COST_PRICE("cost_price", false, "cost", "costprice", "purchaseprice", "buyprice"),
    OPENING_STOCK("opening_stock", false, "stock", "qty", "quantity", "openingqty", "onhand"),
    LOW_STOCK("low_stock", false, "lowstockalert", "minstock", "reorderlevel", "lowstocklevel"),
    BARCODE("barcode", false, "ean", "upc", "code"),
    SKU("sku", false, "itemcode", "productcode"),
    BATCH("batch", false, "batchno", "batchnumber", "lot"),
    EXPIRY_DATE("expiry_date", false, "expiry", "expirydate", "expdate", "bestbefore", "useby"),
    ;

    private val matches: Set<String> = (aliases.toList() + key).map(::normalizeImportHeader).toSet()

    fun matchesHeader(header: String): Boolean = normalizeImportHeader(header) in matches
}

/**
 * Lower case letters and digits only, so "Selling Price", "selling_price" and "SELLING-PRICE" compare equal.
 * (Top level, not in the enum's companion, which would not exist yet while the enum's entries are being built.)
 */
internal fun normalizeImportHeader(text: String): String = text.lowercase().filter { it.isLetterOrDigit() }

/** Which file column holds each [ImportColumn]; columns left out are read as blank. */
data class ColumnMapping(val indexes: Map<ImportColumn, Int> = emptyMap()) {
    fun indexOf(column: ImportColumn): Int? = indexes[column]

    fun with(column: ImportColumn, index: Int?): ColumnMapping =
        ColumnMapping(if (index == null) indexes - column else indexes + (column to index))

    /** The file can be read: it has the columns without which no product can be made. */
    val isComplete: Boolean get() = ImportColumn.entries.filter { it.required }.all { it in indexes }

    companion object {
        /** Matches each column to the first file column whose heading looks like it. A file column is used once. */
        fun auto(header: List<String>): ColumnMapping {
            val used = mutableSetOf<Int>()
            val found = mutableMapOf<ImportColumn, Int>()
            for (column in ImportColumn.entries) {
                val index = header.indices.firstOrNull { it !in used && column.matchesHeader(header[it]) } ?: continue
                used += index
                found[column] = index
            }
            return ColumnMapping(found)
        }
    }
}

/** What to do with cells the file leaves blank. */
data class ImportOptions(
    /** Category for rows whose category cell is blank (or whose column is not mapped); null makes such rows invalid. */
    val defaultCategory: ProductCategory? = null,
)

/** A problem that is not one of the product form's own errors. */
enum class ImportParseError {
    UNKNOWN_CATEGORY,
    UNKNOWN_DIET,
    UNKNOWN_UNIT,
    INVALID_DATE,
}

/** Either a [FieldError] the product form's rules found, or an [ImportParseError] found while reading the file. */
data class ImportIssue(
    val rowNumber: Int,
    val column: ImportColumn?,
    val fieldError: FieldError? = null,
    val parseError: ImportParseError? = null,
)

enum class ImportStatus { READY, INVALID, EXISTS }

/**
 * One product to be created: every file row with the same name and category is one variant of it.
 * [product] is set only when the status is [ImportStatus.READY].
 */
data class ImportItem(
    val name: String,
    val category: ProductCategory?,
    val rowNumbers: List<Int>,
    val status: ImportStatus,
    val issues: List<ImportIssue> = emptyList(),
    val product: Product? = null,
) {
    val variantCount: Int get() = rowNumbers.size
}

data class ImportPreview(val items: List<ImportItem>, val rowCount: Int) {
    val ready: List<ImportItem> get() = items.filter { it.status == ImportStatus.READY }
    val invalid: List<ImportItem> get() = items.filter { it.status == ImportStatus.INVALID }
    val existing: List<ImportItem> get() = items.filter { it.status == ImportStatus.EXISTS }
    val readyVariants: Int get() = ready.sumOf { it.variantCount }
    val canImport: Boolean get() = ready.isNotEmpty()
}

/** The file people fill in: its heading row and a few example rows to copy the style from. */
object ProductImportTemplate {
    const val FILE_NAME = "auxo-products-template.csv"

    val header: String get() = ImportColumn.entries.joinToString(",") { it.key }

    private fun row(vararg values: Pair<ImportColumn, String>): String {
        val byColumn = values.toMap()
        return ImportColumn.entries.joinToString(",") { csvCell(byColumn[it].orEmpty()) }
    }

    fun csv(): String = listOf(
        header,
        row(
            ImportColumn.NAME to "Cotton T-Shirt", ImportColumn.CATEGORY to "Garment", ImportColumn.BRAND to "Acme",
            ImportColumn.MATERIAL to "Cotton", ImportColumn.GST_RATE to "Apparel", ImportColumn.HSN to "6109",
            ImportColumn.SIZE to "M", ImportColumn.COLOUR to "Blue", ImportColumn.SELLING_PRICE to "499",
            ImportColumn.MRP to "599", ImportColumn.COST_PRICE to "250", ImportColumn.OPENING_STOCK to "10",
            ImportColumn.LOW_STOCK to "3", ImportColumn.SKU to "TS-BLU-M",
        ),
        row(
            ImportColumn.NAME to "Cotton T-Shirt", ImportColumn.CATEGORY to "Garment", ImportColumn.BRAND to "Acme",
            ImportColumn.MATERIAL to "Cotton", ImportColumn.GST_RATE to "Apparel", ImportColumn.HSN to "6109",
            ImportColumn.SIZE to "L", ImportColumn.COLOUR to "Blue", ImportColumn.SELLING_PRICE to "499",
            ImportColumn.MRP to "599", ImportColumn.COST_PRICE to "250", ImportColumn.OPENING_STOCK to "8",
            ImportColumn.LOW_STOCK to "3", ImportColumn.SKU to "TS-BLU-L",
        ),
        row(
            ImportColumn.NAME to "Glucose Biscuits", ImportColumn.CATEGORY to "Packaged food", ImportColumn.BRAND to "Tasty",
            ImportColumn.DIET to "Veg", ImportColumn.GST_RATE to "18", ImportColumn.HSN to "1905",
            ImportColumn.NET_QUANTITY to "200", ImportColumn.UNIT to "g", ImportColumn.SELLING_PRICE to "20",
            ImportColumn.MRP to "22", ImportColumn.COST_PRICE to "16", ImportColumn.OPENING_STOCK to "48",
            ImportColumn.LOW_STOCK to "12", ImportColumn.BATCH to "B-101", ImportColumn.EXPIRY_DATE to "2027-03-31",
        ),
        row(
            ImportColumn.NAME to "Mango Juice", ImportColumn.CATEGORY to "Beverage", ImportColumn.GST_RATE to "12",
            ImportColumn.NET_QUANTITY to "1", ImportColumn.UNIT to "L", ImportColumn.SELLING_PRICE to "95",
            ImportColumn.MRP to "99", ImportColumn.OPENING_STOCK to "24", ImportColumn.EXPIRY_DATE to "31/12/2026",
        ),
    ).joinToString("\r\n") + "\r\n"

    /** Quotes a cell when it holds a comma, quote or line break. */
    fun csvCell(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) "\"" + value.replace("\"", "\"\"") + "\"" else value
}

/** Turns the rows of a CSV file into products, using the same rules the product editor applies. */
object ProductImport {

    // Strict, so 31/02/2027 is refused instead of being moved to 28 February.
    private val DATE_FORMATS = listOf("uuuu-M-d", "d/M/uuuu", "d-M-uuuu", "d.M.uuuu")
        .map { DateTimeFormatter.ofPattern(it).withResolverStyle(ResolverStyle.STRICT) }

    /**
     * Checks every row and groups them into products. Nothing is saved. A product that already exists (same name
     * and category) is left alone, so importing the same file twice does not duplicate anything.
     */
    fun preview(table: CsvTable, mapping: ColumnMapping, options: ImportOptions, existing: List<Product>): ImportPreview {
        val reader = RowReader(mapping)
        val items = ArrayList<ImportItem>()
        val groups = LinkedHashMap<Pair<String, ProductCategory>, MutableList<CsvRow>>()

        for (row in table.rows) {
            val name = reader.text(row, ImportColumn.NAME)
            if (name.isEmpty()) {
                items += ImportItem("", null, listOf(row.number), ImportStatus.INVALID, listOf(ImportIssue(row.number, ImportColumn.NAME, FieldError.REQUIRED)))
                continue
            }
            val categoryText = reader.text(row, ImportColumn.CATEGORY)
            val category = if (categoryText.isEmpty()) options.defaultCategory else parseCategory(categoryText)
            if (category == null) {
                val issue = if (categoryText.isEmpty()) {
                    ImportIssue(row.number, ImportColumn.CATEGORY, FieldError.REQUIRED)
                } else {
                    ImportIssue(row.number, ImportColumn.CATEGORY, parseError = ImportParseError.UNKNOWN_CATEGORY)
                }
                items += ImportItem(name, null, listOf(row.number), ImportStatus.INVALID, listOf(issue))
                continue
            }
            groups.getOrPut(name.lowercase() to category) { mutableListOf() } += row
        }

        val existingKeys = existing.map { it.name.trim().lowercase() to it.category }.toSet()
        val takenBarcodes = existing.flatMap { it.variants }.map { it.barcode }.filter { it.isNotBlank() }.toSet()
        val takenSkus = existing.flatMap { it.variants }.mapNotNull { it.sku }.map { it.lowercase() }.toSet()

        // Products to check, with the barcodes and SKUs their rows use (to find repeats between products).
        class Candidate(val key: Pair<String, ProductCategory>, val rows: List<CsvRow>, val build: BuiltForm)

        val candidates = ArrayList<Candidate>()
        val groupItems = HashMap<Pair<String, ProductCategory>, ImportItem>()
        for ((key, rows) in groups) {
            val displayName = reader.text(rows.first(), ImportColumn.NAME)
            if (key in existingKeys) {
                groupItems[key] = ImportItem(displayName, key.second, rows.map { it.number }, ImportStatus.EXISTS)
                continue
            }
            candidates += Candidate(key, rows, buildForm(reader, key.second, rows))
        }

        val barcodeOwners = HashMap<String, MutableSet<Pair<String, ProductCategory>>>()
        val skuOwners = HashMap<String, MutableSet<Pair<String, ProductCategory>>>()
        for (candidate in candidates) {
            for (variant in candidate.build.form.variants) {
                variant.barcode.trim().takeIf { it.isNotEmpty() }?.let { barcodeOwners.getOrPut(it) { mutableSetOf() } += candidate.key }
                variant.sku.trim().lowercase().takeIf { it.isNotEmpty() }?.let { skuOwners.getOrPut(it) { mutableSetOf() } += candidate.key }
            }
        }

        for (candidate in candidates) {
            val build = candidate.build
            val issues = build.issues.toMutableList()
            val firstRow = candidate.rows.first().number
            val parsed = issues.mapNotNull { issue -> issue.column?.let { issue.rowNumber to it } }.toSet()

            when (val result = ProductFormValidator.validate(build.form)) {
                is ProductValidation.Valid -> Unit
                is ProductValidation.Invalid -> {
                    result.errors.fields.forEach { (field, error) ->
                        val column = field.column()
                        if (firstRow to column !in parsed) issues += ImportIssue(firstRow, column, error)
                    }
                    for ((variantKey, errors) in result.errors.variants) {
                        val rowNumber = variantKey.removePrefix("r").toInt()
                        errors.forEach { (field, error) ->
                            val column = field.column()
                            if (rowNumber to column !in parsed) issues += ImportIssue(rowNumber, column, error)
                        }
                    }
                }
            }

            // A product made of rows that cannot be told apart would become identical variants.
            val category = candidate.key.second
            if (!category.usesSizeAndColour && !category.usesNetQuantity && candidate.rows.size > 1) {
                candidate.rows.drop(1).forEach { issues += ImportIssue(it.number, ImportColumn.NAME, FieldError.DUPLICATE_VARIANT) }
            }

            for (variant in build.form.variants) {
                val rowNumber = variant.key.removePrefix("r").toInt()
                val barcode = variant.barcode.trim()
                if (barcode.isNotEmpty()) {
                    if (barcode in takenBarcodes) issues += ImportIssue(rowNumber, ImportColumn.BARCODE, FieldError.BARCODE_IN_USE)
                    // Repeats inside one product are found by the form's own rules; here, repeats between products.
                    if (barcodeOwners[barcode].orEmpty().size > 1) issues += ImportIssue(rowNumber, ImportColumn.BARCODE, FieldError.DUPLICATE_BARCODE)
                }
                val sku = variant.sku.trim().lowercase()
                if (sku.isNotEmpty()) {
                    if (sku in takenSkus) issues += ImportIssue(rowNumber, ImportColumn.SKU, FieldError.SKU_IN_USE)
                    if (skuOwners[sku].orEmpty().size > 1) issues += ImportIssue(rowNumber, ImportColumn.SKU, FieldError.DUPLICATE_SKU)
                }
            }

            val distinct = issues.distinct().sortedBy { it.rowNumber }
            val rowNumbers = candidate.rows.map { it.number }
            val displayName = reader.text(candidate.rows.first(), ImportColumn.NAME)
            groupItems[candidate.key] = if (distinct.isEmpty()) {
                val product = (ProductFormValidator.validate(build.form) as ProductValidation.Valid).product
                ImportItem(product.name, category, rowNumbers, ImportStatus.READY, product = product)
            } else {
                ImportItem(displayName, category, rowNumbers, ImportStatus.INVALID, distinct)
            }
        }

        val ordered = items + groups.keys.mapNotNull { groupItems[it] }
        return ImportPreview(ordered.sortedBy { it.rowNumbers.first() }, table.rows.size)
    }

    private class BuiltForm(val form: ProductForm, val issues: List<ImportIssue>)

    private fun buildForm(reader: RowReader, category: ProductCategory, rows: List<CsvRow>): BuiltForm {
        val issues = mutableListOf<ImportIssue>()
        val first = rows.first()
        fun issue(row: CsvRow, column: ImportColumn, error: ImportParseError) {
            issues += ImportIssue(row.number, column, parseError = error)
        }

        // Product-level values come from the product's first row.
        val dietText = reader.text(first, ImportColumn.DIET)
        val diet = if (dietText.isEmpty() || category != ProductCategory.PACKAGED_FOOD) {
            null
        } else {
            parseDiet(dietText).also { if (it == null) issue(first, ImportColumn.DIET, ImportParseError.UNKNOWN_DIET) }
        }

        var gst = GstChoice.defaultFor(category)
        var customRate = ""
        val gstText = reader.text(first, ImportColumn.GST_RATE)
        if (gstText.isNotEmpty()) {
            val cleaned = gstText.removeSuffix("%").trim()
            if (cleaned.equals("apparel", ignoreCase = true)) {
                gst = GstChoice.Apparel
            } else {
                val basisPoints = parseRatePercent(cleaned)
                if (basisPoints == null) {
                    issues += ImportIssue(first.number, ImportColumn.GST_RATE, FieldError.INVALID_GST_RATE)
                } else if (basisPoints in GstRate.STANDARD_BASIS_POINTS) {
                    gst = GstChoice.Standard(basisPoints)
                } else {
                    gst = GstChoice.Custom
                    customRate = cleaned
                }
            }
        }

        val stockUnit = StockUnit.UNIT

        val variants = rows.map { row ->
            val unitText = reader.text(row, ImportColumn.UNIT)
            val unit = if (unitText.isEmpty()) null else parseUnit(unitText)
            if (unitText.isNotEmpty() && unit == null) issue(row, ImportColumn.UNIT, ImportParseError.UNKNOWN_UNIT)

            val expiryText = reader.text(row, ImportColumn.EXPIRY_DATE)
            val expiry = if (expiryText.isEmpty()) null else parseDate(expiryText)
            if (expiryText.isNotEmpty() && expiry == null) issue(row, ImportColumn.EXPIRY_DATE, ImportParseError.INVALID_DATE)

            VariantForm(
                key = "r${row.number}",
                size = reader.text(row, ImportColumn.SIZE),
                colour = reader.text(row, ImportColumn.COLOUR),
                netQuantity = reader.number(row, ImportColumn.NET_QUANTITY),
                unit = unit,
                sellingPrice = reader.number(row, ImportColumn.SELLING_PRICE),
                mrp = reader.number(row, ImportColumn.MRP),
                costPrice = reader.number(row, ImportColumn.COST_PRICE),
                openingStock = reader.quantity(row, ImportColumn.OPENING_STOCK, stockUnit),
                lowStockThreshold = reader.quantity(row, ImportColumn.LOW_STOCK, stockUnit),
                barcode = reader.text(row, ImportColumn.BARCODE),
                sku = reader.text(row, ImportColumn.SKU),
                batchNumber = reader.text(row, ImportColumn.BATCH),
                expiryDate = expiry,
            )
        }

        val form = ProductForm(
            category = category,
            name = reader.text(first, ImportColumn.NAME),
            brand = reader.text(first, ImportColumn.BRAND),
            description = reader.text(first, ImportColumn.DESCRIPTION),
            material = reader.text(first, ImportColumn.MATERIAL),
            dietType = diet,
            gst = gst,
            customGstRate = customRate,
            hsnCode = reader.text(first, ImportColumn.HSN),
            sizes = variants.map { it.size }.filter { it.isNotBlank() }.distinct(),
            colours = variants.map { it.colour }.filter { it.isNotBlank() }.distinct(),
            variants = variants,
            stockUnit = stockUnit,
        )
        return BuiltForm(form, issues)
    }

    private class RowReader(private val mapping: ColumnMapping) {
        fun text(row: CsvRow, column: ImportColumn): String =
            mapping.indexOf(column)?.let { row.cells.getOrNull(it) }?.trim().orEmpty()

        /** A money or quantity cell, with a rupee sign, "Rs" and thousands commas (1,250.50) removed. */
        fun number(row: CsvRow, column: ImportColumn): String {
            val raw = text(row, column).removePrefix("₹").removePrefix("Rs.").removePrefix("Rs").trim()
            return if (THOUSANDS.matches(raw)) raw.replace(",", "") else raw
        }

        /** Stock cells; whole pieces may be written "10.0" by spreadsheets. */
        fun quantity(row: CsvRow, column: ImportColumn, unit: StockUnit): String {
            val value = number(row, column)
            return if (unit == StockUnit.UNIT && WHOLE_DECIMAL.matches(value)) value.substringBefore('.') else value
        }
    }

    private val THOUSANDS = Regex("""\d{1,3}(,\d{3})+(\.\d+)?""")
    private val WHOLE_DECIMAL = Regex("""\d+\.0+""")

    fun parseCategory(text: String): ProductCategory? = when (normalizeImportHeader(text)) {
        "garment", "garments", "clothing", "clothes", "apparel", "cloth" -> ProductCategory.GARMENT
        "packagedfood", "packaged", "food", "grocery", "snack", "snacks" -> ProductCategory.PACKAGED_FOOD
        "beverage", "beverages", "drink", "drinks" -> ProductCategory.BEVERAGE
        else -> null
    }

    fun parseDiet(text: String): DietType? = when (normalizeImportHeader(text)) {
        "veg", "vegetarian", "v" -> DietType.VEG
        "nonveg", "nonvegetarian", "nv" -> DietType.NON_VEG
        else -> null
    }

    fun parseUnit(text: String): QuantityUnit? = when (normalizeImportHeader(text)) {
        "g", "gm", "gms", "gram", "grams" -> QuantityUnit.GRAM
        "kg", "kgs", "kilogram", "kilograms" -> QuantityUnit.KILOGRAM
        "ml", "millilitre", "milliliter", "millilitres", "milliliters" -> QuantityUnit.MILLILITRE
        "l", "ltr", "litre", "liter", "litres", "liters" -> QuantityUnit.LITRE
        "pcs", "pc", "piece", "pieces", "nos", "no" -> QuantityUnit.PIECE
        else -> null
    }

    /** Day first (31/12/2026), as written in India, or year first (2026-12-31). */
    fun parseDate(text: String): LocalDate? {
        val value = text.trim()
        for (format in DATE_FORMATS) {
            try {
                return LocalDate.parse(value, format)
            } catch (_: DateTimeParseException) {
                // try the next format
            }
        }
        return null
    }

    private fun FormField.column(): ImportColumn = when (this) {
        // Restaurant items are not imported, so a subcategory error cannot come from a file; point it at the name.
        FormField.NAME, FormField.VARIANTS, FormField.SUBCATEGORY -> ImportColumn.NAME
        FormField.BRAND -> ImportColumn.BRAND
        FormField.DESCRIPTION -> ImportColumn.DESCRIPTION
        FormField.MATERIAL -> ImportColumn.MATERIAL
        FormField.SIZES -> ImportColumn.SIZE
        FormField.COLOURS -> ImportColumn.COLOUR
        FormField.GST_RATE -> ImportColumn.GST_RATE
        FormField.HSN -> ImportColumn.HSN
    }

    private fun VariantField.column(): ImportColumn = when (this) {
        VariantField.SIZE -> ImportColumn.SIZE
        VariantField.COLOUR -> ImportColumn.COLOUR
        VariantField.NET_QUANTITY -> ImportColumn.NET_QUANTITY
        VariantField.UNIT -> ImportColumn.UNIT
        VariantField.SELLING_PRICE -> ImportColumn.SELLING_PRICE
        VariantField.MRP -> ImportColumn.MRP
        VariantField.COST_PRICE -> ImportColumn.COST_PRICE
        VariantField.OPENING_STOCK -> ImportColumn.OPENING_STOCK
        VariantField.LOW_STOCK -> ImportColumn.LOW_STOCK
        VariantField.REORDER -> ImportColumn.LOW_STOCK
        VariantField.BARCODE -> ImportColumn.BARCODE
        VariantField.SKU -> ImportColumn.SKU
        VariantField.BATCH -> ImportColumn.BATCH
    }
}
