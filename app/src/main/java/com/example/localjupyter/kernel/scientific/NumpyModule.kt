package com.example.localjupyter.kernel.scientific

import kotlin.math.*
import kotlin.random.Random

/**
 * Android-compatible NumPy implementation providing ndarray, vector/matrix operations,
 * creation routines, and mathematical ufuncs.
 */
class NumpyModule {

    class NDArray(
        val data: DoubleArray,
        val shape: IntArray
    ) {
        val size: Int get() = data.size
        val ndim: Int get() = shape.size

        operator fun plus(other: Any): NDArray = elementwiseOp(other) { a, b -> a + b }
        operator fun minus(other: Any): NDArray = elementwiseOp(other) { a, b -> a - b }
        operator fun times(other: Any): NDArray = elementwiseOp(other) { a, b -> a * b }
        operator fun div(other: Any): NDArray = elementwiseOp(other) { a, b -> a / b }

        fun pow(exp: Double): NDArray {
            val result = DoubleArray(data.size) { data[it].pow(exp) }
            return NDArray(result, shape.clone())
        }

        fun sum(): Double = data.sum()
        fun mean(): Double = if (data.isNotEmpty()) data.average() else 0.0
        fun min(): Double = data.minOrNull() ?: 0.0
        fun max(): Double = data.maxOrNull() ?: 0.0
        fun std(): Double {
            val m = mean()
            val variance = data.map { (it - m).pow(2) }.average()
            return sqrt(variance)
        }
        fun varValue(): Double {
            val m = mean()
            return data.map { (it - m).pow(2) }.average()
        }

        fun reshape(newShape: IntArray): NDArray {
            val total = newShape.reduce { acc, v -> acc * v }
            require(total == size) { "Cannot reshape array of size $size into shape ${newShape.contentToString()}" }
            return NDArray(data.clone(), newShape)
        }

        fun toList(): List<Any> {
            if (ndim == 1) {
                return data.map { formatNumber(it) }
            }
            if (ndim == 2) {
                val rows = shape[0]
                val cols = shape[1]
                return (0 until rows).map { r ->
                    (0 until cols).map { c -> formatNumber(data[r * cols + c]) }
                }
            }
            return data.map { formatNumber(it) }
        }

        private fun elementwiseOp(other: Any, op: (Double, Double) -> Double): NDArray {
            return when (other) {
                is Number -> {
                    val scalar = other.toDouble()
                    val res = DoubleArray(data.size) { op(data[it], scalar) }
                    NDArray(res, shape.clone())
                }
                is NDArray -> {
                    if (shape.contentEquals(other.shape)) {
                        val res = DoubleArray(data.size) { op(data[it], other.data[it]) }
                        NDArray(res, shape.clone())
                    } else if (other.size == 1) {
                        val scalar = other.data[0]
                        val res = DoubleArray(data.size) { op(data[it], scalar) }
                        NDArray(res, shape.clone())
                    } else {
                        throw IllegalArgumentException("Operands could not be broadcast together with shapes ${shape.contentToString()} and ${other.shape.contentToString()}")
                    }
                }
                else -> throw IllegalArgumentException("Unsupported operand type: ${other::class.java.simpleName}")
            }
        }

        override fun toString(): String {
            return if (ndim == 1) {
                val formatted = data.joinToString(", ") { formatNumber(it) }
                "array([$formatted])"
            } else if (ndim == 2) {
                val rows = shape[0]
                val cols = shape[1]
                val rowStrings = (0 until rows).joinToString(",\n       ") { r ->
                    val rowData = (0 until cols).joinToString(", ") { c -> formatNumber(data[r * cols + c]) }
                    "[$rowData]"
                }
                "array([$rowStrings])"
            } else {
                "array(${data.contentToString()})"
            }
        }

        private fun formatNumber(d: Double): String {
            return if (d == floor(d) && !d.isInfinite() && d.toLong().toDouble() == d) {
                d.toLong().toString()
            } else {
                String.format(java.util.Locale.US, "%.4f", d).trimEnd('0').trimEnd('.')
            }
        }
    }

    // Array creation routines
    fun array(elements: Any): NDArray {
        return when (elements) {
            is DoubleArray -> NDArray(elements.clone(), intArrayOf(elements.size))
            is IntArray -> NDArray(DoubleArray(elements.size) { elements[it].toDouble() }, intArrayOf(elements.size))
            is List<*> -> {
                if (elements.isEmpty()) return NDArray(DoubleArray(0), intArrayOf(0))
                if (elements[0] is List<*>) {
                    // 2D list
                    val rows = elements.size
                    val cols = (elements[0] as List<*>).size
                    val flat = DoubleArray(rows * cols)
                    var idx = 0
                    for (row in elements) {
                        val rList = row as List<*>
                        for (item in rList) {
                            flat[idx++] = (item as Number).toDouble()
                        }
                    }
                    NDArray(flat, intArrayOf(rows, cols))
                } else {
                    val flat = DoubleArray(elements.size) { ((elements[it] as? Number)?.toDouble()) ?: 0.0 }
                    NDArray(flat, intArrayOf(elements.size))
                }
            }
            is NDArray -> elements
            else -> throw IllegalArgumentException("Cannot convert ${elements::class.java.simpleName} to ndarray")
        }
    }

    fun arange(start: Double, stop: Double? = null, step: Double = 1.0): NDArray {
        val s = if (stop == null) 0.0 else start
        val e = stop ?: start
        val count = max(0, ceil((e - s) / step).toInt())
        val data = DoubleArray(count) { i -> s + i * step }
        return NDArray(data, intArrayOf(count))
    }

