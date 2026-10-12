package com.auxo.pos.core.auth

/** Thrown by the data layer when the signed-in person is not allowed an operation. Nothing was changed. */
class PermissionDeniedException(val permission: Permission) : SecurityException("Not allowed: $permission")

/**
 * The one place that answers "may the person using the app do this right now?". Repositories and use cases
 * ask it before touching data, so a hidden button or a hand-made navigation call cannot get around it.
 *
 * This is a guard against mistakes and against using the app's own screens in ways they were not meant for.
 * It is not a defence against someone who can read or change the device's storage.
 */
interface Authorizer {
    /** Who is signed in, or null when nobody is. */
    val user: SessionUser?

    fun can(permission: Permission): Boolean

    fun require(permission: Permission) {
        if (!can(permission)) throw PermissionDeniedException(permission)
    }
}

/** Follows the current sign-in. Nobody signed in means nothing is allowed. */
class SessionAuthorizer(private val auth: AuthRepository) : Authorizer {
    override val user: SessionUser? get() = (auth.state.value as? AuthState.SignedIn)?.user

    override fun can(permission: Permission): Boolean {
        val signedIn = auth.state.value as? AuthState.SignedIn ?: return false
        return permission in signedIn.permissions
    }
}

/** For tests and previews. */
class FixedAuthorizer(private val role: Role?, private val granted: Set<Permission> = emptySet()) : Authorizer {
    override val user: SessionUser?
        get() = role?.let { SessionUser(if (it == Role.SUPER_ADMIN) 1L else 2L, if (it == Role.SUPER_ADMIN) "Test Admin" else "Test Employee", it, RolePermissions.of(it, granted)) }

    override fun can(permission: Permission): Boolean = role != null && RolePermissions.allows(role, permission, granted)
}
