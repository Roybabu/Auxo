package com.invision.pos.core.auth

import android.util.Base64
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

sealed interface StoredAccounts {
    /** No account has been created: this is a first launch. */
    data object Missing : StoredAccounts

    data class Present(val accounts: List<UserAccount>) : StoredAccounts {
        val superAdmin: UserAccount? get() = accounts.firstOrNull { it.role == Role.SUPER_ADMIN }
    }

    /** A record exists but cannot be read. It must never be treated as "no accounts". */
    data object Unreadable : StoredAccounts
}

enum class CreateOutcome { CREATED, ALREADY_EXISTS, FAILED }

enum class UpdateOutcome { UPDATED, REJECTED, FAILED }

/** Where the accounts are kept. */
interface AccountStore {
    fun read(): StoredAccounts

    /** Saves [first] as the only account, but only if there are no accounts yet; otherwise changes nothing. */
    fun createIfAbsent(first: UserAccount): CreateOutcome

    /**
     * Reads the accounts, applies [change] and saves the result as one step. Return null from [change] to
     * refuse. Fails if there is no readable registry, so it can never create the first account.
     */
    fun update(change: (List<UserAccount>) -> List<UserAccount>?): UpdateOutcome
}

/**
 * One small file in the app's private storage holding every account. Its existence is the whole
 * "initialised" state, so a separate flag cannot disagree with the accounts. It is written to a temporary
 * file, flushed to disk and then moved into place, so a crash leaves the old registry or the new one.
 *
 * An install made before roles existed has a single-account `admin_account.json`. It is carried over once,
 * unchanged, as the Super Admin.
 *
 * The file lives outside the Room database and the backup export on purpose: restoring a backup must not
 * change who can sign in. App backup is off, so the file is lost with the rest of the app's data when the
 * app is uninstalled or its data is cleared. That is a reset, not a security boundary, and anyone with
 * full access to the device's storage can change this file.
 */
class FileAccountStore(directory: File) : AccountStore {

    private val target = File(directory, FILE_NAME)
    private val temp = File(directory, "$FILE_NAME.tmp")
    private val legacy = File(directory, LEGACY_FILE_NAME)

    override fun read(): StoredAccounts = synchronized(LOCK) { readLocked() }

    private fun readLocked(): StoredAccounts {
        if (target.exists()) return decodeFile(target) { decodeRegistry(it) }
        if (!legacy.exists()) return StoredAccounts.Missing
        // Upgrade from the single-administrator file. The old file is only removed once the new one is safely in place.
        val migrated = decodeFile(legacy) { listOf(decodeLegacy(it)) }
        if (migrated is StoredAccounts.Present && write(migrated.accounts)) legacy.delete()
        return migrated
    }

    override fun createIfAbsent(first: UserAccount): CreateOutcome = synchronized(LOCK) {
        if (target.exists() || legacy.exists()) return CreateOutcome.ALREADY_EXISTS
        if (write(listOf(first))) CreateOutcome.CREATED else CreateOutcome.FAILED
    }

    override fun update(change: (List<UserAccount>) -> List<UserAccount>?): UpdateOutcome = synchronized(LOCK) {
        val current = readLocked() as? StoredAccounts.Present ?: return UpdateOutcome.FAILED
        val next = change(current.accounts) ?: return UpdateOutcome.REJECTED
        if (write(next)) UpdateOutcome.UPDATED else UpdateOutcome.FAILED
    }

    private fun write(accounts: List<UserAccount>): Boolean = try {
        temp.delete()
        FileOutputStream(temp).use { out ->
            out.write(encodeRegistry(accounts).toByteArray(Charsets.UTF_8))
            out.fd.sync()
        }
        try {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } catch (e: AtomicMoveNotSupportedException) {
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
        true
    } catch (e: IOException) {
        temp.delete()
        false
    }

    private inline fun decodeFile(file: File, decode: (String) -> List<UserAccount>): StoredAccounts = try {
        StoredAccounts.Present(decode(file.readText()))
    } catch (e: IOException) {
        StoredAccounts.Unreadable
    } catch (e: JSONException) {
        StoredAccounts.Unreadable
    } catch (e: IllegalArgumentException) {
        StoredAccounts.Unreadable
    }

    private fun encodeRegistry(accounts: List<UserAccount>): String = JSONObject()
        .put("version", VERSION)
        .put(
            "accounts",
            JSONArray().also { array ->
                accounts.forEach { a ->
                    array.put(
                        JSONObject()
                            .put("id", a.id)
                            .put("name", a.name)
                            .put("identifier", a.identifier)
                            .put("role", a.role.name)
                            .put("active", a.active)
                            .put("salt", Base64.encodeToString(a.salt, Base64.NO_WRAP))
                            .put("hash", Base64.encodeToString(a.hash, Base64.NO_WRAP))
                            .put("iterations", a.iterations)
                            .put("createdAt", a.createdAtMillis),
                    )
                }
            },
        )
        .toString()

    private fun decodeRegistry(text: String): List<UserAccount> {
        val json = JSONObject(text)
        require(json.getInt("version") == VERSION) { "unknown account version" }
        val array = json.getJSONArray("accounts")
        val accounts = (0 until array.length()).map { i ->
            val a = array.getJSONObject(i)
            UserAccount(
                id = a.getLong("id"),
                name = a.getString("name"),
                identifier = a.getString("identifier"),
                role = Role.valueOf(a.getString("role")),
                active = a.getBoolean("active"),
                salt = Base64.decode(a.getString("salt"), Base64.NO_WRAP),
                hash = Base64.decode(a.getString("hash"), Base64.NO_WRAP),
                iterations = a.getInt("iterations"),
                createdAtMillis = a.getLong("createdAt"),
            )
        }
        require(accounts.isNotEmpty())
        accounts.forEach { require(it.identifier.isNotEmpty() && it.salt.isNotEmpty() && it.hash.isNotEmpty() && it.iterations > 0) }
        require(accounts.map { it.id }.toSet().size == accounts.size) { "duplicate account id" }
        require(accounts.map { it.identifier }.toSet().size == accounts.size) { "duplicate username" }
        // Exactly one Super Admin. A registry that says otherwise has been damaged or edited.
        require(accounts.count { it.role == Role.SUPER_ADMIN } == 1) { "expected exactly one Super Admin" }
        return accounts
    }

    private fun decodeLegacy(text: String): UserAccount {
        val json = JSONObject(text)
        require(json.getInt("version") == 1) { "unknown account version" }
        return UserAccount(
            id = FIRST_ID,
            name = json.getString("name"),
            identifier = json.getString("identifier"),
            role = Role.SUPER_ADMIN,
            active = true,
            salt = Base64.decode(json.getString("salt"), Base64.NO_WRAP),
            hash = Base64.decode(json.getString("hash"), Base64.NO_WRAP),
            iterations = json.getInt("iterations"),
            createdAtMillis = json.getLong("createdAt"),
        )
    }

    companion object {
        const val FILE_NAME = "accounts.json"
        const val LEGACY_FILE_NAME = "admin_account.json"
        const val FIRST_ID = 1L
        private const val VERSION = 2

        /** One lock for the whole process, whichever store instance is asked. */
        private val LOCK = Any()
    }
}
