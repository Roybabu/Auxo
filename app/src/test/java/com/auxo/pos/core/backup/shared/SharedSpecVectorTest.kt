// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.backup.shared

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Base64

/**
 * The test vector in docs/BACKUP_FORMAT.md section 5, opened the way a restore opens a file: decrypted, checked and
 * planned. The vector holds one settings row and no other records.
 */
class SharedSpecVectorTest {

    private val header = "4155584f424b5550000150424b4446322d484d41432d53484132353600004145532d3235362d47434d00000927c0000102030405060708090a0b0c0d0e0f101112131415161718191a1b"
    private val ciphertext = "v2QGyjtGPdiMvXuzjdzNAu0WNoJd6Po3Ns9JztzRiTqOz45zQd017wyHgwWcUI6Fw7QEAMwG+YxGicA5+U4dfiD2b1xR5AO7EbrbWAxNq3aldJ3+x2S6LoFEYRndViMn63td1fkrS4VeFpqcQOMMu/AF51eK4M8Fr8dle5gXFUNUBbhsM8Jb5mGTBhjI0DWXMgacgGfAoq0eCHwPm10O6q1eVglH4OBeN3NCbkAuh02CvmrpjLq42Dplpzjk9hSSPrv2aJmwGOR6DEOiy44RglP8o9vUTVrc4edev2O07BwiE5PUtBzmtFrRhSt5iYS5YsgeYYu3hKDwhikk1CdiXnsxpO/ETIzb3uGLr0FUG11sNCIsqttd6ca9YGW7rMLpD1WiGpHW2dEsJJOApSvdMv3FkyDjMNrL24vZiPKlNLK+3KYDDwITRsVMA2FnyA8gWwqC40hXYub4lBQQehDpK1gDl5K2eDQYK6tm99DJRNEiipTcmrj/9XWbGFnCDv1HReoq5aIDmucSWaFWMdlK8q7z+SHISUqyhaUkn3Vtm2EI5+d83OS1rS3lLkIw7ifjDON/PYcdhN/lRSRRXQ2RPJ/ffpISmb07MZw4J62l4Se/YFJr4z3BoVKQZW/O/jTaII1XTa6ulD3gyre8ngLb7h3vFf9oXqnloaEYA2/h40GN2gymzFD+AlJHhiIRdXDuf4TJTAmQ6/Jj3UIS/o4U4HOJPUHanqhazXfW3f+f6zPFKjIHUXV+Lc74klqr6U+mw0XHgTn97/4e8lyHlUaKmcrWlghRFW6JYLSG4skKB8MVsV+dgY1bjEe/stT/pWuutDIP7w/pLVswhyd0hBqSuOwev0ptqtHNXGzskfTz2DJKQWjbC+CY1oynFuGP8DRXGDqCSPlFFaVIhGQKmv9ndgxCIxyg716iG1618sEnjp21uJA5tV6Bvgnb7cFIBtG6i5/4dau4+veUF4F3a8XG9gbhhbU7/kimb2WZ3w7buMA0StIeLqt+XWf/vp7w0ltIscWSksBDkOnhCw5Cnf8MmqIs7kK4BOU5HhEAs+8LQsy/EkDiRfPU5RGkCOcdX2CGckA+34vZY5feR6yby+nmkxUSlJVxNLTqsvWm/8VYfVx6B4d9NdhXNhfg8s4Nw2ABJrI20FxgTCIEMAz/VFlDzjGXOL4sdWyTHu+oEL2qElGY5zEmPaZ88P0oilH+thuGTD7HS9p7sM3gsMgSVcd0rQs48oHIKS7ERyjQXbs/+XcMMSk2HV/skGseDXLCB9Dm7bBteqi1MOr+eYQ8E1PxbLvRLGqkOoJKVsreCPlNfxbJI2lpbtjav7yoxGLXFJuZld1bJ9h6LOOKLgW6M4vTdRWh1ZQ1QSde3AM2ZJS4lf7FR4JQ2LBw81NUaVih/UAl7Bg/Hq6Dg0viuDkR5/Uhsy2AgXvkksB7xxZn3UT2vL/nJpnZxft984ZXkf/v03jYAOtKYU2efrLrw4ntwAh1pOKZMd65rtmIb4U57+tyDJ+kXTFeME20lt5B/caIlLGPkbe0Pavu8igcsZJCua8zHpV68jfSxZd6k21QPuD2X2w4Rd1lAnXLSw+4n/70lqEycZWyGrGvslufAVdNTktSWlpBofOoQ1O/Yb0zVcK0F00WsQ=="

    private fun vectorFile(): ByteArray =
        header.chunked(2).map { it.toInt(16).toByte() }.toByteArray() + Base64.getDecoder().decode(ciphertext)

    @Test
    fun theVector_opensAndPlans_withOneSettingsRow() {
        val document = SharedBackupReader.read(vectorFile(), "AUXO test")
        assertEquals("A", document.shopName)
        assertEquals(1, document.rowCount("settings"))

        val plan = SharedRestorePlanner.plan(document)
        assertEquals("A", plan.settings.storeName)
        assertEquals(1, plan.rowCounts["settings"])
        assertEquals(0, plan.rowCounts["products"])
    }
}
