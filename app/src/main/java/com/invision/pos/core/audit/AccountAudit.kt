package com.invision.pos.core.audit

import com.invision.pos.core.auth.AccountEvent
import com.invision.pos.core.auth.AccountEventKind

/** How an account event reads in the activity log. Passwords are never mentioned beyond the fact one was reset. */
fun AccountEvent.toAuditDraft(): AuditDraft {
    val who = targetName ?: actor.name
    return when (kind) {
        AccountEventKind.SIGNED_IN -> AuditDraft(AuditAction.SIGNED_IN, AuditEntity.USER, actor.id, "${actor.name} signed in")
        AccountEventKind.SIGNED_OUT -> AuditDraft(AuditAction.SIGNED_OUT, AuditEntity.USER, actor.id, "${actor.name} signed out")
        AccountEventKind.EMPLOYEE_CREATED -> AuditDraft(AuditAction.EMPLOYEE_CREATED, AuditEntity.USER, targetId, "Created employee account for $who")
        AccountEventKind.EMPLOYEE_TURNED_ON -> AuditDraft(AuditAction.EMPLOYEE_TURNED_ON, AuditEntity.USER, targetId, "Turned on the account of $who")
        AccountEventKind.EMPLOYEE_TURNED_OFF -> AuditDraft(AuditAction.EMPLOYEE_TURNED_OFF, AuditEntity.USER, targetId, "Turned off the account of $who")
        AccountEventKind.PASSWORD_RESET -> AuditDraft(AuditAction.PASSWORD_RESET, AuditEntity.USER, targetId, "Reset the password of $who")
    }
}
