package com.invision.pos.core.auth

import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONException
import org.json.JSONObject

sealed interface StoredAdmin {
    /** No administrator has been created: this is a first launch. */
    data object Missing : StoredAdmin

    data class Present(val account: AdminAccount) : StoredAdmin

    /** A record exists but cannot be read. It must never be treated as "no administrator". */
    data object Unreadable : StoredAdmin
}

enum class CreateOutcome { CREATED, ALREADY_EXISTS, FAILED }

/** Where the administrator is kept. */
interface AdminAccountStore {
    fun read(): StoredAdmin

    /** Saves [account] only if there is no administrator yet; otherwise leaves the existing one untouched. */
    fun createIfAbsent(account: AdminAccount): CreateOutcome
}

/**
 * One small file in the app's private storage. The file's existence is the whole "initialised" state,
 * so a separate flag cannot disagree with the account. It is written to a temporary file, flushed to disk
 * and then moved into place, so a crash leaves either no account or a complete one.
 *
 * It lives outside the Room database and the backup export on purpose: restoring a backup must not
 * change who can sign in. App backup is off, so the file is lost with the rest of the app's data when the
 * app is uninstalled or its data is cleared. That is a reset, not a security boundary.
 */
class FileAdminAccountStore(directory: File) : AdminAccountStore {

    private val target = File(directory, FILE_NAME)
    private val temp = File(directory, "$FILE_NAME.tmp")

    override fun read(): StoredAdmin = synchronized(LOCK) {
        if (!target.exists()) return StoredAdmin.Missing
        try {
            StoredAdmin.Present(decode(target.readText()))
        } catch (e: IOException) {
            StoredAdmin.Unreadable
        } catch (e: JSONException) {
            StoredAdmin.Unreadable
        } catch (e: IllegalArgumentException) {
            StoredAdmin.Unreadable
        }
    }

    override fun createIfAbsent(account: AdminAccount): CreateOutcome = synchronized(LOCK) {
        if (target.exists()) return CreateOutcome.ALREADY_EXISTS
        try {
            temp.delete()
            FileOutputStream(temp).use { out ->
                out.write(encode(account).toByteArray(Charsets.UTF_8))
                out.fd.sync()
            }
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
            } catch (e: AtomicMoveNotSupportedException) {
                check(temp.renameTo(target)) { "could not move the account file into place" }
            }
            CreateOutcome.CREATED
        } catch (e: IOException) {
            temp.delete()
            CreateOutcome.FAILED
        } catch (e: IllegalStateException) {
            temp.delete()
            CreateOutcome.FAILED
        }
    }

    private fun encode(account: AdminAccount): String = JSONObject()
        .put("version", VERSION)
        .put("name", account.name)
        .put("identifier", account.identifier)
        .put("salt", Base64.encodeToString(account.salt, Base64.NO_WRAP))
        .put("hash", Base64.encodeToString(account.hash, Base64.NO_WRAP))
        .put("iterations", account.iterations)
        .put("createdAt", account.createdAtMillis)
        .toString()

    private fun decode(text: String): AdminAccount {
        val json = JSONObject(text)
        require(json.getInt("version") == VERSION) { "unknown account version" }
        val account = AdminAccount(
            name = json.getString("name"),
            identifier = json.getString("identifier"),
            salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP),
            hash = Base64.decode(json.getString("hash"), Base64.NO_WRAP),
            iterations = json.getInt("iterations"),
            createdAtMillis = json.getLong("createdAt"),
        )
        require(account.identifier.isNotEmpty() && account.salt.isNotEmpty() && account.hash.isNotEmpty() && account.iterations > 0)
        return account
    }

    private companion object {
        const val FILE_NAME = "admin_account.json"
        const val VERSION = 1

        /** One lock for the whole process, whichever store instance is asked. */
        val LOCK = Any()
    }
}
