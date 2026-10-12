package com.auxo.pos.core.auth

/** A signed-in person with a fixed role, for tests. */
class FixedAuthorizer(private val role: Role?, private val granted: Set<Permission> = emptySet()) : Authorizer {
    override val user: SessionUser?
        get() = role?.let { SessionUser(if (it == Role.SUPER_ADMIN) 1L else 2L, if (it == Role.SUPER_ADMIN) "Test Admin" else "Test Employee", it, RolePermissions.of(it, granted)) }

    override fun can(permission: Permission): Boolean = role != null && RolePermissions.allows(role, permission, granted)
}
