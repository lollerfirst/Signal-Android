package org.thoughtcrime.securesms.payments.rates

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import org.signal.core.util.logging.Log
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Currency

/**
 * Simple FX provider using Coinbase spot price API to convert sats <-> fiat.
 * Not wired into UI yet; safe to use from background threads.
 */
interface RatesProvider {
  /**
   * Returns fiat per BTC (e.g., USD per BTC) for the given currency code.
   */
  suspend fun btcPriceFiat(currency: Currency): Result<BigDecimal>

  /**
   * Convert sats to fiat using btc price. Rounds to 2 decimals.
   */
  suspend fun satsToFiat(sats: Long, currency: Currency): Result<BigDecimal> {
    return btcPriceFiat(currency).map { pricePerBtc ->
      val btc = BigDecimal(sats).divide(BigDecimal(100_000_000L), 8, RoundingMode.DOWN)
      btc.multiply(pricePerBtc).setScale(2, RoundingMode.HALF_UP)
    }
  }
}

class CoinbaseRatesProvider(
  private val client: OkHttpClient = OkHttpClient()
) : RatesProvider {

  companion object {
    private val TAG = Log.tag(CoinbaseRatesProvider::class.java)
  }

  override suspend fun btcPriceFiat(currency: Currency): Result<BigDecimal> = withContext(Dispatchers.IO) {
    val code = currency.currencyCode.uppercase()
    val url = "https://api.coinbase.com/v2/prices/BTC-$code/spot"
    val request = Request.Builder().url(url).header("Accept", "application/json").build()
    try {
      client.newCall(request).execute().use { resp ->
        if (!resp.isSuccessful) return@withContext Result.failure(IOException("HTTP ${'$'}{resp.code}"))
        val body = resp.body?.string() ?: return@withContext Result.failure(IOException("Empty body"))
        val json = JSONObject(body)
        val amountStr = json.getJSONObject("data").getString("amount")
        val value = BigDecimal(amountStr)
        Result.success(value)
      }
    } catch (e: Throwable) {
      Log.w(TAG, "coinbase fx error", e)
      Result.failure(e)
    }
  }
}
