package com.lumaread.app

import android.app.Application
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader

class LumaReadApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        PDFBoxResourceLoader.init(this)
    }
}
