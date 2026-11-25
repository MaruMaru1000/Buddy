package com.buddyapp.Buddy.manager

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import java.util.concurrent.atomic.AtomicInteger
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("secure_keys_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_ALIAS = "typeslate_secure_key"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_SEPARATOR = "]"
        private const val PREF_KEY_ARRAY = "keys_array"

        const val PROVIDER_GEMINI = "gemini"
        const val PROVIDER_GROQ = "groq"
        const val PROVIDER_CUSTOM = "custom"

        /**
         * Which provider a key belongs to, decided by its prefix.
         * A key is only ever sent to its own provider.
         * Anything that is not a Gemini or Groq key is a custom-endpoint key.
         */
        fun providerForKey(key: String): String = when {
            key.startsWith("AIza") -> PROVIDER_GEMINI
            key.startsWith("gsk_") -> PROVIDER_GROQ
            else -> PROVIDER_CUSTOM
        }
    }

    private val rateLimitedKeys = mutableMapOf<String, Long>()
    private val invalidKeys = mutableSetOf<String>()
    private val roundRobinIndex = AtomicInteger(0)

    init {
        try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            if (!keyStore.containsAlias(KEY_ALIAS)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val keyGenParameterSpec = KeyGenParameterSpec.Builder(
                    KEY_ALIAS,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
                keyGenerator.init(keyGenParameterSpec)
                keyGenerator.generateKey()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun getSecretKey(): SecretKey? {
        return try {
            val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE)
            keyStore.load(null)
            keyStore.getKey(KEY_ALIAS, null) as? SecretKey
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /** Returns null if encryption is not possible. Never falls back to plain text. */
    private fun encrypt(plainText: String): String? {
        return try {
            val secretKey = getSecretKey() ?: return null
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, secretKey)
            val iv = cipher.iv
            val cipherText = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
            val ivString = Base64.encodeToString(iv, Base64.NO_WRAP)
            val cipherTextString = Base64.encodeToString(cipherText, Base64.NO_WRAP)
            "$ivString$IV_SEPARATOR$cipherTextString"
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun decrypt(encryptedString: String): String {
        return try {
            if (!encryptedString.contains(IV_SEPARATOR)) {
                return encryptedString // Assume it's plain text fallback or unencrypted legacy data
            }
            val parts = encryptedString.split(IV_SEPARATOR)
            if (parts.size != 2) return encryptedString
            
            val iv = Base64.decode(parts[0], Base64.NO_WRAP)
            val cipherText = Base64.decode(parts[1], Base64.NO_WRAP)

            val secretKey = getSecretKey() ?: return encryptedString
            val cipher = Cipher.getInstance(TRANSFORMATION)
            val spec = GCMParameterSpec(128, iv)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, spec)
            
            val plainTextBytes = cipher.doFinal(cipherText)
            String(plainTextBytes, StandardCharsets.UTF_8)
        } catch (e: Exception) {
            e.printStackTrace()
            "[]" // Fallback to empty array on decryption failure
        }
    }

    fun getKeys(): List<String> {
        val encryptedStr = prefs.getString(PREF_KEY_ARRAY, null) ?: return emptyList()
        val jsonStr = decrypt(encryptedStr)
        val list = mutableListOf<String>()
        try {
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                list.add(arr.getString(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return list
    }

    /** Returns false (and writes nothing) if the keys could not be encrypted. */
    private fun saveKeys(keys: List<String>): Boolean {
        val arr = JSONArray(keys)
        val encryptedStr = encrypt(arr.toString()) ?: return false
        prefs.edit().putString(PREF_KEY_ARRAY, encryptedStr).apply()
        return true
    }

    /** Returns false if the key could not be stored encrypted (it is then not stored at all). */
    fun addKey(key: String): Boolean {
        val keys = getKeys().toMutableList()
        if (!keys.contains(key)) {
            keys.add(key)
            if (!saveKeys(keys)) return false
        }
        invalidKeys.remove(key)
        return true
    }

    fun removeKey(key: String) {
        val keys = getKeys().toMutableList()
        keys.remove(key)
        saveKeys(keys)
        rateLimitedKeys.remove(key)
        invalidKeys.remove(key)
    }

    /** Keys that belong to [provider] (see [providerForKey]). */
    fun getKeysFor(provider: String): List<String> =
        getKeys().filter { providerForKey(it) == provider }

    fun getNextKey(provider: String): String? {
        val keys = getKeysFor(provider)
        if (keys.isEmpty()) return null
        
        val now = System.currentTimeMillis()
        val validKeys = keys.filter { key ->
            if (invalidKeys.contains(key)) return@filter false
            val limitTime = rateLimitedKeys[key] ?: 0L
            now > limitTime
        }
        
        if (validKeys.isEmpty()) return null
        
        val idx = (roundRobinIndex.getAndIncrement() and Int.MAX_VALUE) % validKeys.size
        return validKeys[idx]
    }

    fun reportRateLimit(key: String, retryAfterSeconds: Long = 60) {
        val cooldown = retryAfterSeconds.coerceIn(1, 600)
        rateLimitedKeys[key] = System.currentTimeMillis() + cooldown * 1_000
    }

    fun markInvalid(key: String) {
        invalidKeys.add(key)
    }

    fun getShortestWaitTimeMs(provider: String): Long? {
        val keys = getKeysFor(provider)
        if (keys.isEmpty()) return null
        val now = System.currentTimeMillis()
        val waits = keys.filter { !invalidKeys.contains(it) }
            .mapNotNull { key ->
                val limitTime = rateLimitedKeys[key] ?: return@mapNotNull null
                val remaining = limitTime - now
                if (remaining > 0) remaining else null
            }
        return waits.minOrNull()
    }
}
