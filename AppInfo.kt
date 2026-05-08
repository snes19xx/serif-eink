package com.snes19xx.einklauncher

import android.graphics.drawable.Drawable

// Data class for holding installed app information
data class AppInfo(
    val label: CharSequence,
    val packageName: CharSequence,
    val icon: Drawable
)