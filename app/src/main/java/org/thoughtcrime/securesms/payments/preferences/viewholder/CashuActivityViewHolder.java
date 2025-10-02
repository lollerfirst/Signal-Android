package org.thoughtcrime.securesms.payments.preferences.viewholder;

import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.payments.preferences.model.CashuActivityItem;
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

    // Fix id: actual ID is cashu_activity_title
    title = itemView.findViewById(R.id.cashu_activity_title);

    title.setText(model.getTitle());
    date.setText(model.getDate(itemView.getContext()));
    amount.setText(model.getAmountText());
    amount.setTextColor(itemView.getResources().getColor(model.getAmountColor()))
    ;
  }
}
