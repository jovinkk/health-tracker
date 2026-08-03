package com.healthtracker.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.healthtracker.app.databinding.ActivityMainBinding
import com.healthtracker.app.sync.SyncWorker
import com.healthtracker.app.ui.dashboard.DashboardFragment
import com.healthtracker.app.ui.login.LoginActivity

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    // Health Connect permissions are not standard runtime permissions — requesting
    // them via ActivityResultContracts.RequestMultiplePermissions silently returns
    // all-denied without ever showing a prompt. The SDK's own contract is required.
    private val healthPermissionsLauncher = registerForActivityResult(
        PermissionController.createRequestPermissionResultContract()
    ) { granted: Set<String> ->
        val app = application as HealthTrackerApp
        val required = app.healthConnectManager.requiredPermissions
        if (granted.containsAll(required)) {
            // A fresh grant may expose history that earlier reads couldn't see
            SyncWorker.resetBackfill(this)
            SyncWorker.runNow(this)
            Toast.makeText(this, "Health Connect permissions granted.", Toast.LENGTH_SHORT).show()
            val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            val dashboard = navHost?.childFragmentManager?.fragments?.firstOrNull { it is DashboardFragment } as? DashboardFragment
            dashboard?.onHealthPermissionsGranted()
        } else {
            // Health Connect stops showing its prompt after repeated denials, so a
            // silent all-denied result needs a manual route into its settings.
            val denied = required - granted
            AlertDialog.Builder(this)
                .setTitle("Health Connect permissions needed")
                .setMessage(
                    "${denied.size} of ${required.size} permission(s) were not granted.\n\n" +
                        "If no permission screen appeared, Health Connect may have stopped " +
                        "prompting. You can grant them directly under " +
                        "App permissions → HealthTracker."
                )
                .setPositiveButton("Open Health Connect") { _, _ -> openHealthConnectSettings() }
                .setNegativeButton("Not now", null)
                .show()
        }
    }

    fun openHealthConnectSettings() {
        try {
            startActivity(Intent(HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "Could not open Health Connect settings.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate and setContentView
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        // LoginActivity normally gets here first; this covers being restored from
        // recents after a logout, so the app UI is never shown signed out.
        if (getSharedPreferences("auth", Context.MODE_PRIVATE).getString("token", null) == null) {
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Push content down below the status bar
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = systemBars.top)
            // Bottom padding handled by the BottomNavigationView itself
            insets
        }

        // Let the BottomNavigationView absorb the nav-bar inset
        ViewCompat.setOnApplyWindowInsetsListener(binding.bottomNavigation) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(bottom = systemBars.bottom)
            insets
        }

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNavigation.setupWithNavController(navController)

        SyncWorker.schedule(this)
        // Covers the case where Health Connect has data but the dashboard isn't
        // the screen being opened; WorkManager dedupes if one is already queued.
        SyncWorker.runNow(this)
    }

    fun requestHealthConnectPermissions() {
        val app = application as HealthTrackerApp
        // Ask for history alongside the reads; declining it only caps backfill at 30 days.
        healthPermissionsLauncher.launch(app.healthConnectManager.allPermissions)
    }
}
