package com.example.localjupyter.kernel

import com.example.localjupyter.kernel.scientific.MatplotlibModule
import com.example.localjupyter.kernel.scientific.NumpyModule
import com.example.localjupyter.kernel.scientific.PandasModule
import com.example.localjupyter.model.CellOutput
import com.example.localjupyter.model.KernelStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlin.math.*

/**
 * Local persistent Python kernel engine.
 * Maintains persistent namespace across cells, executes multi-statement code cells,
 * evaluates expressions, captures stdout/stderr, and returns rich MIME bundles.
 */
class LocalPythonInterpreter : PythonKernelEngine {

    private val _status = MutableStateFlow(KernelStatus.IDLE)
    override val status: StateFlow<KernelStatus> = _status.asStateFlow()

    private val _executionCount = MutableStateFlow(0)
    override val executionCount: StateFlow<Int> = _executionCount.asStateFlow()

    private val globals: MutableMap<String, Any?> = mutableMapOf()
    private val np = NumpyModule()
    private val pd = PandasModule()
    private lateinit var plt: MatplotlibModule.Pyplot

    private var activeOutputCollector: ((CellOutput) -> Unit)? = null

    init {
        resetNamespace()
    }

    override suspend fun initialize() {
        _status.value = KernelStatus.STARTING
        resetNamespace()
        _status.value = KernelStatus.IDLE
    }

    private fun resetNamespace() {
        globals.clear()
        plt = MatplotlibModule.Pyplot { mimeBundle ->
            activeOutputCollector?.invoke(
                CellOutput.DisplayData(
                    data = mimeBundle,
                    metadata = emptyMap()
                )
            )
        }

        // Built-in modules
        globals["np"] = np
        globals["numpy"] = np
        globals["plt"] = plt
        globals["matplotlib"] = object { val pyplot = plt }
        globals["pd"] = pd
        globals["pandas"] = pd
        globals["math"] = object {
            val pi = kotlin.math.PI
            val e = kotlin.math.E
            fun sqrt(x: Double) = kotlin.math.sqrt(x)
            fun sin(x: Double) = kotlin.math.sin(x)
            fun cos(x: Double) = kotlin.math.cos(x)
            fun tan(x: Double) = kotlin.math.tan(x)
            fun exp(x: Double) = kotlin.math.exp(x)
            fun log(x: Double) = kotlin.math.ln(x)
            fun floor(x: Double) = kotlin.math.floor(x)
            fun ceil(x: Double) = kotlin.math.ceil(x)
            fun pow(x: Double, y: Double) = x.pow(y)
        }
        globals["random"] = object {
            fun random() = kotlin.random.Random.nextDouble()
            fun randint(a: Int, b: Int) = kotlin.random.Random.nextInt(a, b + 1)
            fun choice(list: List<Any?>) = if (list.isNotEmpty()) list[kotlin.random.Random.nextInt(list.size)] else null
        }
        globals["json"] = object {
            fun dumps(obj: Any?): String = formatJson(obj)
        }
        globals["sys"] = object {
            val version = "3.11.0 (LocalJupyter Android Embedded)"
            val platform = "android"
        }
    }

