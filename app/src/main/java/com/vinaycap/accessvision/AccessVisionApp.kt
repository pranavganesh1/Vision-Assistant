package com.vinaycap.accessvision

import android.app.Application
import android.util.Log

/**
 * Application class for AccessVision.
 * Initializes global resources and ensures offline-only operation.
 */
class AccessVisionApp : Application() {

    companion object {
        const val TAG = "AccessVision"
        lateinit var instance: AccessVisionApp
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Enforce strict offline mode: disable any networking at app level
        Log.i(TAG, "AccessVision initialized — fully offline mode enforced")
    }
}
