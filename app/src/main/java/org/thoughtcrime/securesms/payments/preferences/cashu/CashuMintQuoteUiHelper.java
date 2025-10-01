package org.thoughtcrime.securesms.payments.preferences.cashu;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.payments.engine.CashuUiInteractor;
import org.thoughtcrime.securesms.payments.engine.MintQuote;

/**
 * Helper to build a display string/QR content for mint quotes.
 * NOTE: For now, returns a simple text payload while we decide on the exact QR schema.
 */
public final class CashuMintQuoteUiHelper {
  private static final String TAG = Log.tag(CashuMintQuoteUiHelper.class);

  private CashuMintQuoteUiHelper() {}

  public static @NonNull String getOrCreateMintQuoteQr(@NonNull Context context) {
    try {
      long amountSats = 10_000L; // stub: 10k sats; next step: allow user to specify
      var result = CashuUiInteractor.requestMintQuoteBlocking(context, amountSats);
      if (result.isSuccess()) {
        MintQuote quote = result.getOrNull();
        if (quote != null) {
          // Placeholder QR string — replace with proper Cashu/NUT format when implementing real flows
          return "cashu:mint-quote?mint=" + quote.getMintUrl() + "&amount=" + quote.getAmountSats() + "&fee=" + quote.getFeeSats() + "&total=" + quote.getTotalSats();
        }
      }
      return "cashu:mint-quote:unavailable";
    } catch (Throwable t) {
      Log.w(TAG, "mint quote failed", t);
      return "cashu:mint-quote:error";
    }
  }
}