    override suspend fun execute(
        code: String,
        cellId: String,
        onOutput: (CellOutput) -> Unit
    ): ExecutionResultSummary = withContext(Dispatchers.Default) {
        _status.value = KernelStatus.BUSY
        activeOutputCollector = onOutput
        val outputs = mutableListOf<CellOutput>()

        fun emitOutput(output: CellOutput) {
            outputs.add(output)
            onOutput(output)
        }

        val currentCount = _executionCount.value + 1
        _executionCount.value = currentCount

        try {
            val trimmed = code.trim()
            if (trimmed.isEmpty()) {
                _status.value = KernelStatus.IDLE
                return@withContext ExecutionResultSummary(
                    status = "ok",
                    executionCount = currentCount,
                    outputs = emptyList()
                )
            }

            // Execute lines/blocks
            val lines = trimmed.lines()
            var i = 0
            while (i < lines.size) {
                val line = lines[i].trim()
                if (line.isEmpty() || line.startsWith("#")) {
                    i++
                    continue
                }

                // Check for block structures (def, for, while, if)
                if (line.startsWith("def ") || line.startsWith("for ") || line.startsWith("while ") || line.startsWith("if ")) {
                    val blockLines = mutableListOf(lines[i])
                    i++
                    while (i < lines.size && (lines[i].startsWith(" ") || lines[i].startsWith("\t") || lines[i].isBlank())) {
                        blockLines.add(lines[i])
                        i++
                    }
                    executeBlock(blockLines.joinToString("\n"), ::emitOutput)
                    continue
                }

                // Last statement: check if it's an expression that should produce execute_result
                val isLast = (i == lines.size - 1) || lines.subList(i + 1, lines.size).all { it.trim().isEmpty() || it.trim().startsWith("#") }
                if (isLast && !line.contains("=") && !line.startsWith("import ") && !line.startsWith("from ") && !line.startsWith("print(")) {
                    val evalResult = evaluateExpression(line)
                    if (evalResult != Unit && evalResult != null) {
                        val mimeBundle = formatMimeBundle(evalResult)
                        emitOutput(
                            CellOutput.ExecuteResult(
                                executionCount = currentCount,
                                data = mimeBundle,
                                metadata = emptyMap()
                            )
                        )
                    }
                    i++
                    break
                }

                executeStatement(line, ::emitOutput)
                i++
            }

            _status.value = KernelStatus.IDLE
            ExecutionResultSummary(
                status = "ok",
                executionCount = currentCount,
                outputs = outputs
            )
        } catch (e: Throwable) {
            _status.value = KernelStatus.IDLE
            val ename = e::class.java.simpleName
            val evalue = e.message ?: "Unknown execution error"
            val traceback = listOf(
                "\u001b[31;1mTraceback (most recent call last):\u001b[0m",
                "  \u001b[36mCell In[$currentCount]\u001b[0m",
                "\u001b[31m$ename: $evalue\u001b[0m"
            )
            val errOutput = CellOutput.Error(ename, evalue, traceback)
            emitOutput(errOutput)
            ExecutionResultSummary(
                status = "error",
                executionCount = currentCount,
                outputs = outputs,
                errorName = ename,
                errorMessage = evalue,
                traceback = traceback
            )
        } finally {
            activeOutputCollector = null
        }
    }

    private fun executeStatement(statement: String, emit: (CellOutput) -> Unit) {
        val s = statement.trim()
        if (s.isEmpty() || s.startsWith("#")) return

        // Print statement
        if (s.startsWith("print(") && s.endsWith(")")) {
            val content = s.substring(6, s.length - 1)
            val printed = evaluatePrintArgs(content)
            emit(CellOutput.Stream("stdout", "$printed\n"))
            return
        }

        // Imports
        if (s.startsWith("import ") || s.startsWith("from ")) {
            handleImport(s)
            return
        }

        // In-place operator assignments (+=, -=, *=, /=)
        for (op in listOf("+=", "-=", "*=", "/=")) {
            if (s.contains(" $op ")) {
                val parts = s.split(" $op ", limit = 2)
                val varName = parts[0].trim()
                val rhs = evaluateExpression(parts[1].trim())
                val current = globals[varName]
                val updated = when (op) {
                    "+=" -> applyBinaryOp(current, rhs, "+")
                    "-=" -> applyBinaryOp(current, rhs, "-")
                    "*=" -> applyBinaryOp(current, rhs, "*")
                    "/=" -> applyBinaryOp(current, rhs, "/")
                    else -> rhs
                }
                globals[varName] = updated
                return
            }
        }

        // Standard variable assignment (x = ...)
        if (s.contains("=") && !s.contains("==") && !s.contains("<=") && !s.contains(">=")) {
            val parts = s.split("=", limit = 2)
            val target = parts[0].trim()
            val expr = parts[1].trim()

            // Multiple assignment: a, b = 1, 2
            if (target.contains(",")) {
                val targets = target.split(",").map { it.trim() }
                val evalRhs = evaluateExpression(expr)
                if (evalRhs is List<*>) {
                    targets.forEachIndexed { idx, t ->
                        globals[t] = evalRhs.getOrNull(idx)
                    }
                } else if (evalRhs is NumpyModule.NDArray) {
                    val list = evalRhs.toList()
                    targets.forEachIndexed { idx, t ->
                        globals[t] = list.getOrNull(idx)
                    }
                }
            } else {
                globals[target] = evaluateExpression(expr)
            }
            return
        }

        // Expression statement without assignment
        evaluateExpression(s)
    }

