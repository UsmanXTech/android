package com.example

import com.example.localjupyter.kernel.LocalPythonInterpreter
import com.example.localjupyter.kernel.scientific.NumpyModule
import com.example.localjupyter.kernel.scientific.PandasModule
import com.example.localjupyter.model.Cell
import com.example.localjupyter.model.CellOutput
import com.example.localjupyter.model.CellType
import com.example.localjupyter.model.Notebook
import com.example.localjupyter.model.NotebookMetadata
import com.example.localjupyter.parser.NotebookSerializer
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LocalJupyterKernelTest {

    private lateinit var interpreter: LocalPythonInterpreter

    @Before
    fun setUp() {
        interpreter = LocalPythonInterpreter()
    }

    @Test
    fun testPersistentExecutionAcrossCells() = runBlocking {
        // Cell 1: Define variable
        val result1 = interpreter.execute(
            code = """
                import numpy as np
                x = np.arange(1, 6)
            """.trimIndent(),
            cellId = "cell-1"
        ) {}
        assertEquals("ok", result1.status)
        assertEquals(1, result1.executionCount)

        // Cell 2: Use variable 'x' from Cell 1
        var capturedOutput: CellOutput? = null
        val result2 = interpreter.execute(
            code = """
                y = x ** 2
                y
            """.trimIndent(),
            cellId = "cell-2"
        ) { capturedOutput = it }

        assertEquals("ok", result2.status)
        assertEquals(2, result2.executionCount)
        assertNotNull(capturedOutput)

        val execResult = capturedOutput as? CellOutput.ExecuteResult
        assertNotNull("Should produce execute_result for final expression", execResult)
        val text = execResult?.data?.get("text/plain")?.toString() ?: ""
        assertTrue("Output should contain 1, 4, 9, 16, 25: $text", text.contains("1, 4, 9, 16, 25"))
    }

    @Test
    fun testFinalExpressionEvaluation() = runBlocking {
        var output: CellOutput? = null
        val result = interpreter.execute(
            code = """
                a = 15
                b = 27
                a + b
            """.trimIndent(),
            cellId = "eval-cell"
        ) { output = it }

        assertEquals("ok", result.status)
        val execResult = output as? CellOutput.ExecuteResult
        assertEquals("42", execResult?.data?.get("text/plain")?.toString()?.trim())
    }

    @Test
    fun testStdoutStreamCapture() = runBlocking {
        val outputs = mutableListOf<CellOutput>()
        val result = interpreter.execute(
            code = "print('Hello LocalJupyter!')",
            cellId = "print-cell"
        ) { outputs.add(it) }

        assertEquals("ok", result.status)
        val stream = outputs.filterIsInstance<CellOutput.Stream>().firstOrNull()
        assertNotNull(stream)
        assertEquals("stdout", stream?.name)
        assertTrue(stream?.text?.contains("Hello LocalJupyter!") == true)
    }

    @Test
    fun testExceptionHandling() = runBlocking {
        val outputs = mutableListOf<CellOutput>()
        val result = interpreter.execute(
            code = "undefined_variable + 10",
            cellId = "error-cell"
        ) { outputs.add(it) }

        assertEquals("error", result.status)
        val error = outputs.filterIsInstance<CellOutput.Error>().firstOrNull()
        assertNotNull(error)
        assertTrue(error?.evalue?.contains("undefined_variable") == true)
    }

    @Test
    fun testKernelRestartClearsVariables() = runBlocking {
        interpreter.execute("secret_num = 999", "c1") {}
        assertEquals(999, interpreter.getVariableValue("secret_num"))

        interpreter.restart()
        assertNull(interpreter.getVariableValue("secret_num"))
        assertEquals(0, interpreter.executionCount.value)
    }

    @Test
    fun testNumPyMathAndVectors() {
        val np = NumpyModule()
        val arr = np.arange(0.0, 5.0, 1.0)
        assertEquals(5, arr.size)
        assertEquals(1, arr.ndim)

        val sinArr = np.sin(arr) as NumpyModule.NDArray
        assertEquals(5, sinArr.size)
        assertEquals(0.0, sinArr.data[0], 1e-4)

        val squared = arr * arr
        assertEquals(16.0, squared.data[4], 1e-4)
    }

    @Test
    fun testPandasDataFrameAndHtmlOutput() {
        val pd = PandasModule()
        val df = pd.DataFrame(
            mapOf(
                "A" to listOf(10, 20, 30),
                "B" to listOf("x", "y", "z")
            )
        )
        assertEquals(3, df.rowCount)
        assertEquals(2, df.columns.size)

        val html = df.toHtml()
        assertTrue("HTML must contain table tag", html.contains("<table"))
        assertTrue("HTML must contain column A", html.contains("A</th>"))
        assertTrue("HTML must contain column B", html.contains("B</th>"))
    }

    @Test
    fun testNotebookJsonSerializationRoundTrip() {
        val originalNotebook = Notebook(
            cells = listOf(
                Cell(
                    id = "c1",
                    cellType = CellType.MARKDOWN,
                    source = "# Heading 1\nSome description"
                ),
                Cell(
                    id = "c2",
                    cellType = CellType.CODE,
                    source = "x = 42\nx",
                    executionCount = 1,
                    outputs = listOf(
                        CellOutput.ExecuteResult(
                            executionCount = 1,
                            data = mapOf("text/plain" to "42")
                        )
                    )
                )
            ),
            metadata = NotebookMetadata(
                additionalFields = mapOf("custom_author" to "Tester")
            )
        )

        val json = NotebookSerializer.serialize(originalNotebook)
        val parsed = NotebookSerializer.parse(json)

        assertEquals(2, parsed.cells.size)
        assertEquals(CellType.MARKDOWN, parsed.cells[0].cellType)
        assertEquals(CellType.CODE, parsed.cells[1].cellType)
        assertEquals("Tester", parsed.metadata.additionalFields["custom_author"])
        assertEquals(1, parsed.cells[1].executionCount)
        assertEquals(1, parsed.cells[1].outputs.size)
    }
}
