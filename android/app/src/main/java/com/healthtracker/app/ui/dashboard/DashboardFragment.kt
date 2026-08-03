package com.healthtracker.app.ui.dashboard

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
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
import com.healthtracker.app.ui.setup.SetupActivity
import com.healthtracker.app.R
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
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

        binding.cardNoData.setOnClickListener {
            startActivity(Intent(requireContext(), SetupActivity::class.java))
        }

        binding.btnPrevDay.setOnClickListener { viewModel.shiftDay(-1) }
        binding.btnNextDay.setOnClickListener { viewModel.shiftDay(1) }
        binding.textDate.setOnClickListener { showDatePicker() }

        viewModel.selectedDate.observe(viewLifecycleOwner) { date ->
            binding.textDate.text = labelFor(date)
            // Future days hold nothing, so stop paging forward at today
            binding.btnNextDay.isEnabled = date.isBefore(LocalDate.now())
            binding.btnNextDay.alpha = if (binding.btnNextDay.isEnabled) 1f else 0.3f
        }

        viewModel.snapshot.observe(viewLifecycleOwner) { snap ->
            if (snap == null) {
                binding.textNoData.text = if (viewModel.isToday) {
                    getString(R.string.no_data_help)
                } else {
                    getString(R.string.no_data_title)
                }
                binding.cardNoData.visibility = View.VISIBLE
                binding.metricsGroup.visibility = View.GONE
                binding.textLastSync.text = getString(R.string.last_sync_never)
                return@observe
            }
            // A snapshot that read nothing means Health Connect is reachable but the
            // source app isn't sharing — the metrics below would all show dashes.
            val hasAnything = listOfNotNull(
                snap.steps, snap.heartRateAvg, snap.hrvMs,
                snap.sleepDurationMin, snap.spo2Pct, snap.caloriesActive,
            ).isNotEmpty()
            if (!hasAnything) {
                binding.textNoData.text = getString(R.string.no_data_not_sharing)
            }
            binding.cardNoData.visibility = if (hasAnything) View.GONE else View.VISIBLE
            binding.metricsGroup.visibility = View.VISIBLE

            binding.textSteps.text = snap.steps?.let { "%,d".format(it) } ?: "—"
            binding.textHr.text = snap.heartRateAvg?.let { "%.0f".format(it) } ?: "—"
            binding.textHrv.text = snap.hrvMs?.let { "%.0f".format(it) } ?: "—"
            binding.textSleep.text = snap.sleepDurationMin?.let { "%.1f".format(it / 60f) } ?: "—"
            binding.textSpo2.text = snap.spo2Pct?.let { "%.1f".format(it) } ?: "—"
            binding.textCalories.text = snap.caloriesActive?.let { "%.0f".format(it) } ?: "—"
            binding.textRestingHr.text = snap.heartRateResting?.let { "%.0f".format(it) } ?: "—"

            binding.textLastSync.text = getString(
                R.string.last_sync,
                SimpleDateFormat("MMM d, HH:mm", Locale.getDefault()).format(Date(snap.timestamp)),
            )
        }

        viewModel.syncState.observe(viewLifecycleOwner) { state ->
            val syncing = state == SyncState.SYNCING
            binding.progressSync.visibility = if (syncing) View.VISIBLE else View.GONE
            binding.btnSync.visibility = if (syncing) View.GONE else View.VISIBLE
            if (state == SyncState.ERROR) {
                Toast.makeText(
                    requireContext(),
                    "Sync failed — check your connection and login.",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }

        binding.btnSync.setOnClickListener {
            handleSyncButtonTap()
        }
    }

    override fun onResume() {
        super.onResume()
        maybeAutoSync()
    }

    /**
     * Syncs on open so the manual control is rarely needed, throttled so that
     * hopping between tabs doesn't fire a request every time.
     */
    private fun maybeAutoSync() {
        val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null) ?: return
        val since = System.currentTimeMillis() - prefs.getLong(KEY_LAST_AUTO_SYNC, 0L)
        if (since < AUTO_SYNC_MIN_INTERVAL_MS) return

        val app = requireActivity().application as HealthTrackerApp
        if (!app.healthConnectManager.isAvailable()) return

        lifecycleScope.launch {
            if (!app.healthConnectManager.hasPermissions()) return@launch
            prefs.edit().putLong(KEY_LAST_AUTO_SYNC, System.currentTimeMillis()).apply()
            viewModel.syncNow(token)
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

    private fun labelFor(date: LocalDate): String = when (date) {
        LocalDate.now() -> getString(R.string.today)
        LocalDate.now().minusDays(1) -> getString(R.string.yesterday)
        else -> date.format(
            DateTimeFormatter.ofPattern(
                if (date.year == LocalDate.now().year) "d MMM" else "d MMM yyyy",
                Locale.getDefault(),
            )
        )
    }

    private fun showDatePicker() {
        val current = viewModel.selectedDate.value ?: LocalDate.now()
        DatePickerDialog(
            requireContext(),
            { _, year, month, day -> viewModel.selectDate(LocalDate.of(year, month + 1, day)) },
            current.year,
            current.monthValue - 1,
            current.dayOfMonth,
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    companion object {
        private const val KEY_LAST_AUTO_SYNC = "last_auto_sync"
        private const val AUTO_SYNC_MIN_INTERVAL_MS = 5 * 60 * 1000L
    }
}
