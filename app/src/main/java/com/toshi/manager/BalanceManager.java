/*
 * 	Copyright (c) 2017. Toshi Browser, Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.toshi.manager;


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;

import com.toshi.crypto.HDWallet;
import com.toshi.manager.network.CurrencyService;
import com.toshi.manager.network.EthereumService;
import com.toshi.model.local.Network;
import com.toshi.model.local.Networks;
import com.toshi.model.network.Balance;
import com.toshi.model.network.Currencies;
import com.toshi.model.network.GcmDeregistration;
import com.toshi.model.network.GcmRegistration;
import com.toshi.model.network.MarketRates;
import com.toshi.model.network.ServerTime;
import com.toshi.model.sofa.Payment;
import com.toshi.service.RegistrationIntentService;
import com.toshi.util.CurrencyUtil;
import com.toshi.util.FileNames;
import com.toshi.util.GcmUtil;
import com.toshi.util.LogUtil;
import com.toshi.util.SharedPrefsUtil;
import com.toshi.view.BaseApplication;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;

import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;

public class BalanceManager {

    private final static BehaviorSubject<Balance> balanceObservable = BehaviorSubject.create();
    private static final String LAST_KNOWN_BALANCE = "lkb";

    private HDWallet wallet;
    private SharedPreferences prefs;

    /* package */ BalanceManager() {
    }

    public BehaviorSubject<Balance> getBalanceObservable() {
        return balanceObservable;
    }

    public BalanceManager init(final HDWallet wallet) {
        this.wallet = wallet;
        initCachedBalance();
        attachConnectivityObserver();
        return this;
    }

    private void initCachedBalance() {
        this.prefs = BaseApplication.get().getSharedPreferences(FileNames.BALANCE_PREFS, Context.MODE_PRIVATE);
        final Balance cachedBalance = new Balance(readLastKnownBalance());
        handleNewBalance(cachedBalance);
    }

    private void attachConnectivityObserver() {
        BaseApplication
                .get()
                .isConnectedSubject()
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(
                        __ -> this.refreshBalance(),
                        this::handleConnectionStateError
                );
    }

    private void handleConnectionStateError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error checking connection state", throwable);
    }

    public void refreshBalance() {
            EthereumService
                .getApi()
                .getBalance(this.wallet.getPaymentAddress())
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(
                        this::handleNewBalance,
                        this::handleBalanceError
                );
    }

    private void handleNewBalance(final Balance balance) {
        writeLastKnownBalance(balance);
        balanceObservable.onNext(balance);
    }

    private void handleBalanceError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error while fetching balance", throwable);
    }

    private Single<MarketRates> getRates() {
        return fetchLatestRates()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread());
    }

    private Single<MarketRates> fetchLatestRates() {
        return CurrencyService
                .getApi()
                .getRates("ETH")
                .onErrorReturn(__ -> new MarketRates());
    }

    public Single<Currencies> getCurrencies() {
        return CurrencyService
                .getApi()
                .getCurrencies()
                .subscribeOn(Schedulers.io());
    }

    public Single<String> convertEthToLocalCurrencyString(final BigDecimal ethAmount) {
         return getRates()
                 .flatMap((marketRates) -> mapToString(marketRates, ethAmount));
    }

    private Single<String> mapToString(final MarketRates marketRates,
                               final BigDecimal ethAmount) {
        return Single.fromCallable(() -> {
            final String currency = SharedPrefsUtil.getCurrency();
            final BigDecimal marketRate = marketRates.getRate(currency);
            final BigDecimal localAmount = marketRate.multiply(ethAmount);

            final DecimalFormat numberFormat = CurrencyUtil.getNumberFormat();
            numberFormat.setGroupingUsed(true);
            numberFormat.setMaximumFractionDigits(2);
            numberFormat.setMinimumFractionDigits(2);

            final String amount = numberFormat.format(localAmount);
            final String currencyCode = CurrencyUtil.getCode(currency);
            final String currencySymbol = CurrencyUtil.getSymbol(currency);

            return String.format("%s%s %s", currencySymbol, amount, currencyCode);
        });
    }

    public Single<BigDecimal> convertEthToLocalCurrency(final BigDecimal ethAmount) {
        return getRates()
                .flatMap((marketRates) -> mapToLocalCurrency(marketRates, ethAmount));
    }

    private Single<BigDecimal> mapToLocalCurrency(final MarketRates marketRates,
                                                  final BigDecimal ethAmount) {
        return Single.fromCallable(() -> {
            final String currency = SharedPrefsUtil.getCurrency();
            final BigDecimal marketRate = marketRates.getRate(currency);
            return marketRate.multiply(ethAmount);
        });
    }

    public Single<BigDecimal> convertLocalCurrencyToEth(final BigDecimal localAmount) {
        return getRates()
                .flatMap((marketRates) -> mapToEth(marketRates, localAmount));
    }

    private Single<BigDecimal> mapToEth(final MarketRates marketRates,
                                        final BigDecimal localAmount) {
        return Single.fromCallable(() -> {
            if (localAmount.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }

            final String currency = SharedPrefsUtil.getCurrency();
            final BigDecimal marketRate = marketRates.getRate(currency);
            if (marketRate.compareTo(BigDecimal.ZERO) == 0) {
                return BigDecimal.ZERO;
            }
            return localAmount.divide(marketRate, 8, RoundingMode.HALF_DOWN);
        });
    }

    public Single<Void> registerForGcm(final String token) {
        return EthereumService
                .getApi()
                .getTimestamp()
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .flatMap((st) -> registerForGcmWithTimestamp(token, st));
    }

    public Completable unregisterFromGcm(final String token) {
        return EthereumService
                .getApi()
                .getTimestamp()
                .subscribeOn(Schedulers.io())
                .flatMapCompletable((st) -> unregisterGcmWithTimestamp(token, st));
    }

    private Single<Void> registerForGcmWithTimestamp(final String token, final ServerTime serverTime) {
        if (serverTime == null) {
            throw new IllegalStateException("ServerTime was null");
        }

        return EthereumService
                .getApi()
                .registerGcm(serverTime.get(), new GcmRegistration(token, wallet.getPaymentAddress()));
    }

    private Completable unregisterGcmWithTimestamp(final String token, final ServerTime serverTime) {
        if (serverTime == null) {
            return Completable.error(new IllegalStateException("Unable to fetch server time"));
        }

        return EthereumService
                .getApi()
                .unregisterGcm(serverTime.get(), new GcmDeregistration(token))
                .toCompletable();
    }

    /* package */ Single<Payment> getTransactionStatus(final String transactionHash) {
        return EthereumService
                .get()
                .getStatusOfTransaction(transactionHash);
    }

    private String readLastKnownBalance() {
        return this.prefs
                .getString(LAST_KNOWN_BALANCE, "0x0");
    }

    private void writeLastKnownBalance(final Balance balance) {
        this.prefs
                .edit()
                .putString(LAST_KNOWN_BALANCE, balance.getUnconfirmedBalanceAsHex())
                .apply();
    }

    //Don't unregister the default network
    public Completable changeNetwork(final Network network) {
        final boolean isDefaultNetwork = Networks.getDefaultNetwork().equals(SharedPrefsUtil.getCurrentNetwork().getId());
        if (isDefaultNetwork) {
            return changeEthBaseUrl(network)
                    .andThen(registerEthGcm().first().toCompletable())
                    .subscribeOn(Schedulers.io())
                    .doOnCompleted(() -> SharedPrefsUtil.setCurrentNetwork(network));
        }

        return unregisterEthGcm()
                .andThen(changeEthBaseUrl(network))
                .andThen(registerEthGcm().first().toCompletable())
                .subscribeOn(Schedulers.io())
                .doOnCompleted(() -> SharedPrefsUtil.setCurrentNetwork(network));
    }

    private Observable<Void> registerEthGcm() {
        final Intent intent = new Intent(BaseApplication.get(), RegistrationIntentService.class)
                .putExtra(RegistrationIntentService.FORCE_UPDATE, true)
                .putExtra(RegistrationIntentService.ETH_REGISTRATION_ONLY, true);
        BaseApplication.get().startService(intent);
        return RegistrationIntentService.getGcmRegistrationObservable();
    }

    private Completable unregisterEthGcm() {
        return GcmUtil
                .getGcmToken()
                .flatMapCompletable(token ->
                        BaseApplication
                        .get()
                        .getBalanceManager()
                        .unregisterFromGcm(token));
    }

    private Completable changeEthBaseUrl(final Network network) {
        return Completable.fromAction(() -> EthereumService.get().changeBaseUrl(network.getUrl()));
    }

    public void clear() {
        this.prefs
                .edit()
                .clear()
                .apply();
    }
}
