package org.thoughtcrime.securesms.payments.confirm;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.payments.preferences.cashu.CashuSendHelper;
import org.whispersystems.signalservice.api.payments.Money;

public final class ConfirmCashuInteractor {
  private static final String TAG = Log.tag(ConfirmCashuInteractor.class);
  private final Context appContext;

  public ConfirmCashuInteractor(@NonNull Context context) {
    this.appContext = context.getApplicationContext();
  }

  public void confirm(@NonNull ConfirmPaymentState state, @NonNull java.util.function.Consumer<ConfirmPaymentViewModel.CashuConfirmResult> consumer) {
    new Thread(() -> {
      try {
        long sats = moneyToSats(state.getAmount());
        String token = CashuSendHelper.createTokenBlocking(appContext, sats, state.getNote());
        consumer.accept(new ConfirmPaymentViewModel.CashuConfirmResult.Success());
      } catch (Throwable t) {
        Log.w(TAG, "confirm failed", t);
        consumer.accept(new ConfirmPaymentViewModel.CashuConfirmResult.Error(t.getMessage()));
      }
    }).start();
  }

  private long moneyToSats(@NonNull Money money) {
    try {
      String s = money.toString(org.whispersystems.signalservice.api.payments.FormatterOptions.builder().withoutUnit().build());
      return org.thoughtcrime.securesms.payments.create.CashuAmountAccessor.getAmountSats(s);
    } catch (Throwable t) {
      return 0L;
    }
  }
}
