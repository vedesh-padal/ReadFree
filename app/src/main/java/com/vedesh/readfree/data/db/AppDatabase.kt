package com.vedesh.readfree.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.vedesh.readfree.data.db.dao.ArticleDao
import com.vedesh.readfree.data.db.dao.ListDao
import com.vedesh.readfree.data.db.dao.TagDao
import com.vedesh.readfree.data.db.entity.Article
import com.vedesh.readfree.data.db.entity.ArticleList
import com.vedesh.readfree.data.db.entity.ArticleListXRef
import com.vedesh.readfree.data.db.entity.ArticleTagXRef
import com.vedesh.readfree.data.db.entity.Tag

@Database(
    entities = [
        Article::class,
        ArticleList::class,
        ArticleListXRef::class,
        Tag::class,
        ArticleTagXRef::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun articleDao(): ArticleDao

    abstract fun listDao(): ListDao

    abstract fun tagDao(): TagDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "readfree.db",
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}
