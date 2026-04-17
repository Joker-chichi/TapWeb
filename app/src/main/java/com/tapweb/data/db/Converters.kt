package com.tapweb.data.db

import androidx.room.TypeConverter
import com.tapweb.data.model.IconType
import com.tapweb.data.model.OpenMode

class Converters {
    @TypeConverter
    fun fromOpenMode(mode: OpenMode): String = mode.name

    @TypeConverter
    fun toOpenMode(value: String): OpenMode =
        try { OpenMode.valueOf(value) } catch (_: Exception) { OpenMode.WEBVIEW }

    @TypeConverter
    fun fromIconType(type: IconType): String = type.name

    @TypeConverter
    fun toIconType(value: String): IconType =
        try { IconType.valueOf(value) } catch (_: Exception) { IconType.AUTO }
}
