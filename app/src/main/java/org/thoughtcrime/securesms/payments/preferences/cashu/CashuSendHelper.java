package org.thoughtcrime.securesms.payments.preferences.cashu;

import android.content.Context;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.payments.create.CashuAmountAccessor;
import org.thoughtcrime.securesms.payments.create.CreatePaymentViewModel;
import org.thoughtcrime.securesms.payments.engine.CashuUiInteractor;

public final class CashuSendHelper {
  private CashuSendHelper() {}

  public static long getCurrentAmountSats(@NonNull CreatePaymentViewModel vm) {
    return CashuAmountAccessor.getAmountSats(vm);
  }

  public static @NonNull String createTokenBlocking(@NonNull Context context, long sats, CharSequence memo) {
    String note = memo == null ? null : memo.toString();
    String token = CashuUiInteractor.createSendTokenBlocking(context, sats, note);
    return token != null ? token : "cashu:token:error";
  }
}
