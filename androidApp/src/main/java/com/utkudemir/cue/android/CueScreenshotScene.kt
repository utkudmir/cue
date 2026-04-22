package com.utkudemir.cue.android

enum class CueScreenshotScene {
    ONBOARDING,
    DEMO_HOME,
    DEMO_TRUST;

    companion object {
        private const val EXTRA_KEY = "cue_screenshot_scene"

        fun from(raw: String?): CueScreenshotScene? = when (raw?.trim()?.lowercase()) {
            "onboarding" -> ONBOARDING
            "demo-home" -> DEMO_HOME
            "demo-trust" -> DEMO_TRUST
            else -> null
        }

        fun fromIntentValue(raw: String?): CueScreenshotScene? = from(raw)

        fun extraKey(): String = EXTRA_KEY
    }
}
