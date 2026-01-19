package com.alex.tg.chat

class CubicBezierInterpolator(
    private val x1: Double,
    private val y1: Double,
    private val x2: Double,
    private val y2: Double
) {
    fun getInterpolation(input: Float): Float {
        var t = input.toDouble()
        var x = 0.0
        var y = 0.0
        var i = 0
        while (i < 10) {
            val tx = bezier(t, 0.0, x1, x2, 1.0)
            val d = tx - input
            if (kotlin.math.abs(d) < 1e-3) break
            val dx = dBezier(t, 0.0, x1, x2, 1.0)
            t -= d / dx
            i++
        }
        x = bezier(t, 0.0, x1, x2, 1.0)
        y = bezier(t, 0.0, y1, y2, 1.0)
        return y.toFloat()
    }

    private fun bezier(t: Double, p0: Double, p1: Double, p2: Double, p3: Double): Double {
        val u = 1.0 - t
        return u * u * u * p0 +
            3.0 * u * u * t * p1 +
            3.0 * u * t * t * p2 +
            t * t * t * p3
    }

    private fun dBezier(t: Double, p0: Double, p1: Double, p2: Double, p3: Double): Double {
        val u = 1.0 - t
        return 3.0 * u * u * (p1 - p0) +
            6.0 * u * t * (p2 - p1) +
            3.0 * t * t * (p3 - p2)
    }

    companion object {
        val EaseOut = CubicBezierInterpolator(0.22, 1.0, 0.36, 1.0)
        val EaseInOut = CubicBezierInterpolator(0.42, 0.0, 0.58, 1.0)
    }
}

