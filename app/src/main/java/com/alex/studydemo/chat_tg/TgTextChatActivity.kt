package com.alex.studydemo.chat_tg

import android.content.Context
import android.content.Intent
import android.graphics.RectF
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.view.ViewTreeObserver
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityTgTextChatBinding
import com.alex.studydemo.telegram.Theme
import java.util.ArrayDeque
import java.util.LinkedHashSet
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.math.min

/**
 * TG 文本聊天示例页面
 * - 仅演示文本消息的渲染、输入与发送流程
 * - 发送文本时，优先表现“输入框中的文字飞入消息列表”
 */
class TgTextChatActivity : BaseActivity<ActivityTgTextChatBinding>() {

    private data class InputTextSnapshot(
        val textRect: RectF,
        val bubbleRect: RectF,
        val multiline: Boolean,
    )

    private data class PendingSendAnimation(
        val messageId: Long,
        val text: String,
        val startSnapshot: InputTextSnapshot,
    )

    /** RecyclerView 适配器（负责将不同消息类型映射为对应的 Cell） */
    private lateinit var adapter: TgTextMessageAdapter
    /** 当前消息列表（演示使用内存数据，并通过 ListAdapter 的 Diff 提交） */
    private val items = mutableListOf<TgMessageItem>()
    /** 演示用的自增 id（简化唯一性判断） */
    private var nextId = 1L
    private var recyclerWidth: Int = 0
    private val precomputeExecutor = Executors.newSingleThreadExecutor()
    private val pendingSendQueue = ArrayDeque<PendingSendAnimation>()
    private val animatingMessageIds = LinkedHashSet<Long>()
    private var activeSendAnimation: PendingSendAnimation? = null
    private var activeTextEnterTransition: TgTextMessageEnterTransition? = null
    private val pendingHolderAttachListener = object : RecyclerView.OnChildAttachStateChangeListener {
        override fun onChildViewAttachedToWindow(view: View) {
            maybeProcessPendingSendAnimation()
        }

        override fun onChildViewDetachedFromWindow(view: View) = Unit
    }

