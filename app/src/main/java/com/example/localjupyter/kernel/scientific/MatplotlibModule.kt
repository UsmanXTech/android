package com.example.localjupyter.kernel.scientific

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.Base64
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min

/**
 * Matplotlib Pyplot implementation for Android.
 * Generates genuine high-resolution bitmap figures encoded as Base64 PNG MIME bundles.
 */
class MatplotlibModule {

    companion object {
        fun makeRgb(r: Int, g: Int, b: Int): Int =
            (0xFF shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

        fun makeArgb(a: Int, r: Int, g: Int, b: Int): Int =
            ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)
    }

    data class PlotSeries(
        val x: DoubleArray,
        val y: DoubleArray,
        val label: String? = null,
        val color: Int = makeRgb(56, 189, 248), // Sky blue
        val style: String = "line" // "line", "scatter", "bar"
    )

    class Pyplot(private val onShowPlot: ((Map<String, Any>) -> Unit)? = null) {
        private val seriesList = mutableListOf<PlotSeries>()
        private var plotTitle: String? = null
        private var xLabel: String? = null
        private var yLabel: String? = null
        private var showGrid: Boolean = true
        private var showLegend: Boolean = false
        private var customXLim: Pair<Double, Double>? = null
        private var customYLim: Pair<Double, Double>? = null

        private val defaultColors = intArrayOf(
            makeRgb(56, 189, 248),  // Sky Blue
            makeRgb(243, 118, 38),  // Jupyter Orange
            makeRgb(52, 211, 153),  // Emerald
            makeRgb(251, 191, 36),  // Amber
            makeRgb(192, 132, 252), // Violet
            makeRgb(244, 63, 94)    // Rose
        )

        fun plot(xData: Any, yData: Any? = null, label: String? = null, color: Int? = null) {
            val (x, y) = resolveData(xData, yData)
            val c = color ?: defaultColors[seriesList.size % defaultColors.size]
            seriesList.add(PlotSeries(x, y, label, c, "line"))
        }

        fun scatter(xData: Any, yData: Any? = null, label: String? = null, color: Int? = null) {
            val (x, y) = resolveData(xData, yData)
            val c = color ?: defaultColors[seriesList.size % defaultColors.size]
            seriesList.add(PlotSeries(x, y, label, c, "scatter"))
        }

        fun bar(xData: Any, yData: Any, label: String? = null, color: Int? = null) {
            val (x, y) = resolveData(xData, yData)
            val c = color ?: defaultColors[seriesList.size % defaultColors.size]
            seriesList.add(PlotSeries(x, y, label, c, "bar"))
        }

        fun hist(data: Any, bins: Int = 10, label: String? = null, color: Int? = null) {
            val values = toDoubleArray(data)
            if (values.isEmpty()) return
            val minVal = values.minOrNull() ?: 0.0
            val maxVal = values.maxOrNull() ?: 1.0
            val binWidth = (maxVal - minVal) / max(1, bins)
            val counts = DoubleArray(bins)
            val xCenters = DoubleArray(bins) { i -> minVal + (i + 0.5) * binWidth }

            for (v in values) {
                var binIdx = ((v - minVal) / (binWidth + 1e-9)).toInt()
                if (binIdx >= bins) binIdx = bins - 1
                if (binIdx < 0) binIdx = 0
                counts[binIdx] += 1.0
            }

            val c = color ?: defaultColors[seriesList.size % defaultColors.size]
            seriesList.add(PlotSeries(xCenters, counts, label, c, "bar"))
        }

        fun title(text: String) { plotTitle = text }
        fun xlabel(text: String) { xLabel = text }
        fun ylabel(text: String) { yLabel = text }
        fun grid(visible: Boolean = true) { showGrid = visible }
        fun legend() { showLegend = true }
        fun xlim(minVal: Double, maxVal: Double) { customXLim = minVal to maxVal }
        fun ylim(minVal: Double, maxVal: Double) { customYLim = minVal to maxVal }

        fun figure(figsize: Any? = null) {
            clf()
        }

