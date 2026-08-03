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
import com.healthtracker.app.ui.setup.SetupActivity
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import retrofit2.HttpException

/**
 * Gates the app behind a sign-in. Launched first; hands off to [MainActivity]
 * once a token is stored, so the main UI is never reachable while signed out.
 */
class LoginActivity : AppCompatActivity() {

    private lateinit var binding: ActivityLoginBinding
    private var mode = Mode.SIGN_IN

    private enum class Mode { SIGN_IN, SIGN_UP }

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
        applyMode()

        binding.btnSwitchMode.setOnClickListener {
            mode = if (mode == Mode.SIGN_IN) Mode.SIGN_UP else Mode.SIGN_IN
            setStatus("", android.R.color.transparent)
            applyMode()
        }

        binding.btnSubmit.setOnClickListener {
            val username = binding.editUsername.text.toString().trim()
            val password = binding.editPassword.text.toString()
            if (username.isBlank() || password.isBlank()) {
                setStatus("Enter a username and password", android.R.color.holo_orange_dark)
                return@setOnClickListener
            }
            when (mode) {
                Mode.SIGN_IN -> signIn(username, password)
                Mode.SIGN_UP -> signUp(username, password)
            }
        }
    }

    private fun applyMode() {
        val signIn = mode == Mode.SIGN_IN
        binding.textSubtitle.text = if (signIn) "Sign in to continue" else "Create a new account"
        binding.btnSubmit.text = if (signIn) "Sign In" else "Create Account"
        binding.btnSwitchMode.text =
            if (signIn) "No account? Create one" else "Already have an account? Sign in"
    }

    private fun signIn(username: String, password: String) {
        lifecycleScope.launch {
            setBusy(true)
            try {
                if (authenticate(username, password)) {
                    goToMain()
                } else {
                    setStatus("Incorrect username or password", android.R.color.holo_red_dark)
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

    private fun signUp(username: String, password: String) {
        val app = application as HealthTrackerApp
        lifecycleScope.launch {
            setBusy(true)
            try {
                try {
                    app.apiService.register(CredentialsRequest(username, password))
                } catch (e: HttpException) {
                    setStatus(
                        if (e.code() == 400) "That username is already taken"
                        else "Could not create account: ${e.message}",
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
                setStatus("Could not create account: ${e.message}", android.R.color.holo_red_dark)
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
        binding.btnSubmit.isEnabled = !busy
        binding.btnSwitchMode.isEnabled = !busy
        if (busy) {
            val verb = if (mode == Mode.SIGN_IN) "Signing in" else "Creating account"
            setStatus("$verb… server may take up to a minute to wake", android.R.color.holo_orange_dark)
        }
    }

    private fun setStatus(text: String, colorRes: Int) {
        binding.textStatus.text = text
        binding.textStatus.setTextColor(resources.getColor(colorRes, null))
    }

    private fun goToMain() {
        // First run goes through setup, which explains the Health Connect step
        // that otherwise leaves every metric reading zero.
        val next = if (SetupActivity.hasCompletedSetup(this)) {
            Intent(this, MainActivity::class.java)
        } else {
            Intent(this, SetupActivity::class.java)
        }
        startActivity(next)
        finish()
    }

    private fun prefs() = getSharedPreferences("auth", Context.MODE_PRIVATE)
}
