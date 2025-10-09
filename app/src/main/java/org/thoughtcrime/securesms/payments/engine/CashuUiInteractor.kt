package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import kotlinx.coroutines.runBlocking

/**
 * Synchronous (blocking) helpers for Java callers to interact with PaymentsEngine for Cashu.
 * Do NOT call on main thread unless invoking on a background thread.
 * All methods handle errors gracefully and return null/false/empty on failure.
 */
object CashuUiInteractor {
  private const val TAG = "CashuUiInteractor"
  
  @JvmStatic
  fun requestMintQuoteBlocking(context: Context, amountSats: Long): MintQuote? = runBlocking {
    runCatching {
      PaymentsEngineProvider.get(context).requestMintQuote(amountSats).getOrNull()
    }.getOrElse { throwable ->
      org.signal.core.util.logging.Log.w(TAG, "Failed to request mint quote", throwable)
      null
    }
  }

  @JvmStatic
  fun requestMeltQuoteBlocking(context: Context, invoiceBolt11: String): MeltQuote? = runBlocking {
    runCatching {
      PaymentsEngineProvider.get(context).requestMeltQuote(invoiceBolt11).getOrNull()
    }.getOrElse { throwable ->
      org.signal.core.util.logging.Log.w(TAG, "Failed to request melt quote", throwable)
      null
    }
  }

  @JvmStatic
  fun meltBlocking(context: Context, quote: MeltQuote): Boolean = runBlocking {
    runCatching {
      PaymentsEngineProvider.get(context).melt(quote).isSuccess
    }.getOrElse { throwable ->
      org.signal.core.util.logging.Log.w(TAG, "Failed to melt", throwable)
      false
    }
  }

  @JvmStatic
  fun createSendTokenBlocking(context: Context, amountSats: Long, memo: String? = null): String? = runBlocking {
    runCatching {
      PaymentsEngineProvider.get(context).createSendToken(amountSats, memo).getOrNull()
    }.getOrElse { throwable ->
      org.signal.core.util.logging.Log.w(TAG, "Failed to create send token", throwable)
      null
    }
  }

  @JvmStatic
  fun mintPaidQuoteBlocking(context: Context, secretKeyOrId: String): Boolean = runBlocking {
    runCatching {
      PaymentsEngineProvider.get(context).mintPaidQuote(secretKeyOrId).isSuccess
    }.getOrElse { throwable ->
      org.signal.core.util.logging.Log.w(TAG, "Failed to mint paid quote", throwable)
      false
    }
  }

  @JvmStatic
  fun listHistoryBlocking(context: Context, offset: Int, limit: Int): List<Tx> = runBlocking {
    runCatching {
      PaymentsEngineProvider.get(context).listHistory(offset, limit)
    }.getOrElse { throwable ->
      org.signal.core.util.logging.Log.w("CashuUiInteractor", "Failed to list history", throwable)
      emptyList()
    }
  }
}

