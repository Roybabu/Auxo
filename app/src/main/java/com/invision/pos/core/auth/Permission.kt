package com.invision.pos.core.auth

/**
 * Everything a signed-in person can be allowed to do, drawn from the app's actual screens and data
 * operations. There are two roles and no per-person switches: [RolePermissions] is the whole policy.
 */
enum class Permission {
    // Billing: the new-bill workflow and reading finished invoices.
    /** Create a bill, including searching products, custom items, entering a customer and taking payment. */
    SELL,

    /** Open, print, share and reprint an existing invoice or credit note. Read-only. */
    VIEW_INVOICES,

    /** Issue a credit note (return or refund) against a finished sale. */
    PROCESS_RETURN,

    // Business-wide figures.
    /** Sales totals, payment records and method totals. */
    VIEW_FINANCIALS,
    VIEW_REPORTS,

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
    /** What an Employee may do. Anything not listed here is denied. */
    private val EMPLOYEE = setOf(Permission.SELL, Permission.VIEW_INVOICES)

    fun allows(role: Role, permission: Permission): Boolean = when (role) {
        Role.SUPER_ADMIN -> true
        Role.EMPLOYEE -> permission in EMPLOYEE
    }

    fun of(role: Role): Set<Permission> = Permission.entries.filter { allows(role, it) }.toSet()
}
