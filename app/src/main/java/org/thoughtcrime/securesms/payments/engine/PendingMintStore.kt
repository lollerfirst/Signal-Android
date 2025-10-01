package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.signal.core.util.logging.Log
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Simple file-backed store for pending Cashu mint quotes.
 * These are created when we request a mint quote and are removed after successful mint.
 */
class PendingMintStore(private val appContext: Context) {
  companion object {
    private val TAG = Log.tag(PendingMintStore::class.java)
    private const val FILE_NAME = "cashu_pending_mint_quotes.json"
  }

  private val lock = ReentrantLock()
  private val file: File by lazy { appContext.filesDir.resolve(FILE_NAME) }

  data class PendingMintQuote(
    val id: String?,
    val invoice: String?,
    val amountSats: Long,
    val expiresAtMs: Long,
    val mintUrl: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    var minted: Boolean = false,
    var lastError: String? = null
  )

  fun add(quote: PendingMintQuote) {
    lock.withLock {
      val arr = readArray()
      arr.put(toJson(quote))
      writeArray(arr)
    }
  }

  fun list(): List<PendingMintQuote> = lock.withLock {
    val arr = readArray()
    (0 until arr.length()).mapNotNull { fromJson(arr.optJSONObject(it)) }
  }

  fun markMinted(id: String?) {
    if (id == null) return
    lock.withLock {
      val arr = readArray()
      val newArr = JSONArray()
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i)
        if (obj != null) {
          val match = obj.optString("id", null) == id
          if (!match) newArr.put(obj) // drop minted
        }
      }
      writeArray(newArr)
    }
  }

  fun updateError(id: String?, error: String?) {
    lock.withLock {
      val arr = readArray()
      for (i in 0 until arr.length()) {
        val obj = arr.optJSONObject(i) ?: continue
        if (id != null && obj.optString("id", null) == id) {
          obj.put("lastError", error)
          break
        }
      }
      writeArray(arr)
    }
  }

  private fun toJson(q: PendingMintQuote): JSONObject {
    return JSONObject().apply {
      put("id", q.id)
      put("invoice", q.invoice)
      put("amountSats", q.amountSats)
      put("expiresAtMs", q.expiresAtMs)
      put("mintUrl", q.mintUrl)
      put("createdAtMs", q.createdAtMs)
      put("minted", q.minted)
      put("lastError", q.lastError)
    }
  }

  private fun fromJson(o: JSONObject?): PendingMintQuote? {
    if (o == null) return null
    return try {
      PendingMintQuote(
        id = o.optString("id", null),
        invoice = o.optString("invoice", null),
        amountSats = o.optLong("amountSats", 0L),
        expiresAtMs = o.optLong("expiresAtMs", 0L),
        mintUrl = o.optString("mintUrl", ""),
        createdAtMs = o.optLong("createdAtMs", System.currentTimeMillis()),
        minted = o.optBoolean("minted", false),
        lastError = if (o.has("lastError")) o.optString("lastError") else null
      )
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to parse pending mint json", t)
      null
    }
  }

  private fun readArray(): JSONArray {
    return try {
      if (!file.exists()) return JSONArray()
      val text = file.readText()
      if (text.isBlank()) JSONArray() else JSONArray(text)
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to read pending mint store", t)
      JSONArray()
    }
  }

  private fun writeArray(arr: JSONArray) {
    try {
      file.writeText(arr.toString())
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to write pending mint store", t)
    }
  }
}
