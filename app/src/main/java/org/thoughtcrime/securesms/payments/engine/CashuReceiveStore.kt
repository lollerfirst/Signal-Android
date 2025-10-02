package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.signal.core.util.logging.Log
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * File-backed store for locally recorded Cashu incoming receipts (ecash).
 */
class CashuReceiveStore(private val appContext: Context) {
  companion object {
    private val TAG = Log.tag(CashuReceiveStore::class.java)
    private const val FILE_NAME = "cashu_received_ecash.json"
  }

  data class Received(
    val id: String?,
    val amountSats: Long,
    val timestampMs: Long,
    val memo: String?
  )

  private val lock = ReentrantLock()
  private val file: File by lazy { appContext.filesDir.resolve(FILE_NAME) }

  fun add(entry: Received) {
    lock.withLock {
      val arr = readArray()
      arr.put(toJson(entry))
      writeArray(arr)
    }
  }

  fun list(): List<Received> = lock.withLock {
    val arr = readArray()
    (0 until arr.length()).mapNotNull { fromJson(arr.optJSONObject(it)) }
  }

  private fun toJson(e: Received): JSONObject = JSONObject().apply {
    put("id", e.id)
    put("amountSats", e.amountSats)
    put("timestampMs", e.timestampMs)
    put("memo", e.memo)
  }

  private fun fromJson(o: JSONObject?): Received? {
    if (o == null) return null
    return try {
      Received(
        id = o.optString("id", null),
        amountSats = o.optLong("amountSats", 0L),
        timestampMs = o.optLong("timestampMs", System.currentTimeMillis()),
        memo = if (o.has("memo")) o.optString("memo") else null
      )
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to parse received ecash entry", t)
      null
    }
  }

  private fun readArray(): JSONArray {
    return try {
      if (!file.exists()) return JSONArray()
      val text = file.readText()
      if (text.isBlank()) JSONArray() else JSONArray(text)
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to read CashuReceiveStore", t)
      JSONArray()
    }
  }

  private fun writeArray(arr: JSONArray) {
    try {
      file.writeText(arr.toString())
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to write CashuReceiveStore", t)
    }
  }
}
