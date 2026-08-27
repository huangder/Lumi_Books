package com.huangder.lumibooks.tts

sealed interface TtsProviderSelection {
    val storedValue: String

    data object SystemDefault : TtsProviderSelection {
        override val storedValue: String = SYSTEM_KEY
    }

    data object AiModel : TtsProviderSelection {
        override val storedValue: String = AI_KEY
    }

    data class AndroidEngine(val packageName: String) : TtsProviderSelection {
        init {
            require(packageName.isNotBlank()) { "Android TTS package name cannot be blank" }
        }

        override val storedValue: String = "$ANDROID_PREFIX$packageName"
    }

    companion object {
        private const val SYSTEM_KEY = "system"
        private const val AI_KEY = "ai"
        private const val ANDROID_PREFIX = "android:"

        fun fromStoredValue(value: String?): TtsProviderSelection? = when {
            value == SYSTEM_KEY -> SystemDefault
            value == AI_KEY -> AiModel
            value?.startsWith(ANDROID_PREFIX) == true -> value
                .removePrefix(ANDROID_PREFIX)
                .takeIf(String::isNotBlank)
                ?.let(::AndroidEngine)
            else -> null
        }

        fun resolve(
            storedValue: String?,
            legacyEnginePackage: String?,
            externalTtsConfigured: Boolean
        ): TtsProviderSelection {
            if (storedValue != null) {
                return fromStoredValue(storedValue) ?: SystemDefault
            }
            if (externalTtsConfigured) return AiModel
            return legacyEnginePackage
                ?.takeIf(String::isNotBlank)
                ?.let(::AndroidEngine)
                ?: SystemDefault
        }
    }
}

data class InstalledTtsEngine(
    val packageName: String,
    val label: String
)
