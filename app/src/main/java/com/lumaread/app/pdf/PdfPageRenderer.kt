package com.lumaread.app.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import kotlin.math.roundToInt

object PdfPageRenderer {
    fun pageCount(context: Context, uri: Uri): Int {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Unable to open PDF")
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                return renderer.pageCount
            }
        }
    }

    fun renderPage(
        context: Context,
        uri: Uri,
        pageIndex: Int,
        targetWidthPx: Int = 1400
    ): Bitmap {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Unable to open PDF")
        descriptor.use { pfd ->
            PdfRenderer(pfd).use { renderer ->
                val safePage = pageIndex.coerceIn(0, renderer.pageCount - 1)
                renderer.openPage(safePage).use { page ->
                    val ratio = page.height.toFloat() / page.width.toFloat()
                    val width = targetWidthPx.coerceAtLeast(360)
                    val height = (width * ratio).roundToInt().coerceAtLeast(480)
                    return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    }
                }
            }
        }
    }
}
