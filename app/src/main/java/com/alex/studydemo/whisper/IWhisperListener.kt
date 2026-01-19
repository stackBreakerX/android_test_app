package com.alex.studydemo.whisper

interface IWhisperListener {
    fun onUpdateReceived(message: String)
    fun onResultReceived(result: String)
}