    private fun executeBlock(block: String, emit: (CellOutput) -> Unit) {
        val lines = block.lines()
        val firstLine = lines[0].trim()

        if (firstLine.startsWith("for ") && firstLine.endsWith(":")) {
            val header = firstLine.removePrefix("for ").removeSuffix(":")
            val headerParts = header.split(" in ", limit = 2)
            val loopVar = headerParts[0].trim()
            val iterable = evaluateExpression(headerParts[1].trim())
            val body = lines.subList(1, lines.size).map { it.removePrefix("    ").removePrefix("\t") }

            val items = when (iterable) {
                is Iterable<*> -> iterable.toList()
                is NumpyModule.NDArray -> iterable.toList()
                else -> emptyList<Any?>()
            }

            for (item in items) {
                globals[loopVar] = item
                for (bodyLine in body) {
                    executeStatement(bodyLine, emit)
                }
            }
        } else if (firstLine.startsWith("def ")) {
            val fnHeader = firstLine.removePrefix("def ").substringBefore("(")
            val paramStr = firstLine.substringAfter("(").substringBefore(")")
            val params = paramStr.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            val body = lines.subList(1, lines.size).map { it.removePrefix("    ").removePrefix("\t") }

            globals[fnHeader] = UserFunction(fnHeader, params, body, this)
        } else {
            for (line in lines) {
                executeStatement(line, emit)
            }
        }
    }

    private class UserFunction(
        val name: String,
        val params: List<String>,
        val bodyLines: List<String>,
        val interpreter: LocalPythonInterpreter
    ) {
        fun invoke(args: List<Any?>): Any? {
            val localScope = mutableMapOf<String, Any?>()
            params.forEachIndexed { idx, p ->
                localScope[p] = args.getOrNull(idx)
            }
            val backup = mutableMapOf<String, Any?>()
            localScope.forEach { (k, v) ->
                backup[k] = interpreter.globals[k]
                interpreter.globals[k] = v
            }
            var result: Any? = null
            try {
                for (line in bodyLines) {
                    val trimmed = line.trim()
                    if (trimmed.startsWith("return ")) {
                        result = interpreter.evaluateExpression(trimmed.removePrefix("return ").trim())
                        break
                    }
                    interpreter.executeStatement(trimmed) { /* inner print */ }
                }
            } finally {
                localScope.keys.forEach { k ->
                    if (backup.containsKey(k)) interpreter.globals[k] = backup[k] else interpreter.globals.remove(k)
                }
            }
            return result
        }

        override fun toString(): String = "<function $name>"
    }

    private fun handleImport(statement: String) {
        val s = statement.trim()
        if (s == "import numpy as np" || s == "import numpy") {
            globals["np"] = np
            globals["numpy"] = np
        } else if (s == "import matplotlib.pyplot as plt" || s == "import matplotlib") {
            globals["plt"] = plt
            globals["matplotlib"] = object { val pyplot = plt }
        } else if (s == "import pandas as pd" || s == "import pandas") {
            globals["pd"] = pd
            globals["pandas"] = pd
        } else if (s == "import math") {
            // Already present
        } else if (s == "from math import sqrt, pi" || s.startsWith("from math import")) {
            globals["sqrt"] = { x: Double -> kotlin.math.sqrt(x) }
            globals["pi"] = kotlin.math.PI
            globals["sin"] = { x: Double -> kotlin.math.sin(x) }
            globals["cos"] = { x: Double -> kotlin.math.cos(x) }
        }
    }

    private fun evaluatePrintArgs(argsStr: String): String {
        val tokens = parseArguments(argsStr)
        val evaluated = tokens.map { token ->
            val expr = token.trim()
            val res = evaluateExpression(expr)
            formatString(res)
        }
        return evaluated.joinToString(" ")
    }

