package com.healthtracker.app.health

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import com.healthtracker.app.databinding.ActivityPermissionsRationaleBinding

/**
 * Explains why the app reads Health Connect data.
 *
 * Health Connect requires this screen to be reachable, and on Android 14+ it is
 * how the platform discovers the app as a Health Connect client at all — without
 * the VIEW_PERMISSION_USAGE alias pointing here, the app never appears under
 * Health Connect's app list and its permissions cannot be granted.
 */
class PermissionsRationaleActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val binding = ActivityPermissionsRationaleBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.rationaleRoot) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(top = bars.top, bottom = bars.bottom)
            insets
        }
    }
}
