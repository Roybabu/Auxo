package com.invision.pos.data.security

import android.graphics.Bitmap
import android.net.Uri
import com.invision.pos.core.audit.AuditAction
import com.invision.pos.core.audit.AuditDraft
import com.invision.pos.core.audit.AuditEntity
import com.invision.pos.core.audit.AuditTrail
import com.invision.pos.core.auth.Authorizer
import com.invision.pos.core.auth.Permission
import com.invision.pos.core.backup.BackupService
import com.invision.pos.core.backup.SavedBackup
import com.invision.pos.core.shop.LogoSaveResult
import com.invision.pos.core.shop.ShopPreferences
import com.invision.pos.core.shop.StoreLogoRepository

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

    override suspend fun restore(source: Uri) {
        auth.require(Permission.BACKUP_AND_RESTORE)
        inner.restore(source)
    }

    override fun lastBackupMillis(): Long? {
        auth.require(Permission.BACKUP_AND_RESTORE)
        return inner.lastBackupMillis()
    }
}
