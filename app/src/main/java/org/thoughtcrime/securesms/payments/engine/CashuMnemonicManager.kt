package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import org.cashudevkit.generateMnemonic
import org.json.JSONObject
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.crypto.KeyStoreHelper
import org.thoughtcrime.securesms.keyvalue.SignalStore
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
   * 2. Migrate from MobileCoin SharedPreferences if available (one-time migration)
   * 3. Generate new CDK mnemonic using generateMnemonic()
   */
  fun getOrCreateMnemonic(): String {
    // If we already have a Cashu mnemonic, load it
    if (file.exists()) {
      Log.i(TAG, "Loading existing Cashu mnemonic from encrypted storage")
      return load()
    }
    
    // Try to migrate from MobileCoin SharedPreferences (for existing users)
    val migratedMnemonic = tryMigrateFromMobileCoin()
    if (migratedMnemonic != null) {
      Log.i(TAG, "Successfully migrated MobileCoin mnemonic to Cashu encrypted storage")
      val sealed = KeyStoreHelper.seal(migratedMnemonic.toByteArray())
      write(SealedMnemonic(sealed = sealed.serialize()))
      return migratedMnemonic
    }
    
    // Generate new CDK mnemonic (no MobileCoin dependencies!)
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

  /**
   * Try to migrate existing MobileCoin entropy from SharedPreferences.
   * Returns the mnemonic string if migration is possible, null otherwise.
   */
  private fun tryMigrateFromMobileCoin(): String? {
    return try {
      val paymentsEntropy = SignalStore.payments.paymentsEntropy
      if (paymentsEntropy != null) {
        Log.i(TAG, "Found existing MobileCoin entropy, migrating to Cashu")
        // Convert MobileCoin entropy to mnemonic using their library
        // This ensures existing users don't lose their wallets
        val mnemonic = paymentsEntropy.asMnemonic().mnemonic
        mnemonic
      } else {
        null
      }
    } catch (e: Throwable) {
      Log.w(TAG, "Could not migrate MobileCoin entropy", e)
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
