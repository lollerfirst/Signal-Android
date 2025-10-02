package org.thoughtcrime.securesms.payments.confirm;

import android.content.Context;

import androidx.annotation.NonNull;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.database.SignalDatabase;

import org.thoughtcrime.securesms.payments.preferences.cashu.CashuSendHelper;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.whispersystems.signalservice.api.payments.Money;

/**
 * Cashu-specific confirmation path that mimics the MobileCoin confirmation UI.
 *
 * On confirm:
 *  - Prepares and confirms a Cashu token for the requested sats amount
 *  - Records the local "sent ecash" entry
 *  - Sends the token as a regular text message to the selected recipient
 */
final class ConfirmCashuRepository {
  private static final String TAG = Log.tag(ConfirmCashuRepository.class);
  private final Context appContext;

  ConfirmCashuRepository(@NonNull Context context) {
    this.appContext = context.getApplicationContext();
  }

  void confirm(@NonNull ConfirmPaymentState state, @NonNull java.util.function.Consumer<ConfirmPaymentViewModel.CashuConfirmResult> consumer) {
    try {
      long sats = moneyToSats(state.getAmount());
      if (sats <= 0) {
        consumer.accept(new ConfirmPaymentViewModel.CashuConfirmResult.Error("Invalid sats"));
        return;
      }

      String note = state.getNote();
      String token = CashuSendHelper.createTokenBlocking(appContext, sats, note);
      // Persist local sent entry for Recent Activity, embed recipient metadata in memo: "Sent ecash|rid:<id>|name:<name>"
      String sentMemo = "Sent ecash";
      if (state.getPayee().hasRecipientId()) {
        try {
          RecipientId rid = state.getPayee().requireRecipientId();
          String display = Recipient.resolved(rid).getDisplayName(appContext);
          // simple escaping for separator character '|'
          String safeName = display.replace("|", "\u2758");
          sentMemo = sentMemo + "|rid:" + rid.serialize() + "|name:" + safeName;
        } catch (Throwable ignore) {}
      }
      try {
        new org.thoughtcrime.securesms.payments.engine.CashuSendStore(appContext).add(
            new org.thoughtcrime.securesms.payments.engine.CashuSendStore.Sent(null, sats, System.currentTimeMillis(), sentMemo)
        );
      } catch (Throwable t) {
        Log.w(TAG, "Failed to record CashuSendStore entry", t);
      }

      // Send token as a Signal message using existing pipeline
      if (!state.getPayee().hasRecipientId()) {
        consumer.accept(new ConfirmPaymentViewModel.CashuConfirmResult.Error("No recipient"));
        return;
      }
      RecipientId recipientId = state.getPayee().requireRecipientId();
      Recipient recipient = Recipient.resolved(recipientId);
      long threadId = SignalDatabase.threads().getOrCreateThreadIdFor(recipient);

      org.thoughtcrime.securesms.sms.MessageSender.send(
          appContext,
          org.thoughtcrime.securesms.mms.OutgoingMessage.text(
              recipient,
              token,
              java.util.concurrent.TimeUnit.SECONDS.toMillis(recipient.getExpiresInSeconds()),
              System.currentTimeMillis(),
              null
          ),
          threadId,
          org.thoughtcrime.securesms.sms.MessageSender.SendType.SIGNAL,
          null,
          null
      );

      consumer.accept(new ConfirmPaymentViewModel.CashuConfirmResult.Success());
    } catch (Throwable t) {
      Log.w(TAG, "Cashu confirm failed", t);
      consumer.accept(new ConfirmPaymentViewModel.CashuConfirmResult.Error(t.getMessage()));
    }
  }

  private long moneyToSats(@NonNull Money money) {
    try {
      // Money here is MobileCoin-denominated in legacy UI; interpret number as sats when Cashu is enabled
      String s = money.toString(org.whispersystems.signalservice.api.payments.FormatterOptions.builder().withoutUnit().build());
      return org.thoughtcrime.securesms.payments.create.CashuAmountAccessor.getAmountSats(s);
    } catch (Throwable t) {
      return 0L;
    }
  }
}
