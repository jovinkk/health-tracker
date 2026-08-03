package com.healthtracker.app.ui.trends

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.healthtracker.app.R
import com.healthtracker.app.data.local.entity.WearableSnapshot
import com.healthtracker.app.databinding.FragmentTrendsBinding
import com.healthtracker.app.databinding.ItemTrendChartBinding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

class TrendsFragment : Fragment() {

    private var _binding: FragmentTrendsBinding? = null
    private val binding get() = _binding!!
    private val viewModel: TrendsViewModel by viewModels()

    /** label, unit suffix, and how to pull the value off a snapshot. */
    private data class Metric(
        val labelRes: Int,
        val suffix: String,
        val decimals: Int,
        val select: (WearableSnapshot) -> Float?,
    )

    private val metrics = listOf(
        Metric(R.string.metric_steps, "", 0) { it.steps?.toFloat() },
        Metric(R.string.metric_heart_rate, " bpm", 0) { it.heartRateAvg },
        Metric(R.string.metric_resting_hr, " bpm", 0) { it.heartRateResting },
        Metric(R.string.metric_hrv, " ms", 0) { it.hrvMs },
        Metric(R.string.metric_sleep, " h", 1) { s -> s.sleepDurationMin?.let { it / 60f } },
        Metric(R.string.metric_spo2, " %", 1) { it.spo2Pct },
        Metric(R.string.metric_calories, " kcal", 0) { it.caloriesActive },
    )

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentTrendsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.toggleRange.check(
            when (viewModel.rangeDays.value) {
                30 -> R.id.btn_30
                90 -> R.id.btn_90
                else -> R.id.btn_7
            }
        )
        binding.toggleRange.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            viewModel.setRange(
                when (checkedId) {
                    R.id.btn_30 -> 30
                    R.id.btn_90 -> 90
                    else -> 7
                }
            )
        }

        viewModel.snapshots.observe(viewLifecycleOwner) { snapshots ->
            render(snapshots.orEmpty(), viewModel.rangeDays.value ?: 7)
        }
    }

    private fun render(snapshots: List<WearableSnapshot>, days: Int) {
        binding.chartsContainer.removeAllViews()

        // One slot per calendar day so gaps in the data stay visible as gaps
        // rather than the line closing over missing days.
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val byDay = snapshots.groupBy {
            Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate()
        }
        val timeline = (days - 1 downTo 0).map { today.minusDays(it.toLong()) }

        val anyData = metrics.any { metric ->
            timeline.any { day -> byDay[day]?.firstNotNullOfOrNull(metric.select) != null }
        }
        binding.textTrendsEmpty.visibility = if (anyData) View.GONE else View.VISIBLE
        if (!anyData) return

        metrics.forEach { metric ->
            val series = timeline.map { day -> byDay[day]?.firstNotNullOfOrNull(metric.select) }
            val present = series.filterNotNull()
            if (present.isEmpty()) return@forEach

            val item = ItemTrendChartBinding.inflate(layoutInflater, binding.chartsContainer, false)
            item.textMetricName.text = getString(R.string.trend_average, getString(metric.labelRes).lowercase())
            val avg = present.average()
            item.textMetricAverage.text = "%,.${metric.decimals}f%s".format(avg, metric.suffix)
            item.chart.setValues(series)
            binding.chartsContainer.addView(item.root)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
