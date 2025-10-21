package com.alex.studydemo.module_room

import android.util.Log
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

/**
 * @description
 * @author <a href="mailto:zhangqiushi@snqu.com">张秋实</a>
 * @version
 */
@Dao
interface UserDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insertUser(entity: UserEntity)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    fun insertUserException(entity: UserEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    fun insertList(list: List<UserEntity>): MutableList<Long>

    @Query("select userId from test_user where rowId in (:ids)")
    fun query(ids: MutableList<Long>): MutableList<String>

    @Query("select userId from test_user where userId in (:uids)")
    fun queryByUids(uids: MutableList<String>): MutableList<String>

    @Query("select * from test_user")
    fun getUsers(): MutableList<UserEntity>

    @Query("update test_user set name =:name where userId in (:uids)")
    fun updateUserName(name: String, uids: Collection<String>)

    @Query("update test_user set name =:name where userId in (:uids)")
    fun updateUser(name: String, uids: List<String>): Int


    @Transaction
    fun insertMessageUser(userEntity: UserEntity) {
        insertUser(userEntity)
        val users = queryByUids(mutableListOf(userEntity.userId))
        Log.d("11111111111", "insertMessageUser() called with: users = $users")
    }

}