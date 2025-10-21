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
interface FtsMessageDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(messageEntity: MessageEntity)

    @Query("select * from test_message_fts")
    fun queryAll(): FtsMessageEntity

    @Query("select * from test_message join test_message_fts on test_message.content = test_message_fts.content where test_message_fts match :query")
    fun search(query: String): FtsMessageEntity

}