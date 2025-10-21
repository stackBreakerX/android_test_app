package com.alex.studydemo.module_room

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import java.io.File

/**
 * @description
 *
 * @version
 */
class DataBaseManager private constructor() {

    private var appDataBase: AppDataBase? = null

    private var appFtsDataBase: AppFtsDataBase? = null

    companion object {

        private var instance: DataBaseManager? = null

        const val QUERY_LIMIT = 900

        @JvmName("getInstance1")
        fun getInstance(): DataBaseManager {
            if (instance == null) {
                synchronized(this) {
                    if (instance == null) {
                        instance = DataBaseManager()
                    }
                }
            }
            return instance!!
        }

        fun <T : RoomDatabase> init(context: Context, clazz: Class<T>): T? {
            return try {
                Room
                    .databaseBuilder(context, clazz, "test")
//                .fallbackToDestructiveMigration()
//                    .addMigrations(MIGRATION_2_3, MIGRATION_12_13)
                    .allowMainThreadQueries()
                    .build()
            } catch (e: Exception) {
                e.printStackTrace()
                return null
            }
        }
    }


    fun initFts(context: Context) {
        if (appFtsDataBase == null) {
            try {
                appFtsDataBase = Room
                    .databaseBuilder(context, AppFtsDataBase::class.java, "test_fts")
//                    .createFromFile(File(""), object : RoomDatabase.PrepackagedDatabaseCallback() {
//                        override fun onOpenPrepackagedDatabase(db: SupportSQLiteDatabase) {
//                            super.onOpenPrepackagedDatabase(db)
//                        }
//                    })
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)

                        }
                    })
//                .fallbackToDestructiveMigration()
//                    .addMigrations(MIGRATION_2_3, MIGRATION_12_13)
                    .allowMainThreadQueries()
                    .build()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        appFtsDataBase?.openHelper?.writableDatabase
    }

    fun getDataBase(): AppDataBase? {
        if (appDataBase == null) {
            return null
        }
        return appDataBase as AppDataBase
    }

    fun getFtsDataBase(): AppFtsDataBase? {
        if (appFtsDataBase == null) {
            return null
        }
        return appFtsDataBase as AppFtsDataBase
    }


    fun close() {
        appDataBase = null
    }

    fun clearDataBase() {
        appDataBase?.clearAllTables()
    }
}