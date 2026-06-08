package com.vedesh.readfree.util

import android.view.View
import android.widget.Toast

fun View.tooltipFromContentDescription() {
    if (contentDescription == null) return
    setOnLongClickListener {
        Toast.makeText(context, contentDescription, Toast.LENGTH_SHORT).show()
        true
    }
}
