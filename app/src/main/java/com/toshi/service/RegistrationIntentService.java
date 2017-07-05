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

/**
 * Copyright 2015 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.toshi.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.support.v4.content.LocalBroadcastManager;

import com.toshi.R;
import com.toshi.util.FileNames;
import com.toshi.util.LogUtil;
import com.toshi.view.BaseApplication;
import com.google.android.gms.gcm.GoogleCloudMessaging;
import com.google.android.gms.iid.InstanceID;

import rx.Observable;
import rx.schedulers.Schedulers;
import rx.subjects.PublishSubject;

public class RegistrationIntentService extends IntentService {

    public static final String FORCE_UPDATE = "update_token";
    public static final String ETH_REGISTRATION_ONLY = "eth_registration_only";

    public static final String CHAT_SERVICE_SENT_TOKEN_TO_SERVER = "chatServiceSentTokenToServer";
    public static final String ETH_SERVICE_SENT_TOKEN_TO_SERVER = "sentTokenToServer_v2";
    public static final String REGISTRATION_COMPLETE = "registrationComplete";

    private final static PublishSubject<Void> registrationSubject = PublishSubject.create();
    private final SharedPreferences sharedPreferences;

    public RegistrationIntentService() {
        super("RegIntentService");
        this.sharedPreferences = BaseApplication.get().getSharedPreferences(FileNames.GCM_PREFS, Context.MODE_PRIVATE);
    }

    @Override
    protected void onHandleIntent(final Intent intent) {
        try {
            final InstanceID instanceID = InstanceID.getInstance(this);
            final String token = instanceID.getToken(getString(R.string.gcm_defaultSenderId),
                    GoogleCloudMessaging.INSTANCE_ID_SCOPE, null);
            LogUtil.i(getClass(), "GCM Registration token: " + token);

            final boolean forceUpdate = intent.getBooleanExtra(FORCE_UPDATE, false);
            final boolean ethRegistrationOnly = intent.getBooleanExtra(ETH_REGISTRATION_ONLY, false);
            if (ethRegistrationOnly) {
                registerEthereumServiceGcmToken(token, forceUpdate);
            } else {
                registerEthereumServiceGcmToken(token, forceUpdate);
                registerChatServiceGcm(token, forceUpdate);
            }
        } catch (final Exception ex) {
            LogUtil.d(getClass(), "Failed to complete token refresh" + ex);
            sharedPreferences.edit().putBoolean(ETH_SERVICE_SENT_TOKEN_TO_SERVER, false).apply();
        }

        final Intent registrationComplete = new Intent(REGISTRATION_COMPLETE);
        LocalBroadcastManager.getInstance(this).sendBroadcast(registrationComplete);
    }

    private void registerChatServiceGcm(final String token, final boolean forceUpdate) {
        final boolean sentToServer = sharedPreferences.getBoolean(CHAT_SERVICE_SENT_TOKEN_TO_SERVER, false);
        if (!forceUpdate && sentToServer) {
            return;
        }

        BaseApplication
                .get()
                .getSofaMessageManager()
                .setGcmToken(token);
    }

    private void registerEthereumServiceGcmToken(final String token, final boolean forceUpdate) {
        final boolean sentToServer = sharedPreferences.getBoolean(ETH_SERVICE_SENT_TOKEN_TO_SERVER, false);
        if (!forceUpdate && sentToServer) {
            return;
        }

        BaseApplication
                .get()
                .getBalanceManager()
                .registerForGcm(token)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .subscribe(
                        this::handleGcmSuccess,
                        this::handleGcmFailure
                );
    }

    public void handleGcmSuccess(final Void unused) {
        this.sharedPreferences.edit().putBoolean(ETH_SERVICE_SENT_TOKEN_TO_SERVER, true).apply();
        registrationSubject.onNext(unused);
    }

    public void handleGcmFailure(final Throwable error) {
        this.sharedPreferences.edit().putBoolean(ETH_SERVICE_SENT_TOKEN_TO_SERVER, false).apply();
        LogUtil.exception(getClass(), "Error while registering gcm", error);
        registrationSubject.onError(error);
    }

    public static Observable<Void> getGcmRegistrationObservable() {
        return registrationSubject.asObservable();
    }
}
