package org.thoughtcrime.securesms.payments.preferences.transfer;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.navigation.Navigation;

import org.signal.core.util.logging.Log;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.payments.engine.CashuUiInteractor;
import org.thoughtcrime.securesms.payments.engine.MeltQuote;

/**
 * Confirm screen for paying a Lightning invoice using Cashu (melt).
 */
public class PayInvoiceConfirmFragment extends Fragment {
  private static final String TAG = Log.tag(PayInvoiceConfirmFragment.class);

  private static final String ARG_INVOICE     = "invoice";
  private static final String ARG_AMOUNT_SATS = "amountSats";
  private static final String ARG_FEE_SATS    = "feeSats";
  private static final String ARG_EXPIRES_MS  = "expiresMs";
  private static final String ARG_ID          = "id";

  public PayInvoiceConfirmFragment() {
    super(R.layout.pay_invoice_confirm_fragment);
  }

  public static Bundle argsFromQuote(@NonNull MeltQuote quote) {
    Bundle b = new Bundle();
    b.putString(ARG_INVOICE, quote.getInvoiceBolt11());
    b.putLong(ARG_AMOUNT_SATS, quote.getAmountSats());
    b.putLong(ARG_FEE_SATS, quote.getFeeSats());
    b.putLong(ARG_EXPIRES_MS, quote.getExpiresAtMs());
    if (!TextUtils.isEmpty(quote.getId())) b.putString(ARG_ID, quote.getId());
    return b;
  }

  @Nullable @Override
  public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    return inflater.inflate(R.layout.pay_invoice_confirm_fragment, container, false);
  }

  @Override
  public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);
    TextView amount = view.findViewById(R.id.pay_invoice_amount);
    TextView fee    = view.findViewById(R.id.pay_invoice_fee);
    TextView total  = view.findViewById(R.id.pay_invoice_total);
    TextView expiry = view.findViewById(R.id.pay_invoice_expiry);
    Button   confirm= view.findViewById(R.id.pay_invoice_confirm);
    ProgressBar spinner = view.findViewById(R.id.pay_invoice_spinner);

    Bundle args = getArguments();
    long amt = args != null ? args.getLong(ARG_AMOUNT_SATS, 0L) : 0L;
    long fs  = args != null ? args.getLong(ARG_FEE_SATS, 0L) : 0L;
    long exp = args != null ? args.getLong(ARG_EXPIRES_MS, 0L) : 0L;

    amount.setText(formatSats(amt) + " sat");
    fee.setText(formatSats(fs) + " sat");
    total.setText(formatSats(amt + fs) + " sat");
    if (exp > 0) {
      java.text.DateFormat df = java.text.DateFormat.getDateTimeInstance();
      expiry.setText(df.format(new java.util.Date(exp)));
    }

    confirm.setOnClickListener(v -> {
      confirm.setEnabled(false);
      spinner.setVisibility(View.VISIBLE);
      v.post(() -> doMelt(v));
    });
  }

  private void doMelt(@NonNull View v) {
    Bundle args = getArguments();
    if (args == null) {
      Toast.makeText(requireContext(), R.string.PaymentsPayInvoice__unable_to_pay, Toast.LENGTH_LONG).show();
      return;
    }
    String invoice = args.getString(ARG_INVOICE);
    long amountSats = args.getLong(ARG_AMOUNT_SATS, 0L);
    long feeSats = args.getLong(ARG_FEE_SATS, 0L);
    long expiresAt = args.getLong(ARG_EXPIRES_MS, 0L);
    String id = args.getString(ARG_ID);

    new Thread(() -> {
      try {
        MeltQuote quote = new MeltQuote(amountSats, feeSats, amountSats + feeSats, expiresAt, invoice, id);
        boolean ok = CashuUiInteractor.meltBlocking(AppDependencies.getApplication(), quote);
        requireView().post(() -> {
          Toast.makeText(requireContext(), R.string.PaymentsPayInvoice__invoice_paid, Toast.LENGTH_SHORT).show();
          Navigation.findNavController(v).popBackStack();
        });
      } catch (Throwable t) {
        requireView().post(() -> {
          Toast.makeText(requireContext(), R.string.PaymentsPayInvoice__unable_to_pay, Toast.LENGTH_LONG).show();
          Navigation.findNavController(v).popBackStack();
        });
      }
    }).start();
  }

  private static String formatSats(long sats) {
    java.text.NumberFormat nf = java.text.NumberFormat.getInstance(java.util.Locale.getDefault());
    nf.setGroupingUsed(true);
    nf.setMaximumFractionDigits(0);
    return nf.format(sats);
  }
}
