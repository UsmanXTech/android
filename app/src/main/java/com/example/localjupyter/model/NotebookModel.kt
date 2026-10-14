package com.example.localjupyter.model

import java.util.UUID

/**
 * Jupyter Notebook Format v4 Model.
 * Complies with the official nbformat v4 specification:
 * https://nbformat.readthedocs.io/en/latest/format_description.html
 */
data class Notebook(
    val cells: List<Cell> = emptyList(),
    val metadata: NotebookMetadata = NotebookMetadata(),
    val nbformat: Int = 4,
    val nbformatMinor: Int = 5,
    val unknownFields: Map<String, Any?> = emptyMap()
)

data class NotebookMetadata(
    val kernelSpec: KernelSpec = KernelSpec(),
    val languageInfo: LanguageInfo = LanguageInfo(),
    val authors: List<Map<String, Any?>> = emptyList(),
    val additionalFields: Map<String, Any?> = emptyMap()
)

data class KernelSpec(
    val name: String = "python3",
    val displayName: String = "Python 3 (LocalJupyter)",
    val language: String = "python"
)

data class LanguageInfo(
    val name: String = "python",
    val version: String = "3.11.0",
    val mimetype: String = "text/x-python",
    val fileExtension: String = ".py",
    val pygmentsLexer: String = "ipython3"
)

enum class CellType {
    CODE,
    MARKDOWN,
    RAW
}

data class Cell(
    val id: String = UUID.randomUUID().toString(),
    val cellType: CellType = CellType.CODE,
    val source: String = "",
    val executionCount: Int? = null,
    val outputs: List<CellOutput> = emptyList(),
    val metadata: Map<String, Any?> = emptyMap(),
    // Transient UI state
    val isRunning: Boolean = false,
    val isEditing: Boolean = true,
    val isOutputCollapsed: Boolean = false
)

enum class OutputType {
    EXECUTE_RESULT,
    DISPLAY_DATA,
    STREAM,
    ERROR
}

sealed class CellOutput(val outputType: OutputType) {
    data class Stream(
        val name: String, // "stdout" or "stderr"
        val text: String
    ) : CellOutput(OutputType.STREAM)

    data class ExecuteResult(
        val executionCount: Int,
        val data: Map<String, Any?>, // MIME bundle (text/plain, text/html, image/png, etc.)
        val metadata: Map<String, Any?> = emptyMap()
    ) : CellOutput(OutputType.EXECUTE_RESULT)

    data class DisplayData(
        val data: Map<String, Any?>, // MIME bundle
        val metadata: Map<String, Any?> = emptyMap()
    ) : CellOutput(OutputType.DISPLAY_DATA)

    data class Error(
        val ename: String,
        val evalue: String,
        val traceback: List<String>
    ) : CellOutput(OutputType.ERROR)
}
