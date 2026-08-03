package com.healthtracker.app.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.healthtracker.app.HealthTrackerApp
import com.healthtracker.app.databinding.FragmentSettingsBinding
import com.healthtracker.app.ui.login.LoginActivity

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)

        // Sign-in happens in LoginActivity before this screen is reachable, so
        // there is always a signed-in user here.
        val savedUser = prefs.getString("username", null)
        binding.textStatus.text = if (savedUser != null) "Logged in as $savedUser" else "Not logged in"
        binding.textStatus.setTextColor(resources.getColor(android.R.color.holo_green_dark, null))

        binding.btnLogout.setOnClickListener {
            prefs.edit().remove("token").remove("username").apply()
            Toast.makeText(requireContext(), "Logged out", Toast.LENGTH_SHORT).show()
            val intent = Intent(requireContext(), LoginActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            startActivity(intent)
            requireActivity().finish()
        }

        binding.btnGrantHealth.setOnClickListener {
            requestHealthPermissions()
        }

        binding.btnOpenHealthSettings.setOnClickListener {
            (requireActivity() as? com.healthtracker.app.MainActivity)?.openHealthConnectSettings()
        }
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
