package com.healthtracker.app.ui.insights

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.healthtracker.app.HealthTrackerApp
import com.healthtracker.app.R
import com.healthtracker.app.data.remote.PatternAlert
import com.healthtracker.app.databinding.FragmentInsightsBinding
import kotlinx.coroutines.launch
import retrofit2.HttpException

class InsightsFragment : Fragment() {

    private var _binding: FragmentInsightsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentInsightsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadPatterns()
        binding.btnRefresh.setOnClickListener { loadPatterns() }
    }

    private fun loadPatterns() {
        val app = requireActivity().application as HealthTrackerApp
        val prefs = requireContext().getSharedPreferences("auth", Context.MODE_PRIVATE)
        val token = prefs.getString("token", null)

        if (token == null) {
            binding.textInsightsStatus.visibility = View.VISIBLE
            binding.textInsightsStatus.setText(R.string.insights_sign_in)
            return
        }

        binding.progressInsights.visibility = View.VISIBLE
        binding.alertsContainer.removeAllViews()

        lifecycleScope.launch {
            try {
                val alerts = app.apiService.getPatterns("Bearer $token")
                binding.progressInsights.visibility = View.GONE
                if (alerts.isEmpty()) {
                    binding.textInsightsStatus.visibility = View.VISIBLE
                    binding.textInsightsStatus.setText(R.string.insights_empty)
                } else {
                    binding.textInsightsStatus.visibility = View.GONE
                    alerts.forEach { addAlertCard(it) }
                }
            } catch (e: HttpException) {
                // A 401 already triggers the session interceptor, which signs the
                // user out; anything else is worth naming on screen.
                binding.progressInsights.visibility = View.GONE
                binding.textInsightsStatus.visibility = View.VISIBLE
                binding.textInsightsStatus.text = if (e.code() == 401) {
                    getString(R.string.session_expired)
                } else {
                    getString(R.string.insights_error, e.message())
                }
            } catch (e: Exception) {
                binding.progressInsights.visibility = View.GONE
                binding.textInsightsStatus.visibility = View.VISIBLE
                binding.textInsightsStatus.text =
                    getString(R.string.insights_error, e.message ?: "network error")
            }
        }
    }

    private fun addAlertCard(alert: PatternAlert) {
        val ctx = requireContext()
        val card = layoutInflater.inflate(R.layout.item_insight_alert, binding.alertsContainer, false)
        card.findViewById<TextView>(R.id.text_alert_title).text = alert.title
        card.findViewById<TextView>(R.id.text_alert_desc).text = alert.description
        card.findViewById<TextView>(R.id.text_alert_science).text = alert.scienceNote
        val badge = card.findViewById<TextView>(R.id.text_alert_severity)
        badge.text = alert.severity.uppercase()
        val color = when (alert.severity) {
            "alert" -> ContextCompat.getColor(ctx, R.color.accent_red)
            "warning" -> ContextCompat.getColor(ctx, R.color.accent_yellow)
            else -> ContextCompat.getColor(ctx, R.color.accent)
        }
        badge.setTextColor(color)
        // Tint the card's left stroke to match severity
        (card as? com.google.android.material.card.MaterialCardView)?.strokeColor = color
        binding.alertsContainer.addView(card)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
