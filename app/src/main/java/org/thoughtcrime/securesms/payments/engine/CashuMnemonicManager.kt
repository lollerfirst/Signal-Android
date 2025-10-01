package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import org.json.JSONObject
import org.thoughtcrime.securesms.crypto.KeyStoreHelper
import java.io.File

/**
 * Stores/restores a wallet mnemonic sealed with Android Keystore.
 * NOTE: Using a locally generated base64 entropy string as mnemonic placeholder to avoid hard dependency on CDK helper.
 */
class CashuMnemonicManager(private val appContext: Context) {
  data class SealedMnemonic(val sealed: String)

  private val file = File(appContext.filesDir, "cashu_wallet.json")

  fun getOrCreateMnemonic(): String {
    if (file.exists()) return load()
    val mnemonic = generateLocalMnemonic()
    val sealed = KeyStoreHelper.seal(mnemonic.toByteArray())
    val payload = SealedMnemonic(sealed = sealed.serialize())
    write(payload)
    return mnemonic
  }

  private fun generateLocalMnemonic(): String {
    val rnd = java.security.SecureRandom()
    val bytes = ByteArray(32)
    rnd.nextBytes(bytes)
    return android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
  }

  private fun load(): String {
    val text = file.readText(Charsets.UTF_8)
    val obj = JSONObject(text)
    val sealedStr = obj.getString("mnemonic")
    val sealed = KeyStoreHelper.SealedData.fromString(sealedStr)
    val bytes = KeyStoreHelper.unseal(sealed)
    return bytes.toString(Charsets.UTF_8)
  }

  private fun write(payload: SealedMnemonic) {
    val obj = JSONObject().put("mnemonic", payload.sealed)
    file.writeText(obj.toString(), Charsets.UTF_8)
  }
}
