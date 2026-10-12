package com.invision.pos.core.auth

/**
 * Everything a signed-in person can be allowed to do, drawn from the app's actual screens and data
 * operations. There are two roles. The Super Admin holds everything. An Employee always holds the billing
 * basics ([RolePermissions.BASE]) and holds whatever else the Super Admin switched on for that one person
 * ([PermissionOptions]); nobody can hold [Permission.MANAGE_USERS] except the Super Admin.
 */
enum class Permission {
    // Billing: the new-bill workflow and reading finished invoices.
    /** Create a bill, including searching products, custom items, entering a customer and taking payment. */
    SELL,

    /** Open, print, share and reprint an existing invoice or credit note. Read-only. */
    VIEW_INVOICES,

    /** Issue a credit note (return or refund) against a finished sale. */
    PROCESS_RETURN,

    /** Count the cash at the end of the day, close the day, and read closed days. */
    CLOSE_DAY,

    // Business-wide figures.
    /** Sales totals, payment records and method totals. */
    VIEW_FINANCIALS,
    VIEW_REPORTS,

    /** The GST report and its Excel/CSV export for the accountant. Super Admin only: it cannot be given to an employee. */
    GST_REPORT,

    /** Switch loyalty points on or off and set what a point earns and is worth. Super Admin only: it cannot be given to an employee. */
    MANAGE_LOYALTY,

    /**
     * Give discounts above the limit the Super Admin sets for Employees, and change that limit. Super Admin only:
     * it cannot be given to an employee.
     */
    UNLIMITED_DISCOUNT,

    /** Sales today, stock value and insights on Home. */
    VIEW_DASHBOARD,

    // Inventory.
    MANAGE_PRODUCTS,
    ADJUST_STOCK,
    VIEW_STOCK_RECORDS,

    // Customers.
    MANAGE_CUSTOMERS,
    RECEIVE_ACCOUNT_PAYMENT,

    // Purchasing.
    MANAGE_SUPPLIERS,
    MANAGE_PURCHASES,
    RECEIVE_GOODS,

    // Restaurant menu.
    MANAGE_MENU,

    // Settings and data.
    MANAGE_SETTINGS,
    BACKUP_AND_RESTORE,

    // People.
    MANAGE_USERS,

    /** Read the activity log of changes, returns and deletions. */
    VIEW_ACTIVITY_LOG,
}

object RolePermissions {
    /** What every Employee may do, whatever else they were given. */
    val BASE: Set<Permission> = setOf(Permission.SELL, Permission.VIEW_INVOICES)

    /** The permissions held by [role], given the extras [granted] to one Employee (ignored for the Super Admin). */
    fun of(role: Role, granted: Set<Permission> = emptySet()): Set<Permission> = when (role) {
        Role.SUPER_ADMIN -> Permission.entries.toSet()
        Role.EMPLOYEE -> BASE + PermissionOptions.normalize(granted)
    }

    fun allows(role: Role, permission: Permission, granted: Set<Permission> = emptySet()): Boolean =
        permission in of(role, granted)
}

/** The sections the Super Admin sees when choosing what an Employee may do. */
enum class PermissionGroup(val permissions: List<Permission>) {
    SALES(listOf(Permission.PROCESS_RETURN, Permission.CLOSE_DAY)),
    FIGURES(listOf(Permission.VIEW_DASHBOARD, Permission.VIEW_FINANCIALS, Permission.VIEW_REPORTS)),
    INVENTORY(listOf(Permission.MANAGE_PRODUCTS, Permission.VIEW_STOCK_RECORDS, Permission.ADJUST_STOCK)),
    CUSTOMERS(listOf(Permission.MANAGE_CUSTOMERS, Permission.RECEIVE_ACCOUNT_PAYMENT)),
    PURCHASING(listOf(Permission.MANAGE_SUPPLIERS, Permission.MANAGE_PURCHASES, Permission.RECEIVE_GOODS)),
    SHOP(listOf(Permission.MANAGE_MENU, Permission.MANAGE_SETTINGS, Permission.VIEW_ACTIVITY_LOG)),
}

/**
 * The extras a Super Admin may switch on for one Employee. Billing basics are always on and are not
 * options. User Management and backup/restore are never offered: only the Super Admin holds them.
 */
object PermissionOptions {
    /** Everything that can be switched on, in the order it is shown. */
    val all: List<Permission> = PermissionGroup.entries.flatMap { it.permissions }

    private val grantable: Set<Permission> = all.toSet()

    /** An option that only works together with others: switching it on switches those on too. */
    private val needs: Map<Permission, Set<Permission>> = mapOf(
        Permission.VIEW_DASHBOARD to setOf(Permission.VIEW_FINANCIALS),
        Permission.VIEW_REPORTS to setOf(Permission.VIEW_FINANCIALS),
        Permission.MANAGE_PRODUCTS to setOf(Permission.VIEW_STOCK_RECORDS),
        Permission.ADJUST_STOCK to setOf(Permission.MANAGE_PRODUCTS, Permission.VIEW_STOCK_RECORDS),
        Permission.RECEIVE_ACCOUNT_PAYMENT to setOf(Permission.MANAGE_CUSTOMERS),
        Permission.MANAGE_PURCHASES to setOf(Permission.MANAGE_SUPPLIERS),
        Permission.RECEIVE_GOODS to setOf(Permission.MANAGE_PURCHASES, Permission.MANAGE_SUPPLIERS),
    )

    fun isGrantable(permission: Permission): Boolean = permission in grantable

    /** What [permission] needs switched on as well. */
    fun requirementsOf(permission: Permission): Set<Permission> = needs[permission].orEmpty()

    /** Drops anything that cannot be granted and adds whatever the rest needs, so a saved set always works. */
    fun normalize(requested: Set<Permission>): Set<Permission> {
        val result = requested.filterTo(mutableSetOf()) { it in grantable }
        result += result.flatMap { requirementsOf(it) }
        return result
    }

    fun switchOn(current: Set<Permission>, permission: Permission): Set<Permission> = normalize(current + permission)

    /** Switches [permission] off, and anything that needs it. */
    fun switchOff(current: Set<Permission>, permission: Permission): Set<Permission> {
        var result = current - permission
        do {
            val dropped = result.filter { p -> requirementsOf(p).any { it !in result } }
            result = result - dropped.toSet()
        } while (dropped.isNotEmpty())
        return result
    }
}
