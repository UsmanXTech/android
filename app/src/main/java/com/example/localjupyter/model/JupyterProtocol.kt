package com.example.localjupyter.model

import java.util.UUID

/**
 * Jupyter Messaging Protocol v5.4 models.
 * Implements typed message framing, parent header tracking,
 * and correlation between request and reply.
 */
enum class Channel {
    SHELL,
    IOPUB,
    STDIN,
    CONTROL,
    HEARTBEAT
}

data class MsgHeader(
    val msgId: String = UUID.randomUUID().toString(),
    val username: String = "localjupyter",
    val session: String = UUID.randomUUID().toString(),
    val msgType: String,
    val version: String = "5.4",
    val date: String = System.currentTimeMillis().toString()
)

data class JupyterMessage<T>(
    val header: MsgHeader,
    val parentHeader: MsgHeader? = null,
    val metadata: Map<String, Any?> = emptyMap(),
    val content: T,
    val channel: Channel = Channel.SHELL
)

// Standard Jupyter message content types

data class KernelInfoReplyContent(
    val protocolVersion: String = "5.4",
    val implementation: String = "LocalJupyter",
    val implementationVersion: String = "1.0.0",
    val languageInfo: Map<String, Any?> = mapOf(
        "name" to "python",
        "version" to "3.11.0",
        "mimetype" to "text/x-python",
        "file_extension" to ".py"
    ),
    val banner: String = "LocalJupyter Embedded Python 3 Kernel\nStandalone, ad-free, local scientific computing."
)

data class ExecuteRequestContent(
    val code: String,
    val silent: Boolean = false,
    val storeHistory: Boolean = true,
    val userExpressions: Map<String, String> = emptyMap(),
    val allowStdin: Boolean = false,
    val stopOnError: Boolean = true
)

data class ExecuteReplyContent(
    val status: String, // "ok", "error", "abort"
    val executionCount: Int,
    val payload: List<Map<String, Any?>> = emptyList(),
    val userExpressions: Map<String, Any?> = emptyMap(),
    val ename: String? = null,
    val evalue: String? = null,
    val traceback: List<String>? = null
)

data class ExecuteInputContent(
    val code: String,
    val executionCount: Int
)

data class StatusContent(
    val executionState: String // "busy", "idle", "starting"
)

data class StreamContent(
    val name: String, // "stdout", "stderr"
    val text: String
)

data class ExecuteResultContent(
    val executionCount: Int,
    val data: Map<String, Any?>,
    val metadata: Map<String, Any?> = emptyMap()
)

data class DisplayDataContent(
    val data: Map<String, Any?>,
    val metadata: Map<String, Any?> = emptyMap(),
    val transient: Map<String, Any?> = emptyMap()
)

data class ClearOutputContent(
    val wait: Boolean = false
)

data class ErrorContent(
    val ename: String,
    val evalue: String,
    val traceback: List<String>
)

data class CompleteRequestContent(
    val code: String,
    val cursorPos: Int
)

data class CompleteReplyContent(
    val matches: List<String>,
    val cursorStart: Int,
    val cursorEnd: Int,
    val metadata: Map<String, Any?> = emptyMap(),
    val status: String = "ok"
)

data class InspectRequestContent(
    val code: String,
    val cursorPos: Int,
    val detailLevel: Int = 0
)

data class InspectReplyContent(
    val status: String = "ok",
    val found: Boolean,
    val data: Map<String, Any?> = emptyMap(),
    val metadata: Map<String, Any?> = emptyMap()
)

data class IsCompleteReplyContent(
    val status: String, // "complete", "incomplete", "invalid", "unknown"
    val indent: String = ""
)
