package com.huangder.lumibooks.domain.model

import org.json.JSONArray

object AnnotationTags {
    fun normalize(tags: Iterable<String>): List<String> = tags.map(String::trim)
        .filter(String::isNotEmpty)
        .distinct()

    fun encode(tags: Iterable<String>): String = JSONArray(normalize(tags)).toString()

    fun decode(json: String?): List<String> = runCatching {
        val array = JSONArray(json ?: "[]")
        normalize((0 until array.length()).mapNotNull { array.opt(it) as? String })
    }.getOrDefault(emptyList())

    fun rename(tags: List<String>, old: String, new: String): List<String> =
        normalize(tags.map { if (it == old) new else it })
}
