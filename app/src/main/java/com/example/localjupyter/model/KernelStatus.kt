package com.example.localjupyter.model

enum class KernelStatus {
    IDLE,
    BUSY,
    STARTING,
    RESTARTING,
    DEAD
}

data class KernelInfo(
    val id: String = "local-python3",
    val name: String = "python3",
    val displayName: String = "Python 3 (LocalJupyter)",
    val status: KernelStatus = KernelStatus.IDLE,
    val executionCount: Int = 0,
    val isRunning: Boolean = false
)
