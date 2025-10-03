package org.thoughtcrime.securesms.payments.preferences.model;

import android.content.Context;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.recipients.RecipientId;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;

import java.util.Locale;

public final class CashuActivityItem implements MappingModel<CashuActivityItem> {

  public enum State { PENDING, COMPLETED, SENT, RECEIVED }

  private final String  id;
  private final long    timestampMs;
  private final long    amountSats; // positive for incoming top-up, negative for outgoing send
  private final State   state;
  private final RecipientId peerRecipientId; // optional, used for SENT/RECEIVED to show avatar/name
  private final String  peerDisplayName;     // cached name for display
  private final boolean withdrawal;          // mark as Lightning withdrawal (melt)

  public CashuActivityItem(@NonNull String id, long timestampMs, long amountSats, @NonNull State state) {
    this(id, timestampMs, amountSats, state, null, null, false);
  }

  public CashuActivityItem(@NonNull String id,
                           long timestampMs,
                           long amountSats,
                           @NonNull State state,
                           RecipientId peerRecipientId,
                           String peerDisplayName) {
    this(id, timestampMs, amountSats, state, peerRecipientId, peerDisplayName, false);
  }

  public CashuActivityItem(@NonNull String id,
                           long timestampMs,
                           long amountSats,
                           @NonNull State state,
                           RecipientId peerRecipientId,
                           String peerDisplayName,
                           boolean withdrawal) {
    this.id = id;
    this.timestampMs = timestampMs;
    this.amountSats = amountSats;
    this.state = state;
    this.peerRecipientId = peerRecipientId;
    this.peerDisplayName = peerDisplayName;
    this.withdrawal = withdrawal;
  }

  public @NonNull String getId() { return id; }
  public long getTimestampMs() { return timestampMs; }
  public long getAmountSats() { return amountSats; }
  public @NonNull State getState() { return state; }
  public RecipientId getPeerRecipientId() { return peerRecipientId; }
  public String getPeerDisplayName() { return peerDisplayName; }
  public boolean isWithdrawal() { return withdrawal; }

  public @NonNull String getTitle(@NonNull Context context) {
    if (withdrawal) {
      return context.getString(R.string.PaymentsPayInvoice__invoice_paid);
    }
    switch (state) {
      case SENT: {
        String name = peerDisplayName != null ? peerDisplayName : context.getString(R.string.CashuActivity__unknown);
        return context.getString(R.string.CashuActivity__sent_to_s, name);
      }
      case RECEIVED: {
        String rname = peerDisplayName != null ? peerDisplayName : context.getString(R.string.CashuActivity__unknown);
        return context.getString(R.string.CashuActivity__received_from_s, rname);
      }
      case COMPLETED:
        return context.getString(R.string.CashuActivity__top_up_completed);
      case PENDING:
      default:
        return context.getString(R.string.CashuActivity__pending_top_up);
    }
  }

  public @NonNull String getDate(@NonNull Context context) {
    return DateUtils.formatDateWithoutDayOfWeek(Locale.getDefault(), timestampMs);
  }

  public @NonNull String getAmountText() {
    String base = formatSats(Math.abs(amountSats)) + " sat";
    if (state == State.SENT || amountSats < 0) return "-" + base;
    if (state == State.COMPLETED || state == State.RECEIVED) return "+" + base;
    return base; // pending (no sign)
  }

  public @ColorRes int getAmountColor() {
    if (state == State.SENT || amountSats < 0 || withdrawal) return R.color.signal_alert_primary; // red-ish
    if (state == State.COMPLETED || state == State.RECEIVED) return R.color.core_green;
    return R.color.signal_text_secondary; // pending grey
  }

  private static String formatSats(long sats) {
    java.text.NumberFormat nf = java.text.NumberFormat.getInstance(java.util.Locale.getDefault());
    nf.setGroupingUsed(true);
    nf.setMaximumFractionDigits(0);
    return nf.format(sats);
  }

  @Override
  public boolean areItemsTheSame(@NonNull CashuActivityItem newItem) {
    return id.equals(newItem.id);
  }

  @Override
  public boolean areContentsTheSame(@NonNull CashuActivityItem newItem) {
    return timestampMs == newItem.timestampMs && amountSats == newItem.amountSats && state == newItem.state && withdrawal == newItem.withdrawal;
  }
}
