package com.alex.studydemo.module_room

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @time 2021/5/12 10:59 上午
 * @version
 */
@Database(entities = [FtsMessageEntity::class], version = 2, exportSchema = false)
abstract class AppFtsDataBase : RoomDatabase() {
}