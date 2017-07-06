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

package com.toshi.view.fragment.DialogFragment;

import android.app.Dialog;
import android.databinding.DataBindingUtil;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v4.app.DialogFragment;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import com.toshi.R;
import com.toshi.databinding.FragmentNetworkSwitcherBinding;
import com.toshi.model.local.Network;
import com.toshi.model.local.Networks;
import com.toshi.view.adapter.NetworkAdapter;
import com.toshi.view.adapter.listeners.OnItemClickListener;

public class NetworkSwitcherDialog extends DialogFragment {

    public static final String TAG = "NetworkSwitcherDialog";

    private FragmentNetworkSwitcherBinding binding;
    private OnItemClickListener<Network> listener;
    private Network selectedNetwork;

    public static NetworkSwitcherDialog getInstance() {
        return new NetworkSwitcherDialog();
    }

    public NetworkSwitcherDialog setOnNetworkListener(final OnItemClickListener<Network> listener) {
        this.listener = listener;
        return this;
    }

    @NonNull
    @Override
    public Dialog onCreateDialog(@NonNull Bundle state) {
        final Dialog dialog = super.onCreateDialog(state);
        if (dialog.getWindow() != null) {
            dialog.getWindow().requestFeature(Window.FEATURE_NO_TITLE);
        }
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        this.binding = DataBindingUtil.inflate(inflater, R.layout.fragment_network_switcher, container, false);
        initView();
        return this.binding.getRoot();
    }

    private void initView() {
        initClickListeners();
        initRecyclerView();
    }

    private void initClickListeners() {
        this.binding.cancel.setOnClickListener(__ -> dismiss());
        this.binding.ok.setOnClickListener(__ -> handleOkClicked());
    }

    private void handleOkClicked() {
        this.listener.onItemClick(this.selectedNetwork);
        dismiss();
    }

    private void initRecyclerView() {
        final RecyclerView networks = this.binding.networks;
        networks.setLayoutManager(new LinearLayoutManager(getContext()));
        final NetworkAdapter adapter = new NetworkAdapter(Networks.getInstance().getNetworks())
                .setOnItemClickListener(this::handleNetworkClicked);
        networks.setAdapter(adapter);
    }

    private void handleNetworkClicked(final Network network) {
        this.selectedNetwork = network;
    }
}
