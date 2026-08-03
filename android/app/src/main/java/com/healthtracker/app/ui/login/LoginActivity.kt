package com.healthtracker.app.ui.login

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.lifecycle.lifecycleScope
import com.healthtracker.app.HealthTrackerApp
import com.healthtracker.app.MainActivity
import com.healthtracker.app.data.remote.CredentialsRequest
import com.healthtracker.app.databinding.ActivityLoginBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Gates the app behind a sign-in. Launched first; hands off to [MainActivity]
 * once a token is stored, so the main UI is never reachable while signed out.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        if (prefs().getString("token", null) != null) {
            goToMain()
            return
        }

        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.loginRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        binding.editUsername.setText(prefs().getString("username", ""))

        binding.btnLogin.setOnClickListener {
            val username = binding.editUsername.text.toString().trim()
            val password = binding.editPassword.text.toString()
            if (username.isBlank() || password.isBlank()) {
                setStatus("Enter a username and password", android.R.color.holo_orange_dark)
                return@setOnClickListener
            }
            signIn(username, password)
        }
    }

    private fun signIn(username: String, password: String) {
        val app = application as HealthTrackerApp
        lifecycleScope.launch {
            setBusy(true)
            try {
                if (authenticate(username, password)) {
                    goToMain()
                    return@launch
                }
                // Login 401s for both "no such account" and "wrong password", so
                // try creating the account — a 400 back means it already existed
                // and the password was simply wrong.
                try {
                    app.apiService.register(CredentialsRequest(username, password))
                } catch (e: HttpException) {
                    setStatus(
                        if (e.code() == 400) "Incorrect password" else "Registration failed: ${e.message}",
                        android.R.color.holo_red_dark,
                    )
                    return@launch
                }
                if (authenticate(username, password)) {
                    Toast.makeText(this@LoginActivity, "Account created", Toast.LENGTH_SHORT).show()
                    goToMain()
                } else {
                    setStatus("Account created, but sign-in failed", android.R.color.holo_red_dark)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                setStatus("Sign-in failed: ${e.message}", android.R.color.holo_red_dark)
            } finally {
                setBusy(false)
            }
        }
    }

    /** Signs in and persists the token. Returns false on a 401; other failures propagate. */
    private suspend fun authenticate(username: String, password: String): Boolean {
        val app = application as HealthTrackerApp
        val response = try {
            app.apiService.login(username, password)
        } catch (e: HttpException) {
            if (e.code() == 401) return false
            throw e
        }
        prefs().edit()
            .putString("token", response.accessToken)
            .putString("username", username)
            .apply()
        return true
    }

    private fun setBusy(busy: Boolean) {
        binding.btnLogin.isEnabled = !busy
        if (busy) {
            setStatus("Signing in… server may take up to a minute to wake", android.R.color.holo_orange_dark)
        }
    }

    private fun setStatus(text: String, colorRes: Int) {
        binding.textStatus.text = text
        binding.textStatus.setTextColor(resources.getColor(colorRes, null))
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    private fun prefs() = getSharedPreferences("auth", Context.MODE_PRIVATE)
}
