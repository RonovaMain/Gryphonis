package dev.ronova.gryphonis.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class TransferState(
    val title: String,
    val current: String = "",
    val done: Long = 0,
    val total: Long = 0,
    val bytesMode: Boolean = true,
    val filesDone: Int = 0,
    val filesTotal: Int = 0,
    val speed: Long = 0,
    val etaSeconds: Long = -1,
    val error: String? = null,
) {
    val fraction: Float get() = if (total > 0) (done.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
}

sealed interface TransferResult {
    data object Success : TransferResult
    data object Cancelled : TransferResult
    data class Failed(val message: String) : TransferResult
}

/** Передаётся в долгие операции: считает прогресс, скорость и оставшееся время. */
class ProgressTracker(
    private val job: Job,
    private val title: String,
    private val bytesMode: Boolean,
    private val publish: (TransferState) -> Unit,
) {
    private var total = 0L
    private var done = 0L
    private var filesTotal = 0
    private var filesDone = 0
    private var current = ""

    private val startNs = System.nanoTime()
    private var lastPublishNs = 0L
    private var lastSpeedNs = startNs
    private var lastSpeedDone = 0L
    private var speed = 0.0

    fun check() = job.ensureActive()

    fun begin(total: Long, files: Int) {
        this.total = total
        this.filesTotal = files
        push(true)
    }

    fun file(name: String) {
        check()
        current = name
        push(true)
    }

    fun fileDone(n: Int = 1) {
        filesDone += n
        push(false)
    }

    fun add(n: Long) {
        done += n
        check()
        push(false)
    }

    private fun push(force: Boolean) {
        val now = System.nanoTime()
        if (!force && now - lastPublishNs < 120_000_000L) return
        val dt = (now - lastSpeedNs) / 1e9
        if (dt >= 0.4) {
            val inst = (done - lastSpeedDone) / dt
            speed = if (speed == 0.0) inst else speed * 0.7 + inst * 0.3
            lastSpeedDone = done
            lastSpeedNs = now
        }
        val remaining = total - done
        val eta = if (speed > 0.5 && total > 0 && remaining >= 0) (remaining / speed).toLong() else -1L
        lastPublishNs = now
        publish(
            TransferState(
                title = title,
                current = current,
                done = done,
                total = total,
                bytesMode = bytesMode,
                filesDone = filesDone,
                filesTotal = filesTotal,
                speed = speed.toLong(),
                etaSeconds = eta,
            )
        )
    }
}

object TransferManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow<TransferState?>(null)
    val state: StateFlow<TransferState?> = _state.asStateFlow()
    private var job: Job? = null

    val busy: Boolean get() = job?.isActive == true

    /** Запускает операцию. Возвращает false, если уже идёт другая. */
    fun run(
        title: String,
        bytesMode: Boolean = true,
        onResult: (TransferResult) -> Unit = {},
        block: (ProgressTracker) -> Unit,
    ): Boolean {
        if (busy) return false
        _state.value = TransferState(title = title, bytesMode = bytesMode)
        job = scope.launch {
            val tracker = ProgressTracker(this.coroutineContext[Job]!!, title, bytesMode) { _state.value = it }
            try {
                block(tracker)
                _state.value = null
                onResult(TransferResult.Success)
            } catch (e: CancellationException) {
                _state.value = null
                onResult(TransferResult.Cancelled)
            } catch (e: Throwable) {
                val msg = e.message ?: e.javaClass.simpleName
                _state.value = TransferState(title = title, bytesMode = bytesMode, error = msg)
                onResult(TransferResult.Failed(msg))
            }
        }
        return true
    }

    fun cancel() {
        job?.cancel()
    }

    fun dismissError() {
        if (_state.value?.error != null) _state.value = null
    }
}
