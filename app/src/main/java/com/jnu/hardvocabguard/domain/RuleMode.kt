package com.jnu.hardvocabguard.domain

/**
 * 达标规则模式：二选一。
 */
enum class RuleMode {
    /**
     * 仅按前台有效时长达标。
     */
    DURATION,

    /**
     * 仅按完成单词数量达标。
     */
    WORD_COUNT,
}

