package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.signal.core.util.logging.Log
import org.thoughtcrime.securesms.keyvalue.SignalStore
import org.thoughtcrime.securesms.payments.rates.CoinbaseRatesProvider
import java.math.BigDecimal
import java.util.Currency

/**
 * Thin UI helper to bridge PaymentsEngine into existing MobileCoin-centric UI.
 * Provides sats balance and optional fiat conversion string.
 * 
 * WARNING: Avoid using *Blocking() methods on the main thread as they can cause ANRs.
 * Use LiveData methods instead for UI updates.
 */
class CashuUiRepository(private val appContext: Context) {
  companion object {
    private val TAG = Log.tag(CashuUiRepository::class.java)
    private const val FIAT_CACHE_DURATION_MS = 30_000L // Cache fiat conversions for 30 seconds
  }

  private val rates = CoinbaseRatesProvider()
  
  // Cache for fiat conversions to avoid repeated network calls
  private val fiatCache = mutableMapOf<Pair<Long, String>, Pair<String, Long>>()

  private fun engine(): PaymentsEngine = PaymentsEngineProvider.get(appContext)

  /**
   * Get spendable balance asynchronously (safe for main thread via LiveData).
   */
  fun getSpendableSatsLiveData(): LiveData<Long> {
    val liveData = MutableLiveData<Long>()
    GlobalScope.launch(Dispatchers.IO) {
      val sats = getSpendableSats()
      liveData.postValue(sats)
    }
    return liveData
  }

  suspend fun getSpendableSats(): Long = withContext(Dispatchers.IO) {
    if (!SignalStore.payments.cashuEnabled()) return@withContext 0L
    // Each balance fetch also tries to mint any newly-paid quotes
    try { MintWatcher.checkOnce(appContext) } catch (_: Throwable) {}
    runCatching { 
      engine().getBalance().spendableSats 
    }.getOrElse { throwable ->
      Log.w(TAG, "Failed to get balance", throwable)
      0L
    }
  }

  /**
   * DEPRECATED: Blocks the calling thread. Use getSpendableSatsLiveData() instead.
   * Only use this in background threads or ViewModels with LiveDataUtil.mapAsync().
   */
  @Deprecated("Use getSpendableSatsLiveData() to avoid blocking main thread")
  fun getSpendableSatsBlocking(): Long = runBlocking { getSpendableSats() }

  /**
   * Get fiat conversion asynchronously (safe for main thread via LiveData).
   */
  fun satsToFiatStringLiveData(sats: Long, currency: Currency = SignalStore.payments.currentCurrency()): LiveData<String> {
    val liveData = MutableLiveData<String>()
    
    // Check cache first
    val cacheKey = Pair(sats, currency.currencyCode)
    synchronized(fiatCache) {
      fiatCache[cacheKey]?.let { (cachedValue, timestamp) ->
        if (System.currentTimeMillis() - timestamp < FIAT_CACHE_DURATION_MS) {
          liveData.value = cachedValue
          return liveData
        }
      }
    }
    
    // Fetch asynchronously
    GlobalScope.launch(Dispatchers.IO) {
      val fiatString = satsToFiatString(sats, currency)
      synchronized(fiatCache) {
        fiatCache[cacheKey] = Pair(fiatString, System.currentTimeMillis())
        // Limit cache size
        if (fiatCache.size > 100) {
          val oldestKey = fiatCache.minByOrNull { it.value.second }?.key
          oldestKey?.let { fiatCache.remove(it) }
        }
      }
      liveData.postValue(fiatString)
    }
    
    // Return placeholder immediately
    liveData.value = "~ -- ${currency.currencyCode}"
    return liveData
  }
  
  /**
   * Java-friendly overload without currency parameter.
   */
  @JvmOverloads
  fun satsToFiatStringLiveData(sats: Long): LiveData<String> {
    return satsToFiatStringLiveData(sats, SignalStore.payments.currentCurrency())
  }

  suspend fun satsToFiatString(sats: Long, currency: Currency = SignalStore.payments.currentCurrency()): String = withContext(Dispatchers.IO) {
    val result = rates.satsToFiat(sats, currency)
    return@withContext result.fold(
      onSuccess = { amount: BigDecimal -> "~ $${amount.stripTrailingZeros().toPlainString()} ${currency.currencyCode}" },
      onFailure = { throwable ->
        Log.w(TAG, "Failed to convert sats to fiat", throwable)
        "~ -- ${currency.currencyCode}"
      }
    )
  }

  /**
   * DEPRECATED: Blocks the calling thread and makes network calls. 
   * Use satsToFiatStringLiveData() instead to avoid ANRs.
   * Only use this in background threads or ViewModels with LiveDataUtil.mapAsync().
   */
  @Deprecated("Use satsToFiatStringLiveData() to avoid blocking main thread")
  fun satsToFiatStringBlocking(sats: Long): String = runBlocking { satsToFiatString(sats) }
  
  /**
   * Get cached fiat conversion synchronously (does not block, returns cached or placeholder).
   * Safe to call from main thread.
   */
  fun satsToFiatStringCached(sats: Long, currency: Currency = SignalStore.payments.currentCurrency()): String {
    val cacheKey = Pair(sats, currency.currencyCode)
    synchronized(fiatCache) {
      fiatCache[cacheKey]?.let { (cachedValue, timestamp) ->
        if (System.currentTimeMillis() - timestamp < FIAT_CACHE_DURATION_MS) {
          return cachedValue
        }
      }
    }
    return "~ -- ${currency.currencyCode}"
  }
  
  /**
   * Java-friendly overload without currency parameter.
   */
  @JvmOverloads
  fun satsToFiatStringCached(sats: Long): String {
    return satsToFiatStringCached(sats, SignalStore.payments.currentCurrency())
  }
}
