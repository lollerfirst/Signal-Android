package org.thoughtcrime.securesms.payments.engine

import android.content.Context
import org.thoughtcrime.securesms.keyvalue.SignalStore

object PaymentsEngineProvider {
  @Volatile private var engine: PaymentsEngine? = null

  fun get(context: Context): PaymentsEngine {
    val existing = engine
    if (existing != null) return existing

    val enabled = SignalStore.payments.cashuEnabled()
    val created = if (enabled) CashuEngine(context.applicationContext) else MobileCoinEngineAdapter(context.applicationContext)
    engine = created
    return created
  }

  fun reset() {
    engine = null
  }
}
