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

package com.toshi.presenter;

import android.app.Activity;
import android.content.Intent;
import android.support.annotation.NonNull;
import android.view.View;
import android.widget.Toast;

import com.toshi.R;
import com.toshi.crypto.util.TypeConverter;
import com.toshi.exception.CurrencyException;
import com.toshi.model.local.Network;
import com.toshi.util.CurrencyUtil;
import com.toshi.util.EthUtil;
import com.toshi.util.LocaleUtil;
import com.toshi.util.LogUtil;
import com.toshi.util.PaymentType;
import com.toshi.util.SharedPrefsUtil;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.AmountActivity;
import com.toshi.view.adapter.AmountInputAdapter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormatSymbols;

import rx.subscriptions.CompositeSubscription;

public class AmountPresenter implements Presenter<AmountActivity> {

    public static final String INTENT_EXTRA__ETH_AMOUNT = "eth_amount";

    private AmountActivity activity;
    private CompositeSubscription subscriptions;
    private boolean firstTimeAttaching = true;

    private char separator;
    private char zero;
    private String encodedEthAmount;
    private @PaymentType.Type  int viewType;

    @Override
    public void onViewAttached(AmountActivity view) {
        this.activity = view;

        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }

        initShortLivingObjejcts();
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
    }

    private void initShortLivingObjejcts() {
        getIntentData();
        initView();
        setCurrency();
        initSeparator();
    }

    @SuppressWarnings("WrongConstant")
    private void getIntentData() {
        this.viewType = this.activity.getIntent().getIntExtra(AmountActivity.VIEW_TYPE, PaymentType.TYPE_SEND);
    }

    private void initView() {
        initToolbar();
        initNetworkView();
        updateEthAmount();

        this.activity.getBinding().amountInputView.setOnAmountClickedListener(this.amountClickedListener);
        this.activity.getBinding().btnContinue.setOnClickListener(this.continueClickListener);
    }

    private void setCurrency() {
        try {
            final String currency = SharedPrefsUtil.getCurrency();
            final String currencyCode = CurrencyUtil.getCode(currency);
            final String currencySymbol = CurrencyUtil.getSymbol(currency);
            this.activity.getBinding().localCurrencySymbol.setText(currencySymbol);
            this.activity.getBinding().localCurrencyCode.setText(currencyCode);
        } catch (CurrencyException e) {
            Toast.makeText(
                    this.activity,
                    this.activity.getString(R.string.unsupported_currency_message),
                    Toast.LENGTH_SHORT
            ).show();
        }
    }

    private void initSeparator() {
        final DecimalFormatSymbols dcf = LocaleUtil.getDecimalFormatSymbols();
        this.separator = dcf.getMonetaryDecimalSeparator();
        this.zero = dcf.getZeroDigit();
    }

    private void initToolbar() {
        final String title = this.viewType == PaymentType.TYPE_SEND
                ? this.activity.getString(R.string.send)
                : this.activity.getString(R.string.request);

        this.activity.getBinding().title.setText(title);
        this.activity.getBinding().closeButton.setOnClickListener(this.backButtonClickListener);
    }

    private void initNetworkView() {
        final Network network = SharedPrefsUtil.getCurrentNetwork();
        this.activity.getBinding().network.setText(network.getName());
    }

    private View.OnClickListener continueClickListener = new View.OnClickListener() {
        @Override
        public void onClick(final View unused) {
            if (encodedEthAmount == null) {
                return;
            }

            final Intent intent = new Intent();
            intent.putExtra(INTENT_EXTRA__ETH_AMOUNT, encodedEthAmount);
            activity.setResult(Activity.RESULT_OK, intent);
            activity.finish();
        }
    };

    private View.OnClickListener backButtonClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View view) {
            activity.finish();
        }
    };

    private AmountInputAdapter.OnKeyboardItemClicked amountClickedListener = new AmountInputAdapter.OnKeyboardItemClicked() {
        @Override
        public void onValueClicked(final char value) {
            handleValueClicked(value);
        }

        @Override
        public void onBackSpaceClicked() {
            handleBackspaceClicked();
        }
    };

    private void handleBackspaceClicked() {
        final String currentLocalValue = this.activity.getBinding().localValue.getText().toString();
        final int endIndex = Math.max(0, currentLocalValue.length() -1);
        final String newLocalValue = currentLocalValue.substring(0, endIndex);

        if (newLocalValue.equals(String.valueOf(zero))) {
            this.activity.getBinding().localValue.setText("");
        } else {
            this.activity.getBinding().localValue.setText(newLocalValue);
        }
        updateEthAmount();
    }

    private void handleValueClicked(final char value) {
        if (value == this.separator) {
            handleSeparatorClicked();
        } else {
            updateValue(value);
        }
    }

    private void handleSeparatorClicked() {
        final String currentLocalValue = this.activity.getBinding().localValue.getText().toString();

        // Only allow a single decimal separator
        if (currentLocalValue.indexOf(this.separator) >= 0) {
            return;
        }

        updateValue(this.separator);
    }

    private void updateValue(final char value) {
        appendValueInUi(value);
        updateEthAmount();
    }

    private void appendValueInUi(final char value) {
        final String currentLocalValue = this.activity.getBinding().localValue.getText().toString();
        if (currentLocalValue.length() >= 10) {
            return;
        }

        if (currentLocalValue.length() == 0 && value == this.zero) {
            return;
        }

        if (currentLocalValue.length() == 0 && value == this.separator) {
            final String localValue = String.format("%s%s", String.valueOf(this.zero), String.valueOf(this.separator));
            this.activity.getBinding().localValue.setText(localValue);
            return;
        }

        final String newLocalValue = currentLocalValue + value;
        this.activity.getBinding().localValue.setText(newLocalValue);
    }

    private void updateEthAmount() {
        final BigDecimal localValue = getLocalValueAsBigDecimal();

        this.subscriptions.add(
                BaseApplication
                .get()
                .getBalanceManager()
                .convertLocalCurrencyToEth(localValue)
                .subscribe(
                        this::handleEth,
                        this::handleEthError
                )
        );
    }

    private void handleEth(final BigDecimal ethAmount) {
        this.activity.getBinding().ethValue.setText(EthUtil.ethAmountToUserVisibleString(ethAmount));
        this.activity.getBinding().btnContinue.setEnabled(ethAmount.compareTo(BigDecimal.ZERO) != 0);

        final BigInteger weiAmount = EthUtil.ethToWei(ethAmount);
        this.encodedEthAmount = TypeConverter.toJsonHex(weiAmount);
    }

    private void handleEthError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error while converting local currency to eth", throwable);
    }

    @NonNull
    private BigDecimal getLocalValueAsBigDecimal() {
        final String currentLocalValue = this.activity.getBinding().localValue.getText().toString();
        if (currentLocalValue.length() == 0 || currentLocalValue.equals(String.valueOf(this.separator))) {
            return BigDecimal.ZERO;
        }

        final String[] parts = currentLocalValue.split(String.valueOf(this.separator));
        final String integerPart = parts.length == 0 ? currentLocalValue : parts[0];
        final String fractionalPart = parts.length < 2 ? "0" : parts[1];
        final String fullValue = integerPart + "." + fractionalPart;

        final String trimmedValue = fullValue.endsWith(".0")
                ? fullValue.substring(0, fullValue.length() - 2)
                : fullValue;

        return new BigDecimal(trimmedValue);
    }

    @Override
    public void onViewDetached() {
        this.subscriptions.clear();
        this.activity = null;
    }

    @Override
    public void onDestroyed() {
        this.subscriptions = null;
    }
}