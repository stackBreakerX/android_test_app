package com.alex.studydemo.module_flow

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.alex.studydemo.base.BaseActivity
import com.alex.studydemo.databinding.ActivityFlowCompareBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class FlowCompareActivity : BaseActivity<ActivityFlowCompareBinding>() {

    private val sharedFlowReplay0 = MutableSharedFlow<Int>(replay = 0, extraBufferCapacity = 0)
    private val sharedFlowReplay1 = MutableSharedFlow<Int>(replay = 1, extraBufferCapacity = 0)
    private val stateFlow = MutableStateFlow(0)

    private val collectorJobs = mutableListOf<Job>()

    private var seq = 0
    private var sharedReplay0CollectorId = 0
    private var sharedReplay1CollectorId = 0
    private var stateCollectorId = 0

    private val timeFormatter = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    companion object {
        fun newInstance(context: Context) {
            context.startActivity(Intent(context, FlowCompareActivity::class.java))
        }
    }

    override fun inflateBinding(inflater: LayoutInflater): ActivityFlowCompareBinding =
        ActivityFlowCompareBinding.inflate(inflater)

    override fun onViewCreated(savedInstanceState: Bundle?) {
        title = "Flow 对比"

        binding.btnEmitOnce.setOnClickListener { emitOnce() }
        binding.btnTryEmitBurst.setOnClickListener { tryEmitBurst() }
        binding.btnEmitBurst.setOnClickListener { emitBurstSuspend() }

        binding.btnAddSharedCollector.setOnClickListener { addSharedCollector(replay = 0) }
        binding.btnAddSharedReplayCollector.setOnClickListener { addSharedCollector(replay = 1) }
        binding.btnAddStateCollector.setOnClickListener { addStateCollector() }

        binding.btnCancelCollectors.setOnClickListener { cancelCollectors() }
        binding.btnClearLog.setOnClickListener { binding.tvLog.text = "" }

        renderState()
        log("ready: SharedFlow(replay=0/1) & StateFlow(initial=0)")
    }

    override fun onDestroy() {
        cancelCollectors()
        super.onDestroy()
    }

    private fun emitOnce() {
        val v = ++seq
        val ok0 = sharedFlowReplay0.tryEmit(v)
        val ok1 = sharedFlowReplay1.tryEmit(v)
        stateFlow.value = v
        renderState()
        log("emit v=$v | shared(r0).tryEmit=$ok0 | shared(r1).tryEmit=$ok1 | state.value=$v")
    }

    private fun tryEmitBurst() {
        lifecycleScope.launch {
            log("tryEmit burst start (1..20)")
            repeat(20) {
                val v = ++seq
                val ok0 = sharedFlowReplay0.tryEmit(v)
                val ok1 = sharedFlowReplay1.tryEmit(v)
                stateFlow.value = v
                renderState()
                log("tryEmit v=$v | shared(r0)=$ok0 | shared(r1)=$ok1 | state=$v")
                delay(10)
            }
            log("tryEmit burst end")
        }
    }

    private fun emitBurstSuspend() {
        lifecycleScope.launch {
            log("emit burst start (1..20)")
            repeat(20) {
                val v = ++seq
                sharedFlowReplay0.emit(v)
                sharedFlowReplay1.emit(v)
                stateFlow.value = v
                renderState()
                log("emit v=$v")
                delay(10)
            }
            log("emit burst end")
        }
    }

    private fun addSharedCollector(replay: Int) {
        val (name, id, flow) = when (replay) {
            0 -> Triple("SharedFlow(replay=0)", ++sharedReplay0CollectorId, sharedFlowReplay0)
            else -> Triple("SharedFlow(replay=1)", ++sharedReplay1CollectorId, sharedFlowReplay1)
        }

        val job = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                log("$name collector#$id start")
                flow.collect { value ->
                    log("$name collector#$id <- $value")
                    if (binding.cbSlowCollector.isChecked) delay(120)
                }
            }
        }
        collectorJobs += job
        log("added $name collector#$id")
    }

    private fun addStateCollector() {
        val id = ++stateCollectorId
        val job = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                log("StateFlow collector#$id start")
                stateFlow.collect { value ->
                    binding.tvStateValue.text = "StateFlow 当前值：$value"
                    log("StateFlow collector#$id <- $value")
                    if (binding.cbSlowCollector.isChecked) delay(120)
                }
            }
        }
        collectorJobs += job
        log("added StateFlow collector#$id")
    }

    private fun cancelCollectors() {
        if (collectorJobs.isEmpty()) return
        collectorJobs.forEach { it.cancel() }
        collectorJobs.clear()
        log("all collectors cancelled")
    }

    private fun renderState() {
        binding.tvStateValue.text = "StateFlow 当前值：${stateFlow.value}"
    }

    private fun log(message: String) {
        val ts = timeFormatter.format(Date())
        val line = "$ts $message"

        val prev = binding.tvLog.text?.toString().orEmpty()
        val merged = if (prev.isEmpty()) line else "$prev\n$line"
        binding.tvLog.text = if (merged.length > 8000) merged.takeLast(8000) else merged
    }
}
