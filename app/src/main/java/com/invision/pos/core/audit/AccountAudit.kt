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
        AccountEventKind.ACCESS_CHANGED -> AuditDraft(
            AuditAction.EMPLOYEE_ACCESS_CHANGED,
            AuditEntity.USER,
            targetId,
            "Changed what $who may do: " + (granted.orEmpty().takeIf { it.isNotEmpty() }?.joinToString { it.name.lowercase().replace('_', ' ') } ?: "billing only"),
        )
        AccountEventKind.PASSWORD_RESET -> AuditDraft(AuditAction.PASSWORD_RESET, AuditEntity.USER, targetId, "Reset the password of $who")
        AccountEventKind.RECOVERY_CODE_SET -> AuditDraft(AuditAction.RECOVERY_CODE_SET, AuditEntity.USER, actor.id, "${actor.name} created a new recovery code")
        AccountEventKind.PASSWORD_RESET_WITH_CODE -> AuditDraft(
            AuditAction.PASSWORD_RESET_WITH_CODE,
            AuditEntity.USER,
            actor.id,
            "${actor.name} reset their own password with the recovery code and was given a new code",
        )
    }
}
