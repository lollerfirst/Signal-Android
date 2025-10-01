package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import org.thoughtcrime.securesms.BuildConfig
import org.thoughtcrime.securesms.keyvalue.SignalStore

object PaymentsEngineProvider {
  @Volatile private var engine: PaymentsEngine? = null

  @JvmStatic
  fun get(context: Context): PaymentsEngine {
    val existing = engine
    if (existing != null) return existing

    val enabled = BuildConfig.DEBUG || SignalStore.payments.cashuEnabled()
    val created = if (enabled) CashuEngine(context.applicationContext) else MobileCoinEngineAdapter(context.applicationContext)
    engine = created
    return created
  }

  @JvmStatic
  fun reset() {
    engine = null
  }
}
