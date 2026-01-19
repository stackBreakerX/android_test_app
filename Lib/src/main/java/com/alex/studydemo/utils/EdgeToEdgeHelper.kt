package com.alex.studydemo.utils

import android.app.Activity
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class EdgeToEdgeHelper(activity: Activity) {
    fun insetsPadding(
        view: View,
        left: Boolean = false,
        top: Boolean = false,
        right: Boolean = false,
        bottom: Boolean = false,
    ) {
        ViewCompat.setOnApplyWindowInsetsListener(view) { v: View, insets: WindowInsetsCompat ->
            val typeMask = WindowInsetsCompat.Type.systemBars()
            val systemBars = insets.getInsets(typeMask)
            v.setPadding(
                if (left) systemBars.left else v.paddingLeft,
                if (top) systemBars.top else v.paddingTop,
                if (right) systemBars.right else v.paddingRight,
                if (bottom) systemBars.bottom else v.paddingBottom,
            )
            insets
        }
    }
}

