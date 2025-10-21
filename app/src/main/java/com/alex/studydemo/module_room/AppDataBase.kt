package com.alex.studydemo.module_room

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.Transaction

/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @time 2021/5/12 10:59 上午
 * @version
 */
@Database(entities = [UserEntity::class], version = 1, exportSchema = true)
abstract class AppDataBase : RoomDatabase() {
    abstract fun userDao(): UserDao
}
