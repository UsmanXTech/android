package com.example.localjupyter.kernel

import com.example.localjupyter.model.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel as CoroutineChannel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Jupyter Kernel Client that speaks the Jupyter Messaging Protocol v5.4.
 * Coordinates execution queues, tracks parent headers, emits IOPub events,
 * and maintains protocol state.
 */
class JupyterKernelClient(
    val engine: PythonKernelEngine,
    private val scope: CoroutineScope
) {
    private val session = UUID.randomUUID().toString()

    private val _ioPubMessages = MutableSharedFlow<JupyterMessage<*>>(extraBufferCapacity = 64)
    val ioPubMessages: SharedFlow<JupyterMessage<*>> = _ioPubMessages.asSharedFlow()

    private val executionQueue = CoroutineChannel<Pair<JupyterMessage<ExecuteRequestContent>, (JupyterMessage<ExecuteReplyContent>) -> Unit>>(CoroutineChannel.UNLIMITED)

    init {
        scope.launch(Dispatchers.Default) {
            for ((request, callback) in executionQueue) {
                processExecuteRequest(request, callback)
            }
        }
    }

    suspend fun sendExecuteRequest(
        code: String,
        onReply: (JupyterMessage<ExecuteReplyContent>) -> Unit
    ): JupyterMessage<ExecuteRequestContent> {
        val header = MsgHeader(
            msgId = UUID.randomUUID().toString(),
            session = session,
            msgType = "execute_request"
        )
        val msg = JupyterMessage(
            header = header,
            content = ExecuteRequestContent(code = code),
            channel = Channel.SHELL
        )
        executionQueue.send(msg to onReply)
        return msg
    }

    private suspend fun processExecuteRequest(
        request: JupyterMessage<ExecuteRequestContent>,
        callback: (JupyterMessage<ExecuteReplyContent>) -> Unit
    ) {
        val parentHeader = request.header

        // Emit status: busy on IOPub
        emitIOPub(
            msgType = "status",
            content = StatusContent(executionState = "busy"),
            parentHeader = parentHeader
        )

        // Emit execute_input on IOPub
        val currentCount = engine.executionCount.value + 1
        emitIOPub(
            msgType = "execute_input",
            content = ExecuteInputContent(code = request.content.code, executionCount = currentCount),
            parentHeader = parentHeader
        )

        // Execute via engine
        val summary = engine.execute(request.content.code, parentHeader.msgId) { cellOutput ->
            when (cellOutput) {
                is CellOutput.Stream -> {
                    emitIOPub(
                        msgType = "stream",
                        content = StreamContent(name = cellOutput.name, text = cellOutput.text),
                        parentHeader = parentHeader
                    )
                }
                is CellOutput.ExecuteResult -> {
                    emitIOPub(
                        msgType = "execute_result",
                        content = ExecuteResultContent(
                            executionCount = cellOutput.executionCount,
                            data = cellOutput.data,
                            metadata = cellOutput.metadata
                        ),
                        parentHeader = parentHeader
                    )
                }
                is CellOutput.DisplayData -> {
                    emitIOPub(
                        msgType = "display_data",
                        content = DisplayDataContent(
                            data = cellOutput.data,
                            metadata = cellOutput.metadata
                        ),
                        parentHeader = parentHeader
                    )
                }
                is CellOutput.Error -> {
                    emitIOPub(
                        msgType = "error",
                        content = ErrorContent(
                            ename = cellOutput.ename,
                            evalue = cellOutput.evalue,
                            traceback = cellOutput.traceback
                        ),
                        parentHeader = parentHeader
                    )
                }
            }
        }

        // Emit status: idle on IOPub
        emitIOPub(
            msgType = "status",
            content = StatusContent(executionState = "idle"),
            parentHeader = parentHeader
        )

        // Reply on Shell channel
        val replyHeader = MsgHeader(
            session = session,
            msgType = "execute_reply"
        )
        val reply = JupyterMessage(
            header = replyHeader,
            parentHeader = parentHeader,
            content = ExecuteReplyContent(
                status = summary.status,
                executionCount = summary.executionCount,
                ename = summary.errorName,
                evalue = summary.errorMessage,
                traceback = summary.traceback
            ),
            channel = Channel.SHELL
        )
        callback(reply)
    }

    suspend fun kernelInfoRequest(): JupyterMessage<KernelInfoReplyContent> {
        val header = MsgHeader(session = session, msgType = "kernel_info_reply")
        return JupyterMessage(
            header = header,
            content = KernelInfoReplyContent(),
            channel = Channel.SHELL
        )
    }

    suspend fun completeRequest(code: String, cursorPos: Int): JupyterMessage<CompleteReplyContent> {
        val matches = engine.complete(code, cursorPos)
        val header = MsgHeader(session = session, msgType = "complete_reply")
        return JupyterMessage(
            header = header,
            content = CompleteReplyContent(
                matches = matches,
                cursorStart = cursorPos - 1,
                cursorEnd = cursorPos
            ),
            channel = Channel.SHELL
        )
    }

    suspend fun inspectRequest(code: String, cursorPos: Int): JupyterMessage<InspectReplyContent> {
        val doc = engine.inspect(code, cursorPos)
        val header = MsgHeader(session = session, msgType = "inspect_reply")
        val data = if (doc != null) mapOf("text/plain" to doc) else emptyMap<String, Any?>()
        return JupyterMessage(
            header = header,
            content = InspectReplyContent(found = doc != null, data = data),
            channel = Channel.SHELL
        )
    }

    private fun <T> emitIOPub(msgType: String, content: T, parentHeader: MsgHeader) {
        val header = MsgHeader(session = session, msgType = msgType)
        val msg = JupyterMessage(
            header = header,
            parentHeader = parentHeader,
            content = content,
            channel = Channel.IOPUB
        )
        _ioPubMessages.tryEmit(msg)
    }
}
