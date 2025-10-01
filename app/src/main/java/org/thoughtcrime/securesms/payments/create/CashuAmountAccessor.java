package org.thoughtcrime.securesms.payments.create;

import androidx.annotation.NonNull;

public final class CashuAmountAccessor {
  private CashuAmountAccessor() {}

  public static long getAmountSats(@NonNull CreatePaymentViewModel vm) {
    try {
      InputState state = vm.getInputState().getValue();
      if (state != null) {
        String money = state.getMoneyAmount();
        if (money != null && !money.isEmpty()) return Long.parseLong(money);
        String fiat = state.getFiatAmount();
        if (fiat != null && !fiat.isEmpty()) return Long.parseLong(fiat);
      }
      return 0L;
    } catch (Throwable t) {
      return 0L;
    }
  }
}
