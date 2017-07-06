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

package com.toshi.view.adapter.viewholder;

import android.support.annotation.NonNull;
import android.support.v7.widget.RecyclerView;
import android.view.View;
import android.widget.RadioButton;
import android.widget.TextView;

import com.toshi.R;
import com.toshi.model.local.Network;
import com.toshi.view.adapter.listeners.OnItemClickListener;

public class NetworkViewHolder extends RecyclerView.ViewHolder {
    private @NonNull RadioButton radioButton;
    private @NonNull TextView network;

    public NetworkViewHolder(View v) {
        super(v);
        this.radioButton = (RadioButton) v.findViewById(R.id.radio_button);
        this.network = (TextView) v.findViewById(R.id.network);
        this.radioButton.setClickable(false);
    }

    public NetworkViewHolder setNetwork(final @NonNull Network network) {
        this.network.setText(network.getName());
        return this;
    }

    public NetworkViewHolder setChecked(final boolean isChecked) {
        this.radioButton.setChecked(isChecked);
        return this;
    }

    public NetworkViewHolder setOnItemClickListener(final OnItemClickListener<String> listener) {
        this.itemView.setOnClickListener(__ -> listener.onItemClick(null));
        return this;
    }
}
