package com.kirdev.pulsedeckpanel.data.local

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import com.kirdev.pulsedeckpanel.data.model.PairedPc
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Paired PCs (several are allowed, one is active) and each PC's access token. Tokens are
 * encrypted with an AES key that lives in the Android Keystore and never leaves it; app data
 * backup is disabled. A PC is identified by its certificate fingerprint.
 */
class PairingStore(context: Context) {
    private val prefs = context.getSharedPreferences("pairing", Context.MODE_PRIVATE)
    private val lock = Any()

    init {
        migrateSingle()
    }

    /** The active PC, or null when nothing is paired. */
    fun load(): PairedPc? = synchronized(lock) {
        val all = all()
        all.firstOrNull { it.id == prefs.getString(ACTIVE, null) } ?: all.firstOrNull()
    }

    fun all(): List<PairedPc> = synchronized(lock) { records().map { it.toPc() } }

    /** Token of the active PC. */
    fun token(): String? = synchronized(lock) {
        val id = load()?.id ?: return null
        records().firstOrNull { it.optString("id") == id }?.optString("token")?.let(::decrypt)
    }

    /** Adds or replaces the PC (same certificate = same PC) and makes it active. */
    fun save(pc: PairedPc, token: String) = synchronized(lock) {
        val list = records().filterNot { it.optString("id") == pc.id }.toMutableList()
        list += JSONObject().put("id", pc.id).put("name", pc.name).put("host", pc.host).put("port", pc.port)
            .put("fingerprint", pc.fingerprint).put("token", encrypt(token))
        write(list, pc.id)
    }

    fun activate(id: String) = synchronized(lock) {
        if (records().any { it.optString("id") == id }) prefs.edit().putString(ACTIVE, id).apply()
    }

    /** Removes the PC and its token from this phone (the PC itself revokes on its «Телефон» screen). */
    fun forget(id: String) = synchronized(lock) {
        val list = records().filterNot { it.optString("id") == id }
        write(list, if (prefs.getString(ACTIVE, null) == id) list.firstOrNull()?.optString("id") else prefs.getString(ACTIVE, null))
    }

    /** The active PC got a new address (DHCP); the pinned certificate stays the same. */
    fun updateHost(host: String, port: Int) = synchronized(lock) {
        val id = load()?.id ?: return
        val list = records().map { if (it.optString("id") == id) it.put("host", host).put("port", port) else it }
        write(list, id)
    }

    /** The active PC revoked this phone: forget it. */
    fun clear() {
        load()?.let { forget(it.id) }
    }

    private fun records(): List<JSONObject> {
        val array = runCatching { JSONArray(prefs.getString(PCS, "[]")) }.getOrDefault(JSONArray())
        return (0 until array.length()).mapNotNull { array.optJSONObject(it) }
    }

    private fun write(list: List<JSONObject>, active: String?) {
        prefs.edit().putString(PCS, JSONArray(list).toString()).putString(ACTIVE, active).apply()
    }

    private fun JSONObject.toPc() = PairedPc(optString("id"), optString("name"), optString("host"),
        optInt("port", 8767), optString("fingerprint"))

    /** Version 1.0 builds kept one PC in flat keys: move it into the list. */
    private fun migrateSingle() = synchronized(lock) {
        val host = prefs.getString("host", null) ?: return
        val fingerprint = prefs.getString("fingerprint", null)
        val sealed = prefs.getString("token", null)
        if (fingerprint != null && sealed != null && records().isEmpty()) {
            val id = PairedPc.idOf(fingerprint)
            val record = JSONObject().put("id", id).put("name", prefs.getString("name", host)).put("host", host)
                .put("port", prefs.getInt("port", 8767)).put("fingerprint", fingerprint).put("token", sealed)
            write(listOf(record), id)
        }
        prefs.edit().remove("host").remove("fingerprint").remove("token").remove("name").remove("port").apply()
    }

    private fun encrypt(token: String): String {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
        return Base64.encodeToString(cipher.iv + cipher.doFinal(token.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    private fun decrypt(sealed: String): String? = runCatching {
        val data = Base64.decode(sealed, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, data.copyOfRange(0, IV_BYTES)))
        String(cipher.doFinal(data, IV_BYTES, data.size - IV_BYTES), Charsets.UTF_8)
    }.getOrNull()

    private fun key(): SecretKey {
        val store = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (store.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build()
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val ALIAS = "pulsedeck_panel_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val IV_BYTES = 12
        const val PCS = "pcs"
        const val ACTIVE = "active"
    }
}
