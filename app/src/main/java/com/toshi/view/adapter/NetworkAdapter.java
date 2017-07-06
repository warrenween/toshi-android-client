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

package com.toshi.view.adapter;

import android.support.v7.widget.RecyclerView;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.toshi.R;
import com.toshi.model.local.Network;
import com.toshi.view.adapter.listeners.OnItemClickListener;
import com.toshi.view.adapter.viewholder.NetworkViewHolder;

import java.util.List;

public class NetworkAdapter extends RecyclerView.Adapter<NetworkViewHolder> {

    private List<Network> networks;
    private OnItemClickListener<Network> listener;
    private int selectedItem = -1;

    public NetworkAdapter(final List<Network> networks) {
        this.networks = networks;
    }

    public NetworkAdapter setOnItemClickListener(final OnItemClickListener<Network> listener) {
        this.listener = listener;
        return this;
    }

    @Override
    public NetworkViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        final View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.list_item__network_item, parent, false);
        return new NetworkViewHolder(v);
    }

    @Override
    public void onBindViewHolder(NetworkViewHolder holder, int position) {
        final Network network = this.networks.get(position);
        holder.setNetwork(network)
                .setChecked(position == this.selectedItem)
                .setOnItemClickListener(__ -> handleItemClicked(holder, network));
    }

    private void handleItemClicked(final NetworkViewHolder holder,
                                   final Network network) {
        final int prevSelectedItem = selectedItem;
        this.selectedItem = holder.getAdapterPosition();
        notifyItemChanged(prevSelectedItem);
        notifyItemChanged(this.selectedItem);
        this.listener.onItemClick(network);
    }

    @Override
    public int getItemCount() {
        return this.networks.size();
    }
}
