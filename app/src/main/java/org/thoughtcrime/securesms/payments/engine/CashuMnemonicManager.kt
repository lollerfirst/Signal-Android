package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import org.cashudevkit.generateMnemonic
import org.json.JSONObject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.KeyStoreHelper
import java.io.File

/**
 * Stores/restores a Cashu wallet mnemonic sealed with Android Keystore.
 * Uses CDK's native mnemonic generation, completely independent of MobileCoin.
 */
class CashuMnemonicManager(private val appContext: Context) {
  
  companion object {
    private val TAG = Log.tag(CashuMnemonicManager::class.java)
  }
  
  data class SealedMnemonic(val sealed: String)

  private val file = File(appContext.filesDir, "cashu_wallet.json")

  /**
   * Get or create a Cashu wallet mnemonic.
   * 
   * Priority:
   * 1. Load existing encrypted Cashu mnemonic if exists
   * 2. Generate new CDK mnemonic using generateMnemonic()
   * 
   * IMPORTANT: MobileCoin and Cashu use DIFFERENT key derivation schemes.
   * A MobileCoin mnemonic CANNOT be used to restore Cashu funds!
   * They must remain separate. Fund migration should be handled separately.
   */
  fun getOrCreateMnemonic(): String {
    // If we already have a Cashu mnemonic, load it
    if (file.exists()) {
      Log.i(TAG, "Loading existing Cashu mnemonic from encrypted storage")
      return load()
    }
    
    // Generate new CDK mnemonic (completely independent from MobileCoin)
    Log.i(TAG, "Generating new Cashu wallet mnemonic using CDK")
    val mnemonic = generateMnemonic()
    
    // Encrypt and store
    val sealed = KeyStoreHelper.seal(mnemonic.toByteArray())
    write(SealedMnemonic(sealed = sealed.serialize()))
    
    return mnemonic
  }
  
  /**
   * Get the current mnemonic if it exists, or null
   */
  fun getMnemonicOrNull(): String? {
    return if (file.exists()) {
      try {
        load()
      } catch (e: Throwable) {
        Log.w(TAG, "Failed to load Cashu mnemonic", e)
        null
      }
    } else {
      null
    }
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
