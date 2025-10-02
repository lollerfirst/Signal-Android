package org.thoughtcrime.securesms.payments.preferences.model;

import android.content.Context;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.util.DateUtils;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModel;

import java.util.Locale;

public final class CashuActivityItem implements MappingModel<CashuActivityItem> {

  public enum State { PENDING, COMPLETED }

  private final String  id;
  private final long    timestampMs;
  private final long    amountSats; // positive for incoming top-up
  private final State   state;

  public CashuActivityItem(@NonNull String id, long timestampMs, long amountSats, @NonNull State state) {
    this.id = id;
    this.timestampMs = timestampMs;
    this.amountSats = amountSats;
    this.state = state;
  }

  public @NonNull String getId() { return id; }
  public long getTimestampMs() { return timestampMs; }
  public long getAmountSats() { return amountSats; }
  public @NonNull State getState() { return state; }

  public @NonNull String getTitle() {
    return state == State.COMPLETED ? "Top-up completed" : "Pending top-up";
  }

  public @NonNull String getDate(@NonNull Context context) {
    return DateUtils.formatDateWithoutDayOfWeek(Locale.getDefault(), timestampMs);
  }

  public @NonNull String getAmountText() {
    if (state == State.PENDING) return formatSats(amountSats) + " sat";
    return "+" + formatSats(amountSats) + " sat";
  }

  public @ColorRes int getAmountColor() {
    return state == State.COMPLETED ? R.color.core_green : R.color.signal_text_secondary;
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
    return timestampMs == newItem.timestampMs && amountSats == newItem.amountSats && state == newItem.state;
  }
}
