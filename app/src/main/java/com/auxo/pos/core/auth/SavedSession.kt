// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.auth

import javax.crypto.Cipher

/**
 * "This person was signed in", kept on the phone so the app can pick them back up after an update or a restart, but only
 * after the phone's fingerprint check. Nothing secret is saved: no password and no sign-in token. What is saved is the
 * person's number, locked with a key that the phone releases only for a fingerprint.
 */
interface SavedSessionVault {
    /** The name to show on the unlock screen, or null when nothing is saved. */
    fun savedName(): String?

    /** Saves that [userId] is signed in. False when the phone cannot protect it (no fingerprint enrolled, no screen lock). */
    fun save(userId: Long, name: String): Boolean

    /** Forgets the saved session and its key. */
    fun clear()

    /**
     * A cipher that opens the saved session once the fingerprint check has passed. Null (and the saved session is cleared)
     * when it can no longer be opened, for example because the phone's fingerprints were changed.
     */
    fun unlockCipher(): Cipher?

    /** The person's number, read with the cipher that has just passed the fingerprint check. */
    fun open(cipher: Cipher): Long?
}

/** Used where nothing is saved (tests, previews). */
object NoSavedSession : SavedSessionVault {
    override fun savedName(): String? = null
    override fun save(userId: Long, name: String): Boolean = false
    override fun clear() = Unit
    override fun unlockCipher(): Cipher? = null
    override fun open(cipher: Cipher): Long? = null
}
