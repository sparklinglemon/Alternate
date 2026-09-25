package com.lulu786.Alternate

import android.app.Activity
import android.app.Application
import android.os.Bundle

class MainApplication : Application() {
    private var started = 0

    override fun onCreate() {
        super.onCreate()
        Lock.onForeground(this)
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityStarted(a: Activity) {
                if (started++ == 0) Lock.onForeground(a)
            }

            override fun onActivityStopped(a: Activity) {
                if (--started == 0 && !a.isChangingConfigurations) Lock.onBackground(a)
            }

            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityResumed(a: Activity) {}
            override fun onActivityPaused(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
    }
}
