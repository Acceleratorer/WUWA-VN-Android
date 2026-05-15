package com.acceleratorer.wuwavn

import android.app.Activity
import android.app.AlertDialog

class DialogFactory(
    private val activity: Activity,
) {
    fun showMessage(title: String, message: String) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }

    fun showSelection(
        title: String,
        labels: Array<String>,
        onSelected: (Int) -> Unit,
    ) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setItems(labels) { _, which -> onSelected(which) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    fun showConfirmation(
        title: String,
        message: String,
        positiveLabel: String,
        onConfirmed: () -> Unit,
    ) {
        AlertDialog.Builder(activity)
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton(positiveLabel) { _, _ -> onConfirmed() }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