    fun evaluateExpression(expr: String): Any? {
        val s = expr.trim()
        if (s.isEmpty()) return null

        if ((s.startsWith("\"") && s.endsWith("\"")) || (s.startsWith("'") && s.endsWith("'"))) {
            return s.substring(1, s.length - 1)
        }

        s.toIntOrNull()?.let { return it }
        s.toLongOrNull()?.let { return it }
        s.toDoubleOrNull()?.let { return it }

        if (s == "True") return true
        if (s == "False") return false
        if (s == "None") return null

        if (s.startsWith("[") && s.endsWith("]")) {
            val inside = s.substring(1, s.length - 1).trim()
            if (inside.isEmpty()) return emptyList<Any?>()
            val items = parseArguments(inside)
            return items.map { evaluateExpression(it) }
        }

        if (s.startsWith("{") && s.endsWith("}")) {
            val inside = s.substring(1, s.length - 1).trim()
            if (inside.isEmpty()) return emptyMap<String, Any?>()
            val entries = parseArguments(inside)
            val map = mutableMapOf<String, Any?>()
            for (entry in entries) {
                if (entry.contains(":")) {
                    val parts = entry.split(":", limit = 2)
                    val key = evaluateExpression(parts[0].trim())?.toString() ?: ""
                    val value = evaluateExpression(parts[1].trim())
                    map[key] = value
                }
            }
            return map
        }

        if (s.contains("**")) {
            val parts = s.split("**", limit = 2)
            val left = evaluateExpression(parts[0].trim())
            val right = evaluateExpression(parts[1].trim())
            return applyBinaryOp(left, right, "**")
        }

        val addIdx = findOperatorIndex(s, listOf("+", "-"))
        if (addIdx != -1) {
            val op = s[addIdx].toString()
            val left = evaluateExpression(s.substring(0, addIdx).trim())
            val right = evaluateExpression(s.substring(addIdx + 1).trim())
            return applyBinaryOp(left, right, op)
        }

        val mulIdx = findOperatorIndex(s, listOf("*", "/", "%"))
        if (mulIdx != -1) {
            val op = s[mulIdx].toString()
            val left = evaluateExpression(s.substring(0, mulIdx).trim())
            val right = evaluateExpression(s.substring(mulIdx + 1).trim())
            return applyBinaryOp(left, right, op)
        }

        if (s.contains(".")) {
            val dotIdx = s.indexOf('.')
            val targetName = s.substring(0, dotIdx).trim()
            val rest = s.substring(dotIdx + 1).trim()

            val target = globals[targetName] ?: evaluateExpression(targetName)
            if (target != null) {
                return evaluateMemberAccess(target, rest)
            }
        }

        if (s.contains("(") && s.endsWith(")")) {
            val fnName = s.substringBefore("(").trim()
            val argsStr = s.substringAfter("(").substringBeforeLast(")")
            val args = parseArguments(argsStr).map { evaluateExpression(it) }
            return callBuiltinOrGlobal(fnName, args)
        }

        if (s.contains("[") && s.endsWith("]")) {
            val targetName = s.substringBefore("[").trim()
            val indexExpr = s.substringAfter("[").substringBeforeLast("]").trim()
            val target = globals[targetName]
            val indexVal = evaluateExpression(indexExpr)
            return evaluateIndex(target, indexVal)
        }

        if (globals.containsKey(s)) {
            return globals[s]
        }

        throw IllegalArgumentException("NameError: name '$s' is not defined")
    }

    private fun callBuiltinOrGlobal(name: String, args: List<Any?>): Any? {
        val fn = globals[name]
        if (fn is UserFunction) {
            return fn.invoke(args)
        }

        return when (name) {
            "len" -> {
                when (val arg = args.firstOrNull()) {
                    is List<*> -> arg.size
                    is String -> arg.length
                    is Map<*, *> -> arg.size
                    is NumpyModule.NDArray -> arg.size
                    is PandasModule.DataFrame -> arg.rowCount
                    is PandasModule.Series -> arg.size
                    else -> 0
                }
            }
            "range" -> {
                when (args.size) {
                    1 -> (0 until (args[0] as Number).toInt()).toList()
                    2 -> ((args[0] as Number).toInt() until (args[1] as Number).toInt()).toList()
                    3 -> ((args[0] as Number).toInt() until (args[1] as Number).toInt() step (args[2] as Number).toInt()).toList()
                    else -> emptyList<Int>()
                }
            }
            "sum" -> {
                when (val arg = args.firstOrNull()) {
                    is List<*> -> arg.mapNotNull { (it as? Number)?.toDouble() }.sum()
                    is NumpyModule.NDArray -> arg.sum()
                    else -> 0.0
                }
            }
            "min" -> {
                when (val arg = args.firstOrNull()) {
                    is List<*> -> arg.mapNotNull { (it as? Number)?.toDouble() }.minOrNull() ?: 0.0
                    is NumpyModule.NDArray -> arg.min()
                    else -> 0.0
                }
            }
            "max" -> {
                when (val arg = args.firstOrNull()) {
                    is List<*> -> arg.mapNotNull { (it as? Number)?.toDouble() }.maxOrNull() ?: 0.0
                    is NumpyModule.NDArray -> arg.max()
                    else -> 0.0
                }
            }
            "abs" -> {
                when (val arg = args.firstOrNull()) {
                    is Number -> kotlin.math.abs(arg.toDouble())
                    is NumpyModule.NDArray -> np.abs(arg)
                    else -> 0.0
                }
            }
            "type" -> {
                val arg = args.firstOrNull()
                "<class '${arg?.let { it::class.java.simpleName } ?: "NoneType"}'>"
            }
            "str" -> formatString(args.firstOrNull())
            "int" -> (args.firstOrNull() as? Number)?.toInt() ?: args.firstOrNull()?.toString()?.toIntOrNull() ?: 0
            "float" -> (args.firstOrNull() as? Number)?.toDouble() ?: args.firstOrNull()?.toString()?.toDoubleOrNull() ?: 0.0
            else -> throw IllegalArgumentException("NameError: name '$name' is not defined")
        }
    }

