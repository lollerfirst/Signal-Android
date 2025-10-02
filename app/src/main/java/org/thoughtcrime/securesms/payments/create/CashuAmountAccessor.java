package org.thoughtcrime.securesms.payments.create;

import androidx.annotation.NonNull;

public final class CashuAmountAccessor {
  private CashuAmountAccessor() {}

  public static long getAmountSats(@NonNull String moneyAmountString) {
    try {
      if (moneyAmountString == null || moneyAmountString.isEmpty()) return 0L;
      return Long.parseLong(moneyAmountString);
    } catch (Throwable t) { return 0L; }
  }
}
