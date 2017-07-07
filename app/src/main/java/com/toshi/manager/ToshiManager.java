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


import com.toshi.crypto.HDWallet;
import com.toshi.crypto.signal.SignalPreferences;
import com.toshi.manager.store.DbMigration;
import com.toshi.util.LogUtil;
import com.toshi.util.SharedPrefsUtil;
import com.toshi.view.BaseApplication;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import io.realm.Realm;
import io.realm.RealmConfiguration;
import rx.Single;
import rx.schedulers.Schedulers;
import rx.subjects.BehaviorSubject;

public class ToshiManager {

    public static final long CACHE_TIMEOUT = 1000 * 60 * 5;

    private final BehaviorSubject<HDWallet> walletSubject = BehaviorSubject.create();

    private AppsManager appsManager;
    private BalanceManager balanceManager;
    private HDWallet wallet;
    private SofaMessageManager sofaMessageManager;
    private TransactionManager transactionManager;
    private UserManager userManager;
    private RecipientManager recipientManager;
    private ReputationManager reputationManager;
    private ExecutorService singleExecutor;
    private boolean areManagersInitialised = false;
    private RealmConfiguration realmConfig;

    public ToshiManager() {
        this.singleExecutor = Executors.newSingleThreadExecutor();
        this.appsManager = new AppsManager();
        this.balanceManager = new BalanceManager();
        this.userManager = new UserManager();
        this.reputationManager = new ReputationManager();
        this.sofaMessageManager = new SofaMessageManager();
        this.transactionManager = new TransactionManager();
        this.recipientManager = new RecipientManager();
        this.walletSubject.onNext(null);

        tryInit()
                .subscribe(
                        __ -> {},
                        ex -> LogUtil.i(getClass(), "Early init failed."));
    }

    public Single<ToshiManager> init() {
        if (this.wallet != null && this.areManagersInitialised) {
            return Single.just(this);
        }

        return new HDWallet()
                .getOrCreateWallet()
                .doOnSuccess(this::setWallet)
                .flatMap(__ -> initManagers())
                .subscribeOn(Schedulers.from(this.singleExecutor));
    }

    public Single<ToshiManager> init(final HDWallet wallet) {
        this.setWallet(wallet);
        return initManagers()
                .subscribeOn(Schedulers.from(this.singleExecutor));
    }

    public Single<ToshiManager> tryInit() {
        if (this.wallet != null && this.areManagersInitialised) {
            return Single.just(this);
        }
        return new HDWallet()
                .getExistingWallet()
                .doOnSuccess(this::setWallet)
                .flatMap(__ -> initManagers())
                .subscribeOn(Schedulers.from(this.singleExecutor));
    }

    private void setWallet(final HDWallet wallet) {
        this.wallet = wallet;
        this.walletSubject.onNext(wallet);
    }

    private Single<ToshiManager> initManagers() {
        return Single.fromCallable(() -> {
            if (!this.areManagersInitialised) {
                initRealm();
                this.balanceManager.init(this.wallet);
                this.sofaMessageManager.init(this.wallet);
                this.transactionManager.init(this.wallet);
                this.userManager.init(this.wallet);
                this.reputationManager = new ReputationManager();
                this.areManagersInitialised = true;
            }
            return this;
        });
    }

    private void initRealm() {
        if (this.realmConfig != null) return;

        final byte[] key = this.wallet.generateDatabaseEncryptionKey();
        Realm.init(BaseApplication.get());
        this.realmConfig = new RealmConfiguration
                .Builder()
                .schemaVersion(16)
                .migration(new DbMigration(this.wallet))
                .name(this.wallet.getOwnerAddress())
                .encryptionKey(key)
                .build();
        Realm.setDefaultConfiguration(this.realmConfig);
    }

    public final Single<Realm> getRealm() {
        return Single.fromCallable(() -> {
            while (this.realmConfig == null) {
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }
            return Realm.getDefaultInstance();
        });

    }

    public final SofaMessageManager getSofaMessageManager() {
        return this.sofaMessageManager;
    }

    public final TransactionManager getTransactionManager() {
        return this.transactionManager;
    }

    public final UserManager getUserManager() {
        return this.userManager;
    }

    public final RecipientManager getRecipientManager() {
        return this.recipientManager;
    }

    public final BalanceManager getBalanceManager() {
        return this.balanceManager;
    }

    public final AppsManager getAppsManager() {
        return this.appsManager;
    }

    public final ReputationManager getReputationManager() {
        return this.reputationManager;
    }

    public Single<HDWallet> getWallet() {
        return
                this.walletSubject
                .filter(wallet -> wallet != null)
                .doOnError(t -> LogUtil.exception(getClass(), "Wallet is null", t))
                .onErrorReturn(__ -> null)
                .first()
                .toSingle();
    }

    public void clearUserData() {
        this.sofaMessageManager.clear();
        this.userManager.clear();
        this.recipientManager.clear();
        this.balanceManager.clear();
        this.transactionManager.clear();
        this.wallet.clear();
        this.areManagersInitialised = false;
        closeDatabase();
        SignalPreferences.clear();
        SharedPrefsUtil.setSignedOut();
        SharedPrefsUtil.clear();
        setWallet(null);
    }

    private void closeDatabase() {
        this.realmConfig = null;
        Realm.removeDefaultConfiguration();
    }
}
