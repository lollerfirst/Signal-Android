package org.thoughtcrime.securesms.payments.preferences.cashu;

import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

/**
 * Deprecated placeholder. The Cashu mint quote flow now lives in PaymentsAddMoneyFragment.
 * This fragment is kept as a no-op to avoid resource/build errors.
 */
public final class CashuMintQuoteFragment extends Fragment {
  public CashuMintQuoteFragment() {
    super(android.R.layout.simple_list_item_1);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    if (view instanceof TextView) {
      ((TextView) view).setText("This screen is deprecated. Use Add Money.");
    }
  }
}
