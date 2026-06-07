package com.vedesh.readfree.data.db

import androidx.room.TypeConverter
import com.vedesh.readfree.data.db.entity.ReadState

class Converters {
    @TypeConverter
    fun fromReadState(s: ReadState) = s.name

    @TypeConverter
    fun toReadState(s: String) = ReadState.valueOf(s)
}
