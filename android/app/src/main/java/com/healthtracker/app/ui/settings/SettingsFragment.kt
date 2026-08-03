package com.healthtracker.app.ui.settings

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.healthtracker.app.HealthTrackerApp
import com.healthtracker.app.MainActivity
import com.healthtracker.app.R
import com.healthtracker.app.databinding.FragmentSettingsBinding
import com.healthtracker.app.health.HealthConnectManager
import com.healthtracker.app.settings.ThemeMode
import com.healthtracker.app.settings.UnitSystem
import com.healthtracker.app.ui.login.LoginActivity
import com.healthtracker.app.ui.setup.SetupActivity
import kotlinx.coroutines.launch
import java.util.Locale

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val app get() = requireActivity().application as HealthTrackerApp
    private val settings get() = app.settings

    /** Cached so the picker can be rebuilt without re-querying Health Connect. */
    private var stepSources: List<HealthConnectManager.StepSource> = emptyList()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)

        val savedUser = prefs.getString("username", null)
        binding.textStatus.text = savedUser
            ?.let { getString(R.string.logged_in_as, it) }
            ?: getString(R.string.not_logged_in)

        binding.btnLogout.setOnClickListener {
            prefs.edit().remove("token").remove("username").apply()
            Toast.makeText(requireContext(), R.string.logged_out, Toast.LENGTH_SHORT).show()
            startActivity(
                Intent(requireContext(), LoginActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
            )
            requireActivity().finish()
        }

        binding.rowTheme.setOnClickListener { pickTheme() }
        binding.rowUnits.setOnClickListener { pickUnits() }
        binding.rowLanguage.setOnClickListener { pickLanguage() }
        binding.rowStepSource.setOnClickListener { pickStepSource() }

        binding.btnSetupHelp.setOnClickListener {
            startActivity(Intent(requireContext(), SetupActivity::class.java))
        }
        binding.btnGrantHealth.setOnClickListener { requestHealthPermissions() }
        binding.btnOpenHealthSettings.setOnClickListener {
            (requireActivity() as? MainActivity)?.openHealthConnectSettings()
        }

        refreshValues()
        loadStepSources()
    }

    // ── Value labels ──────────────────────────────────────────────────────────

    private fun refreshValues() {
        binding.textThemeValue.text = getString(
            when (settings.themeMode) {
                ThemeMode.SYSTEM -> R.string.theme_system
                ThemeMode.LIGHT -> R.string.theme_light
                ThemeMode.DARK -> R.string.theme_dark
            }
        )
        binding.textUnitsValue.text = getString(
            if (settings.unitSystem.isMetric) R.string.units_metric else R.string.units_imperial
        )
        binding.textLanguageValue.text = settings.languageTag
            ?.let { Locale.forLanguageTag(it).displayLanguage }
            ?: getString(R.string.language_system)
        binding.textStepSourceValue.text = settings.stepSourcePackage
            ?.let { appLabel(it) }
            ?: getString(R.string.step_source_all)
    }

    // ── Pickers ───────────────────────────────────────────────────────────────

    private fun pickTheme() {
        val modes = listOf(ThemeMode.SYSTEM, ThemeMode.LIGHT, ThemeMode.DARK)
        val labels = modes.map {
            getString(
                when (it) {
                    ThemeMode.SYSTEM -> R.string.theme_system
                    ThemeMode.LIGHT -> R.string.theme_light
                    ThemeMode.DARK -> R.string.theme_dark
                }
            )
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(labels.toTypedArray(), modes.indexOf(settings.themeMode)) { dialog, which ->
                // Recreates activities to apply, so update the label first
                settings.themeMode = modes[which]
                dialog.dismiss()
            }
            .show()
    }

    private fun pickUnits() {
        val systems = listOf(UnitSystem.METRIC, UnitSystem.IMPERIAL)
        val labels = arrayOf(getString(R.string.units_metric), getString(R.string.units_imperial))
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_units)
            .setSingleChoiceItems(labels, systems.indexOf(settings.unitSystem)) { dialog, which ->
                settings.unitSystem = systems[which]
                refreshValues()
                dialog.dismiss()
            }
            .show()
    }

    private fun pickLanguage() {
        // Only English ships today; the picker and resources are in place so a
        // translation can be added without touching code.
        val tags = listOf<String?>(null, "en")
        val labels = arrayOf(
            getString(R.string.language_system),
            Locale.ENGLISH.displayLanguage,
        )
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_language)
            .setSingleChoiceItems(labels, tags.indexOf(settings.languageTag)) { dialog, which ->
                settings.languageTag = tags[which]
                refreshValues()
                dialog.dismiss()
            }
            .show()
    }

    private fun loadStepSources() {
        if (!app.healthConnectManager.isAvailable()) return
        lifecycleScope.launch {
            stepSources = app.healthConnectManager.stepDataSources()
            refreshValues()
        }
    }

    private fun pickStepSource() {
        if (stepSources.isEmpty()) {
            Toast.makeText(requireContext(), R.string.step_source_none, Toast.LENGTH_LONG).show()
            return
        }
        val packages = listOf<String?>(null) + stepSources.map { it.packageName }
        val labels = (
            listOf(getString(R.string.step_source_all)) +
                stepSources.map { "${appLabel(it.packageName)}\n${getString(R.string.steps_last_7_days, it.stepsLast7Days)}" }
            ).toTypedArray()

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.settings_step_source)
            .setSingleChoiceItems(labels, packages.indexOf(settings.stepSourcePackage)) { dialog, which ->
                settings.stepSourcePackage = packages[which]
                refreshValues()
                dialog.dismiss()
            }
            .show()
    }

    /** Human-readable app name, falling back to the package when it isn't installed. */
    private fun appLabel(packageName: String): String = runCatching {
        val pm = requireContext().packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrDefault(packageName)

    private fun requestHealthPermissions() {
        if (!app.healthConnectManager.isAvailable()) {
            Toast.makeText(requireContext(), R.string.health_connect_unavailable, Toast.LENGTH_LONG).show()
            return
        }
        (requireActivity() as? MainActivity)?.requestHealthConnectPermissions()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
