package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.signal.core.util.logging.Log

/**
 * Background watcher that checks the status of pending mint quotes and mints them when paid.
 * - Starts when Add Money screen creates a quote
 * - Also exposes a one-shot check method to be called on Home refresh/open
 */
object MintWatcher {
  private val TAG = Log.tag(MintWatcher::class.java)
  private var job: Job? = null

  fun start(context: Context) {
    if (job?.isActive == true) return
    val app = context.applicationContext
    job = CoroutineScope(Dispatchers.IO).launch {
      val store = PendingMintStore(app)
      while (isActive) {
        try {
          checkOnce(app, store)
        } catch (t: Throwable) {
          Log.w(TAG, "mint watcher tick failed", t)
        }
        delay(5_000L)
      }
    }
  }

  fun stop() {
    job?.cancel()
    job = null
  }

  suspend fun checkOnce(context: Context, provided: PendingMintStore? = null) {
    val engine = PaymentsEngineProvider.get(context)
    val store = provided ?: PendingMintStore(context)
    val pending = store.list()
    if (pending.isEmpty()) return

    // We don't yet have a poll API, so try minting with known secret/invoice where applicable
    for (q in pending) {
      val key = q.id ?: q.invoice
      if (key.isNullOrBlank()) continue
      val result = try { engine.mintPaidQuote(key) } catch (t: Throwable) { Result.failure(t) }
      if (result.isSuccess) {
        Log.i(TAG, "Mint succeeded for quote id=${'$'}{q.id}")
        store.markMinted(q.id)
      } else {
        store.updateError(q.id, result.exceptionOrNull()?.message)
      }
    }
  }
}
