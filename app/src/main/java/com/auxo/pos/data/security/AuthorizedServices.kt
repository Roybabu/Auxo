// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.data.security

import android.graphics.Bitmap
import android.net.Uri
import com.auxo.pos.core.audit.AuditAction
import com.auxo.pos.core.audit.AuditDraft
import com.auxo.pos.core.audit.AuditEntity
import com.auxo.pos.core.audit.AuditTrail
import com.auxo.pos.core.auth.Authorizer
import com.auxo.pos.core.auth.Permission
import com.auxo.pos.core.backup.BackupService
import com.auxo.pos.core.backup.SavedBackup
import com.auxo.pos.core.shop.LogoSaveResult
import com.auxo.pos.core.shop.ShopPreferences
import com.auxo.pos.core.shop.StoreLogoRepository

/** Turning shop features on or off is a business setting. Reading them is free. */
class AuthorizedShopPreferences(private val inner: ShopPreferences, private val auth: Authorizer) : ShopPreferences by inner {
    override fun setRestaurantMenu(enabled: Boolean) {
        auth.require(Permission.MANAGE_SETTINGS)
        inner.setRestaurantMenu(enabled)
    }

    override fun setSuppliers(enabled: Boolean) {
        auth.require(Permission.MANAGE_SETTINGS)
        inner.setSuppliers(enabled)
    }

    override fun setCategorySetupDone(done: Boolean) {
        auth.require(Permission.MANAGE_SETTINGS)
        inner.setCategorySetupDone(done)
    }

    override fun setTableCount(count: Int) {
        auth.require(Permission.MANAGE_SETTINGS)
        inner.setTableCount(count)
    }

    override fun setMetalRates(rates: com.auxo.pos.domain.jewellery.MetalRates) {
        auth.require(Permission.MANAGE_SETTINGS)
        inner.setMetalRates(rates)
    }
}

/** The logo is shown to everyone; only the Super Admin can change it. */
class AuthorizedStoreLogoRepository(private val inner: StoreLogoRepository, private val auth: Authorizer) : StoreLogoRepository by inner {
    override suspend fun save(source: Uri): LogoSaveResult {
        auth.require(Permission.MANAGE_SETTINGS)
        return inner.save(source)
    }

    override suspend fun save(bitmap: Bitmap): LogoSaveResult {
        auth.require(Permission.MANAGE_SETTINGS)
        return inner.save(bitmap)
    }

    override suspend fun remove() {
        auth.require(Permission.MANAGE_SETTINGS)
        inner.remove()
    }
}

/** A backup holds every record in the app and a restore replaces them, so both are Super Admin only. */
class AuthorizedBackupService(
    private val inner: BackupService,
    private val auth: Authorizer,
    private val audit: AuditTrail,
) : BackupService {
    override suspend fun backUp(): SavedBackup {
        auth.require(Permission.BACKUP_AND_RESTORE)
        val saved = inner.backUp()
        // A restore replaces the whole database, including the log, so only the backup itself can be logged.
        audit.record(AuditDraft(AuditAction.BACKUP_CREATED, AuditEntity.BACKUP, null, "Backup saved as ${saved.fileName}"))
        return saved
    }

    override suspend fun restore(source: Uri, passphrase: String?) {
        auth.require(Permission.BACKUP_AND_RESTORE)
        inner.restore(source, passphrase)
    }

    override fun isProtected(): Boolean {
        auth.require(Permission.BACKUP_AND_RESTORE)
        return inner.isProtected()
    }

    override fun setPassphrase(passphrase: String) {
        auth.require(Permission.BACKUP_AND_RESTORE)
        inner.setPassphrase(passphrase)
    }

    override fun clearPassphrase() {
        auth.require(Permission.BACKUP_AND_RESTORE)
        inner.clearPassphrase()
    }

    override fun lastBackupMillis(): Long? {
        auth.require(Permission.BACKUP_AND_RESTORE)
        return inner.lastBackupMillis()
    }
}
