package com.lumaread.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.devanagari.DevanagariTextRecognizerOptions
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

object TextPageLoader {
    suspend fun load(context: Context, uri: Uri, pageIndex: Int): String {
        val embedded = withContext(Dispatchers.IO) {
            runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    PDDocument.load(input).use { document ->
                        PDFTextStripper().apply {
                            startPage = pageIndex + 1
                            endPage = pageIndex + 1
                            sortByPosition = true
                        }.getText(document).cleanBookText()
                    }
                }.orEmpty()
            }.getOrDefault("")
        }

        if (embedded.count { it.isLetterOrDigit() } >= 24) return embedded

        val bitmap = withContext(Dispatchers.IO) {
            PdfPageRenderer.renderPage(context, uri, pageIndex, 1800)
        }
        return try {
            val latin = recognizeLatin(bitmap)
            val devanagari = if (latin.count { it.isLetterOrDigit() } < 80) recognizeDevanagari(bitmap) else ""
            if (devanagari.length > latin.length) devanagari.cleanBookText() else latin.cleanBookText()
        } finally {
            bitmap.recycle()
        }
    }

    private suspend fun recognizeLatin(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val client = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        val task = client.process(InputImage.fromBitmap(bitmap, 0))
        task.addOnSuccessListener { result ->
            client.close()
            if (cont.isActive) cont.resume(result.text)
        }.addOnFailureListener {
            client.close()
            if (cont.isActive) cont.resume("")
        }
    }

    private suspend fun recognizeDevanagari(bitmap: Bitmap): String = suspendCancellableCoroutine { cont ->
        val client = TextRecognition.getClient(DevanagariTextRecognizerOptions.Builder().build())
        val task = client.process(InputImage.fromBitmap(bitmap, 0))
        task.addOnSuccessListener { result ->
            client.close()
            if (cont.isActive) cont.resume(result.text)
        }.addOnFailureListener {
            client.close()
            if (cont.isActive) cont.resume("")
        }
    }

    private fun String.cleanBookText(): String =
        replace(Regex("-\\s*\\n\\s*"), "")
            .replace(Regex("[\\t ]+"), " ")
            .replace(Regex("\\n{3,}"), "\\n\\n")
            .trim()
}
