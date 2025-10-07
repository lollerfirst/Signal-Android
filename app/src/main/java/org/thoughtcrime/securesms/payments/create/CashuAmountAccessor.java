package org.thoughtcrime.securesms.payments.create;

import androidx.annotation.NonNull;

public final class CashuAmountAccessor {
  private CashuAmountAccessor() {}

  public static long getAmountSats(@NonNull String moneyAmountString) {
    try {
      if (moneyAmountString == null) return 0L;
      // Sanitize: convert any unicode digits to ASCII, strip non-digits (remove grouping, spaces, punctuation)
      StringBuilder digits = new StringBuilder(moneyAmountString.length());
      for (int i = 0; i < moneyAmountString.length(); i++) {
        char c = moneyAmountString.charAt(i);
        if (Character.isDigit(c)) {
          int val = Character.getNumericValue(c);
          if (val >= 0 && val <= 9) digits.append((char) ('0' + val));
        }
      }
      if (digits.length() == 0) return 0L;
      return Long.parseLong(digits.toString());
    } catch (Throwable t) { return 0L; }
  }
}
