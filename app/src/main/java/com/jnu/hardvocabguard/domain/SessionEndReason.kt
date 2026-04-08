package com.jnu.hardvocabguard.domain

/**
 * 一次监督学习的结束原因。
 */
enum class SessionEndReason {
    /** 达标自动结束 */
    COMPLETED,

    /** 用户手动结束 */
    MANUAL_STOP,

    /** 紧急解锁结束（需要留档） */
    EMERGENCY_UNLOCK,
}

