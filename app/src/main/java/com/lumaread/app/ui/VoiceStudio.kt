package com.lumaread.app.ui

import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.util.Locale

data class VoiceStudioSettings(val voiceName: String, val speed: Float, val pitch: Float)

@Composable
fun VoiceStudioDialog(settings: VoiceStudioSettings, onDismiss: () -> Unit, onSave: (VoiceStudioSettings) -> Unit) {
    val context = LocalContext.current
    var engine by remember { mutableStateOf<TextToSpeech?>(null) }
    var voices by remember { mutableStateOf<List<Voice>>(emptyList()) }
    var selected by remember { mutableStateOf(settings.voiceName) }
    var speed by remember { mutableFloatStateOf(settings.speed) }
    var pitch by remember { mutableFloatStateOf(settings.pitch) }
    var status by remember { mutableStateOf("Loading installed voices…") }

    DisposableEffect(Unit) {
        var tts: TextToSpeech? = null
        tts = TextToSpeech(context) { result ->
            if (result == TextToSpeech.SUCCESS) {
                engine = tts
                voices = tts?.voices.orEmpty().sortedWith(
                    compareBy<Voice> { voicePriority(it.locale) }
                        .thenBy { it.isNetworkConnectionRequired }
                        .thenByDescending { it.quality }
                        .thenBy { it.name.lowercase() }
                )
                if (selected.isBlank()) selected = voices.firstOrNull()?.name.orEmpty()
                status = if (voices.isEmpty()) "No voices are installed. Add one in Android text-to-speech settings." else "${voices.size} installed voices"
            } else status = "Android text-to-speech is unavailable."
        }
        onDispose { tts?.stop(); tts?.shutdown() }
    }

    fun preview(voice: Voice) {
        val tts = engine ?: return
        tts.voice = voice; tts.setSpeechRate(speed); tts.setPitch(pitch)
        val sample = if (voice.locale.language == "hi") "यह लूमारेड आवाज़ का नमूना है।" else "This is a preview of your LumaRead voice."
        tts.speak(sample, TextToSpeech.QUEUE_FLUSH, null, "voice_preview")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Voice Studio") },
        text = {
            Column(Modifier.fillMaxWidth()) {
                Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(12.dp))
                LazyColumn(Modifier.fillMaxWidth().heightIn(max = 270.dp)) {
                    items(voices, key = { it.name }) { voice ->
                        Surface(
                            onClick = { selected = voice.name },
                            color = if (selected == voice.name) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                        ) {
                            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected == voice.name, { selected = voice.name })
                                Column(Modifier.weight(1f)) {
                                    Text(voice.name, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
                                    Text(
                                        "${voice.locale.displayName} · ${if (voice.isNetworkConnectionRequired) "Network required" else "Available offline"}",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { preview(voice) }, modifier = Modifier.size(48.dp)) { Icon(Icons.Default.PlayArrow, "Preview ${voice.name}") }
                            }
                        }
                    }
                }
                SettingSlider("Speed", speed, 0.65f..1.75f, { speed = it }, "%.2f×".format(speed))
                SettingSlider("Pitch", pitch, 0.7f..1.3f, { pitch = it }, "%.2f".format(pitch))
                Text("Voice labels come from your installed TTS engine. LumaRead does not guess gender.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { Button(onClick = { onSave(VoiceStudioSettings(selected, speed, pitch)) }, enabled = selected.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable private fun SettingSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit, valueLabel: String) {
    Row(Modifier.fillMaxWidth().padding(top = 12.dp)) { Text(label, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)); Text(valueLabel, color = MaterialTheme.colorScheme.primary) }
    Slider(value, onChange, valueRange = range)
}

private fun voicePriority(locale: Locale): Int = when {
    locale.language == "en" && locale.country == "IN" -> 0
    locale.language == "hi" && locale.country == "IN" -> 1
    locale.language == "en" -> 2
    locale.language == "hi" -> 3
    else -> 4
}
