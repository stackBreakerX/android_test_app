package com.alex.studydemo.module_room

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * 统一的应用数据库，合并用户、消息以及消息FTS相关表
 */
@Database(
    entities = [
        UserEntity::class,
        MessageEntity::class,
        FtsMessageEntity::class,
    ],
    version = 1,
    exportSchema = true
)
abstract class UnifiedAppDataBase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun messageDao(): MessageDao
}