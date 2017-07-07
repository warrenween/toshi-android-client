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
import android.graphics.drawable.Drawable;
import android.support.annotation.ColorInt;
import android.support.v4.content.ContextCompat;
import android.support.v7.content.res.AppCompatResources;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.toshi.R;
import com.toshi.databinding.ActivityViewAppBinding;
import com.toshi.model.local.ActivityResultHolder;
import com.toshi.model.local.User;
import com.toshi.model.network.App;
import com.toshi.model.network.ReputationScore;
import com.toshi.util.ImageUtil;
import com.toshi.util.LogUtil;
import com.toshi.util.OnSingleClickListener;
import com.toshi.util.PaymentType;
import com.toshi.util.SoundManager;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.AmountActivity;
import com.toshi.view.activity.ChatActivity;
import com.toshi.view.activity.ViewAppActivity;

import rx.Completable;
import rx.Single;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.schedulers.Schedulers;
import rx.subscriptions.CompositeSubscription;

public class ViewAppPresenter implements Presenter<ViewAppActivity> {

    private static final int ETH_PAY_CODE = 2;

    private ViewAppActivity activity;
    private CompositeSubscription subscriptions;
    private boolean firstTimeAttaching = true;
    private App app;
    private User appAsUser;
    private String appTokenId;

    @Override
    public void onViewAttached(ViewAppActivity view) {
        this.activity = view;

        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }
        initShortLivingObjects();
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
    }

    private void initShortLivingObjects() {
        processIntentData();
        loadApp();
        fetchUserReputation();
        initClickListeners();
    }

    private void processIntentData() {
        this.appTokenId = this.activity.getIntent().getStringExtra(ViewAppActivity.APP_OWNER_ADDRESS);
        if (this.appTokenId == null) {
            Toast.makeText(this.activity, R.string.error__app_loading, Toast.LENGTH_LONG).show();
            this.activity.finish();
        }
    }

    private void loadApp() {
        final Subscription appSub =
                getAppById(this.appTokenId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleAppLoaded,
                        this::handleAppLoadingFailed
                );

        final Subscription userSub =
                BaseApplication
                .get()
                .getRecipientManager()
                .getUserFromTokenId(this.appTokenId)
                .subscribeOn(Schedulers.io())
                .observeOn(AndroidSchedulers.mainThread())
                .doOnSuccess(__ -> this.updateFavoriteButtonState())
                .subscribe(
                        user -> this.appAsUser = user,
                        this::handleAppLoadingFailed
                );

        this.subscriptions.add(appSub);
        this.subscriptions.add(userSub);
    }

    private Single<App> getAppById(final String appId) {
        return BaseApplication
                .get()
                .getAppsManager()
                .getApp(appId);
    }

    private void handleAppLoaded(final App app) {
        this.app = app;
        if (this.app == null || this.activity == null) return;
        initViewWithAppData();
    }

    private void handleAppLoadingFailed(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error during fetching of app", throwable);
        if (this.activity == null) return;
        this.activity.finish();
        Toast.makeText(this.activity, R.string.error__app_loading, Toast.LENGTH_LONG).show();
    }

    private void fetchUserReputation() {
        final Subscription reputationSub =
                BaseApplication
                .get()
                .getReputationManager()
                .getReputationScore(this.appTokenId)
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleReputationResponse,
                        this::handleReputationError
                );

        this.subscriptions.add(reputationSub);
    }

    private void handleReputationResponse(final ReputationScore reputationScore) {
        final int revCount = reputationScore.getReviewCount();
        final String ratingText = this.activity.getResources().getQuantityString(R.plurals.ratings, revCount, revCount);
        this.activity.getBinding().reviewCount.setText(ratingText);
        this.activity.getBinding().ratingView.setStars(reputationScore.getAverageRating());
        // NOTE: design has decided that rating should be showed here instead of reputation score for now
        this.activity.getBinding().reputationScore.setText(String.valueOf(reputationScore.getAverageRating()));
        this.activity.getBinding().ratingInfo.setRatingInfo(reputationScore);
    }

    private void handleReputationError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error during reputation fetching", throwable);
    }

    private void initViewWithAppData() {
        if (this.app == null) {
            return;
        }
        final ActivityViewAppBinding binding = this.activity.getBinding();
        binding.title.setText(this.app.getDisplayName());
        binding.name.setText(this.app.getDisplayName());
        binding.username.setText(this.app.getUsername());
        ImageUtil.load(this.app.getAvatar(), binding.avatar);
    }

    private void initClickListeners() {
        this.activity.getBinding().closeButton.setOnClickListener(this::handleClosedClicked);
        this.activity.getBinding().messageContactButton.setOnClickListener(this::handleOnMessageClicked);
        this.activity.getBinding().favorite.setOnClickListener(this.toggleFavorite);
        this.activity.getBinding().pay.setOnClickListener(v -> handlePayClicked());
    }

    private void handleClosedClicked(final View view) {
        this.activity.finish();
    }

    private void handleOnMessageClicked(final View v) {
        if (this.app == null) {
            return;
        }
        final Intent intent = new Intent(this.activity, ChatActivity.class);
        intent.putExtra(ChatActivity.EXTRA__THREAD_ID, this.app.getTokenId());
        this.activity.startActivity(intent);
    }

    private final OnSingleClickListener toggleFavorite = new OnSingleClickListener() {
        @Override
        public void onSingleClick(final View v) {
            final Subscription sub =
                    isFavorited()
                    .subscribe(
                            this::toggleFavorite,
                            throwable -> handleFavoredError(throwable)
                    );

            subscriptions.add(sub);
        }

        private void toggleFavorite(final boolean isCurrentlyFavorited) {
            if (isCurrentlyFavorited) {
                removeFromFavorites()
                    .subscribe(() -> updateFavoriteButtonState());
            } else {
                addToFavorites()
                    .doOnCompleted(() -> SoundManager.getInstance().playSound(SoundManager.ADD_CONTACT))
                    .subscribe(() -> updateFavoriteButtonState());
            }
        }
    };

    private Single<Boolean> isFavorited() {
        return BaseApplication
                .get()
                .getRecipientManager()
                .isUserAContact(this.appAsUser)
                .observeOn(AndroidSchedulers.mainThread());
    }

    private Completable removeFromFavorites() {
        return BaseApplication
                .get()
                .getRecipientManager()
                .deleteContact(this.appAsUser);
    }

    private Completable addToFavorites() {
        return BaseApplication
                .get()
                .getRecipientManager()
                .saveContact(this.appAsUser);
    }

    private void handlePayClicked() {
        final Intent intent = new Intent(this.activity, AmountActivity.class)
                .putExtra(AmountActivity.VIEW_TYPE, PaymentType.TYPE_SEND);
        this.activity.startActivityForResult(intent, ETH_PAY_CODE);
    }

    public boolean handleActivityResult(final ActivityResultHolder resultHolder) {
        if (resultHolder.getResultCode() != Activity.RESULT_OK || this.activity == null) return false;

        final int requestCode = resultHolder.getRequestCode();
        if (requestCode == ETH_PAY_CODE) {
            goToChatActivityFromPay(resultHolder.getIntent());
        }

        return true;
    }
    private void goToChatActivityFromPay(final Intent payResultIntent) {
        final String ethAmount = payResultIntent.getStringExtra(AmountPresenter.INTENT_EXTRA__ETH_AMOUNT);
        final String appId = this.activity.getIntent().getStringExtra(ViewAppActivity.APP_OWNER_ADDRESS);
        final Intent intent = new Intent(this.activity, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA__THREAD_ID, appId)
                .putExtra(ChatActivity.EXTRA__ETH_AMOUNT, ethAmount)
                .putExtra(ChatActivity.EXTRA__PAYMENT_ACTION, PaymentType.TYPE_SEND);
        this.activity.startActivity(intent);
        this.activity.finish();
    }

    private void updateFavoriteButtonState() {
        final Subscription sub =
                isFavorited()
                .subscribe(
                        this::updateFavoriteButtonState,
                        this::handleFavoredError
                );

        this.subscriptions.add(sub);
    }

    private void updateFavoriteButtonState(final boolean isCurrentlyFavorited) {
        final Button favoriteButton = this.activity.getBinding().favorite;
        favoriteButton.setSoundEffectsEnabled(isCurrentlyFavorited);

        final Drawable checkMark = isCurrentlyFavorited
                ? AppCompatResources.getDrawable(this.activity, R.drawable.ic_star_selected)
                : AppCompatResources.getDrawable(this.activity, R.drawable.ic_star_unselected);
        favoriteButton.setCompoundDrawablesWithIntrinsicBounds(null, checkMark, null, null);

        final @ColorInt int color = isCurrentlyFavorited
                ? ContextCompat.getColor(this.activity, R.color.colorPrimary)
                : ContextCompat.getColor(this.activity, R.color.profile_icon_text_color);
        favoriteButton.setTextColor(color);
    }

    private void handleFavoredError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error while checking if app is favored", throwable);
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
