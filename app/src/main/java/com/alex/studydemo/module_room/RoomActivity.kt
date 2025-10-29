package com.alex.studydemo.module_room

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import com.alex.studydemo.base.BaseActivity
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alex.studydemo.databinding.ActivityRoomBinding
import com.alibaba.android.arouter.facade.annotation.Route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlin.random.Random

@Route(path = RoomActivity.PATH)
class RoomActivity : BaseActivity<ActivityRoomBinding>() {

    private val mViewBinding: ActivityRoomBinding get() = binding

    private var userDataBase: AppDataBase? = null

    private var imDataBase: IMAppDataBase? = null

//    var ids = mutableListOf<String>()

    companion object {
        const val PATH = "/view/Room"

        private const val TAG = "RoomActivity"
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityRoomBinding =
        ActivityRoomBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {


        userDataBase = DataBaseManager.init(this, AppDataBase::class.java)
//        userDataBase?.openHelper?.writableDatabase?.execSQL("")
        val writableDatabase = userDataBase?.openHelper?.writableDatabase


//        writableDatabase?.execSQL("CREATE TABLE IF NOT EXISTS `test_user` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT)")
//        writableDatabase?.execSQL("CREATE TABLE IF NOT EXISTS `test_message` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `content` TEXT)")

        imDataBase = DataBaseManager.init(this, IMAppDataBase::class.java)


        mViewBinding.btnAddMessage.setOnClickListener {
            sqlInsertMessage(writableDatabase)
        }

        mViewBinding.btnAddMessageApi.setOnClickListener {
            val content = mViewBinding.etContent.text.toString()
            val message = MessageEntity()
            message.content = content
            imDataBase
                ?.messageDto()
                ?.insertMessage(message)
        }

        mViewBinding.btnGetMessageApi.setOnClickListener {
            val messages = imDataBase
                ?.messageDto()
                ?.getMessages()
            Toast.makeText(this, messages.toString(), Toast.LENGTH_LONG).show()
        }

        mViewBinding.btnAddUser.setOnClickListener {
//            val userEntity = UserEntity()
//            userEntity.name = Random.nextInt(1,10).toString()
//            userDataBase?.userDao()?.insertUser(userEntity)

            val list = mutableListOf<UserEntity>()
            var userEntity = UserEntity()
            userEntity.name = Random.nextInt(1, 10).toString()
            userEntity.userId = "10"
            list.add(userEntity)

            userEntity = UserEntity()
            userEntity.name = Random.nextInt(1, 10).toString()
            userEntity.userId = "11"
            list.add(userEntity)

            userEntity = UserEntity()
            userEntity.name = Random.nextInt(1, 10).toString()
            userEntity.userId = "12"
            list.add(userEntity)

            userEntity = UserEntity()
            userEntity.name = Random.nextInt(1, 10).toString()
            userEntity.userId = "13"
            list.add(userEntity)

            userEntity = UserEntity()
            userEntity.name = Random.nextInt(1, 10).toString()
            userEntity.userId = "14"
            list.add(userEntity)


            userEntity = UserEntity()
            userEntity.name = Random.nextInt(1, 10).toString()
            userEntity.userId = "15"
            list.add(userEntity)


            try {
                userDataBase?.userDao()?.insertList(list)
            } catch (e: Exception) {
                e.printStackTrace()
            }
//            sqlInsertUser(writableDatabase)

        }

        mViewBinding.btnUpdateUsers.setOnClickListener {
            val userIds = mutableListOf("10", "11", "12", "13")
            val rows = userDataBase?.userDao()?.updateUser("test", userIds)
            Toast.makeText(this, "rows = $rows", Toast.LENGTH_SHORT).show()
        }

        mViewBinding.btnUpdateUsersNoRows.setOnClickListener {
//            val userIds = mutableListOf("10", "11", "12", "13")
            val userIds = mutableListOf("99", "55")
            val rows = userDataBase?.userDao()?.updateUser("android 111", userIds)
            Toast.makeText(this, "rows = $rows", Toast.LENGTH_SHORT).show()
        }

//
        mViewBinding.btnAddUser1.setOnClickListener {
            val list = mutableListOf<UserEntity>()
            var userEntity = UserEntity()
            userEntity.name = Random.nextInt(1, 10).toString()
            userEntity.userId = "14"
            list.add(userEntity)

            userEntity = UserEntity()
            userEntity.name = Random.nextInt(1, 10).toString()
            userEntity.userId = "15"
            list.add(userEntity)

            userEntity = UserEntity()
            userEntity.name = Random.nextInt(1, 10).toString()
            userEntity.userId = "16"
            list.add(userEntity)


            userEntity = UserEntity()
            userEntity.name = Random.nextInt(1, 10).toString()
            userEntity.userId = "17"
            list.add(userEntity)


            var rowIds = mutableListOf<Long>()
            var uids = mutableListOf<String>()
            try {
                uids = list.map {
                    it.userId
                }.toMutableList()
                rowIds = userDataBase?.userDao()?.insertList(list) ?: mutableListOf()
                Log.d("1111111", "insert rowIds $rowIds")
                val insertedRowIds = rowIds.filter { rowId ->
                    rowId != -1L
                }.toMutableList()
                Log.d("1111111", "insert insertedRowIds $insertedRowIds")
                val insertUids = userDataBase?.userDao()?.query(insertedRowIds) ?: mutableSetOf()
                val notInsertUids = uids.subtract(insertUids.toSet())
                Log.d("22222", "not insert uids  $notInsertUids")
                userDataBase?.userDao()?.updateUserName("哈哈哈", notInsertUids)
            } catch (e: Exception) {

                e.printStackTrace()
            }
//            userDataBase?.userDao()?.getUsers()
        }

        mViewBinding.btnAddUserTransaction.setOnClickListener {


            var userEntity = UserEntity()
            userEntity.name = Random.nextInt(1, 10).toString()
            userEntity.userId = "99"

            userDataBase?.userDao()?.insertMessageUser(userEntity)
        }

        mViewBinding.btnTransactionTest.setOnClickListener {

                lifecycleScope.launch(Dispatchers.IO) {
                    try {
                        userDataBase?.withTransaction {

                            //failed
//                            val userIds = mutableListOf("101")
//                            val rows = userDataBase?.userDao()?.updateUser("test", userIds)
//                            Log.d(TAG, "btnTransactionTest() updateUser rows $rows")
//
//
//                            var userEntity = UserEntity()
//                            userEntity.name = Random.nextInt(1, 10).toString()
//                            userEntity.userId = "102"
//                            userDataBase?.userDao()?.insertUserException(userEntity)
                            //success

                            var userEntity = UserEntity()
                            userEntity.name = Random.nextInt(1, 10).toString()
                            userEntity.userId = "102"
                            userDataBase?.userDao()?.insertUser(userEntity)

                            val userIds = mutableListOf("999")
                            val rows = userDataBase?.userDao()?.updateUser("test", userIds)
                            Log.d(TAG, "btnTransactionTest() updateUser rows $rows")

                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
        }

//        mViewBinding.btnGetUser.setOnClickListener {
//            try {
//                val ids = userDataBase?.userDao()?.query()
//                Log.d("1111111", "onCreate() called $ids")
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//        }

    }

    fun sqlInsertUser(db: SupportSQLiteDatabase?) {
        db?.execSQL("INSERT OR REPLACE INTO `test_user` (`id`,`name`) VALUES (nullif(?, 0),'aaaa')")
    }

    fun sqlInsertMessage(db: SupportSQLiteDatabase?) {
        db?.execSQL("INSERT OR REPLACE INTO `test_message` (`id`,`content`) VALUES (nullif(?, 0),'alex is null')")
    }
}