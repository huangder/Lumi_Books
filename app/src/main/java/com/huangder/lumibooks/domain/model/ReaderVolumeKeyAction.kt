package com.huangder.lumibooks.domain.model

enum class ReaderVolumeKeyAction(val storageValue: String) {
    SYSTEM_VOLUME("system_volume"),
    PREVIOUS_PAGE("previous_page"),
    NEXT_PAGE("next_page"),
    BRIGHTNESS_DOWN("brightness_down"),
    BRIGHTNESS_UP("brightness_up"),
    FONT_SIZE_DOWN("font_size_down"),
    FONT_SIZE_UP("font_size_up"),
    TOGGLE_BOOKMARK("toggle_bookmark");

    companion object {
        fun fromStorage(value: String?): ReaderVolumeKeyAction =
            entries.firstOrNull { it.storageValue == value } ?: SYSTEM_VOLUME
    }
}
