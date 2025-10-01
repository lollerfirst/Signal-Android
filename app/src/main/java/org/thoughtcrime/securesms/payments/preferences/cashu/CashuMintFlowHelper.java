package org.thoughtcrime.securesms.payments.preferences.cashu;

import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.Nullable;

import org.cashudevkit.ActiveSubscription;
import org.cashudevkit.NotificationPayload;
import org.cashudevkit.SubscribeParams;
import org.cashudevkit.SubscriptionKind;
import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.payments.engine.CashuUiInteractor;
import org.thoughtcrime.securesms.payments.engine.MintQuote;
import org.thoughtcrime.securesms.payments.engine.PaymentsEngineProvider;

/**
 * Minimal helper to request a mint quote and return its Lightning invoice (BOLT11) string
 * by subscribing to BOLT11_MINT_QUOTE updates until we observe a request/invoice.
 *
 * NOTE: This is a temporary blocking helper. In production, use a ViewModel + coroutine flow and
 * parse NotificationPayload subtypes once exposed by CDK.
 */
public final class CashuMintFlowHelper {
  private static final String TAG = Log.tag(CashuMintFlowHelper.class);

  private CashuMintFlowHelper() {}

  public static @Nullable String getInvoiceBlocking(Context context, long amountSats) {
    try {
      // 1) Request quote to trigger mint creation
      MintQuote quote = CashuUiInteractor.requestMintQuoteBlocking(context, amountSats);
      if (quote == null) return null;

      // 2) Attempt to subscribe via CDK for quote updates (requires engine exposure)
      org.thoughtcrime.securesms.payments.engine.CashuEngine engine = (org.thoughtcrime.securesms.payments.engine.CashuEngine) PaymentsEngineProvider.get(context);
      org.cashudevkit.Wallet cdkWallet = engine.getCdkWalletUnsafe();
      if (cdkWallet == null) return null;

      SubscribeParams params = new SubscribeParams(SubscriptionKind.BOLT11_MINT_QUOTE, java.util.Collections.singletonList(quote.getMintUrl()), quote.getMintUrl());
      ActiveSubscription sub = (ActiveSubscription) cdkWallet.subscribe(params);
      try {
        NotificationPayload payload = (NotificationPayload) sub.tryRecv();
        if (payload == null) payload = (NotificationPayload) sub.recv();
        // TODO: Parse payload into concrete subtype when available (e.g., MintQuoteUpdate with request/invoice)
        // Fallback: use initial quote.request if CDK mapped it (not yet exposed in our MintQuote)
        if (!TextUtils.isEmpty(quote.getMintUrl())) {
          return quote.getMintUrl();
        }
      } finally {
        sub.close();
      }
    } catch (Throwable t) {
      Log.w(TAG, "getInvoiceBlocking failed", t);
    }
    return null;
  }
}
