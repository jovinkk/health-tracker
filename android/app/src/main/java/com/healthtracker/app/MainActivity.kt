package com.healthtracker.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.healthtracker.app.databinding.ActivityMainBinding
import com.healthtracker.app.sync.SyncWorker
import com.healthtracker.app.ui.dashboard.DashboardFragment

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val healthPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted ->
        val allGranted = granted.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "Health Connect permissions granted.", Toast.LENGTH_SHORT).show()
            val navHost = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment
            val dashboard = navHost?.childFragmentManager?.fragments?.firstOrNull { it is DashboardFragment } as? DashboardFragment
            dashboard?.onHealthPermissionsGranted()
        } else {
            val deniedCount = granted.values.count { !it }
            Toast.makeText(this, "$deniedCount permission(s) denied. Some data may be unavailable.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must be called before super.onCreate and setContentView
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
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
        val permissions = app.healthConnectManager.requiredPermissions
            .map { it.toString() }
            .toTypedArray()
        healthPermissionsLauncher.launch(permissions)
    }
}
