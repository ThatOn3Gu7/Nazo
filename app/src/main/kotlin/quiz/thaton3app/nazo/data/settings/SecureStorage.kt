package quiz.thaton3app.nazo.data.settings

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Secure on-device storage for sensitive values (API keys).
 *
 * Uses the Android Keystore (non-deprecated `KeyGenerator` + AES/GCM) to wrap a
 * plaintext SharedPreferences file that holds the Base64 ciphertext + IV. This is the
 * modern replacement for the now-deprecated `EncryptedSharedPreferences` / `MasterKey`
 * convenience classes (deprecated as of androidx.security:security-crypto 1.1.0).
 */
class SecureStorage(context: Context) {

    private val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    private val prefs: SharedPreferences =
        context.getSharedPreferences("nazo_secure", Context.MODE_PRIVATE)
    private val keyAlias = "nazo_api_keys"

    init {
        if (!keyStore.containsAlias(keyAlias)) {
            val generator = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES,
                "AndroidKeyStore",
            )
            generator.init(
                KeyGenParameterSpec.Builder(
                    keyAlias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setUserAuthenticationRequired(false)
                    .build(),
            )
            generator.generateKey()
        }
    }

    private fun getKey(): SecretKey = keyStore.getKey(keyAlias, null) as SecretKey

    fun put(key: String, value: String) {
        if (value.isBlank()) {
            prefs.edit().remove(encKey(key)).remove(ivKey(key)).apply()
            return
        }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getKey())
        val iv = cipher.iv
        val encrypted = cipher.doFinal(value.toByteArray(Charsets.UTF_8))
        prefs.edit()
            .putString(encKey(key), Base64.encodeToString(encrypted, Base64.DEFAULT))
            .putString(ivKey(key), Base64.encodeToString(iv, Base64.DEFAULT))
            .apply()
    }

    fun get(key: String): String? {
        val enc = prefs.getString(encKey(key), null) ?: return null
        val iv = prefs.getString(ivKey(key), null) ?: return null
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getKey(),
                GCMParameterSpec(GCM_TAG_LENGTH, Base64.decode(iv, Base64.DEFAULT)),
            )
            String(cipher.doFinal(Base64.decode(enc, Base64.DEFAULT)), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }

    fun remove(key: String) {
        prefs.edit().remove(encKey(key)).remove(ivKey(key)).apply()
    }

    private fun encKey(k: String) = "enc_$k"
    private fun ivKey(k: String) = "iv_$k"

    private companion object {
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_TAG_LENGTH = 128
    }
}
