package com.toshi.model.local;

import android.support.annotation.StringDef;

import com.toshi.BuildConfig;
import com.toshi.R;
import com.toshi.view.BaseApplication;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class Networks {
    @StringDef({DEV, ROPSTEN})
    public @interface Type {}
    private static final String DEV = "116";
    private static final String ROPSTEN = "3";

    private static Networks instance;
    private List<Network> networks;

    public static Networks getInstance() {
        if (instance == null) {
            instance = new Networks();
        }
        return instance;
    }

    private Networks() {
        this.networks = getNetworkList();
    }

    public List<Network> getNetworks() {
        return this.networks;
    }

    public Network getNetworkById(final @Networks.Type String id) {
        if (id.equals(DEV)) {
            return networks.get(0);
        }
        return networks.get(1);
    }

    private static List<Network> getNetworkList() {
        final String[] networkArray = BaseApplication.get().getResources().getStringArray(R.array.networks);
        return convertToNetworkList(Arrays.asList(networkArray));
    }

    private static List<Network> convertToNetworkList(final List<String> strings) {
        final List<Network> networks = new ArrayList<>();
        for (final String s : strings) {
            networks.add(convertToNetwork(s));
        }

        return networks;
    }

    private static Network convertToNetwork(final String value) {
        final String[] splittedString = value.split("\\|");

        if (splittedString.length != 3) throw new IllegalArgumentException();

        final String url = splittedString[0];
        final String name = splittedString[1];
        final String id = splittedString[2];

        return new Network()
                .setId(id)
                .setName(name)
                .setUrl(url);
    }

    public static @Networks.Type String getDefaultNetwork() {
        if (BuildConfig.DEBUG) {
            return Networks.DEV;
        }
        return Networks.ROPSTEN;
    }
}