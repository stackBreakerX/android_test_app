package com.alex.studydemo.module_room

import com.alex.studydemo.ui.StudyPage
import com.alex.studydemo.ui.StudyTheme
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.room.withTransaction
import androidx.sqlite.db.SupportSQLiteDatabase
import com.alibaba.android.arouter.facade.annotation.Route
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** 保留用户批量插入、冲突更新、消息 SQL/DAO 和事务实验；所有数据库操作在 IO 上串行执行。 */
@Route(path = RoomActivity.PATH)
class RoomActivity : ComponentActivity() {
    private var content by mutableStateOf("")
    private var output by mutableStateOf("选择操作以查看实际数据库结果")
    private var busy by mutableStateOf(false)
    private var dataBase: UnifiedAppDataBase? = null
    private val operationLock = Mutex()
    companion object { const val PATH = "/view/Room" }

    /** 数据库延迟到首个 IO 操作创建；不在重组时打开或写入数据库。 */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            StudyTheme {
                StudyPage("Room 数据库", onBack = { finish() }) {
                    OutlinedTextField(content, { content = it }, label = { Text("消息内容") }, modifier = Modifier.fillMaxWidth())
                    Button(enabled = !busy, onClick = { execute("SQL 插入消息") { sqlInsertMessage(it.openHelper.writableDatabase); "已插入 alex is null" } }) { Text("SQL 插入消息") }
                    Button(enabled = !busy, onClick = {
                        val text = content
                        execute("DAO 插入消息") { it.messageDao().insertMessage(MessageEntity().apply { this.content = text }); "已写入：$text" }
                    }) { Text("DAO 插入消息") }
                    Button(enabled = !busy, onClick = { execute("查询消息") { it.messageDao().getMessages().orEmpty().joinToString("\n") } }) { Text("查询消息") }
                    Button(enabled = !busy, onClick = { execute("批量插入 10..15") { db -> "rowIds=${db.userDao().insertList(users(10..15))}" } }) { Text("批量插入用户 10..15") }
                    Button(enabled = !busy, onClick = { execute("更新已有用户") { "影响行数=${it.userDao().updateUser("test", listOf("10", "11", "12", "13"))}" } }) { Text("更新用户 10..13") }
                    Button(enabled = !busy, onClick = { execute("更新 99 / 55") { "影响行数=${it.userDao().updateUser("android 111", listOf("99", "55"))}" } }) { Text("更新 99 / 55（可观察零行）") }
                    Button(enabled = !busy, onClick = { execute("插入并更新冲突") { db ->
                        // IGNORE 返回 -1 表示冲突；插入和补充更新位于同一事务，避免并发读到半完成结果。
                        db.withTransaction {
                            val batch = users(14..17)
                            val rowIds = db.userDao().insertList(batch)
                            val inserted = db.userDao().query(rowIds.filter { it != -1L }.toMutableList())
                            val conflicts = batch.map { it.userId }.toSet() - inserted.toSet()
                            db.userDao().updateUserName("哈哈哈", conflicts)
                            "rowIds=$rowIds\n新增=$inserted\n冲突更新=$conflicts"
                        }
                    } }) { Text("插入 14..17 并更新冲突用户") }
                    Button(enabled = !busy, onClick = { execute("DAO 事务") { it.userDao().insertMessageUser(users(99..99).single()); "事务插入并查询用户 99 完成" } }) { Text("DAO @Transaction") }
                    Button(enabled = !busy, onClick = { execute("协程事务") { db ->
                        db.withTransaction {
                            db.userDao().insertUser(users(102..102).single())
                            "插入 102；更新 999 影响行数=${db.userDao().updateUser("test", listOf("999"))}"
                        }
                    } }) { Text("withTransaction") }
                    Button(enabled = !busy, onClick = { execute("查询用户") { it.userDao().getUsers().joinToString("\n") { user -> "${user.userId}：${user.name}" } } }) { Text("查询用户") }
                    if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
                    SelectionContainer { Text(output) }
                }
            }
        }
    }

    /** 随机名称保留重复插入的可观察差异，用户主键取闭区间内的十进制字符串。 */
    private fun users(ids: IntRange) = ids.map { id -> UserEntity().apply { userId = id.toString(); name = Random.nextInt(1, 10).toString() } }

    /** 串行执行写入与查询；取消不转成业务错误，SQL 或 schema 异常展示原始原因且不清库。 */
    private fun execute(label: String, action: suspend (UnifiedAppDataBase) -> String) {
        if (busy) return
        busy = true
        lifecycleScope.launch {
            try {
                output = withContext(Dispatchers.IO) {
                    operationLock.withLock {
                        val db = dataBase ?: requireNotNull(DataBaseManager.init(applicationContext, UnifiedAppDataBase::class.java)) { "数据库初始化失败" }.also { dataBase = it }
                        "$label\n${action(db)}"
                    }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { output = "$label 失败：${e.message}" }
            finally { busy = false }
        }
    }

    /** 兼容已有 SQL 入口；空连接不写入，用户表以 userId 为主键，调用方负责 IO 调度。 */
    fun sqlInsertUser(db: SupportSQLiteDatabase?) {
        db?.execSQL("INSERT OR REPLACE INTO test_user (userId, name) VALUES (?, ?)", arrayOf("sql-user", "aaaa"))
    }

    /** 兼容消息 SQL 入口；省略自增主键，避免未绑定占位符导致执行失败。 */
    fun sqlInsertMessage(db: SupportSQLiteDatabase?) {
        db?.execSQL("INSERT INTO test_message (content) VALUES (?)", arrayOf("alex is null"))
    }

    /** 等待已进入临界区的操作退出后关闭本页连接，避免在 IO 写入途中关闭数据库。 */
    override fun onDestroy() {
        super.onDestroy()
        // 使用独立 IO 清理作用域；即使销毁时初始化仍在执行，也在同一把锁内读取最终连接。
        kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            operationLock.withLock { dataBase?.close(); dataBase = null }
        }
    }

}
