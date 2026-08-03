package com.healthtracker.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
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
            Toast.makeText(this, "Health Connect permissions granted.", Toast.LENGTH_SHORT).show()
            val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            val dashboard = navHost?.childFragmentManager?.fragments?.firstOrNull { it is DashboardFragment } as? DashboardFragment
            dashboard?.onHealthPermissionsGranted()
        } else {
            val denied = required - granted
            Toast.makeText(
                this,
                "${denied.size} permission(s) denied. Some data may be unavailable.",
                Toast.LENGTH_LONG,
            ).show()
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
    }

    fun requestHealthConnectPermissions() {
        val app = application as HealthTrackerApp
        healthPermissionsLauncher.launch(app.healthConnectManager.requiredPermissions)
    }
}
