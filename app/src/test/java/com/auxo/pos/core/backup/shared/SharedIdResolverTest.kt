// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import org.junit.Assert.assertEquals
import org.junit.Test

class SharedIdResolverTest {
    private val resolver = SharedIdResolver(mapOf("product" to mapOf(7L to "linked-product")))

    @Test
    fun aLinkedRecord_getsItsStoredId() {
        assertEquals("linked-product", resolver.of("product", 7L))
    }

    @Test
    fun anUnlinkedRecord_getsTheDerivedId() {
        assertEquals(SharedIds.derive("product", "8"), resolver.of("product", 8L))
    }

    @Test
    fun aLinkInAnotherTable_isNotUsed() {
        assertEquals(SharedIds.derive("variant", "7"), resolver.of("variant", 7L))
    }

    @Test
    fun aCustomCategory_usesItsStoredId() {
        val linkedCategories = SharedIdResolver(mapOf("category" to mapOf(3L to "linked-category")))
        assertEquals("linked-category", linkedCategories.category("MINE", false, 3L))
    }

    @Test
    fun aBuiltInCategory_keepsItsFixedId() {
        val linkedCategories = SharedIdResolver(mapOf("category" to mapOf(3L to "linked-category")))
        assertEquals("21698313-7bd8-56ea-9c69-9ebe0fc84a82", linkedCategories.category("GARMENT", true, 3L))
    }
}
