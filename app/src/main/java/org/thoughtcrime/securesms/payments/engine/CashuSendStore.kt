package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import org.signal.core.util.logging.Log
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * File-backed store for locally recorded Cashu outgoing sends (ecash).
 */
class CashuSendStore(private val appContext: Context) {
  companion object {
    private val TAG = Log.tag(CashuSendStore::class.java)
    private const val FILE_NAME = "cashu_sent_ecash.json"
  }

  data class Sent(
    val id: String?,
    val amountSats: Long,
    val timestampMs: Long,
    val memo: String?
  )

  private val lock = ReentrantLock()
  private val file: File by lazy { appContext.filesDir.resolve(FILE_NAME) }

  fun add(entry: Sent) {
    lock.withLock {
      val arr = readArray()
      arr.put(toJson(entry))
      writeArray(arr)
    }
  }

  fun list(): List<Sent> = lock.withLock {
    val arr = readArray()
    (0 until arr.length()).mapNotNull { fromJson(arr.optJSONObject(it)) }
  }

  private fun toJson(e: Sent): JSONObject = JSONObject().apply {
    put("id", e.id)
    put("amountSats", e.amountSats)
    put("timestampMs", e.timestampMs)
    put("memo", e.memo)
  }

  private fun fromJson(o: JSONObject?): Sent? {
    if (o == null) return null
    return try {
      Sent(
        id = o.optString("id", null),
        amountSats = o.optLong("amountSats", 0L),
        timestampMs = o.optLong("timestampMs", System.currentTimeMillis()),
        memo = if (o.has("memo")) o.optString("memo") else null
      )
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to parse sent ecash entry", t)
      null
    }
  }

  private fun readArray(): JSONArray {
    return try {
      if (!file.exists()) return JSONArray()
      val text = file.readText()
      if (text.isBlank()) JSONArray() else JSONArray(text)
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to read CashuSendStore", t)
      JSONArray()
    }
  }

  private fun writeArray(arr: JSONArray) {
    try {
      file.writeText(arr.toString())
    } catch (t: Throwable) {
      Log.w(TAG, "Failed to write CashuSendStore", t)
    }
  }
}
