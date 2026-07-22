package com.valekapimda.driver

import android.content.Context

object DriverTrackingStore {
    private const val PREFS = "driver_tracking"
    private const val REQUEST_ID = "request_id"
    fun save(context: Context, requestId: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(REQUEST_ID, requestId).apply()
    fun get(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(REQUEST_ID, null)
    fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(REQUEST_ID).apply()
}
