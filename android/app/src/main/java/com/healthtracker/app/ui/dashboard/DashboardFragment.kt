package com.healthtracker.app.ui.dashboard

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.healthtracker.app.HealthTrackerApp
import com.healthtracker.app.MainActivity
import com.healthtracker.app.databinding.FragmentDashboardBinding
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!
    private val viewModel: DashboardViewModel by viewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel.latestSnapshot.observe(viewLifecycleOwner) { snap ->
            if (snap == null) {
                binding.cardNoData.visibility = View.VISIBLE
                binding.metricsGroup.visibility = View.GONE
                return@observe
            }
            binding.cardNoData.visibility = View.GONE
            binding.metricsGroup.visibility = View.VISIBLE

            binding.textSteps.text = snap.steps?.let { "%,d".format(it) } ?: "—"
            binding.textHr.text = snap.heartRateAvg?.let { "%.0f".format(it) } ?: "—"
            binding.textHrv.text = snap.hrvMs?.let { "%.0f".format(it) } ?: "—"
            binding.textSleep.text = snap.sleepDurationMin?.let { "%.1f".format(it / 60f) } ?: "—"
            binding.textSpo2.text = snap.spo2Pct?.let { "%.1f".format(it) } ?: "—"
            binding.textCalories.text = snap.caloriesActive?.let { "%.0f".format(it) } ?: "—"
            binding.textRestingHr.text = snap.heartRateResting?.let { "%.0f".format(it) } ?: "—"

            val date = Date(snap.timestamp)
            binding.textLastSync.text = "Last sync: ${SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(date)}"
        }

        viewModel.syncState.observe(viewLifecycleOwner) { state ->
            when (state) {
                SyncState.SYNCING -> {
                    binding.btnSync.isEnabled = false
                    binding.btnSync.text = "Syncing…"
                }
                SyncState.DONE -> {
                    binding.btnSync.isEnabled = true
                    binding.btnSync.text = "Sync Health Data"
                }
                SyncState.ERROR -> {
                    binding.btnSync.isEnabled = true
                    binding.btnSync.text = "Sync Health Data"
                    Toast.makeText(requireContext(), "Sync failed — check your connection and login.", Toast.LENGTH_LONG).show()
                }
                else -> {
                    binding.btnSync.isEnabled = true
                    binding.btnSync.text = "Sync Health Data"
                }
            }
        }

        binding.btnSync.setOnClickListener {
            handleSyncButtonTap()
        }
    }

    private fun handleSyncButtonTap() {
        val app = requireActivity().application as HealthTrackerApp

        if (!app.healthConnectManager.isAvailable()) {
            Toast.makeText(requireContext(), "Health Connect is not available on this device.", Toast.LENGTH_LONG).show()
            return
        }

        val token = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
            .getString("token", null)
        if (token == null) {
            Toast.makeText(requireContext(), "Not signed in. Go to Settings to log in first.", Toast.LENGTH_LONG).show()
            return
        }

        lifecycleScope.launch {
            if (!app.healthConnectManager.hasPermissions()) {
                Toast.makeText(requireContext(), "Requesting Health Connect permissions…", Toast.LENGTH_SHORT).show()
                (requireActivity() as? MainActivity)?.requestHealthConnectPermissions()
                return@launch
            }
            viewModel.syncNow(token)
        }
    }

    fun onHealthPermissionsGranted() {
        val token = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
            .getString("token", null) ?: return
        viewModel.syncNow(token)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