    /** 从相册选 1～2 张图：一张为全宽单图，两张为并排双图（对齐 Telegram 相册） */
    private val pickImageLauncher = registerForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(2)
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@registerForActivityResult
        val first = uris[0]
        val second = uris.getOrNull(1)
        items.add(
            makeImageItem(
                fromMe = true,
                time = formatTime(nextId),
                imageUri = first,
                secondImageUri = second
            )
        )
        adapter.submitList(items.toList()) {
            binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
        }
    }

    override fun inflateBinding(inflater: android.view.LayoutInflater): ActivityTgTextChatBinding =
        ActivityTgTextChatBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        initTelegramBubbleTheme()
        title = "TG 文本消息"
        setupRecycler()
        setupInput()
        setupFuncTest()
        seedMessages()
    }

    override fun onDestroy() {
        activeTextEnterTransition?.cancel()
        activeTextEnterTransition = null
        binding.sendAnimationOverlay.clearTransitions()
        activeSendAnimation = null
        animatingMessageIds.clear()
        pendingSendQueue.clear()
        binding.recyclerView.removeOnChildAttachStateChangeListener(pendingHolderAttachListener)
        precomputeExecutor.shutdownNow()
        super.onDestroy()
    }

    /**
     * 在本页进入时统一初始化 TG 画笔密度与 [Theme] 气泡圆角，保证列表首次测量前即与 [TgSharedConfig] 一致。
     */
    private fun initTelegramBubbleTheme() {
        val density = resources.displayMetrics.density
        TgTheme.init(density)
        Theme.bubbleRadiusDp = TgSharedConfig.bubbleRadius
    }

    private fun setupFuncTest() {
        binding.funcTestButton.setOnClickListener { view: View ->
            val popup = android.widget.PopupMenu(this, view)
            popup.menu.add("文本消息编辑功能")
            popup.setOnMenuItemClickListener { item ->
                if (item.title == "文本消息编辑功能") {
                    editLastTextMessage()
                    true
                } else {
                    false
                }
            }
            popup.show()
        }
    }

    private fun editLastTextMessage() {
        val index = items.indexOfLast { it is TgMessageItem.Text }
        if (index == -1) return
        val item = items[index] as TgMessageItem.Text

        val shortText = "编辑后的短文本"
        val longText = "这是一段编辑后的长文本消息，用来测试气泡的大小变化动画效果。Telegram 的气泡在编辑时会平滑地改变大小，而不是突变。这段文本足够长，可以触发换行和气泡尺寸的显著变化。"

        val newText = if (item.text == longText) shortText else longText
        updateMessageText(item, newText)
    }

    private fun setupRecycler() {
        adapter = TgTextMessageAdapter { item, view ->
            showEditMenu(item, view)
        }
        binding.recyclerView.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false).apply {
            stackFromEnd = true
        }
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = TgChatListItemAnimator()
        binding.recyclerView.addOnChildAttachStateChangeListener(pendingHolderAttachListener)
        binding.recyclerView.addItemDecoration(AvatarGroupDecoration(adapter))
        val density = resources.displayMetrics.density
        binding.recyclerView.addItemDecoration(ChatVerticalSpaceDecoration((6f * density).toInt()))
        binding.recyclerView.addItemDecoration(ChatGapOverlayDecoration((6f * density).toInt()))
        binding.recyclerView.post {
            recyclerWidth = binding.recyclerView.width
        }
    }

    private fun showEditMenu(item: TgMessageItem.Text, view: View) {
        val popup = android.widget.PopupMenu(this, view)
        popup.menu.add("编辑消息")
        popup.setOnMenuItemClickListener { menuItem ->
            if (menuItem.title == "编辑消息") {
                showEditDialog(item)
                true
            } else {
                false
            }
        }
        popup.show()
    }

    private var editingMessageId: Long? = null

    private fun showEditDialog(item: TgMessageItem.Text) {
        editingMessageId = item.id
        binding.inputEdit.setText(item.text)
        binding.inputEdit.setSelection(item.text.length)
        binding.inputEdit.requestFocus()
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        imm.showSoftInput(binding.inputEdit, android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT)
        binding.sendButton.text = "编辑"
    }

    private fun updateMessageText(item: TgMessageItem.Text, newText: String) {
        val hasExtra = !item.quote.isNullOrBlank() ||
            !item.translation.isNullOrBlank() ||
            !item.reactions.isNullOrBlank() ||
            !item.userName.isNullOrBlank()
        precomputeExecutor.execute {
            val pack = TgTextLayoutPrecomputer.precompute(
                text = newText,
                time = item.time,
                fromMe = item.fromMe,
                containerWidth = recyclerWidth,
                density = resources.displayMetrics.density,
                hasExtraBlock = hasExtra,
                inlineTimeWithText = true
            )
            runOnUiThread {
                val idx = items.indexOfFirst { it is TgMessageItem.Text && it.id == item.id }
                if (idx >= 0) {
                    val old = items[idx] as TgMessageItem.Text
                    val updated = old.copy(text = newText, layoutPack = pack)
                    items[idx] = updated
                    adapter.submitList(items.toList())
                }
            }
        }
    }

    private fun setupInput() {
        binding.sendButton.setOnClickListener {
            val raw = binding.inputEdit.text?.toString().orEmpty()
            val text = normalizeOutgoingText(raw)
            val editId = editingMessageId

            if (editId != null) {
                binding.inputEdit.setText("")
                val index = items.indexOfFirst { it.id == editId && it is TgMessageItem.Text }
                if (index >= 0) {
                    val item = items[index] as TgMessageItem.Text
                    if (text != item.text) {
                        updateMessageText(item, text)
                    }
                }
                editingMessageId = null
                binding.sendButton.text = "发送"
            } else {
                val startSnapshot = captureInputTextSnapshot(text)
                binding.inputEdit.setText("")
                sendTextMessageWithFlyAnimation(text, startSnapshot)
            }
        }
        binding.demoAllButton.setOnClickListener {
            val options = arrayOf(
                "全部元素（用户名+引用+正文+翻译+点赞）",
                "纯文本",
                "用户名 + 正文",
                "用户名 + 引用 + 正文",
                "用户名 + 正文 + 翻译",
                "用户名 + 引用 + 正文 + 翻译 + 点赞",
                "图片消息（相册，可选 2 张）",
                "视频消息",
                "文件消息"
            )
            androidx.appcompat.app.AlertDialog.Builder(this)
                .setTitle("选择要发送的内容")
                .setItems(options) { _, which ->
                    if (which == 6) {
                        pickImageLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                        return@setItems
                    }
                    when (which) {
                        0 -> {
                            val text = "演示：Cell 中所有元素"
                            items.add(makeTextItem(text, true, formatTime(nextId), "Jane Cooper", "引用内容示例", "翻译内容示例", "👍 ❤️ 🎉"))
                        }
                        1 -> {
                            items.add(makeTextItem("纯文本消息", true, formatTime(nextId), null, null, null, null))
                        }
                        2 -> {
                            items.add(makeTextItem("用户名+正文", true, formatTime(nextId), "Jane Cooper", null, null, null))
                        }
                        3 -> {
                            items.add(makeTextItem("用户名+引用+正文", true, formatTime(nextId), "Jane Cooper", "引用内容示例", null, null))
                        }
                        4 -> {
                            items.add(makeTextItem("用户名+正文+翻译", true, formatTime(nextId), "Jane Cooper", null, "翻译内容示例", null))
                        }
                        5 -> {
                            items.add(makeTextItem("用户名+引用+正文+翻译+点赞", true, formatTime(nextId), "Jane Cooper", "引用内容示例", "翻译内容示例", "👍 ❤️ 🎉"))
                        }
                        7 -> {
                            items.add(TgMessageItem.Video(id = nextId++, fromMe = true, time = formatTime(nextId)))
                        }
                        8 -> {
                            items.add(TgMessageItem.File(id = nextId++, name = "demo.txt", size = "1 KB", fromMe = true, time = formatTime(nextId)))
                        }
                    }
                    adapter.submitList(items.toList()) {
                        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                    }
                }
                .show()
        }
    }

    private fun seedMessages() {
        items.add(makeTextItem("这个页面只保留 TG 文本消息的渲染逻辑", false, "09:41", "Ralph Edwards", "引用文本示例", "这是翻译文本的示例", "👍 ❤️ 🎉"))
        items.add(makeImageItem(fromMe = true, time = "09:42"))
        items.add(makeImageItem(fromMe = false, time = "09:42"))
        items.add(makeImageItem(fromMe = true, time = "11:53", albumDual = true))
        items.add(makeImageItem(fromMe = false, time = "09:43"))
        items.add(makeImageItem(fromMe = true, time = "09:44"))
        items.add(TgMessageItem.Video(id = nextId++, fromMe = false, time = "09:43"))
        items.add(TgMessageItem.File(id = nextId++, name = "design_spec.pdf", size = "2.4 MB", fromMe = true, time = "09:44"))
        items.add(makeTextItem("发送消息在右侧显示", true, "09:45", null, null, null, null))
        adapter.submitList(items.toList())
        binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
    }

    private fun sendTextMessageWithFlyAnimation(text: String, startSnapshot: InputTextSnapshot) {
        val item = makeTextItem(text, fromMe = true, formatTime(nextId), null, null, null, null)
        items.add(item)
        animatingMessageIds.add(item.id)
        adapter.hidePendingMessage(item.id)
        val pending = PendingSendAnimation(item.id, text, startSnapshot)
        adapter.submitList(items.toList()) {
            binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
            pendingSendQueue.addLast(pending)
            maybeProcessPendingSendAnimation()
        }
    }

    private fun maybeProcessPendingSendAnimation() {
        if (activeSendAnimation != null || pendingSendQueue.isEmpty()) return
        val pending = pendingSendQueue.first()
        val holder = binding.recyclerView.findViewHolderForItemId(pending.messageId) as? TgTextMessageAdapter.TextVH
        if (holder == null) {
            binding.recyclerView.postOnAnimation {
                if (activeSendAnimation == null && pendingSendQueue.isNotEmpty()) {
                    binding.recyclerView.scrollToPosition(adapter.itemCount - 1)
                    maybeProcessPendingSendAnimation()
                }
            }
            return
        }
        pendingSendQueue.removeFirst()
        activeSendAnimation = pending
        startPendingSendAnimationWhenReady(pending, holder)
    }

    private fun startPendingSendAnimationWhenReady(
        pending: PendingSendAnimation,
        holder: TgTextMessageAdapter.TextVH
    ) {
        if (!holder.itemView.isLaidOut || holder.itemView.width == 0 || holder.itemView.height == 0) {
            holder.itemView.viewTreeObserver.addOnPreDrawListener(object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    if (!holder.itemView.viewTreeObserver.isAlive) {
                        return true
                    }
                    holder.itemView.viewTreeObserver.removeOnPreDrawListener(this)
                    if (binding.recyclerView.findViewHolderForItemId(pending.messageId) === holder) {
                        startPendingSendAnimation(pending, holder)
                    } else {
                        finishPendingSendAnimation(pending, holder = null)
                    }
                    return true
                }
            })
            holder.itemView.invalidate()
            return
        }
        startPendingSendAnimation(pending, holder)
    }

    private fun startPendingSendAnimation(
        pending: PendingSendAnimation,
        holder: TgTextMessageAdapter.TextVH
    ) {
        val endTextRect = holder.cell.getTextContentRect().offsetInRoot(holder.cell, binding.root)
        val endBubbleRect = holder.cell.getBubbleRect().offsetInRoot(holder.cell, binding.root)
        holder.itemView.alpha = 0f
        holder.itemView.translationY = dp(10f)
        holder.itemView.scaleX = 0.985f
        holder.itemView.scaleY = 0.985f
        activeTextEnterTransition?.cancel()
        activeTextEnterTransition = TgTextMessageEnterTransition(
            rootView = binding.root,
            container = binding.sendAnimationOverlay,
            sourceTextRect = pending.startSnapshot.textRect,
            sourceBubbleRect = pending.startSnapshot.bubbleRect,
            targetTextRect = endTextRect,
            targetBubbleRect = endBubbleRect,
            text = pending.text,
            multiline = pending.startSnapshot.multiline,
            onProgress = { progress ->
                applyEnterViewTransitionState(progress)
                val reveal = ((progress - 0.58f) / 0.42f).coerceIn(0f, 1f)
                holder.itemView.alpha = reveal
                holder.itemView.translationY = dp(10f) * (1f - reveal)
                val scale = 0.985f + 0.015f * reveal
                holder.itemView.scaleX = scale
                holder.itemView.scaleY = scale
            },
            onFinished = {
                activeTextEnterTransition = null
                finishPendingSendAnimation(pending, holder)
            }
        ).also { transition ->
            transition.start()
        }
    }

    private fun finishPendingSendAnimation(
        pending: PendingSendAnimation,
        holder: TgTextMessageAdapter.TextVH?
    ) {
        applyEnterViewTransitionState(1f)
        adapter.showPendingMessage(pending.messageId)
        animatingMessageIds.remove(pending.messageId)
        holder?.itemView?.apply {
            alpha = 1f
            translationY = 0f
            scaleX = 1f
            scaleY = 1f
        }
        activeSendAnimation = null
        maybeProcessPendingSendAnimation()
    }

    private fun applyEnterViewTransitionState(progress: Float) {
        val clamped = progress.coerceIn(0f, 1f)
        val sendButtonProgress = (clamped / 0.4f).coerceIn(0f, 1f)
        binding.inputEdit.alpha = clamped
        binding.inputEdit.translationY = dp(6f) * (1f - clamped)
        binding.sendButton.alpha = sendButtonProgress
        binding.sendButton.translationX = dp(18f) * (1f - sendButtonProgress)
        binding.demoAllButton.alpha = 0.5f + 0.5f * sendButtonProgress
    }

    private fun captureInputTextSnapshot(text: String): InputTextSnapshot {
        val edit = binding.inputEdit
        val inputRect = edit.rectInRoot(binding.root)
        val innerLeft = inputRect.left + edit.totalPaddingLeft
        val innerTop = inputRect.top + edit.totalPaddingTop
        val innerRight = inputRect.right - edit.totalPaddingRight
        val innerBottom = inputRect.bottom - edit.totalPaddingBottom
        val layout = edit.layout

        if (layout == null) {
            val width = edit.paint.measureText(text).coerceAtLeast(dp(20f))
            val height = edit.lineHeight.toFloat().coerceAtLeast(dp(20f))
            val textRect = RectF(
                innerLeft,
                innerTop,
                min(innerRight, innerLeft + width),
                min(innerBottom, innerTop + height)
            )
            return InputTextSnapshot(textRect, expandBubbleRect(textRect), false)
        }

        val contentHeight = max(1, edit.height - edit.totalPaddingTop - edit.totalPaddingBottom)
        val firstVisibleLine = layout.getLineForVertical(edit.scrollY.coerceAtLeast(0))
        val lastVisibleLine = layout.getLineForVertical((edit.scrollY + contentHeight - 1).coerceAtLeast(0))
        var minLineLeft = Float.MAX_VALUE
        var maxLineRight = 0f
        for (line in firstVisibleLine..lastVisibleLine) {
            minLineLeft = min(minLineLeft, layout.getLineLeft(line))
            maxLineRight = max(maxLineRight, layout.getLineRight(line))
        }

        if (minLineLeft == Float.MAX_VALUE || maxLineRight <= minLineLeft) {
            val fallbackWidth = edit.paint.measureText(text).coerceAtLeast(dp(20f))
            maxLineRight = minLineLeft.takeIf { it != Float.MAX_VALUE } ?: 0f
            maxLineRight += fallbackWidth
            minLineLeft = 0f
        }

        val textLeft = (innerLeft - edit.scrollX + minLineLeft).coerceIn(innerLeft, innerRight)
        val textRight = (innerLeft - edit.scrollX + maxLineRight).coerceIn(textLeft + dp(1f), innerRight)
        val textTop = (innerTop - edit.scrollY + layout.getLineTop(firstVisibleLine)).coerceIn(innerTop, innerBottom)
        val textBottom = (innerTop - edit.scrollY + layout.getLineBottom(lastVisibleLine)).coerceIn(textTop + dp(1f), innerBottom)
        val textRect = RectF(textLeft, textTop, textRight, textBottom)
        return InputTextSnapshot(textRect, expandBubbleRect(textRect), firstVisibleLine != lastVisibleLine)
    }

    private fun expandBubbleRect(textRect: RectF): RectF = RectF(
        textRect.left - dp(12f),
        textRect.top - dp(8f),
        textRect.right + dp(16f),
        textRect.bottom + dp(8f)
    )

    private fun normalizeOutgoingText(raw: String): String {
        val normalized = raw.replace("\r\n", "\n")
        if (normalized.isBlank()) return "空消息"
        val trimmed = normalized.trimEnd('\n', '\r')
        return if (trimmed.isBlank()) "空消息" else trimmed
    }

    private fun View.rectInRoot(root: View): RectF {
        val rootLocation = IntArray(2)
        val viewLocation = IntArray(2)
        root.getLocationOnScreen(rootLocation)
        getLocationOnScreen(viewLocation)
        val left = viewLocation[0] - rootLocation[0].toFloat()
        val top = viewLocation[1] - rootLocation[1].toFloat()
        return RectF(left, top, left + width.toFloat(), top + height.toFloat())
    }

    private fun RectF.offsetInRoot(sourceView: View, root: View): RectF {
        val sourceRect = sourceView.rectInRoot(root)
        return RectF(
            sourceRect.left + left,
            sourceRect.top + top,
            sourceRect.left + right,
            sourceRect.top + bottom
        )
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun makeTextItem(
        text: String,
        fromMe: Boolean,
        time: String,
        userName: String?,
        quote: String?,
        translation: String?,
        reactions: String?
    ): TgMessageItem.Text {
        val id = nextId++
        val hasExtra = !quote.isNullOrBlank() ||
            !translation.isNullOrBlank() ||
            !reactions.isNullOrBlank() ||
            !userName.isNullOrBlank()
        if (recyclerWidth > 0) {
            schedulePrecompute(id, text, fromMe, time, hasExtra)
        }
        return TgMessageItem.Text(
            id = id,
            text = text,
            fromMe = fromMe,
            time = time,
            userName = userName,
            quote = quote,
            translation = translation,
            reactions = reactions,
            layoutPack = null
        )
    }

    private fun makeImageItem(
        fromMe: Boolean,
        time: String,
        imageUri: Uri? = null,
        secondImageUri: Uri? = null,
        albumDual: Boolean = false
    ) = TgMessageItem.Image(
        id = nextId++,
        fromMe = fromMe,
        time = time,
        imageUri = imageUri,
        secondImageUri = secondImageUri,
        albumDual = albumDual
    )

    private fun schedulePrecompute(id: Long, text: String, fromMe: Boolean, time: String, hasExtra: Boolean) {
        val containerWidth = recyclerWidth
        val density = resources.displayMetrics.density
        precomputeExecutor.execute {
            val pack = TgTextLayoutPrecomputer.precompute(
                text = text,
                time = time,
                fromMe = fromMe,
                containerWidth = containerWidth,
                density = density,
                hasExtraBlock = hasExtra,
                inlineTimeWithText = true
            )
            runOnUiThread {
                val idx = items.indexOfFirst { it is TgMessageItem.Text && it.id == id }
                if (idx >= 0) {
                    val old = items[idx] as TgMessageItem.Text
                    val updated = old.copy(text = text, layoutPack = pack)
                    items[idx] = updated
                    adapter.submitList(items.toList())
                }
            }
        }
    }

    private fun formatTime(seed: Long): String {
        val minute = (seed % 60).toInt().toString().padStart(2, '0')
        val hour = ((seed / 60) % 24).toInt().toString().padStart(2, '0')
        return "$hour:$minute"
    }

    companion object {
        fun newInstance(context: Context) {
            context.startActivity(Intent(context, TgTextChatActivity::class.java))
        }
    }
}
