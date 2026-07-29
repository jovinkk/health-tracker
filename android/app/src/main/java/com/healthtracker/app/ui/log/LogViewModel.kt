package com.healthtracker.app.ui.log

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.healthtracker.app.HealthTrackerApp
import com.healthtracker.app.data.local.entity.HealthEntry
import kotlinx.coroutines.launch

class LogViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = (app as HealthTrackerApp).repository
    val entries = repo.allEntries

    fun processVoiceInput(transcript: String) = viewModelScope.launch {
        repo.processAndSaveSpeechInput(transcript)
    }

    fun deleteEntry(entry: HealthEntry) = viewModelScope.launch {
        (getApplication<HealthTrackerApp>()).database.healthEntryDao().delete(entry)
    }
}
