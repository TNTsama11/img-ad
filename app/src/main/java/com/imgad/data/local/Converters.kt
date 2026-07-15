package com.imgad.data.local

import androidx.room.TypeConverter
import com.imgad.domain.model.AssetSource
import com.imgad.domain.model.MessageRole
import com.imgad.domain.model.TaskState
import org.json.JSONArray

class Converters {
    @TypeConverter
    fun fromStringSet(value: Set<String>?): String? = value
        ?.toList()
        ?.sorted()
        ?.let { values ->
            JSONArray().apply { values.forEach { put(it) } }.toString()
        }

    @TypeConverter
    fun toStringSet(value: String?): Set<String> {
        if (value.isNullOrBlank()) return emptySet()
        val values = JSONArray(value)
        return buildSet(values.length()) {
            repeat(values.length()) { index -> add(values.getString(index)) }
        }
    }

    @TypeConverter
    fun fromMessageRole(value: MessageRole?): String? = value?.name

    @TypeConverter
    fun toMessageRole(value: String?): MessageRole? = value?.let(MessageRole::valueOf)

    @TypeConverter
    fun fromTaskState(value: TaskState?): String? = value?.name

    @TypeConverter
    fun toTaskState(value: String?): TaskState? = value?.let(TaskState::valueOf)

    @TypeConverter
    fun fromAssetSource(value: AssetSource?): String? = value?.name

    @TypeConverter
    fun toAssetSource(value: String?): AssetSource? = value?.let(AssetSource::valueOf)
}
