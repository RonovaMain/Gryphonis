package dev.ronova.gryphonis.data

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import java.util.UUID
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Сохранённое подключение к FTP. Пароль лежит только в зашифрованном виде (secret). */
data class FtpProfile(
    val id: String,
    val name: String,
    val host: String,
    val port: Int,
    val user: String,
    val secret: String,
    val path: String,
) {
    val title: String get() = name.ifBlank { host }
}

object FtpProfiles {

    fun newId(): String = UUID.randomUUID().toString()

    fun encode(list: List<FtpProfile>): String {
        val arr = JSONArray()
        for (p in list) {
            val o = JSONObject()
            o.put("id", p.id)
            o.put("name", p.name)
            o.put("host", p.host)
            o.put("port", p.port)
            o.put("user", p.user)
            o.put("secret", p.secret)
            o.put("path", p.path)
            arr.put(o)
        }
        return arr.toString()
    }

    fun decode(json: String?): List<FtpProfile> {
        if (json.isNullOrBlank()) return emptyList()
        return try {
            val arr = JSONArray(json)
            val out = ArrayList<FtpProfile>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(
                    FtpProfile(
                        id = o.optString("id").ifEmpty { newId() },
                        name = o.optString("name"),
                        host = o.optString("host"),
                        port = o.optInt("port", 21),
                        user = o.optString("user"),
                        secret = o.optString("secret"),
                        path = o.optString("path", "/").ifEmpty { "/" },
                    )
                )
            }
            out
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun seal(password: String): String = SecretBox.encrypt(password)

    fun password(p: FtpProfile): String = SecretBox.decrypt(p.secret)
}

/** Шифрование паролей ключом из Android Keystore (AES-GCM). Ключ не покидает устройство. */
object SecretBox {
    private const val ALIAS = "gryphonis_ftp_secret"
    private const val ENC = "enc1:"
    private const val RAW = "raw:"

    private fun key(): SecretKey? = try {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val existing = ks.getKey(ALIAS, null) as? SecretKey
        if (existing != null) {
            existing
        } else {
            val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            gen.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build()
            )
            gen.generateKey()
        }
    } catch (e: Exception) {
        null
    }

    private fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun unb64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)

    fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val k = key()
        if (k != null) {
            try {
                val c = Cipher.getInstance("AES/GCM/NoPadding")
                c.init(Cipher.ENCRYPT_MODE, k)
                val ct = c.doFinal(plain.toByteArray(Charsets.UTF_8))
                return ENC + b64(c.iv) + ":" + b64(ct)
            } catch (_: Exception) {
            }
        }
        return RAW + b64(plain.toByteArray(Charsets.UTF_8))
    }

    fun decrypt(stored: String): String {
        if (stored.isEmpty()) return ""
        return try {
            when {
                stored.startsWith(ENC) -> {
                    val parts = stored.removePrefix(ENC).split(":")
                    val k = key() ?: return ""
                    val c = Cipher.getInstance("AES/GCM/NoPadding")
                    c.init(Cipher.DECRYPT_MODE, k, GCMParameterSpec(128, unb64(parts[0])))
                    String(c.doFinal(unb64(parts[1])), Charsets.UTF_8)
                }
                stored.startsWith(RAW) -> String(unb64(stored.removePrefix(RAW)), Charsets.UTF_8)
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }
}
