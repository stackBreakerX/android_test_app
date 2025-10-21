package com.alex.studydemo.module_room

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @time 2021/5/11 7:37 下午
 * @version
 */
@Entity(tableName = "test_user", indices = [Index("userId", unique = true)])
class UserEntity {
    @PrimaryKey
    var userId: String = ""
    var name: String? = ""

}