package com.valekapimda.customer

import android.content.Context

object CustomerTrackingStore {
    private const val PREFS = "customer_tracking"
    fun save(context: Context, requestId: String, phone: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("request_id", requestId).putString("phone", phone).apply()
    fun requestId(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("request_id", null)
    fun phone(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("phone", null)
    fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}
