package com.stl.autolauncher.service

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AutoLauncherAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() {
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    companion object {
        @Volatile
        private var instance: AutoLauncherAccessibilityService? = null

        fun goHome(): Boolean {
            return instance?.performGlobalAction(GLOBAL_ACTION_HOME) == true
        }
    }
}
