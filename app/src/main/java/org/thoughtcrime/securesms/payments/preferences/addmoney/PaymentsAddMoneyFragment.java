package org.thoughtcrime.securesms.payments.preferences.addmoney;
import org.thoughtcrime.securesms.payments.engine.MintWatcher;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import org.thoughtcrime.securesms.LoggingFragment;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.qr.QrView;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.preferences.cashu.CashuMintQuoteUiHelper;
import org.thoughtcrime.securesms.util.views.LearnMoreTextView;

public final class PaymentsAddMoneyFragment extends LoggingFragment {

  public PaymentsAddMoneyFragment() {
    super(R.layout.payments_add_money_fragment);
  }

  @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
    super.onViewCreated(view, savedInstanceState);

    PaymentsAddMoneyViewModel viewModel = new ViewModelProvider(this, new PaymentsAddMoneyViewModel.Factory()).get(PaymentsAddMoneyViewModel.class);

    Toolbar           toolbar                  = view.findViewById(R.id.payments_add_money_toolbar);
    QrView            qrImageView              = view.findViewById(R.id.payments_add_money_qr_image);
    TextView          walletAddressAbbreviated = view.findViewById(R.id.payments_add_money_abbreviated_wallet_address);
    View              copyAddress              = view.findViewById(R.id.payments_add_money_copy_address_button);
    LearnMoreTextView info                     = view.findViewById(R.id.payments_add_money_info);
    View              qrBorder                 = view.findViewById(R.id.payments_add_money_qr_border);
    android.widget.EditText amountInput        = view.findViewById(R.id.cashu_amount_input);
    View              getInvoiceButton         = view.findViewById(R.id.cashu_get_invoice_button);

    info.setLearnMoreVisible(true);
    info.setLink(getString(R.string.PaymentsAddMoneyFragment__learn_more__information));

    toolbar.setNavigationOnClickListener(v -> Navigation.findNavController(v).popBackStack());

    // Cashu path: inline UI (amount input + button -> show QR)
    if (SignalStore.payments().cashuEnabled()) {
      qrBorder.setVisibility(View.GONE);
      getInvoiceButton.setOnClickListener(v -> {
        String vtext = amountInput.getText() != null ? amountInput.getText().toString().trim() : "";
        final long sats;
        try { sats = Long.parseLong(vtext); } catch (Throwable t) {
          Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show();
          return;
        }
        if (sats <= 0L) {
          Toast.makeText(requireContext(), "Invalid amount", Toast.LENGTH_SHORT).show();
          return;
        }
        // Fetch in background
        new Thread(() -> {
          String text;
          try {
            org.thoughtcrime.securesms.payments.engine.MintQuote quote = org.thoughtcrime.securesms.payments.preferences.cashu.CashuMintQuoteUiHelper.requestMintQuote(requireContext(), sats);
            if (quote != null && quote.getInvoiceBolt11() != null && !quote.getInvoiceBolt11().isEmpty()) {
              text = quote.getInvoiceBolt11();
            } else if (quote != null) {
              text = "cashu:mint-quote?mint=" + quote.getMintUrl() + "&amount=" + quote.getAmountSats() + "&total=" + quote.getTotalSats();
            } else {
              text = "cashu:mint-quote:unavailable";
            }
          } catch (Throwable t) {
            text = "cashu:mint-quote:error";
          }
          final String qrText = text;
          requireActivity().runOnUiThread(() -> {
            // Hide amount input and button after we have a quote
            View amountContainer = getView().findViewById(R.id.cashu_amount_container);
            if (amountContainer != null) amountContainer.setVisibility(View.GONE);
            qrBorder.setVisibility(View.VISIBLE);
            TextView walletLabel = getView().findViewById(R.id.payments_add_money_your_wallet_address);
            if (walletLabel != null) walletLabel.setText("Your lightning invoice");
            walletAddressAbbreviated.setText(qrText);
            qrImageView.setQrText(qrText);
            info.setText("To add funds, pay this lightning invoice.");
            // Notify home to refresh recent activity
            getParentFragmentManager().setFragmentResult("cashu_history_changed", new Bundle());

          });
        }).start();
      });
      copyAddress.setOnClickListener(v -> copyAddressToClipboard(walletAddressAbbreviated.getText().toString()));
      MintWatcher.INSTANCE.start(requireContext());
      return;
    }

