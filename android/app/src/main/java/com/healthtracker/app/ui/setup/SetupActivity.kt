package com.healthtracker.app.ui.setup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import com.google.android.material.button.MaterialButton
import com.healthtracker.app.HealthTrackerApp
import com.healthtracker.app.MainActivity
import com.healthtracker.app.databinding.ActivitySetupBinding
import com.healthtracker.app.sync.SyncWorker

/**
 * Walks a new user through the part that isn't obvious: their fitness app has to
 * share with Health Connect before HealthTracker can read anything. Skippable,
 * and reachable again from Settings.
 */
class SetupActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySetupBinding
    private var selected: TrackerGuide? = null

    private val permissionsLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        val required = (application as HealthTrackerApp).healthConnectManager.requiredPermissions
        if (granted.containsAll(required)) {
            // A fresh grant may expose history that wasn't readable before.
            SyncWorker.resetBackfill(this)
            Toast.makeText(this, "Access granted", Toast.LENGTH_SHORT).show()
            finishSetup()
        } else {
            Toast.makeText(
                this,
                "Not granted. You can also allow HealthTracker inside Health Connect.",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        binding = ActivitySetupBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.setupRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }

        buildTrackerList()

        binding.btnSkip.setOnClickListener { finishSetup() }
        binding.btnBack.setOnClickListener { showPicker() }
        binding.btnOpenHealthConnect.setOnClickListener { openHealthConnect() }
        binding.btnOpenSourceApp.setOnClickListener { selected?.packageName?.let { openApp(it) } }
        binding.btnGrant.setOnClickListener {
            val health = (application as HealthTrackerApp).healthConnectManager
            if (!health.isAvailable()) {
                Toast.makeText(this, "Health Connect is not available on this device.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            permissionsLauncher.launch(health.allPermissions)
        }
    }

    private fun buildTrackerList() {
        binding.groupPick.removeAllViews()
        TRACKER_GUIDES.forEach { guide ->
            val button = MaterialButton(
                this,
                null,
                com.google.android.material.R.attr.materialButtonOutlinedStyle,
            ).apply {
                text = guide.name
                isAllCaps = false
                setOnClickListener { showSteps(guide) }
            }
            binding.groupPick.addView(button)
        }
    }

    private fun showPicker() {
        selected = null
        binding.textTitle.text = "Where does your health data come from?"
        binding.textSubtitle.visibility = View.VISIBLE
        binding.groupPick.visibility = View.VISIBLE
        binding.groupSteps.visibility = View.GONE
    }

    private fun showSteps(guide: TrackerGuide) {
        selected = guide
        binding.textTitle.text = guide.name
        binding.textSubtitle.visibility = View.GONE
        binding.groupPick.visibility = View.GONE
        binding.groupSteps.visibility = View.VISIBLE
        binding.textSteps.text = guide.steps
        binding.btnOpenSourceApp.visibility =
            if (guide.packageName != null && isInstalled(guide.packageName)) View.VISIBLE else View.GONE
    }

    private fun isInstalled(packageName: String): Boolean =
        packageManager.getLaunchIntentForPackage(packageName) != null

    private fun openApp(packageName: String) {
        val intent = packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            Toast.makeText(this, "That app doesn't seem to be installed.", Toast.LENGTH_LONG).show()
            return
        }
        startActivity(intent)
    }

    private fun openHealthConnect() {
        try {
            startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Could not open Health Connect settings.", Toast.LENGTH_LONG).show()
        }
    }

    private fun finishSetup() {
        getSharedPreferences("auth", Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_SETUP_DONE, true).apply()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }

    companion object {
        const val KEY_SETUP_DONE = "setup_completed"

        fun hasCompletedSetup(context: Context): Boolean =
            context.getSharedPreferences("auth", Context.MODE_PRIVATE)
                .getBoolean(KEY_SETUP_DONE, false)
    }
}
