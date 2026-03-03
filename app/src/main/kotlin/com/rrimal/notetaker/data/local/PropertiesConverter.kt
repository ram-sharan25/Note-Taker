package com.rrimal.notetaker.data.local

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room TypeConverter for Map<String, String> properties
 */
class PropertiesConverter {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromProperties(properties: Map<String, String>): String {
        return json.encodeToString(properties)
    }

    @TypeConverter
    fun toProperties(value: String): Map<String, String> {
        if (value.isEmpty()) return emptyMap()
        return try {
            json.decodeFromString(value)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
