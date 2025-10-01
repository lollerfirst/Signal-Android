package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import kotlinx.coroutines.runBlocking

/**
 * Synchronous (blocking) helpers for Java callers to interact with PaymentsEngine for Cashu.
 * Do NOT call on main thread unless invoking on a background thread.
 */
object CashuUiInteractor {
  @JvmStatic
  fun requestMintQuoteBlocking(context: Context, amountSats: Long): Result<MintQuote> = runBlocking {
    PaymentsEngineProvider.get(context).requestMintQuote(amountSats)
  }

  @JvmStatic
  fun createSendTokenBlocking(context: Context, amountSats: Long, memo: String? = null): Result<String> = runBlocking {
    PaymentsEngineProvider.get(context).createSendToken(amountSats, memo)
  }
}
