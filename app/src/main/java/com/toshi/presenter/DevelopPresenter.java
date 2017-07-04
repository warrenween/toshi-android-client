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

import com.toshi.view.activity.DevelopActivity;

public class DevelopPresenter implements Presenter<DevelopActivity> {

    private DevelopActivity activity;

    @Override
    public void onViewAttached(DevelopActivity view) {
        this.activity = view;
    }

    @Override
    public void onViewDetached() {
        this.activity = null;
    }

    @Override
    public void onDestroyed() {
        this.activity = null;
    }
}