    private fun evaluateMemberAccess(target: Any, memberExpr: String): Any? {
        val isCall = memberExpr.contains("(") && memberExpr.endsWith(")")
        val memberName = if (isCall) memberExpr.substringBefore("(") else memberExpr
        val args = if (isCall) {
            parseArguments(memberExpr.substringAfter("(").substringBeforeLast(")")).map { evaluateExpression(it) }
        } else emptyList()

        if (target is NumpyModule) {
            return when (memberName) {
                "arange" -> np.arange(
                    (args.getOrNull(0) as? Number)?.toDouble() ?: 0.0,
                    (args.getOrNull(1) as? Number)?.toDouble(),
                    (args.getOrNull(2) as? Number)?.toDouble() ?: 1.0
                )
                "linspace" -> np.linspace(
                    (args.getOrNull(0) as? Number)?.toDouble() ?: 0.0,
                    (args.getOrNull(1) as? Number)?.toDouble() ?: 1.0,
                    (args.getOrNull(2) as? Number)?.toInt() ?: 50
                )
                "array" -> np.array(args[0] ?: emptyList<Double>())
                "zeros" -> np.zeros(args[0] ?: 1)
                "ones" -> np.ones(args[0] ?: 1)
                "eye" -> np.eye((args.getOrNull(0) as? Number)?.toInt() ?: 3)
                "sin" -> np.sin(args[0] ?: 0.0)
                "cos" -> np.cos(args[0] ?: 0.0)
                "tan" -> np.tan(args[0] ?: 0.0)
                "sqrt" -> np.sqrt(args[0] ?: 0.0)
                "exp" -> np.exp(args[0] ?: 0.0)
                "log" -> np.log(args[0] ?: 1.0)
                "dot" -> np.dot(args[0] as NumpyModule.NDArray, args[1] as NumpyModule.NDArray)
                "pi" -> np.pi
                "e" -> np.e
                "random" -> np.random
                else -> throw IllegalArgumentException("AttributeError: 'numpy' has no attribute '$memberName'")
            }
        }

        if (target is MatplotlibModule.Pyplot) {
            return when (memberName) {
                "plot" -> plt.plot(args[0] ?: emptyList<Double>(), args.getOrNull(1))
                "scatter" -> plt.scatter(args[0] ?: emptyList<Double>(), args.getOrNull(1))
                "bar" -> plt.bar(args[0] ?: emptyList<Double>(), args[1] ?: emptyList<Double>())
                "hist" -> plt.hist(args[0] ?: emptyList<Double>(), (args.getOrNull(1) as? Number)?.toInt() ?: 10)
                "title" -> plt.title(args.getOrNull(0)?.toString() ?: "")
                "xlabel" -> plt.xlabel(args.getOrNull(0)?.toString() ?: "")
                "ylabel" -> plt.ylabel(args.getOrNull(0)?.toString() ?: "")
                "grid" -> plt.grid((args.getOrNull(0) as? Boolean) ?: true)
                "legend" -> plt.legend()
                "show" -> plt.show()
                "clf" -> plt.clf()
                "figure" -> plt.figure()
                else -> throw IllegalArgumentException("AttributeError: 'matplotlib.pyplot' has no attribute '$memberName'")
            }
        }

        if (target is PandasModule) {
            return when (memberName) {
                "DataFrame" -> pd.DataFrame(args.getOrNull(0))
                "Series" -> pd.Series(args.getOrNull(0))
                "read_csv" -> pd.read_csv(args.getOrNull(0)?.toString() ?: "")
                else -> throw IllegalArgumentException("AttributeError: 'pandas' has no attribute '$memberName'")
            }
        }

        if (target is PandasModule.DataFrame) {
            return when (memberName) {
                "head" -> target.head((args.getOrNull(0) as? Number)?.toInt() ?: 5)
                "tail" -> target.tail((args.getOrNull(0) as? Number)?.toInt() ?: 5)
                "describe" -> target.describe()
                "info" -> target.info()
                "mean" -> target.mean()
                "sum" -> target.sum()
                "columns" -> target.columns
                "shape" -> target.shape
                "to_html" -> target.toHtml()
                else -> throw IllegalArgumentException("AttributeError: 'DataFrame' has no attribute '$memberName'")
            }
        }

        if (target is NumpyModule.NDArray) {
            return when (memberName) {
                "sum" -> target.sum()
                "mean" -> target.mean()
                "min" -> target.min()
                "max" -> target.max()
                "std" -> target.std()
                "reshape" -> {
                    val sh = if (args.size == 1 && args[0] is List<*>) {
                        (args[0] as List<*>).map { (it as Number).toInt() }.toIntArray()
                    } else {
                        args.map { (it as Number).toInt() }.toIntArray()
                    }
                    target.reshape(sh)
                }
                "shape" -> target.shape.toList()
                "size" -> target.size
                "ndim" -> target.ndim
                else -> throw IllegalArgumentException("AttributeError: 'ndarray' has no attribute '$memberName'")
            }
        }

        throw IllegalArgumentException("AttributeError: Object of type '${target::class.java.simpleName}' has no attribute '$memberName'")
    }

