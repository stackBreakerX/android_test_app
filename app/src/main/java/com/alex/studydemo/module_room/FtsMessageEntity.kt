package com.alex.studydemo.module_room

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey
import java.sql.RowId

/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @time 2021/5/11 7:37 下午
 * @version
 */
@Entity(tableName = "test_message_fts")
//@Fts4(contentEntity = MessageEntity::class)
class FtsMessageEntity(
    @ColumnInfo(name = "rowid")
    @PrimaryKey
    var id: Long,
    var content: String? = ""
)