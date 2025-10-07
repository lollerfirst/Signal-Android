package org.thoughtcrime.securesms.payments.confirm;

import androidx.annotation.NonNull;

import org.thoughtcrime.securesms.payments.Payee;
import org.whispersystems.signalservice.api.payments.Money;

public class ConfirmPaymentState {
  private final Payee     payee;
  private final CharSequence balanceText; // UI-ready balance label (e.g., "1,234 sat") when Cashu, MOB string otherwise
  private final Money     amount;
  private final String    note;
  private final Money     fee;
  private final FeeStatus feeStatus;
  private final org.signal.core.util.money.FiatMoney exchange;
  private final Status    status;
  private final Money     total;
  private final java.util.UUID      paymentId;

  public ConfirmPaymentState(@NonNull Payee payee,
                             @NonNull Money amount,
                             String note)
  {
    this(payee,
         amount.toZero().toString(org.whispersystems.signalservice.api.payments.FormatterOptions.defaults()),
         amount,
         note,
         amount.toZero(),
         FeeStatus.STILL_LOADING,
         null,
         Status.CONFIRM,
         null);
  }

  private ConfirmPaymentState(@NonNull Payee payee,
                             @NonNull CharSequence balanceText,
                             @NonNull Money amount,
                             String note,
                             @NonNull Money fee,
                             @NonNull FeeStatus feeStatus,
                             org.signal.core.util.money.FiatMoney exchange,
                             @NonNull Status status,
                             java.util.UUID paymentId)
  {
    this.payee     = payee;
    this.balanceText   = balanceText;
    this.amount    = amount;
    this.note      = note;
    this.fee       = fee;
    this.feeStatus = feeStatus;
    this.exchange  = exchange;
    this.status    = status;
    this.paymentId = paymentId;
    this.total     = amount.add(fee);
  }

  public @NonNull Payee getPayee() {
    return payee;
  }

  public @NonNull CharSequence getBalanceText() {
    return balanceText;
  }

  public @NonNull Money getAmount() {
    return amount;
  }

  public String getNote() {
    return note;
  }

  public @NonNull Money getFee() {
    return fee;
  }

  public @NonNull FeeStatus getFeeStatus() {
    return feeStatus;
  }

  public org.signal.core.util.money.FiatMoney getExchange() {
    return exchange;
  }

  public @NonNull Status getStatus() {
    return status;
  }

  public @NonNull Money getTotal() {
    return total;
  }

  public java.util.UUID getPaymentId() {
    return paymentId;
  }

  public @NonNull ConfirmPaymentState updateStatus(@NonNull Status status) {
    return new ConfirmPaymentState(this.payee, this.balanceText, this.amount, this.note, this.fee, this.feeStatus, this.exchange, status, this.paymentId);
  }

  public @NonNull ConfirmPaymentState updateBalanceText(@NonNull CharSequence balanceText) {
    return new ConfirmPaymentState(this.payee, balanceText, this.amount, this.note, this.fee, this.feeStatus, this.exchange, this.status, this.paymentId);
  }

  public @NonNull ConfirmPaymentState updateFee(@NonNull Money fee) {
    return new ConfirmPaymentState(this.payee, this.balanceText, this.amount, this.note, fee, FeeStatus.SET, this.exchange, this.status, this.paymentId);
  }

  public @NonNull ConfirmPaymentState updateFeeStillLoading() {
    return new ConfirmPaymentState(this.payee, this.balanceText, this.amount, this.note, this.amount.toZero(), FeeStatus.STILL_LOADING, this.exchange, this.status, this.paymentId);
  }

  public @NonNull ConfirmPaymentState updateFeeError() {
    return new ConfirmPaymentState(this.payee, this.balanceText, this.amount, this.note, this.amount.toZero(), FeeStatus.ERROR, this.exchange, this.status, this.paymentId);
  }

  public @NonNull ConfirmPaymentState updatePaymentId(java.util.UUID paymentId) {
    return new ConfirmPaymentState(this.payee, this.balanceText, this.amount, this.note, this.fee, this.feeStatus, this.exchange, this.status, paymentId);
  }

  public @NonNull ConfirmPaymentState updateExchange(org.signal.core.util.money.FiatMoney exchange) {
    return new ConfirmPaymentState(this.payee, this.balanceText, this.amount, this.note, this.fee, this.feeStatus, exchange, this.status, this.paymentId);
  }

  public @NonNull ConfirmPaymentState timeout() {
    return new ConfirmPaymentState(this.payee, this.balanceText, this.amount, this.note, this.fee, this.feeStatus, this.exchange, Status.TIMEOUT, this.paymentId);
  }

  enum Status {
    CONFIRM,
    SUBMITTING,
    PROCESSING,
    DONE,
    ERROR,
    TIMEOUT;

    boolean isTerminalStatus() {
      return this == DONE || this == ERROR || this == TIMEOUT;
    }
  }
  
  enum FeeStatus {
    NOT_SET,
    STILL_LOADING,
    SET,
    ERROR
  }
}
