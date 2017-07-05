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

package com.toshi.manager.network;


import com.squareup.moshi.Moshi;
import com.toshi.R;
import com.toshi.manager.network.interceptor.LoggingInterceptor;
import com.toshi.manager.network.interceptor.SigningInterceptor;
import com.toshi.manager.network.interceptor.UserAgentInterceptor;
import com.toshi.model.adapter.BigIntegerAdapter;
import com.toshi.model.local.Network;
import com.toshi.model.sofa.Payment;
import com.toshi.model.sofa.SofaAdapters;
import com.toshi.model.sofa.SofaMessage;
import com.toshi.util.SharedPrefsUtil;
import com.toshi.view.BaseApplication;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.adapter.rxjava.RxJavaCallAdapterFactory;
import retrofit2.converter.moshi.MoshiConverterFactory;
import rx.Single;
import rx.schedulers.Schedulers;

public class EthereumService {

    private String baseUrl;

    private static EthereumService instance;

    private EthereumInterface ethereumInterface;
    private final OkHttpClient.Builder client;

    public static EthereumInterface getApi() {
        return getInstance().ethereumInterface;
    }

    public static EthereumService get() {
        return getInstance();
    }

    private static synchronized EthereumService getInstance() {
        if (instance == null) {
            instance = new EthereumService();
        }
        return instance;
    }

    private EthereumService() {
        this.client = new OkHttpClient.Builder();

        addUserAgentHeader();
        addSigningInterceptor();
        addLogging();

        this.baseUrl = getBaseUrl();
        this.ethereumInterface = buildEthereumInterface(this.baseUrl);
    }

    private String getBaseUrl() {
        final Network network =  SharedPrefsUtil.getCurrentNetwork();
        return network.getUrl();
    }

    public void changeBaseUrl(final String baseUrl) {
        this.baseUrl = baseUrl;
        this.ethereumInterface = buildEthereumInterface(this.baseUrl);
    }

    private EthereumInterface buildEthereumInterface(final String baseUrl) {
        final Moshi moshi = new Moshi.Builder()
                .add(new BigIntegerAdapter())
                .build();

        final RxJavaCallAdapterFactory rxAdapter = RxJavaCallAdapterFactory
                .createWithScheduler(Schedulers.io());

        final Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(baseUrl)
                .addConverterFactory(MoshiConverterFactory.create(moshi))
                .addCallAdapterFactory(rxAdapter)
                .client(this.client.build())
                .build();
        return retrofit.create(EthereumInterface.class);
    }

    private void addUserAgentHeader() {
        this.client.addInterceptor(new UserAgentInterceptor());
    }

    private void addSigningInterceptor() {
        this.client.addInterceptor(new SigningInterceptor());
    }

    private void addLogging() {
        final HttpLoggingInterceptor interceptor = new HttpLoggingInterceptor(new LoggingInterceptor());
        interceptor.setLevel(HttpLoggingInterceptor.Level.BODY);
        this.client.addInterceptor(interceptor);
    }

    public Single<Payment> getStatusOfTransaction(final String transactionHash) {
        return Single.fromCallable(() -> {
            final String url = String.format(
                    "%s%s%s%s",
                    BaseApplication.get().getResources().getString(R.string.balance_url),
                    "/v1/tx/",
                    transactionHash,
                    "?format=sofa"
            );
            final Request request = new Request.Builder()
                    .url(url)
                    .build();

            final Response response = new OkHttpClient()
                    .newCall(request)
                    .execute();

            if (response.code() == 404) {
                return null;
            }

            final SofaMessage sofaMessage = new SofaMessage()
                    .makeNew(response.body().string());
            
            response.close();
            return SofaAdapters.get().paymentFrom(sofaMessage.getPayload());
        });
    }
}
