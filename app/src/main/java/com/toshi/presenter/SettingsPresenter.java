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

import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.Toast;

import com.toshi.R;
import com.toshi.model.local.User;
import com.toshi.model.network.Balance;
import com.toshi.util.ImageUtil;
import com.toshi.util.LogUtil;
import com.toshi.util.OnSingleClickListener;
import com.toshi.util.SharedPrefsUtil;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.AboutActivity;
import com.toshi.view.activity.BackupPhraseInfoActivity;
import com.toshi.view.activity.CurrencyActivity;
import com.toshi.view.activity.DepositActivity;
import com.toshi.view.activity.QrCodeActivity;
import com.toshi.view.activity.SignOutActivity;
import com.toshi.view.activity.TransactionOverviewActivity;
import com.toshi.view.activity.TrustedFriendsActivity;
import com.toshi.view.activity.ViewProfileActivity;
import com.toshi.view.adapter.SettingsAdapter;
import com.toshi.view.custom.HorizontalLineDivider;
import com.toshi.view.fragment.toplevel.SettingsFragment;

import java.math.BigInteger;

import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public final class SettingsPresenter implements
        Presenter<SettingsFragment> {

    private User localUser;
    private SettingsFragment fragment;
    private CompositeSubscription subscriptions;
    private boolean firstTimeAttaching = true;

    @Override
    public void onViewAttached(final SettingsFragment fragment) {
        this.fragment = fragment;

        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }

        initShortLivingObjects();
    }

    private void initShortLivingObjects() {
        fetchUser();
        initRecyclerView();
        updateUi();
        setSecurityState();
        attachBalanceSubscriber();
        initClickListeners();
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
    }

    private void fetchUser() {
        final Subscription sub =
                BaseApplication
                .get()
                .getUserManager()
                .getUserObservable()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleUserLoaded,
                        this::handleUserError
                );

        if (!BaseApplication.get()
                .getUserManager()
                .getUserObservable()
                .hasValue()) {
            handleNoUser();
        }

        this.subscriptions.add(sub);
    }

    private void handleUserLoaded(final User user) {
        if (user == null) {
            handleNoUser();
            return;
        }
        this.localUser = user;
        updateUi();
    }

    private void handleUserError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error during fetching user", throwable);
    }

    private void handleNoUser() {
        if (this.fragment == null) {
            return;
        }

        this.fragment.getBinding().name.setText(this.fragment.getString(R.string.profile__unknown_name));
        this.fragment.getBinding().username.setText("");
        this.fragment.getBinding().ratingView.setStars(0.0);
        final String reviewCount = this.fragment.getString(R.string.parentheses, 0);
        this.fragment.getBinding().numberOfRatings.setText(reviewCount);
        this.fragment.getBinding().avatar.setImageResource(R.drawable.ic_unknown_user_24dp);
    }

    private void initRecyclerView() {
        final SettingsAdapter adapter = new SettingsAdapter();
        adapter.setOnItemClickListener(this::handleItemClickListener);
        final RecyclerView recyclerView = this.fragment.getBinding().settings;
        final RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this.fragment.getContext());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);

        final int padding = fragment.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        final HorizontalLineDivider lineDivider =
                new HorizontalLineDivider(ContextCompat.getColor(this.fragment.getContext(), R.color.divider))
                        .setRightPadding(padding)
                        .setLeftPadding(padding);
        recyclerView.addItemDecoration(lineDivider);
    }

    private void handleItemClickListener(final int option) {
        switch (option) {
            case SettingsAdapter.LOCAL_CURRENCY: {
                goToCurrencyActivity();
                break;
            }
            case SettingsAdapter.ABOUT: {
                final Intent intent = new Intent(this.fragment.getContext(), AboutActivity.class);
                this.fragment.getContext().startActivity(intent);
                break;
            }
            case SettingsAdapter.TRANSACTIONS: {
                final Intent intent = new Intent(this.fragment.getContext(), TransactionOverviewActivity.class);
                this.fragment.getContext().startActivity(intent);
                break;
            }
            case SettingsAdapter.SIGN_OUT: {
                dialogHandler();
                break;
            }
            default: {
                Toast.makeText(this.fragment.getContext(), "This option is not supported", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void goToCurrencyActivity() {
        final Intent intent = new Intent(this.fragment.getContext(), CurrencyActivity.class);
        this.fragment.startActivity(intent);
    }

    private void dialogHandler() {
        final Subscription sub =
                BaseApplication
                .get()
                .getBalanceManager()
                .getBalanceObservable()
                .first()
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::showDialog,
                        this::handleDialogError
                );

        this.subscriptions.add(sub);
    }

    private void handleDialogError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error showing dialog", throwable);
    }

    private void showDialog(final Balance balance) {
        if (shouldCancelSignOut(balance)) {
            showSignOutCancelledDialog();
        } else {
            showSignOutWarning();
        }
    }

    private boolean shouldCancelSignOut(final Balance balance) {
        return !SharedPrefsUtil.hasBackedUpPhrase() && !isWalletEmpty(balance);
    }
    private boolean isWalletEmpty(final Balance balance) {
        return balance.getUnconfirmedBalance().compareTo(BigInteger.ZERO) == 0;
    }

    private void showSignOutCancelledDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this.fragment.getContext(), R.style.AlertDialogCustom);
        builder.setTitle(R.string.sign_out_cancelled_title)
                .setMessage(R.string.sign_out_cancelled_message)
                .setPositiveButton(R.string.ok, (dialog, which) -> {
                    dialog.dismiss();
                });
        builder.create().show();
    }

    private void showSignOutWarning() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this.fragment.getContext(), R.style.AlertDialogCustom);
        builder.setTitle(R.string.sign_out_warning_title)
                .setMessage(R.string.sign_out_warning_message)
                .setPositiveButton(R.string.yes, (dialog, which) -> {
                    dialog.dismiss();
                    showSignOutConfirmationDialog();
                })
                .setNegativeButton(R.string.no, (dialog, which) -> {
                    dialog.dismiss();
                });
        builder.create().show();
    }

    private void showSignOutConfirmationDialog() {
        final AlertDialog.Builder builder = new AlertDialog.Builder(this.fragment.getContext(), R.style.AlertDialogCustom);
        builder.setTitle(R.string.sign_out_confirmation_title)
                .setPositiveButton(R.string.sign_out, (dialog, which) -> {
                    goToSignOutActivity();
                    dialog.dismiss();
                })
                .setNegativeButton(R.string.cancel, (dialog, which) -> {
                    dialog.dismiss();
                });
        builder.create().show();
    }

    private void goToSignOutActivity() {
        final Intent intent = new Intent(this.fragment.getActivity(), SignOutActivity.class);
        this.fragment.getActivity().startActivity(intent);
        this.fragment.getActivity().finish();
    }

    private void updateUi() {
        if (this.localUser == null || this.fragment == null) {
            return;
        }

        this.fragment.getBinding().name.setText(this.localUser.getDisplayName());
        this.fragment.getBinding().username.setText(this.localUser.getUsername());
        this.fragment.getBinding().ratingView.setStars(this.localUser.getAverageRating());
        final String reviewCount = this.fragment.getString(R.string.parentheses, this.localUser.getReviewCount());
        this.fragment.getBinding().numberOfRatings.setText(reviewCount);
        ImageUtil.load(this.localUser.getAvatar(), this.fragment.getBinding().avatar);
    }

    private void setSecurityState() {
        if (SharedPrefsUtil.hasBackedUpPhrase()) {
            this.fragment.getBinding().checkboxBackupPhrase.setChecked(true);
            this.fragment.getBinding().securityStatus.setVisibility(View.GONE);
        }
    }

    private void attachBalanceSubscriber() {
        final Subscription sub =
                BaseApplication
                .get()
                .getBalanceManager()
                .getBalanceObservable()
                .observeOn(AndroidSchedulers.mainThread())
                .filter(balance -> balance != null)
                .subscribe(
                        this::renderBalance,
                        this::handleBalanceError
                );

        this.subscriptions.add(sub);
    }

    private void renderBalance(final Balance balance) {
        if (this.fragment == null) return;

        this.fragment.getBinding().ethBalance.setText(balance.getFormattedUnconfirmedBalance());
        final Subscription getLocalBalanceSub =
                balance
                        .getFormattedLocalBalance()
                        .observeOn(AndroidSchedulers.mainThread())
                        .subscribe(
                                this::handleFormattedLocalBalance,
                                this::handleFormattedLocalBalanceError
                        );

        this.subscriptions.add(getLocalBalanceSub);
    }

    private void handleBalanceError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error during fetching balance", throwable);
    }

    private void handleFormattedLocalBalance(final String localBalance) {
        this.fragment.getBinding().localCurrencyBalance.setText(localBalance);
    }

    private void handleFormattedLocalBalanceError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error while getting local balance", throwable);
    }

    private void initClickListeners() {
        this.fragment.getBinding().myProfileCard.setOnClickListener(this.handleMyProfileClicked);
        this.fragment.getBinding().trustedFriends.setOnClickListener(this::handleTrustedFriendsClicked);
        this.fragment.getBinding().backupPhrase.setOnClickListener(this::handleBackupPhraseClicked);
        this.fragment.getBinding().myQrCode.setOnClickListener(this::handleMyQrCodeClicked);
        this.fragment.getBinding().addMoney.setOnClickListener(this::handleAddMoneyClicked);
    }

    private final OnSingleClickListener handleMyProfileClicked = new OnSingleClickListener() {
        @Override
        public void onSingleClick(final View v) {
            if (fragment == null) return;
            final Intent intent = new Intent(fragment.getActivity(), ViewProfileActivity.class);
            fragment.startActivity(intent);
        }
    };

    private void handleTrustedFriendsClicked(final View view) {
        if (this.fragment == null) return;
        final Intent intent = new Intent(this.fragment.getContext(), TrustedFriendsActivity.class);
        this.fragment.getContext().startActivity(intent);
    }

    private void handleBackupPhraseClicked(final View view) {
        if (this.fragment == null) return;
        final Intent intent = new Intent(this.fragment.getContext(), BackupPhraseInfoActivity.class);
        this.fragment.getContext().startActivity(intent);
    }

    private void handleMyQrCodeClicked(final View view) {
        if (this.fragment == null) return;
        final Intent intent = new Intent(this.fragment.getContext(), QrCodeActivity.class);
        this.fragment.getContext().startActivity(intent);
    }

    private void handleAddMoneyClicked(final View view) {
        if (fragment == null) return;
        final Intent intent = new Intent(fragment.getActivity(), DepositActivity.class);
        fragment.getContext().startActivity(intent);
    }

    @Override
    public void onViewDetached() {
        this.subscriptions.clear();
        this.fragment = null;
    }

    @Override
    public void onDestroyed() {
        this.fragment = null;
    }
}
