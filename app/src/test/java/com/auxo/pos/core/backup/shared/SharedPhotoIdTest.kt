// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SharedPhotoIdTest {

    private val name = "0123456789abcdef0123456789abcdef.jpg"
    private val uuid = "01234567-89ab-cdef-0123-456789abcdef"

    @Test
    fun aPhotoFileName_givesItsUuid_andBack() {
        assertEquals(uuid, SharedIds.photo(name))
        assertEquals(name, SharedIds.photoFileName(uuid))
    }

    @Test
    fun aNameThatIsNotAPhotoName_getsADerivedId_andNoFileName() {
        assertEquals(SharedIds.derive("photo", "missing.jpg"), SharedIds.photo("missing.jpg"))
        assertNull(SharedIds.photoFileName("not-a-uuid"))
    }
}
