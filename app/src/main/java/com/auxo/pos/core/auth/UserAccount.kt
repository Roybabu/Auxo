package com.auxo.pos.core.auth

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * A local sign-in account. Only a salted hash of the password is kept, never the password itself.
 * [identifier] is the normalised (lower-case) username or email address. [id] is never reused: accounts are
 * deactivated, not deleted, so records that name an account keep pointing at the same person.
 */
class UserAccount(
    val id: Long,
    val name: String,
    val identifier: String,
    val role: Role,
    val active: Boolean,
    val salt: ByteArray,
    val hash: ByteArray,
    val iterations: Int,
    val createdAtMillis: Long,
    /** Extras the Super Admin switched on for this Employee. Always empty for the Super Admin, who holds everything. */
    val granted: Set<Permission> = emptySet(),
    /** Proof of the Super Admin's recovery code, if one was set up. Always null for an Employee. */
    val recovery: RecoveryRecord? = null,
    /** This person's quick-unlock PIN, if they set one. */
    val pin: PinRecord? = null,
    /** This person chose to unlock with the device's fingerprint. */
    val fingerprint: Boolean = false,
) {
    val permissions: Set<Permission> get() = RolePermissions.of(role, granted)

    fun copy(
        active: Boolean = this.active,
        salt: ByteArray = this.salt,
        hash: ByteArray = this.hash,
        iterations: Int = this.iterations,
        granted: Set<Permission> = this.granted,
        recovery: RecoveryRecord? = this.recovery,
        pin: PinRecord? = this.pin,
        fingerprint: Boolean = this.fingerprint,
    ) = UserAccount(id, name, identifier, role, active, salt, hash, iterations, createdAtMillis, granted, recovery, pin, fingerprint)

    fun toManaged() = ManagedUser(id, name, identifier, role, active, createdAtMillis, if (role == Role.EMPLOYEE) granted else emptySet())

    fun toSession() = SessionUser(id, name, role, permissions)
}

/** Salted PBKDF2-HMAC-SHA256, available on every supported Android version (API 26+). */
class PasswordHasher(
    /** Work factor for new accounts. Each account records its own count, so this can be raised later. */
    val iterations: Int = DEFAULT_ITERATIONS,
    private val random: SecureRandom = SecureRandom(),
) {
    fun newSalt(): ByteArray = ByteArray(SALT_BYTES).also(random::nextBytes)

    fun hash(password: String, salt: ByteArray, iterations: Int = this.iterations): ByteArray {
        val chars = password.toCharArray()
        val spec = PBEKeySpec(chars, salt, iterations, HASH_BITS)
        try {
            return SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            chars.fill('\u0000')
        }
    }

    /** Compares in constant time. */
    fun matches(password: String, account: UserAccount): Boolean =
        // An empty password is never a right one, and some Android crypto providers refuse to hash it at all
        // (the error was a crash when "show recovery code" was tapped with the password box empty).
        password.isNotEmpty() && MessageDigest.isEqual(hash(password, account.salt, account.iterations), account.hash)

    companion object {
        const val DEFAULT_ITERATIONS = 600_000
        const val SALT_BYTES = 16
        const val HASH_BITS = 256
        private const val ALGORITHM = "PBKDF2WithHmacSHA256"
    }
}
