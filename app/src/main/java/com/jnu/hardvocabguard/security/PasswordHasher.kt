package com.jnu.hardvocabguard.security

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * 紧急解锁密码加密：
 * - 仅支持 6 位数字密码
 * - 本地仅存 salt + hash（Base64），不存明文
 */
object PasswordHasher {
    private const val ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256

    fun createSaltBase64(): String {
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        return Base64.encodeToString(salt, Base64.NO_WRAP)
    }

    fun hashSixDigitPassword(password: String, saltBase64: String): String {
        require(password.matches(Regex("\\d{6}"))) { "密码必须为6位数字" }
        val salt = Base64.decode(saltBase64, Base64.NO_WRAP)
        val spec = PBEKeySpec(password.toCharArray(), salt, ITERATIONS, KEY_LENGTH_BITS)
        val skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val hash = skf.generateSecret(spec).encoded
        return Base64.encodeToString(hash, Base64.NO_WRAP)
    }

    fun verifySixDigitPassword(password: String, saltBase64: String, expectedHashBase64: String): Boolean {
        if (!password.matches(Regex("\\d{6}"))) return false
        val actual = hashSixDigitPassword(password, saltBase64)
        return constantTimeEquals(actual, expectedHashBase64)
    }

    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) {
            diff = diff or (a[i].code xor b[i].code)
        }
        return diff == 0
    }
}

