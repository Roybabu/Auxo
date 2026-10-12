package com.invision.pos.core.auth

/** The only two roles. A role is saved with the account and read again at every sign-in and restart. */
enum class Role { SUPER_ADMIN, EMPLOYEE }

/** What the rest of the app may know about a person who is signed in. */
data class SessionUser(val id: Long, val name: String, val role: Role)

/** An account as shown in User Management. Never carries the password hash. */
data class ManagedUser(
    val id: Long,
    val name: String,
    val identifier: String,
    val role: Role,
    val active: Boolean,
    val createdAtMillis: Long,
)