        fun clf() {
            seriesList.clear()
            plotTitle = null
            xLabel = null
            yLabel = null
            showGrid = true
            showLegend = false
            customXLim = null
            customYLim = null
        }

        fun show(): Map<String, Any> {
            val mimeBundle = renderPlotMimeBundle()
            onShowPlot?.invoke(mimeBundle)
            clf()
            return mimeBundle
        }

        fun renderPlotMimeBundle(): Map<String, Any> {
            val width = 900
            val height = 550
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)

            // Background - dark modern theme
            val bgPaint = Paint().apply { color = makeRgb(24, 24, 27) }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

            // Plot area insets
            val left = 90f
            val top = if (plotTitle != null) 60f else 40f
            val right = width - 40f
            val bottom = height - (if (xLabel != null) 70f else 50f)
            val plotRect = RectF(left, top, right, bottom)

            // Plot area background
            val plotBgPaint = Paint().apply { color = makeRgb(39, 39, 42) }
            canvas.drawRect(plotRect, plotBgPaint)

            // Calculate data bounds
            var minX = Double.MAX_VALUE
            var maxX = -Double.MAX_VALUE
            var minY = Double.MAX_VALUE
            var maxY = -Double.MAX_VALUE

            for (s in seriesList) {
                for (vx in s.x) {
                    if (vx < minX) minX = vx
                    if (vx > maxX) maxX = vx
                }
                for (vy in s.y) {
                    if (vy < minY) minY = vy
                    if (vy > maxY) maxY = vy
                }
            }

            if (minX == Double.MAX_VALUE) { minX = 0.0; maxX = 1.0 }
            if (minY == Double.MAX_VALUE) { minY = 0.0; maxY = 1.0 }
            if (minX == maxX) { minX -= 1.0; maxX += 1.0 }
            if (minY == maxY) { minY -= 1.0; maxY += 1.0 }

            customXLim?.let { (x0, x1) -> minX = x0; maxX = x1 }
            customYLim?.let { (y0, y1) -> minY = y0; maxY = y1 }

            val gridPaint = Paint().apply {
                color = makeRgb(63, 63, 70)
                strokeWidth = 1.5f
                style = Paint.Style.STROKE
            }
            val textPaint = Paint().apply {
                color = makeRgb(161, 161, 170)
                textSize = 22f
                isAntiAlias = true
            }

            // Draw grid & ticks
            val xSteps = 5
            for (i in 0..xSteps) {
                val frac = i.toFloat() / xSteps
                val px = left + frac * (right - left)
                if (showGrid) {
                    canvas.drawLine(px, top, px, bottom, gridPaint)
                }
                val xVal = minX + frac * (maxX - minX)
                val labelText = String.format(java.util.Locale.US, "%.2f", xVal).trimEnd('0').trimEnd('.')
                canvas.drawText(labelText, px - 20f, bottom + 30f, textPaint)
            }

            val ySteps = 5
            for (i in 0..ySteps) {
                val frac = i.toFloat() / ySteps
                val py = bottom - frac * (bottom - top)
                if (showGrid) {
                    canvas.drawLine(left, py, right, py, gridPaint)
                }
                val yVal = minY + frac * (maxY - minY)
                val labelText = String.format(java.util.Locale.US, "%.2f", yVal).trimEnd('0').trimEnd('.')
                canvas.drawText(labelText, 15f, py + 8f, textPaint)
            }

