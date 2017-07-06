package com.toshi.util;

import android.content.Context;
import android.content.DialogInterface;
import android.support.annotation.StringRes;
import android.support.v7.app.AlertDialog;

import com.toshi.R;

public class DialogUtil {
    public static AlertDialog.Builder getBaseDialog(final Context context,
                                                    final @StringRes int title,
                                                    final @StringRes int message,
                                                    final @StringRes int negativeButtonText) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialogCustom);
        return builder
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(negativeButtonText, (dialog, which) -> {
                    dialog.dismiss();
                });
    }

    public static AlertDialog.Builder getBaseDialog(final Context context,
                                                    final @StringRes int title,
                                                    final @StringRes int message,
                                                    final @StringRes int negativeButtonText,
                                                    final DialogInterface.OnClickListener listener) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialogCustom);
        return builder
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(negativeButtonText, listener);
    }

    public static AlertDialog.Builder getBaseDialog(final Context context,
                                                     final @StringRes int title,
                                                     final @StringRes int message,
                                                     final @StringRes int positiveButtonText,
                                                     final @StringRes int negativeButtonText,
                                                     final DialogInterface.OnClickListener listener) {
        final AlertDialog.Builder builder = new AlertDialog.Builder(context, R.style.AlertDialogCustom);
        return builder
                .setTitle(title)
                .setMessage(message)
                .setNegativeButton(negativeButtonText, (dialog, which) -> {
                    dialog.dismiss();
                })
                .setPositiveButton(positiveButtonText, listener);
    }
}
