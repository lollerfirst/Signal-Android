package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.signal.core.util.logging.Log
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Simple file-backed history for completed Cashu top-ups.
 */
class CashuHistoryStore(private val appContext: Context) {
  companion object {
    private val TAG = Log.tag(CashuHistoryStore::class.java)
    private const val FILE_NAME = "cashu_completed_topups.json"
  }

  data class CompletedTopUp(
    val id: String?,
    val amountSats: Long,
    val timestampMs: Long
  )

  private val lock = ReentrantLock()
  private val file: File by lazy { appContext.filesDir.resolve(FILE_NAME) }

  fun add(entry: CompletedTopUp) {
    lock.withLock {
      val arr = readArray()
      arr.put(toJson(entry))
      writeArray(arr)
    }
  }

  fun list(): List<CompletedTopUp> = lock.withLock {
    val arr = readArray()
    (0 until arr.length()).mapNotNull { fromJson(arr.optJSONObject(it)) }
  }

  private fun toJson(e: CompletedTopUp): JSONObject = JSONObject().apply {
    put("id", e.id)
    put("amountSats", e.amountSats)
    put("timestampMs", e.timestampMs)
  }

  private fun fromJson(o: JSONObject?): CompletedTopUp? {
    if (o == null) return null
    return try {
      CompletedTopUp(
        id = o.optString("id", null),
        amountSats = o.optLong("amountSats", 0L),
        timestampMs = o.optLong("timestampMs", System.currentTimeMillis())
      )
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to parse completed topup", t)
      null
    }
  }

  private fun readArray(): JSONArray {
    return try {
      if (!file.exists()) return JSONArray()
      val text = file.readText()
      if (text.isBlank()) JSONArray() else JSONArray(text)
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to read cashu history", t)
      JSONArray()
    }
  }

  private fun writeArray(arr: JSONArray) {
    try {
      file.writeText(arr.toString())
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to write cashu history", t)
    }
  }
}
