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
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.google.gson.Gson
import com.healthtracker.app.R
import com.healthtracker.app.data.local.entity.HealthEntry
import com.healthtracker.app.databinding.DialogEditEntryBinding
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
            holder.binding.root.setOnClickListener { showEntryOptions(entry) }
        }
    }

    private fun showEntryOptions(entry: HealthEntry) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(entry.entryType.replaceFirstChar { it.uppercase() })
            .setItems(
                arrayOf(getString(R.string.action_edit), getString(R.string.action_delete))
            ) { _, which ->
                if (which == 0) showEditDialog(entry) else confirmDelete(entry)
            }
            .show()
    }

    private fun showEditDialog(entry: HealthEntry) {
        val dialogBinding = DialogEditEntryBinding.inflate(layoutInflater)
        dialogBinding.editNote.setText(entry.rawInput.orEmpty())
        dialogBinding.editScore.setText(entry.numericValue?.let { "%.0f".format(it) }.orEmpty())

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.edit_entry)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_save) { _, _ ->
                val note = dialogBinding.editNote.text.toString().trim()
                // Blank clears the score rather than being read as zero
                val score = dialogBinding.editScore.text.toString().trim()
                    .takeIf { it.isNotEmpty() }?.toFloatOrNull()?.coerceIn(0f, 10f)
                viewModel.updateEntry(
                    entry.copy(
                        rawInput = note.ifBlank { null },
                        numericValue = score,
                    )
                )
            }
            .show()
    }

    private fun confirmDelete(entry: HealthEntry) {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.delete_entry)
            .setMessage(R.string.delete_entry_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.deleteEntry(entry)
                Snackbar.make(binding.root, R.string.entry_deleted, Snackbar.LENGTH_SHORT).show()
            }
            .show()
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
