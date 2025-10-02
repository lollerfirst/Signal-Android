package org.thoughtcrime.securesms.payments.preferences.viewholder;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.AvatarImageView;
import org.thoughtcrime.securesms.payments.preferences.model.CashuActivityItem;
import org.thoughtcrime.securesms.recipients.Recipient;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingViewHolder;

public final class CashuActivityViewHolder extends MappingViewHolder<CashuActivityItem> {

  public CashuActivityViewHolder(@NonNull View itemView) {
    super(itemView);
  }

  @Override
  public void bind(@NonNull CashuActivityItem model) {
    TextView title  = itemView.findViewById(R.id.cashu_activity_title);
    TextView date   = itemView.findViewById(R.id.cashu_activity_date);
    TextView amount = itemView.findViewById(R.id.cashu_activity_amount);
    AvatarImageView avatar = itemView.findViewById(R.id.cashu_activity_avatar);

    if (model.getState() == CashuActivityItem.State.SENT && model.getPeerRecipientId() != null) {
      Recipient peer = Recipient.resolved(model.getPeerRecipientId());
      avatar.setAvatar(peer);
      String display = model.getPeerDisplayName() != null ? model.getPeerDisplayName() : peer.getDisplayName(itemView.getContext());
      title.setText(itemView.getContext().getString(R.string.CashuActivity__sent_to_s, display));
      amount.setTextColor(itemView.getResources().getColor(R.color.signal_alert_primary));
    } else if (model.getState() == CashuActivityItem.State.RECEIVED && model.getPeerRecipientId() != null) {
      Recipient peer = Recipient.resolved(model.getPeerRecipientId());
      avatar.setAvatar(peer);
      String display = model.getPeerDisplayName() != null ? model.getPeerDisplayName() : peer.getDisplayName(itemView.getContext());
      title.setText(itemView.getContext().getString(R.string.CashuActivity__received_from_s, display));
      amount.setTextColor(itemView.getResources().getColor(R.color.core_green));
    } else {
      avatar.setImageResource(R.drawable.ic_mobilecoin_avatar_24);
      title.setText(model.getTitle(itemView.getContext()));
      amount.setTextColor(itemView.getResources().getColor(model.getAmountColor()));
    }

    date.setText(model.getDate(itemView.getContext()));
    amount.setText(model.getAmountText());
  }
}
