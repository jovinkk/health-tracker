package com.healthtracker.app.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.healthtracker.app.HealthTrackerApp
import com.healthtracker.app.data.remote.CredentialsRequest
import com.healthtracker.app.databinding.FragmentSettingsBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)

        // Pre-fill saved username
        binding.editUsername.setText(prefs.getString("username", ""))

        binding.btnLogin.setOnClickListener {
            val username = binding.editUsername.text.toString().trim()
            val password = binding.editPassword.text.toString()
            if (username.isBlank() || password.isBlank()) {
                Toast.makeText(requireContext(), "Enter username and password", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            login(username, password)
        }

        binding.btnLogout.setOnClickListener {
            prefs.edit().remove("token").remove("username").apply()
            binding.textStatus.text = "Logged out"
            binding.textStatus.setTextColor(resources.getColor(android.R.color.holo_orange_dark, null))
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
        }

        // Show current status
        val hasToken = prefs.getString("token", null) != null
        val savedUser = prefs.getString("username", null)
        if (hasToken && savedUser != null) {
            binding.textStatus.text = "Logged in as $savedUser"
            binding.textStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        } else {
            binding.textStatus.text = "Not logged in"
            binding.textStatus.setTextColor(resources.getColor(android.R.color.holo_orange_dark, null))
        }

        // Health Connect permissions
        binding.btnGrantHealth.setOnClickListener {
            requestHealthPermissions()
        }
    }

    private fun login(username: String, password: String) {
        val app = requireActivity().application as HealthTrackerApp
        lifecycleScope.launch {
            try {
                if (authenticate(app, username, password)) {
                    toast("Login successful")
                    return@launch
                }
                // Login 401s for both "no such account" and "wrong password", so
                // try creating the account — a 400 back means it already existed
                // and the password was simply wrong.
                try {
                    app.apiService.register(CredentialsRequest(username, password))
                } catch (e: HttpException) {
                    toast(if (e.code() == 400) "Incorrect password" else "Registration failed: ${e.message}")
                    return@launch
                }
                if (authenticate(app, username, password)) {
                    toast("Account created — logged in")
                } else {
                    toast("Account created, but sign-in failed")
                }
            } catch (e: Exception) {
                toast("Login failed: ${e.message}")
            }
        }
    }

    /** Signs in and persists the token. Returns false on a 401; other failures propagate. */
    private suspend fun authenticate(app: HealthTrackerApp, username: String, password: String): Boolean {
        val response = try {
            app.apiService.login(username, password)
        } catch (e: HttpException) {
            if (e.code() == 401) return false
            throw e
        }
        requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE).edit()
            .putString("token", response.accessToken)
            .putString("username", username)
            .apply()
        binding.textStatus.text = "Logged in as $username"
        binding.textStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))
        return true
    }

    private fun toast(message: String) {
        Toast.makeText(requireContext(), message, Toast.LENGTH_LONG).show()
    }

    private fun requestHealthPermissions() {
        val app = requireActivity().application as HealthTrackerApp
        if (!app.healthConnectManager.isAvailable()) {
            Toast.makeText(requireContext(), "Health Connect not available on this device", Toast.LENGTH_LONG).show()
            return
        }
        // Launch Health Connect permission request via MainActivity
        (requireActivity() as? com.healthtracker.app.MainActivity)?.requestHealthConnectPermissions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