    // Legacy MOB path
    viewModel.getSelfAddressAbbreviated().observe(getViewLifecycleOwner(), walletAddressAbbreviated::setText);
    viewModel.getSelfAddressB58().observe(getViewLifecycleOwner(), base58 -> copyAddress.setOnClickListener(v -> copyAddressToClipboard(base58)));
    // Note we are choosing to put Base58 directly into QR here
    viewModel.getSelfAddressB58().observe(getViewLifecycleOwner(), qrImageView::setQrText);

    viewModel.getErrors().observe(getViewLifecycleOwner(), error -> {
      switch (error) {
        case PAYMENTS_NOT_ENABLED: throw new AssertionError("Payments are not enabled");
        default                  : throw new AssertionError();
      }
    });
  }

  private void copyAddressToClipboard(@NonNull String text) {
    Context          context   = requireContext();
    ClipboardManager clipboard = (android.content.ClipboardManager) context.getSystemService(Context.CLIPBOARD_SERVICE);
    clipboard.setPrimaryClip(ClipData.newPlainText(context.getString(R.string.app_name), text));
    Toast.makeText(context, R.string.PaymentsAddMoneyFragment__copied_to_clipboard, Toast.LENGTH_SHORT).show();
  }

  private void showAmountPromptAndMintQuote(@NonNull QrView qrImageView, @NonNull TextView display) {
    final android.widget.EditText input = new android.widget.EditText(requireContext());
    input.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
    input.setHint("Amount in sats");

    new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
        .setTitle("Add funds")
        .setMessage("Enter amount in sats")
        .setView(input)
        .setPositiveButton(android.R.string.ok, (d, w) -> {
          String v = input.getText().toString().trim();
          final long sats;
          try {
            sats = Long.parseLong(v);
          } catch (Throwable ignore) {
            android.widget.Toast.makeText(requireContext(), "Invalid amount", android.widget.Toast.LENGTH_SHORT).show();
            return;
          }
          if (sats <= 0L) {
            android.widget.Toast.makeText(requireContext(), "Invalid amount", android.widget.Toast.LENGTH_SHORT).show();
            return;
          }
          // Show immediate feedback while fetching invoice off main thread
          display.setText("Loading invoice...");
          qrImageView.setQrText("");
          // Fetch the mint quote in a background thread to avoid NetworkOnMainThreadException
          new Thread(() -> {
            String text;
            try {
              org.thoughtcrime.securesms.payments.engine.MintQuote quote = org.thoughtcrime.securesms.payments.preferences.cashu.CashuMintQuoteUiHelper.requestMintQuote(requireContext(), sats);
              if (quote != null && quote.getInvoiceBolt11() != null && !quote.getInvoiceBolt11().isEmpty()) {
                text = quote.getInvoiceBolt11();
              } else if (quote != null) {
                text = "cashu:mint-quote?mint=" + quote.getMintUrl() + "&amount=" + quote.getAmountSats() + "&total=" + quote.getTotalSats();
              } else {
                text = "cashu:mint-quote:unavailable";
              }
            } catch (Throwable t) {
              text = "cashu:mint-quote:error";
            }
            final String qrText = text;
            requireActivity().runOnUiThread(() -> {
              display.setText(qrText);
              qrImageView.setQrText(qrText);
              android.widget.Toast.makeText(requireContext(), "Invoice ready", android.widget.Toast.LENGTH_SHORT).show();
            });
          }).start();
        })
        .setNegativeButton(android.R.string.cancel, (d, w) -> d.dismiss())
        .show();
  }
}
