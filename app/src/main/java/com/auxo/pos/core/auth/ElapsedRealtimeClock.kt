// SPDX-License-Identifier: GPL-3.0-or-later
// Copyright (C) 2026 The Auxo authors. See the LICENSE file in the project root.

package com.auxo.pos.core.auth

import android.os.SystemClock
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * A clock that counts milliseconds since the phone started and ignores the date and time setting. The wait after wrong
 * passwords is counted on it, so changing the phone's date does not shorten the wait. It is not a calendar clock: its
 * "instants" are only good for measuring how much time has passed.
 */
class ElapsedRealtimeClock : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC

    override fun withZone(zone: ZoneId): Clock = this

    override fun instant(): Instant = Instant.ofEpochMilli(SystemClock.elapsedRealtime())

    override fun millis(): Long = SystemClock.elapsedRealtime()
}
