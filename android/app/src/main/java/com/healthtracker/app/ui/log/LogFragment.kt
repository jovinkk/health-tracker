package com.healthtracker.app.ui.log

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.gson.Gson
import com.healthtracker.app.data.local.entity.HealthEntry
import com.healthtracker.app.databinding.FragmentLogBinding
import com.healthtracker.app.databinding.ItemLogEntryBinding
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.*

class LogFragment : Fragment() {

    companion object {
        private const val SPEECH_REQ = 2001
    }

    private var _binding: FragmentLogBinding? = null
    private val binding get() = _binding!!
    private val viewModel: LogViewModel by viewModels()
    private val dateFormat = SimpleDateFormat("MMM d, HH:mm", Locale.getDefault())
    private val gson = Gson()

    private val adapter = object : RecyclerView.Adapter<EntryViewHolder>() {
        var items: List<HealthEntry> = emptyList()
        override fun getItemCount() = items.size
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
            EntryViewHolder(ItemLogEntryBinding.inflate(layoutInflater, parent, false))
        override fun onBindViewHolder(holder: EntryViewHolder, position: Int) {
            val entry = items[position]
            holder.binding.textType.text = entry.entryType.replaceFirstChar { it.uppercase() }
            holder.binding.textRaw.text = entry.rawInput ?: "(no transcript)"
            holder.binding.textTime.text = dateFormat.format(Date(entry.timestamp))
            holder.binding.textScore.text = entry.numericValue?.let { "%.0f/10".format(it) } ?: ""
            holder.binding.textScore.visibility = if (entry.numericValue != null) View.VISIBLE else View.GONE
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentLogBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerLog.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerLog.adapter = adapter

        viewModel.entries.observe(viewLifecycleOwner) { entries ->
            adapter.items = entries
            adapter.notifyDataSetChanged()
            binding.textEmptyLog.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
        }

        binding.fabSpeech.setOnClickListener { startSpeechCapture() }
    }

    private fun startSpeechCapture() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your health: pain, stress, food, mood…")
        }
        startActivityForResult(intent, SPEECH_REQ)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SPEECH_REQ && resultCode == Activity.RESULT_OK) {
            val transcript = data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()?.trim()
            if (!transcript.isNullOrBlank()) {
                Toast.makeText(requireContext(), "Processing: \"$transcript\"", Toast.LENGTH_SHORT).show()
                viewModel.processVoiceInput(transcript)
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

class EntryViewHolder(val binding: ItemLogEntryBinding) : RecyclerView.ViewHolder(binding.root)
