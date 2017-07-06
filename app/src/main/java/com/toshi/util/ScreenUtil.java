package com.toshi.util;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Point;
import android.view.Display;
import android.view.WindowManager;

import com.toshi.view.BaseApplication;

public class ScreenUtil {

    public static int getWidthOfScreen() {
        final Point size = new Point();
        getDisplay().getSize(size);
        return size.x;
    }

    public static double getScreenRatio() {
        final Point size = new Point();
        getDisplay().getSize(size);
        if (isPortrait()) {
            return (double)size.y / (double)size.x;
        }
        return (double)size.x / (double)size.y;
    }

    private static Display getDisplay() {
        final WindowManager wm = (WindowManager) BaseApplication.get().getSystemService(Context.WINDOW_SERVICE);
        return wm.getDefaultDisplay();
    }

    public static boolean isPortrait() {
        return BaseApplication
                .get()
                .getResources()
                .getConfiguration()
                .orientation == Configuration.ORIENTATION_PORTRAIT;
    }
}
