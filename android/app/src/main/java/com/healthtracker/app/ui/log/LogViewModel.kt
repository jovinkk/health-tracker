package com.healthtracker.app.ui.log

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import com.healthtracker.app.HealthTrackerApp
import com.healthtracker.app.data.local.entity.HealthEntry
import kotlinx.coroutines.launch

class LogViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as HealthTrackerApp).repository
    val entries = repo.allEntries

    /** Non-null while a transcript is being parsed, so the UI can show progress. */
    val processing = MutableLiveData(false)
    val error = MutableLiveData<String?>(null)

    fun processVoiceInput(transcript: String) = viewModelScope.launch {
        processing.value = true
        error.value = null
        try {
            repo.processAndSaveSpeechInput(transcript)
        } catch (e: Exception) {
            error.value = e.message ?: "Could not understand that"
        } finally {
            processing.value = false
        }
    }

    fun updateEntry(entry: HealthEntry) = viewModelScope.launch {
        repo.updateEntry(entry, token())
    }

    fun deleteEntry(entry: HealthEntry) = viewModelScope.launch {
        repo.deleteEntry(entry, token())
    }

    private fun token(): String? = getApplication<HealthTrackerApp>()
        .getSharedPreferences("auth", Context.MODE_PRIVATE)
        .getString("token", null)
}
