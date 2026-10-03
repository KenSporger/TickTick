package com.personalticktick.app

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

/** Keeps instrumentation off the cloud service so device tests stay on a fresh local database. */
class TickTickTestRunner : AndroidJUnitRunner() {
    override fun callApplicationOnCreate(app: Application) {
        app.getSharedPreferences("ticktick", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("sync_enabled", false)
            .commit()
        super.callApplicationOnCreate(app)
    }
}
