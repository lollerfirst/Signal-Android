package org.thoughtcrime.securesms.payments.preferences;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Pair;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.lifecycle.ViewModel;
import androidx.lifecycle.ViewModelProvider;

import com.annimon.stream.Stream;

import org.signal.core.util.logging.Log;
import org.signal.core.util.money.FiatMoney;
import org.thoughtcrime.securesms.R;
import org.thoughtcrime.securesms.components.settings.SettingHeader;
import org.thoughtcrime.securesms.dependencies.AppDependencies;
import org.thoughtcrime.securesms.keyvalue.PaymentsAvailability;
import org.thoughtcrime.securesms.keyvalue.PaymentsValues;
import org.thoughtcrime.securesms.keyvalue.SignalStore;
import org.thoughtcrime.securesms.payments.Balance;
import org.thoughtcrime.securesms.payments.Payment;
import org.thoughtcrime.securesms.payments.UnreadPaymentsRepository;
import org.thoughtcrime.securesms.payments.currency.CurrencyExchange;
import org.thoughtcrime.securesms.payments.currency.CurrencyExchangeRepository;
import org.thoughtcrime.securesms.payments.engine.CashuUiRepository;
import org.thoughtcrime.securesms.payments.preferences.model.CashuActivityItem;
import org.thoughtcrime.securesms.payments.preferences.model.InProgress;
import org.thoughtcrime.securesms.payments.preferences.model.InfoCard;
import org.thoughtcrime.securesms.payments.preferences.model.IntroducingPayments;
import org.thoughtcrime.securesms.payments.preferences.model.NoRecentActivity;
import org.thoughtcrime.securesms.payments.preferences.model.PaymentItem;
import org.thoughtcrime.securesms.util.AsynchronousCallback;
import org.thoughtcrime.securesms.util.SingleLiveEvent;
import org.thoughtcrime.securesms.util.adapter.mapping.MappingModelList;
import org.thoughtcrime.securesms.util.livedata.LiveDataUtil;
import org.thoughtcrime.securesms.util.livedata.Store;
import org.whispersystems.signalservice.api.payments.Money;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class PaymentsHomeViewModel extends ViewModel {

  private static final String TAG = Log.tag(PaymentsHomeViewModel.class);

  private static final int MAX_PAYMENT_ITEMS = 4;

  private final Store<PaymentsHomeState>           store;
  private final LiveData<MappingModelList>         list;
  private final LiveData<Boolean>                  paymentsEnabled;
  private final LiveData<Money>                    balance;
  private final LiveData<FiatMoney>                exchange;
  private final SingleLiveEvent<PaymentStateEvent> paymentStateEvents;
  private final SingleLiveEvent<ErrorEnabling>     errorEnablingPayments;
  private final LiveData<Boolean>                  enclaveFailure;

  private final PaymentsHomeRepository     paymentsHomeRepository;
  private final CurrencyExchangeRepository currencyExchangeRepository;
  private final UnreadPaymentsRepository   unreadPaymentsRepository;
  private final LiveData<LoadState>        exchangeLoadState;

  // Cashu additions
  private final boolean cashuEnabled;
  private final MutableLiveData<Object> cashuRefresh = new MutableLiveData<>(new Object());
  private final CashuUiRepository cashuUiRepository;
  private final LiveData<Long> cashuSatsBalance;
  private final LiveData<String> cashuFiatText;
  private final LiveData<List<CashuActivityItem>> cashuRecentActivity;

  PaymentsHomeViewModel(@NonNull PaymentsHomeRepository paymentsHomeRepository,
                        @NonNull PaymentsRepository paymentsRepository,
                        @NonNull CurrencyExchangeRepository currencyExchangeRepository)
  {
    this.paymentsHomeRepository     = paymentsHomeRepository;
    this.currencyExchangeRepository = currencyExchangeRepository;
    this.unreadPaymentsRepository   = new UnreadPaymentsRepository();
    this.store                      = new Store<>(new PaymentsHomeState(getPaymentsState()));
    this.balance                    = LiveDataUtil.mapDistinct(SignalStore.payments().liveMobileCoinBalance(), Balance::getFullAmount);

    this.paymentsEnabled            = LiveDataUtil.mapDistinct(store.getStateLiveData(), state -> state.getPaymentsState() == PaymentsHomeState.PaymentsState.ACTIVATED);
    this.exchange                   = LiveDataUtil.mapDistinct(store.getStateLiveData(), PaymentsHomeState::getExchangeAmount);
    this.exchangeLoadState          = LiveDataUtil.mapDistinct(store.getStateLiveData(), PaymentsHomeState::getExchangeRateLoadState);
    this.paymentStateEvents         = new SingleLiveEvent<>();
    this.errorEnablingPayments      = new SingleLiveEvent<>();
    this.enclaveFailure             = LiveDataUtil.mapDistinct(SignalStore.payments().enclaveFailure(), isFailure -> isFailure);
    this.store.update(paymentsRepository.getRecentPayments(), this::updateRecentPayments);

    this.cashuEnabled               = SignalStore.payments().cashuEnabled();
    this.cashuUiRepository          = new CashuUiRepository(AppDependencies.getApplication());
    // Sats header
    this.cashuSatsBalance           = LiveDataUtil.mapAsync(store.getStateLiveData(), s -> cashuUiRepository.getSpendableSatsBlocking());
    this.cashuFiatText              = LiveDataUtil.mapAsync(this.cashuSatsBalance, sats -> cashuUiRepository.satsToFiatStringBlocking(sats));

    // Trigger for Cashu recent activity: either an explicit refresh OR a sats balance change
    androidx.lifecycle.LiveData<Object> cashuTrigger = org.thoughtcrime.securesms.util.livedata.LiveDataUtil.combineLatest(
        cashuRefresh,
        cashuSatsBalance,
        (o, sats) -> o
    );

    // Fetch and map Cashu history off main thread based on trigger
    this.cashuRecentActivity        = LiveDataUtil.mapAsync(cashuTrigger, o -> {
      try {
        java.util.List<org.thoughtcrime.securesms.payments.engine.Tx> txs = org.thoughtcrime.securesms.payments.engine.CashuUiInteractor.listHistoryBlocking(AppDependencies.getApplication(), 0, 50);
        java.util.List<CashuActivityItem> items = new ArrayList<>();
        for (org.thoughtcrime.securesms.payments.engine.Tx tx : txs) {
          String memo = tx.getMemo();
          if (memo == null) continue;
          if (memo.startsWith("Pending top-up")) {
            items.add(new CashuActivityItem(tx.getId(), tx.getTimestampMs(), tx.getAmountSats(), CashuActivityItem.State.PENDING));
          } else if (memo.startsWith("Top-up completed")) {
            items.add(new CashuActivityItem(tx.getId(), tx.getTimestampMs(), tx.getAmountSats(), CashuActivityItem.State.COMPLETED));
          } else if (memo.startsWith("Sent ecash")) {
            long sats = Math.abs(tx.getAmountSats());
            org.thoughtcrime.securesms.recipients.RecipientId peer = null;
            String name = null;
            try {
              String[] parts = memo.split("\\|");
              for (String p : parts) {
                if (p.startsWith("rid:")) peer = org.thoughtcrime.securesms.recipients.RecipientId.from(p.substring(4));
                else if (p.startsWith("name:")) name = p.substring(5);
              }
            } catch (Throwable ignore) {}
            items.add(new CashuActivityItem(tx.getId(), tx.getTimestampMs(), -sats, CashuActivityItem.State.SENT, peer, name));
          } else if (memo.startsWith("Received from")) {
            long sats = Math.abs(tx.getAmountSats());
            org.thoughtcrime.securesms.recipients.RecipientId peer = null;
            String name = null;
            try {
              String[] parts = memo.split("\\|");
              for (String p : parts) {
                if (p.startsWith("rid:")) peer = org.thoughtcrime.securesms.recipients.RecipientId.from(p.substring(4));
                else if (p.startsWith("name:")) name = p.substring(5);
              }
            } catch (Throwable ignore) {}
            items.add(new CashuActivityItem(tx.getId(), tx.getTimestampMs(), sats, CashuActivityItem.State.RECEIVED, peer, name));
          }
        }
        return items;
      } catch (Throwable t) {
        return java.util.Collections.emptyList();
      }
    });

    // Build UI list whenever either store state or Cashu activity changes
    androidx.lifecycle.LiveData<androidx.core.util.Pair<PaymentsHomeState, java.util.List<CashuActivityItem>>> combined =
        org.thoughtcrime.securesms.util.livedata.LiveDataUtil.combineLatest(store.getStateLiveData(), cashuRecentActivity, androidx.core.util.Pair::new);
    this.list = androidx.lifecycle.Transformations.map(combined, pair -> createList(pair.first, pair.second));

    LiveData<CurrencyExchange.ExchangeRate> liveExchangeRate = LiveDataUtil.combineLatest(SignalStore.payments().liveCurrentCurrency(),
                                                                                          LiveDataUtil.mapDistinct(store.getStateLiveData(), PaymentsHomeState::getCurrencyExchange),
                                                                                          (currency, exchange) -> exchange.getExchangeRate(currency));

    LiveData<Optional<FiatMoney>> liveExchangeAmount = LiveDataUtil.combineLatest(this.balance,
                                                                                  liveExchangeRate,
                                                                                  (balance, exchangeRate) -> exchangeRate.exchange(balance));
    this.store.update(liveExchangeAmount, (amount, state) -> state.updateCurrencyAmount(amount.orElse(null)));

    if (this.cashuEnabled) {
      // Ensure we load Cashu activity immediately
      this.cashuRefresh.postValue(new Object());
    }

    refreshExchangeRates(true);
  }

  @Override
  protected void onCleared() {
    super.onCleared();
    store.clear();
  }

  boolean isCashuEnabled() { return cashuEnabled; }

  @NonNull LiveData<Long> getCashuSatsBalance() { return cashuSatsBalance; }

  @NonNull LiveData<String> getCashuFiatText() { return cashuFiatText; }

  private static PaymentsHomeState.PaymentsState getPaymentsState() {
    PaymentsValues paymentsValues = SignalStore.payments();

    PaymentsAvailability paymentsAvailability = paymentsValues.getPaymentsAvailability();

    if (paymentsAvailability.canRegister()) {
      return PaymentsHomeState.PaymentsState.NOT_ACTIVATED;
    } else if (paymentsAvailability.isEnabled()) {
      return PaymentsHomeState.PaymentsState.ACTIVATED;
    } else {
      return PaymentsHomeState.PaymentsState.ACTIVATE_NOT_ALLOWED;
    }
  }

  @NonNull LiveData<PaymentStateEvent> getPaymentStateEvents() {
    return paymentStateEvents;
  }

  @NonNull LiveData<ErrorEnabling> getErrorEnablingPayments() {
    return errorEnablingPayments;
  }

  @NonNull LiveData<Boolean> getEnclaveFailure() {
    return enclaveFailure;
  }

  @NonNull boolean isEnclaveFailurePresent() {
    return Boolean.TRUE.equals(getEnclaveFailure().getValue());
  }

  @NonNull LiveData<MappingModelList> getList() {
    return list;
  }

  @NonNull LiveData<Boolean> getPaymentsEnabled() {
    return paymentsEnabled;
  }

  @NonNull LiveData<Money> getBalance() {
    return balance;
  }

  @NonNull LiveData<FiatMoney> getExchange() {
    return exchange;
  }

  @NonNull LiveData<LoadState> getExchangeLoadState() {
    return exchangeLoadState;
  }

  // True when exchange rates are loaded (fiat header) AND recent activity has loaded/cached
  boolean isUiReady(@Nullable MappingModelList listValue) {
    boolean exchangeReady = getExchangeLoadState().getValue() == LoadState.LOADED;
    boolean recentReady   = listValue != null && !listValue.isEmpty();
    return exchangeReady && recentReady;
  }

  void markAllPaymentsSeen() {
    unreadPaymentsRepository.markAllPaymentsSeen();
  }

  void checkPaymentActivationState() {
    PaymentsHomeState.PaymentsState storedState     = store.getState().getPaymentsState();
    boolean                         paymentsEnabled = SignalStore.payments().mobileCoinPaymentsEnabled();

    if (storedState.equals(PaymentsHomeState.PaymentsState.ACTIVATED) && !paymentsEnabled) {
      store.update(s -> s.updatePaymentsEnabled(PaymentsHomeState.PaymentsState.NOT_ACTIVATED));
      paymentStateEvents.setValue(PaymentStateEvent.DEACTIVATED);
    } else if (storedState.equals(PaymentsHomeState.PaymentsState.NOT_ACTIVATED) && paymentsEnabled) {
      store.update(s -> s.updatePaymentsEnabled(PaymentsHomeState.PaymentsState.ACTIVATED));
      paymentStateEvents.setValue(PaymentStateEvent.ACTIVATED);
    }
  }

  private @NonNull MappingModelList createList(@NonNull PaymentsHomeState state, @Nullable List<CashuActivityItem> cashuItems) {
    MappingModelList list = new MappingModelList();

    if (state.getPaymentsState() == PaymentsHomeState.PaymentsState.ACTIVATED) {
      list.add(new SettingHeader.Item(R.string.PaymentsHomeFragment__recent_activity));

      int maxItems = 5;
      int added = 0;

      // Cashu: show pending, completed, and sent items first
      if (cashuEnabled) {
        if (cashuItems == null) {
          // Still loading in background; we'll show InProgress below
        } else if (!cashuItems.isEmpty()) {
          int take = Math.min(MAX_PAYMENT_ITEMS, cashuItems.size());
          for (int i = 0; i < take; i++) { list.add(cashuItems.get(i)); }
          // Added sent items may consume the entire list; skip legacy payments if so
          if (take >= MAX_PAYMENT_ITEMS) return list;
        }
      }

      // Then fill remaining slots with legacy PaymentItems
      if (added < maxItems && state.getTotalPayments() > 0) {
        java.util.List<PaymentItem> payments = state.getPayments();
        int take = Math.min(maxItems - added, payments.size());
        for (int i = 0; i < take; i++) { list.add(payments.get(i)); }
        added += take;
      }

      if (!state.isRecentPaymentsLoaded() || (cashuEnabled && cashuItems == null)) {
        // Show loading if legacy payments not loaded yet OR cashu items still loading
        list.add(new InProgress());
      } else if (added == 0) {
        list.add(new NoRecentActivity());
      }
    } else if (state.getPaymentsState() == PaymentsHomeState.PaymentsState.ACTIVATE_NOT_ALLOWED) {
      Log.w(TAG, "Payments remotely disabled or not in region");
    } else {
      list.add(new IntroducingPayments(state.getPaymentsState()));
    }

    list.addAll(InfoCard.getInfoCards());

    return list;
  }

  private @NonNull PaymentsHomeState updateRecentPayments(@NonNull List<Payment> payments,
                                                          @NonNull PaymentsHomeState state)
  {
    List<PaymentItem> paymentItems = Stream.of(payments)
                                           .limit(MAX_PAYMENT_ITEMS)
                                           .map(PaymentItem::fromPayment)
                                           .toList();

    return state.updatePayments(paymentItems, payments.size());
  }

  // Public: explicit refresh for Cashu recent activity
  public void refreshCashuActivity() {
    if (cashuEnabled) cashuRefresh.postValue(new Object());
  }

  public void updateStore() {
    store.update(s -> s);
  }

  public void activatePayments() {
    if (store.getState().getPaymentsState() != PaymentsHomeState.PaymentsState.NOT_ACTIVATED) {
      return;
    }

    store.update(state -> state.updatePaymentsEnabled(PaymentsHomeState.PaymentsState.ACTIVATING));

    paymentsHomeRepository.activatePayments(new AsynchronousCallback.WorkerThread<Void, PaymentsHomeRepository.Error>() {
      @Override
      public void onComplete(@Nullable Void result) {
        store.update(state -> state.updatePaymentsEnabled(PaymentsHomeState.PaymentsState.ACTIVATED));
        paymentStateEvents.postValue(PaymentStateEvent.ACTIVATED);
      }

      @Override
      public void onError(@Nullable PaymentsHomeRepository.Error error) {
        store.update(state -> state.updatePaymentsEnabled(PaymentsHomeState.PaymentsState.NOT_ACTIVATED));
        if (error == PaymentsHomeRepository.Error.NetworkError) {
          errorEnablingPayments.postValue(ErrorEnabling.NETWORK);
        } else if (error == PaymentsHomeRepository.Error.RegionError) {
          errorEnablingPayments.postValue(ErrorEnabling.REGION);
        } else {
          throw new AssertionError();
        }
      }
    });
  }

  public void deactivatePayments() {
    Money money = balance.getValue();
    if (money == null) {
      paymentStateEvents.setValue(PaymentStateEvent.NO_BALANCE);
    } else if (money.isPositive()) {
      paymentStateEvents.setValue(PaymentStateEvent.DEACTIVATE_WITH_BALANCE);
    } else {
      paymentStateEvents.setValue(PaymentStateEvent.DEACTIVATE_WITHOUT_BALANCE);
    }
  }

  public void confirmDeactivatePayments() {
    if (store.getState().getPaymentsState() != PaymentsHomeState.PaymentsState.ACTIVATED) {
      return;
    }

    store.update(state -> state.updatePaymentsEnabled(PaymentsHomeState.PaymentsState.DEACTIVATING));

    paymentsHomeRepository.deactivatePayments(result -> {
      store.update(state -> state.updatePaymentsEnabled(result ? PaymentsHomeState.PaymentsState.NOT_ACTIVATED : PaymentsHomeState.PaymentsState.ACTIVATED));

      if (result) {
       paymentStateEvents.postValue(PaymentStateEvent.DEACTIVATED);
      }
    });
  }

  public void refreshExchangeRates(boolean refreshIfAble) {
    store.update(state -> state.updateExchangeRateLoadState(LoadState.LOADING));
    currencyExchangeRepository.getCurrencyExchange(new AsynchronousCallback.WorkerThread<CurrencyExchange, Throwable>() {
      @Override
      public void onComplete(@Nullable CurrencyExchange result) {
        store.update(state -> state.updateCurrencyExchange(result, LoadState.LOADED));
      }

      @Override
      public void onError(@Nullable Throwable error) {
        Log.w(TAG, error);
        store.update(state -> state.updateExchangeRateLoadState(LoadState.ERROR));
      }
    }, refreshIfAble);
  }

  public static final class Factory implements ViewModelProvider.Factory {
    @Override
    public @NonNull <T extends ViewModel> T create(@NonNull Class<T> modelClass) {
      //noinspection ConstantConditions
      return modelClass.cast(new PaymentsHomeViewModel(new PaymentsHomeRepository(),
                                                       new PaymentsRepository(),
                                                       new CurrencyExchangeRepository(AppDependencies.getPayments())));
    }
  }

  public enum ErrorEnabling {
    REGION,
    NETWORK
  }
}