            // Draw series
            for (s in seriesList) {
                when (s.style) {
                    "line" -> {
                        val linePaint = Paint().apply {
                            color = s.color
                            strokeWidth = 3.5f
                            style = Paint.Style.STROKE
                            isAntiAlias = true
                        }
                        val path = Path()
                        for (i in s.x.indices) {
                            val px = left + ((s.x[i] - minX) / (maxX - minX)).toFloat() * (right - left)
                            val py = bottom - ((s.y[i] - minY) / (maxY - minY)).toFloat() * (bottom - top)
                            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
                        }
                        canvas.drawPath(path, linePaint)
                    }
                    "scatter" -> {
                        val dotPaint = Paint().apply {
                            color = s.color
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        for (i in s.x.indices) {
                            val px = left + ((s.x[i] - minX) / (maxX - minX)).toFloat() * (right - left)
                            val py = bottom - ((s.y[i] - minY) / (maxY - minY)).toFloat() * (bottom - top)
                            canvas.drawCircle(px, py, 6f, dotPaint)
                        }
                    }
                    "bar" -> {
                        val barPaint = Paint().apply {
                            color = s.color
                            style = Paint.Style.FILL
                            isAntiAlias = true
                        }
                        val barW = ((right - left) / max(1, s.x.size)) * 0.7f
                        for (i in s.x.indices) {
                            val px = left + ((s.x[i] - minX) / (maxX - minX)).toFloat() * (right - left)
                            val py = bottom - ((s.y[i] - minY) / (maxY - minY)).toFloat() * (bottom - top)
                            canvas.drawRect(px - barW / 2, py, px + barW / 2, bottom, barPaint)
                        }
                    }
                }
            }

            // Title
            plotTitle?.let {
                val titlePaint = Paint().apply {
                    color = makeRgb(244, 244, 245)
                    textSize = 28f
                    isFakeBoldText = true
                    isAntiAlias = true
                }
                val tW = titlePaint.measureText(it)
                canvas.drawText(it, left + (right - left - tW) / 2f, 42f, titlePaint)
            }

            // Axis labels
            xLabel?.let {
                val labelPaint = Paint().apply {
                    color = makeRgb(212, 212, 216)
                    textSize = 22f
                    isAntiAlias = true
                }
                val lW = labelPaint.measureText(it)
                canvas.drawText(it, left + (right - left - lW) / 2f, height - 15f, labelPaint)
            }
            yLabel?.let {
                val labelPaint = Paint().apply {
                    color = makeRgb(212, 212, 216)
                    textSize = 22f
                    isAntiAlias = true
                }
                canvas.save()
                canvas.rotate(-90f, 25f, top + (bottom - top) / 2f)
                canvas.drawText(it, 25f, top + (bottom - top) / 2f, labelPaint)
                canvas.restore()
            }

            // Legend
            if (showLegend && seriesList.any { it.label != null }) {
                var legX = right - 180f
                var legY = top + 20f
                val legBoxPaint = Paint().apply {
                    color = makeArgb(200, 24, 24, 27)
                    style = Paint.Style.FILL
                }
                canvas.drawRoundRect(legX - 10f, legY - 10f, right - 10f, legY + seriesList.size * 28f + 10f, 8f, 8f, legBoxPaint)

                for (s in seriesList) {
                    s.label?.let { lbl ->
                        val dotP = Paint().apply { color = s.color; style = Paint.Style.FILL }
                        canvas.drawCircle(legX + 5f, legY + 8f, 5f, dotP)
                        canvas.drawText(lbl, legX + 20f, legY + 15f, textPaint)
                        legY += 28f
                    }
                }
            }

            // Compress to PNG Base64
            val stream = ByteArrayOutputStream()
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
            val base64Png = Base64.encodeToString(stream.toByteArray(), Base64.NO_WRAP)
            bitmap.recycle()

            return mapOf(
                "image/png" to base64Png,
                "text/plain" to "<Figure size ${width}x${height} with 1 Axes>"
            )
        }

        private fun resolveData(xData: Any, yData: Any?): Pair<DoubleArray, DoubleArray> {
            return if (yData == null) {
                val y = toDoubleArray(xData)
                val x = DoubleArray(y.size) { it.toDouble() }
                x to y
            } else {
                val x = toDoubleArray(xData)
                val y = toDoubleArray(yData)
                x to y
            }
        }

        private fun toDoubleArray(data: Any): DoubleArray {
            return when (data) {
                is DoubleArray -> data
                is IntArray -> DoubleArray(data.size) { data[it].toDouble() }
                is FloatArray -> DoubleArray(data.size) { data[it].toDouble() }
                is List<*> -> DoubleArray(data.size) { ((data[it] as? Number)?.toDouble()) ?: 0.0 }
                is NumpyModule.NDArray -> data.data
                else -> doubleArrayOf()
            }
        }
    }
}
