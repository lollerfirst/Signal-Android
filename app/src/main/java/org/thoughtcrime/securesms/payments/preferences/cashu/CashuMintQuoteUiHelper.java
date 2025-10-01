package org.thoughtcrime.securesms.payments.preferences.cashu;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.payments.engine.CashuUiInteractor;
import org.thoughtcrime.securesms.payments.engine.MintQuote;

/**
 * Helper to build a display string/QR content for mint quotes.
 * Now returns the Lightning invoice (BOLT11) when available.
 */
public final class CashuMintQuoteUiHelper {
  private static final String TAG = Log.tag(CashuMintQuoteUiHelper.class);

  private CashuMintQuoteUiHelper() {}

  /**
   * Backwards-compatible default (10k sats). Prefer getMintQuoteQrForAmount.
   */
  public static @NonNull String getOrCreateMintQuoteQr(@NonNull Context context) {
    return getMintQuoteQrForAmount(context, 10_000L);
  }

  public static MintQuote requestMintQuote(@NonNull Context context, long amountSats) {
    try {
      return CashuUiInteractor.requestMintQuoteBlocking(context, amountSats);
    } catch (Throwable t) {
      Log.w(TAG, "mint quote failed", t);
      return null;
    }
  }

  public static @NonNull String getMintQuoteQrForAmount(@NonNull Context context, long amountSats) {
    try {
      MintQuote quote = CashuUiInteractor.requestMintQuoteBlocking(context, amountSats);
      if (quote != null && quote.getInvoiceBolt11() != null && !quote.getInvoiceBolt11().isEmpty()) {
        return quote.getInvoiceBolt11();
      }
      if (quote != null) {
        return "cashu:mint-quote?mint=" + quote.getMintUrl() + "&amount=" + quote.getAmountSats() + "&total=" + quote.getTotalSats();
      }
      return "cashu:mint-quote:unavailable";
    } catch (Throwable t) {
      Log.w(TAG, "mint quote failed", t);
      return "cashu:mint-quote:error";
    }
  }
}
