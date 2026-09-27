package com.huangder.lumibooks.domain.model

import org.json.JSONObject

/** Independent of decode quality and screen brightness; persisted per book. */
data class ReaderImageAdjustments(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val sharpen: Float = 0f
) {
    fun normalized() = ReaderImageAdjustments(
        brightness.takeIf(Float::isFinite)?.coerceIn(-1f, 1f) ?: 0f,
        contrast.takeIf(Float::isFinite)?.coerceIn(0.5f, 2f) ?: 1f,
        sharpen.takeIf(Float::isFinite)?.coerceIn(0f, 1f) ?: 0f
    )

    fun forDisplay(eInk: Boolean) = normalized().let { if (eInk) it.copy(sharpen = 0f) else it }
    val isNeutral: Boolean get() = this == ReaderImageAdjustments()

    fun encode(): String = normalized().let {
        JSONObject().put("brightness", it.brightness).put("contrast", it.contrast)
            .put("sharpen", it.sharpen).toString()
    }

    companion object {
        fun decode(value: String?): ReaderImageAdjustments = runCatching {
            val json = JSONObject(value ?: "{}")
            ReaderImageAdjustments(
                json.optDouble("brightness", 0.0).toFloat(),
                json.optDouble("contrast", 1.0).toFloat(),
                json.optDouble("sharpen", 0.0).toFloat()
            ).normalized()
        }.getOrDefault(ReaderImageAdjustments())
    }
}
