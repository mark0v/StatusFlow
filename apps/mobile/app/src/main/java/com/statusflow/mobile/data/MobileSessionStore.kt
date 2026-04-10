package com.statusflow.mobile.data

import android.content.Context

data class MobileSession(
    val accessToken: String,
    val email: String,
    val name: String,
    val role: String
)

object MobileSessionStore {
    private const val PREFS_NAME = "statusflow_session"
    private const val KEY_ACCESS_TOKEN = "access_token"
    private const val KEY_EMAIL = "email"
    private const val KEY_NAME = "name"
    private const val KEY_ROLE = "role"

    @Volatile
    private var session: MobileSession? = null
    private var initialized = false
    private lateinit var appContext: Context

    fun initialize(context: Context) {
        if (initialized) {
            return
        }

        appContext = context.applicationContext
        session = readSession()
        initialized = true
    }

    fun currentSession(): MobileSession? {
        check(initialized) { "MobileSessionStore must be initialized before use." }
        return session
    }

    fun saveSession(nextSession: MobileSession) {
        check(initialized) { "MobileSessionStore must be initialized before use." }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACCESS_TOKEN, nextSession.accessToken)
            .putString(KEY_EMAIL, nextSession.email)
            .putString(KEY_NAME, nextSession.name)
            .putString(KEY_ROLE, nextSession.role)
            .apply()
        session = nextSession
    }

    fun clearSession() {
        check(initialized) { "MobileSessionStore must be initialized before use." }
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
        session = null
    }

    private fun readSession(): MobileSession? {
        val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val accessToken = prefs.getString(KEY_ACCESS_TOKEN, null) ?: return null
        val email = prefs.getString(KEY_EMAIL, null) ?: return null
        val name = prefs.getString(KEY_NAME, null) ?: return null
        val role = prefs.getString(KEY_ROLE, null) ?: return null

        return MobileSession(
            accessToken = accessToken,
            email = email,
            name = name,
            role = role
        )
    }
}
