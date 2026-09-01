package com.lumaread.app.ocr

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object OcrEngine {
    suspend fun recognize(bitmap: Bitmap): String {
        val latin = awaitText(bitmap, latin = true)
        val devanagari = if (latin.count { it.isLetterOrDigit() } < 80) awaitText(bitmap, latin = false) else ""
        val chosen = if (devanagari.length > latin.length) devanagari else latin
        return chosen.replace(Regex("-\\s*\\n\\s*"), "")
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private suspend fun awaitText(bitmap: Bitmap, latin: Boolean): String = suspendCancellableCoroutine { cont ->
        val client = if (latin) TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        else TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        client.process(InputImage.fromBitmap(bitmap, 0))
            .addOnSuccessListener {
                client.close()
                if (cont.isActive) cont.resume(it.text)
            }
            .addOnFailureListener {
                client.close()
                if (cont.isActive) cont.resume("")
            }
    }
}

data class OcrJob(val bookId: String, val pageIndex: Int, val uri: String)

object OcrQueue {
    private val pending = ArrayDeque<OcrJob>()
    fun enqueue(job: OcrJob) {
        if (pending.none { it.bookId == job.bookId && it.pageIndex == job.pageIndex }) pending += job
    }
    fun next(): OcrJob? = pending.removeFirstOrNull()
    fun pendingCount(): Int = pending.size
}
