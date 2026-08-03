package com.healthtracker.app.data.remote

import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.healthtracker.app.ui.login.LoginActivity
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Clears a dead session and returns to sign-in.
 *
 * Called from an OkHttp interceptor, so it can fire on several threads at once
 * when parallel requests all 401; the guard keeps that to a single redirect.
 */
object SessionExpiry {

    private val handling = AtomicBoolean(false)

    fun handle(context: Context) {
        if (!handling.compareAndSet(false, true)) return

        val appContext = context.applicationContext
        appContext.getSharedPreferences("auth", Context.MODE_PRIVATE)
            .edit().remove("token").apply()

        ContextCompat.getMainExecutor(appContext).execute {
            Toast.makeText(appContext, "Session expired — please sign in again", Toast.LENGTH_LONG).show()
            val intent = Intent(appContext, LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            appContext.startActivity(intent)
            handling.set(false)
        }
    }
}
