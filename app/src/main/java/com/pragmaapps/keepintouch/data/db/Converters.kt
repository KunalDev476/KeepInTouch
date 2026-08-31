package com.pragmaapps.keepintouch.data.db

import androidx.room.TypeConverter
import com.pragmaapps.keepintouch.domain.model.Direction
import com.pragmaapps.keepintouch.domain.model.LogLevel
import com.pragmaapps.keepintouch.domain.model.MatchConfidence
import com.pragmaapps.keepintouch.domain.model.Recurrence

class Converters {
    @TypeConverter
    fun fromLogLevel(value: LogLevel): String = value.name

    @TypeConverter
    fun toLogLevel(value: String): LogLevel = LogLevel.valueOf(value)
    @TypeConverter
    fun fromRecurrence(value: Recurrence): String = value.name

    @TypeConverter
    fun toRecurrence(value: String): Recurrence = Recurrence.valueOf(value)

    @TypeConverter
    fun fromDirection(value: Direction): String = value.name

    @TypeConverter
    fun toDirection(value: String): Direction = Direction.valueOf(value)

    @TypeConverter
    fun fromMatchConfidence(value: MatchConfidence?): String? = value?.name

    @TypeConverter
    fun toMatchConfidence(value: String?): MatchConfidence? = value?.let { MatchConfidence.valueOf(it) }
}
