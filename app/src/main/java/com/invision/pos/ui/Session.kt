package com.invision.pos.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.invision.pos.core.auth.Permission
import com.invision.pos.core.auth.Role
import com.invision.pos.core.auth.RolePermissions

/**
 * What the signed-in person may see and do, for deciding which buttons and pages to show.
 *
 * This only shapes the screens. The real checks are in the data layer (see Authorizer), which refuses a
 * restricted operation whatever the screen did. Previews and tests that show a single screen get the
 * Super Admin view unless they say otherwise.
 */
class Capabilities(val role: Role, val userName: String = "") {
    fun can(permission: Permission): Boolean = RolePermissions.allows(role, permission)
}

val LocalCapabilities = compositionLocalOf { Capabilities(Role.SUPER_ADMIN) }

@Composable
fun can(permission: Permission): Boolean = LocalCapabilities.current.can(permission)

/**
 * Shows [content] only to people who hold [permission]. Anyone else who reaches the page by another route
 * is sent back with [onDenied] and sees nothing of it, so the page's own screen and data are never created.
 */
@Composable
fun RequirePermission(permission: Permission, onDenied: () -> Unit, content: @Composable () -> Unit) {
    if (can(permission)) {
        content()
    } else {
        val leave by rememberUpdatedState(onDenied)
        LaunchedEffect(permission) { leave() }
    }
}
