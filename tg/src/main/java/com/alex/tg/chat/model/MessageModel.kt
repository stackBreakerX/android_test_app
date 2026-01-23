package com.alex.tg.chat.model

data class MessageModel(
    val id: Long,
    val type: Type,
    val text: String? = null,
    val mediaWidth: Int = 0,
    val mediaHeight: Int = 0,
    val time: String,
    val fromUser: String? = null,
    val isOut: Boolean = false,
    val replyText: String? = null,
    val translateText: String? = null,
    val reactions: List<String> = emptyList()
) {
    enum class Type { TEXT, IMAGE, VIDEO, FILE }
}
