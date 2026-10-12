package com.invision.pos.core.auth

enum class AccountEventKind { SIGNED_IN, SIGNED_OUT, EMPLOYEE_CREATED, EMPLOYEE_TURNED_ON, EMPLOYEE_TURNED_OFF, PASSWORD_RESET, ACCESS_CHANGED }

/** Something that happened to the accounts, with the person who did it. */
data class AccountEvent(
    val kind: AccountEventKind,
    val actor: SessionUser,
    val targetId: Long? = null,
    val targetName: String? = null,
    /** For ACCESS_CHANGED: the extras the employee now has. */
    val granted: Set<Permission>? = null,
)

/** Told after an account change has been saved. It must not throw into the sign-in flow. */
fun interface AccountEventListener {
    fun onEvent(event: AccountEvent)
}
