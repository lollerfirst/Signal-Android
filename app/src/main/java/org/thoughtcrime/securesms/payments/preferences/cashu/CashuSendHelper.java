package org.thoughtcrime.securesms.payments.preferences.cashu;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.payments.create.CreatePaymentViewModel;
import org.thoughtcrime.securesms.payments.engine.CashuUiInteractor;

public final class CashuSendHelper {
  private CashuSendHelper() {}

  public static long getCurrentAmountSats(@NonNull CreatePaymentViewModel vm) {
    // For now, treat the money amount as fiat or MOB; we need a conversion to sats. As a placeholder, assume the numeric string is sats.
    // TODO: Replace with proper sats extraction once UI supports sats entry.
    try {
      String s = vm.getInputState().getValue().getMoneyAmount();
      return Long.parseLong(s);
    } catch (Throwable t) {
      return 0L;
    }
  }

  public static @NonNull String createTokenBlocking(@NonNull Context context, long sats, CharSequence memo) {
    String note = memo == null ? null : memo.toString();
    var result = CashuUiInteractor.createSendTokenBlocking(context, sats, note);
    return result.isSuccess() ? result.getOrNull() : "cashu:token:error";
  }
}