    private fun evaluateIndex(target: Any?, indexVal: Any?): Any? {
        return when (target) {
            is List<*> -> {
                val idx = (indexVal as Number).toInt()
                target.getOrNull(if (idx < 0) target.size + idx else idx)
            }
            is Map<*, *> -> target[indexVal]
            is PandasModule.DataFrame -> target[indexVal.toString()]
            is NumpyModule.NDArray -> {
                val idx = (indexVal as Number).toInt()
                target.data[if (idx < 0) target.size + idx else idx]
            }
            else -> throw IllegalArgumentException("TypeError: '${target?.let { it::class.java.simpleName }}' object is not subscriptable")
        }
    }

    private fun applyBinaryOp(left: Any?, right: Any?, op: String): Any? {
        if (left is NumpyModule.NDArray) {
            return when (op) {
                "+" -> left + (right ?: 0.0)
                "-" -> left - (right ?: 0.0)
                "*" -> left * (right ?: 1.0)
                "/" -> left / (right ?: 1.0)
                "**" -> left.pow((right as Number).toDouble())
                else -> throw IllegalArgumentException("Unsupported operator $op for NDArray")
            }
        }

        if (left is Number && right is Number) {
            val ld = left.toDouble()
            val rd = right.toDouble()
            return when (op) {
                "+" -> if (left is Int && right is Int) left + right else ld + rd
                "-" -> if (left is Int && right is Int) left - right else ld - rd
                "*" -> if (left is Int && right is Int) left * right else ld * rd
                "/" -> ld / rd
                "%" -> ld % rd
                "**" -> ld.pow(rd)
                else -> ld + rd
            }
        }

        if (left is String && op == "+") {
            return left + right.toString()
        }

        if (left is List<*> && op == "+" && right is List<*>) {
            return left + right
        }

        throw IllegalArgumentException("TypeError: unsupported operand type(s) for $op: '${left?.let { it::class.java.simpleName }}' and '${right?.let { it::class.java.simpleName }}'")
    }

    private fun formatMimeBundle(result: Any): Map<String, Any?> {
        val bundle = mutableMapOf<String, Any?>()
        bundle["text/plain"] = formatString(result)

        if (result is PandasModule.DataFrame) {
            bundle["text/html"] = result.toHtml()
        }

        return bundle
    }

    private fun formatString(obj: Any?): String {
        return when (obj) {
            null -> "None"
            is String -> obj
            is NumpyModule.NDArray -> obj.toString()
            is PandasModule.DataFrame -> obj.toString()
            is PandasModule.Series -> obj.toString()
            is List<*> -> "[${obj.joinToString(", ") { formatString(it) }}]"
            is Map<*, *> -> "{${obj.entries.joinToString(", ") { "${formatString(it.key)}: ${formatString(it.value)}" }}}"
            is Double -> if (obj == floor(obj) && !obj.isInfinite() && obj.toLong().toDouble() == obj) obj.toLong().toString() else obj.toString()
            else -> obj.toString()
        }
    }

