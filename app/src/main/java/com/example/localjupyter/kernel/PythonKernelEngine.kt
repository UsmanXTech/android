package com.example.localjupyter.kernel

import com.example.localjupyter.model.CellOutput
import com.example.localjupyter.model.KernelStatus
import kotlinx.coroutines.flow.StateFlow

data class ExecutionResultSummary(
    val status: String, // "ok" or "error"
    val executionCount: Int,
    val outputs: List<CellOutput>,
    val errorName: String? = null,
    val errorMessage: String? = null,
    val traceback: List<String>? = null
)

interface PythonKernelEngine {
    val status: StateFlow<KernelStatus>
    val executionCount: StateFlow<Int>

    suspend fun initialize()
    
    suspend fun execute(
        code: String,
        cellId: String,
        onOutput: (CellOutput) -> Unit
    ): ExecutionResultSummary

    suspend fun complete(code: String, cursorPosition: Int): List<String>
    suspend fun inspect(code: String, cursorPosition: Int): String?
    suspend fun isComplete(code: String): Boolean

    suspend fun interrupt()
    suspend fun restart()
    suspend fun shutdown()

    fun getVariableNames(): List<String>
    fun getVariableValue(name: String): Any?
}
