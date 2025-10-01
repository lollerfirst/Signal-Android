package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.payments.rates.CoinbaseRatesProvider
import java.math.BigDecimal
import java.util.Currency

/**
 * Thin UI helper to bridge PaymentsEngine into existing MobileCoin-centric UI.
 * Provides sats balance and optional fiat conversion string.
 */
class CashuUiRepository(private val appContext: Context) {
  private val rates = CoinbaseRatesProvider()

  private fun engine(): PaymentsEngine = PaymentsEngineProvider.get(appContext)

  suspend fun getSpendableSats(): Long = withContext(Dispatchers.IO) {
    if (!SignalStore.payments.cashuEnabled()) return@withContext 0L
    runCatching { engine().getBalance().spendableSats }.getOrDefault(0L)
  }

  fun getSpendableSatsBlocking(): Long = runBlocking { getSpendableSats() }

  suspend fun satsToFiatString(sats: Long, currency: Currency = SignalStore.payments.currentCurrency()): String = withContext(Dispatchers.IO) {
    val result = rates.satsToFiat(sats, currency)
    return@withContext result.fold(
      onSuccess = { amount: BigDecimal -> "~ ${'$'}{amount.stripTrailingZeros().toPlainString()} ${currency.currencyCode}" },
      onFailure = { _ -> "~ -- ${currency.currencyCode}" }
    )
  }

  fun satsToFiatStringBlocking(sats: Long, currency: Currency = SignalStore.payments.currentCurrency()): String = runBlocking { satsToFiatString(sats, currency) }
}