    private fun formatJson(obj: Any?): String {
        return when (obj) {
            null -> "null"
            is Number, is Boolean -> obj.toString()
            is String -> "\"${obj.replace("\"", "\\\"")}\""
            is List<*> -> "[${obj.joinToString(", ") { formatJson(it) }}]"
            is Map<*, *> -> "{${obj.entries.joinToString(", ") { "\"${it.key}\": ${formatJson(it.value)}" }}}"
            else -> "\"$obj\""
        }
    }

    private fun findOperatorIndex(s: String, ops: List<String>): Int {
        var depth = 0
        for (i in s.length - 1 downTo 0) {
            val c = s[i]
            if (c == ')' || c == ']' || c == '}') depth++
            else if (c == '(' || c == '[' || c == '{') depth--
            else if (depth == 0) {
                for (op in ops) {
                    if (s.startsWith(op, i)) {
                        if (op == "-" && i == 0) continue
                        return i
                    }
                }
            }
        }
        return -1
    }

    private fun parseArguments(argsStr: String): List<String> {
        val args = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        var inQuotes = false
        var quoteChar = ' '

        for (c in argsStr) {
            if ((c == '"' || c == '\'') && (quoteChar == ' ' || quoteChar == c)) {
                inQuotes = !inQuotes
                quoteChar = if (inQuotes) c else ' '
                sb.append(c)
            } else if (!inQuotes) {
                if (c == '(' || c == '[' || c == '{') depth++
                else if (c == ')' || c == ']' || c == '}') depth--

                if (c == ',' && depth == 0) {
                    args.add(sb.toString().trim())
                    sb.clear()
                } else {
                    sb.append(c)
                }
            } else {
                sb.append(c)
            }
        }
        if (sb.isNotBlank()) args.add(sb.toString().trim())
        return args
    }

    override suspend fun complete(code: String, cursorPosition: Int): List<String> {
        val prefix = code.take(cursorPosition).substringAfterLast(" ").substringAfterLast("\n").substringAfterLast("(")
        val keywords = listOf("import", "from", "def", "class", "return", "for", "in", "while", "if", "elif", "else", "try", "except", "print", "range", "len", "sum", "abs", "min", "max")
        val available = keywords + globals.keys
        return available.filter { it.startsWith(prefix) }
    }

    override suspend fun inspect(code: String, cursorPosition: Int): String? {
        val word = code.take(cursorPosition).substringAfterLast(" ").substringAfterLast("\n").substringAfterLast("(")
        return when (word) {
            "np.arange" -> "np.arange([start,] stop[, step]): Return evenly spaced values within a given interval."
            "np.linspace" -> "np.linspace(start, stop, num=50): Return evenly spaced numbers over a specified interval."
            "plt.plot" -> "plt.plot(x, y, [label]): Plot lines and/or markers to the Axes."
            "plt.show" -> "plt.show(): Display all open figures as Base64 PNG MIME bundle."
            "pd.DataFrame" -> "pd.DataFrame(data): Two-dimensional tabular data with labeled axes."
            "len" -> "len(obj): Return the number of items in a container."
            "range" -> "range(stop) or range(start, stop[, step]): Return sequence of numbers."
            else -> globals[word]?.let { "Object of type ${it::class.java.simpleName}: $it" }
        }
    }

    override suspend fun isComplete(code: String): Boolean {
        var openParens = 0
        var inQuote = false
        for (c in code) {
            if (c == '"' || c == '\'') inQuote = !inQuote
            else if (!inQuote) {
                if (c == '(' || c == '[' || c == '{') openParens++
                else if (c == ')' || c == ']' || c == '}') openParens--
            }
        }
        return openParens == 0 && !inQuote
    }

    override suspend fun interrupt() {
        _status.value = KernelStatus.IDLE
    }

    override suspend fun restart() {
        _status.value = KernelStatus.RESTARTING
        resetNamespace()
        _executionCount.value = 0
        _status.value = KernelStatus.IDLE
    }

    override suspend fun shutdown() {
        _status.value = KernelStatus.DEAD
        globals.clear()
    }

    override fun getVariableNames(): List<String> = globals.keys.toList()
    override fun getVariableValue(name: String): Any? = globals[name]
}
