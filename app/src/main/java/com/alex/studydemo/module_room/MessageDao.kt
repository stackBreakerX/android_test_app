package com.alex.studydemo.module_room

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @time 2021/5/12 10:57 上午
 * @version
 */
@Dao
interface MessageDao {

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertMessage(messageEntity: MessageEntity)

    @Query("select * from test_message")
    fun getMessages():MutableList<MessageEntity>?

}