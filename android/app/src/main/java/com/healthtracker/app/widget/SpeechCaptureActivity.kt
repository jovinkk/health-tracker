package com.healthtracker.app.widget

import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.healthtracker.app.HealthTrackerApp
import kotlinx.coroutines.launch

/**
 * Transparent full-screen activity that launches the system speech recognizer.
 * On result, it sends the transcript to Gemini for parsing and saves to local DB.
 * Finishes immediately after — user barely notices it opened.
 */
class SpeechCaptureActivity : AppCompatActivity() {

    companion object {
        private const val SPEECH_REQUEST_CODE = 1001
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, "Speech recognition not available", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Describe your health: pain, stress, food, mood…")
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        }

        startActivityForResult(intent, SPEECH_REQUEST_CODE)
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SPEECH_REQUEST_CODE && resultCode == RESULT_OK) {
            val transcript = data
                ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                ?.firstOrNull()
                ?.trim()

            if (!transcript.isNullOrBlank()) {
                processTranscript(transcript)
            } else {
                finish()
            }
        } else {
            finish()
        }
    }

    private fun processTranscript(transcript: String) {
        val app = application as HealthTrackerApp
        lifecycleScope.launch {
            try {
                app.repository.processAndSaveSpeechInput(transcript)
                Toast.makeText(this@SpeechCaptureActivity, "✓ Logged: $transcript", Toast.LENGTH_LONG).show()
            } catch (e: java.net.UnknownHostException) {
                Toast.makeText(this@SpeechCaptureActivity, "No connection — entry saved offline, will sync later.", Toast.LENGTH_LONG).show()
            } catch (e: java.net.SocketTimeoutException) {
                Toast.makeText(this@SpeechCaptureActivity, "Server timeout — entry saved offline, will sync later.", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                val msg = e.message ?: "Unknown error"
                Toast.makeText(this@SpeechCaptureActivity, "Could not parse entry: $msg", Toast.LENGTH_LONG).show()
            } finally {
                finish()
            }
        }
    }
}