    fun linspace(start: Double, stop: Double, num: Int = 50): NDArray {
        require(num > 0) { "Number of samples must be positive" }
        if (num == 1) return NDArray(doubleArrayOf(start), intArrayOf(1))
        val step = (stop - start) / (num - 1)
        val data = DoubleArray(num) { i -> start + i * step }
        return NDArray(data, intArrayOf(num))
    }

    fun zeros(shape: Any): NDArray {
        val sh = parseShape(shape)
        val total = sh.reduce { acc, v -> acc * v }
        return NDArray(DoubleArray(total), sh)
    }

    fun ones(shape: Any): NDArray {
        val sh = parseShape(shape)
        val total = sh.reduce { acc, v -> acc * v }
        return NDArray(DoubleArray(total) { 1.0 }, sh)
    }

    fun eye(n: Int): NDArray {
        val data = DoubleArray(n * n)
        for (i in 0 until n) {
            data[i * n + i] = 1.0
        }
        return NDArray(data, intArrayOf(n, n))
    }

    // Mathematical ufuncs
    fun sin(x: Any): Any = applyUnary(x) { kotlin.math.sin(it) }
    fun cos(x: Any): Any = applyUnary(x) { kotlin.math.cos(it) }
    fun tan(x: Any): Any = applyUnary(x) { kotlin.math.tan(it) }
    fun exp(x: Any): Any = applyUnary(x) { kotlin.math.exp(it) }
    fun log(x: Any): Any = applyUnary(x) { kotlin.math.ln(it) }
    fun sqrt(x: Any): Any = applyUnary(x) { kotlin.math.sqrt(it) }
    fun abs(x: Any): Any = applyUnary(x) { kotlin.math.abs(it) }

    fun dot(a: NDArray, b: NDArray): Any {
        if (a.ndim == 1 && b.ndim == 1) {
            require(a.size == b.size) { "Shapes (${a.size},) and (${b.size},) not aligned" }
            var sum = 0.0
            for (i in 0 until a.size) {
                sum += a.data[i] * b.data[i]
            }
            return sum
        }
        if (a.ndim == 2 && b.ndim == 2) {
            val aRows = a.shape[0]
            val aCols = a.shape[1]
            val bRows = b.shape[0]
            val bCols = b.shape[1]
            require(aCols == bRows) { "Shapes ($aRows, $aCols) and ($bRows, $bCols) not aligned" }
            val res = DoubleArray(aRows * bCols)
            for (i in 0 until aRows) {
                for (j in 0 until bCols) {
                    var s = 0.0
                    for (k in 0 until aCols) {
                        s += a.data[i * aCols + k] * b.data[k * bCols + j]
                    }
                    res[i * bCols + j] = s
                }
            }
            return NDArray(res, intArrayOf(aRows, bCols))
        }
        throw IllegalArgumentException("Unsupported dimensions for dot product: ${a.ndim} and ${b.ndim}")
    }

    // Random submodule
    val random = object {
        private var rng: kotlin.random.Random = Random.Default

        fun seed(s: Long) {
            rng = Random(s)
        }

        fun rand(vararg dimensions: Int): NDArray {
            val sh = if (dimensions.isEmpty()) intArrayOf(1) else dimensions
            val total = sh.reduce { acc, v -> acc * v }
            val data = DoubleArray(total) { rng.nextDouble() }
            return NDArray(data, sh)
        }

        fun randn(vararg dimensions: Int): NDArray {
            val sh = if (dimensions.isEmpty()) intArrayOf(1) else dimensions
            val total = sh.reduce { acc, v -> acc * v }
            // Box-Muller transform for normal distribution
            val data = DoubleArray(total) {
                val u1 = max(1e-15, rng.nextDouble())
                val u2 = rng.nextDouble()
                kotlin.math.sqrt(-2.0 * kotlin.math.ln(u1)) * kotlin.math.cos(2.0 * kotlin.math.PI * u2)
            }
            return NDArray(data, sh)
        }

        fun randint(low: Int, high: Int? = null, size: Int? = null): Any {
            val l = if (high == null) 0 else low
            val h = high ?: low
            return if (size == null) {
                rng.nextInt(l, h)
            } else {
                val data = DoubleArray(size) { rng.nextInt(l, h).toDouble() }
                NDArray(data, intArrayOf(size))
            }
        }
    }

    val pi: Double = kotlin.math.PI
    val e: Double = kotlin.math.E

    private fun applyUnary(x: Any, op: (Double) -> Double): Any {
        return when (x) {
            is Number -> op(x.toDouble())
            is NDArray -> {
                val res = DoubleArray(x.data.size) { op(x.data[it]) }
                NDArray(res, x.shape.clone())
            }
            is List<*> -> {
                val res = DoubleArray(x.size) { op(((x[it] as? Number)?.toDouble()) ?: 0.0) }
                NDArray(res, intArrayOf(x.size))
            }
            else -> throw IllegalArgumentException("Unsupported type for math function: ${x::class.java.simpleName}")
        }
    }

    private fun parseShape(shape: Any): IntArray {
        return when (shape) {
            is Int -> intArrayOf(shape)
            is IntArray -> shape
            is List<*> -> IntArray(shape.size) { (shape[it] as Number).toInt() }
            else -> intArrayOf(1)
        }
    }
}
