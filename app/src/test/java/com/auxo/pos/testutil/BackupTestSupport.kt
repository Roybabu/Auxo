package com.auxo.pos.testutil

import com.auxo.pos.core.audit.AuditDraft
import com.auxo.pos.core.audit.AuditRecord
import com.auxo.pos.core.audit.AuditTrail
import com.auxo.pos.core.auth.SessionUser
import com.auxo.pos.core.backup.auto.AutoBackupScheduler
import com.auxo.pos.core.backup.auto.AutoBackupSettings
import com.auxo.pos.core.backup.auto.AutoBackupSettingsStore
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

/** Settings held in memory, with the same range limits as the real store. */
class FakeAutoBackupStore(initial: AutoBackupSettings = AutoBackupSettings()) : AutoBackupSettingsStore {
    private val state = MutableStateFlow(initial.sanitized())
    override val settings: StateFlow<AutoBackupSettings> = state.asStateFlow()
    override fun update(change: (AutoBackupSettings) -> AutoBackupSettings) {
        state.value = change(state.value).sanitized()
    }
}

/** A clock the test moves by hand. */
class TestClock(var now: Long = 1_700_000_000_000L) : Clock() {
    override fun getZone(): ZoneId = ZoneOffset.UTC
    override fun withZone(zone: ZoneId?): Clock = this
    override fun instant(): Instant = Instant.ofEpochMilli(now)
    fun advance(millis: Long) {
        now += millis
    }
}

class RecordingScheduler : AutoBackupScheduler {
    val calls = mutableListOf<Boolean>()
    override fun sync(active: Boolean) {
        calls += active
    }
}

class RecordingAudit : AuditTrail {
    val drafts = mutableListOf<AuditDraft>()
    override suspend fun <T> recording(block: suspend () -> T, describe: (T) -> AuditDraft?): T = block().also { describe(it)?.let(drafts::add) }
    override suspend fun record(draft: AuditDraft, actor: SessionUser?) {
        drafts += draft
    }
    override fun observeRecent(limit: Int): Flow<List<AuditRecord>> = emptyFlow()
}
