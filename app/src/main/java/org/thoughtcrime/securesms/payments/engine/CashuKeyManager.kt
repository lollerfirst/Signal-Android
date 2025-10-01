package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import org.json.JSONObject
import org.thoughtcrime.securesms.crypto.KeyStoreHelper
import java.io.File
import java.nio.charset.StandardCharsets
import java.security.SecureRandom

/**
 * Minimal P2PK key manager for Cashu.
 * NOTE: This is a placeholder implementation that generates a random 32-byte private key
 * and derives a fake pubkey as hex(SHA-256(priv)). Replace with real Ed25519 P2PK when wiring CDK.
 */
class CashuKeyManager(private val appContext: Context) {

  data class P2pk(val pubkeyHex: String, val sealedPriv: String)

  private val keysFile: File = File(appContext.filesDir, "cashu_keys.json")

  fun getOrCreateP2pk(): P2pk {
    if (keysFile.exists()) {
      return read()
    }
    val created = create()
    write(created)
    return created
  }

  private fun create(): P2pk {
    val rng = SecureRandom()
    val priv = ByteArray(32)
    rng.nextBytes(priv)
    val pub = sha256(priv)
    val sealed = KeyStoreHelper.seal(priv)
    val sealedStr = sealed.serialize()
    return P2pk(pubkeyHex = toHex(pub), sealedPriv = sealedStr)
  }

  private fun write(p: P2pk) {
    val obj = JSONObject()
      .put("pub", p.pubkeyHex)
      .put("priv", p.sealedPriv)
    keysFile.writeText(obj.toString(), Charsets.UTF_8)
  }

  private fun read(): P2pk {
    val text = keysFile.readText(Charsets.UTF_8)
    val obj = JSONObject(text)
    return P2pk(pubkeyHex = obj.getString("pub"), sealedPriv = obj.getString("priv"))
  }

  companion object {
    private fun toHex(b: ByteArray): String = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

    private fun sha256(data: ByteArray): ByteArray {
      val md = java.security.MessageDigest.getInstance("SHA-256")
      return md.digest(data)
    }
  }
}
