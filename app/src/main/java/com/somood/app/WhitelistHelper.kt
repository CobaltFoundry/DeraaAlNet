package com.somood.app

import android.content.Context

object WhitelistHelper {
    private const val PREF = "somood_whitelist"
    private const val KEY = "packages"

    fun getPackages(context: Context): Set<String> {
        return context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getStringSet(KEY, emptySet())
            ?.toSet()
            ?: emptySet()
    }

    fun savePackages(context: Context, packages: Set<String>) {
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .edit()
            .putStringSet(KEY, packages)
            .apply()
    }

    fun count(context: Context): Int = getPackages(context).size
} 