package io.github.nullwrx.openmacro.core

import kotlin.math.pow

data class WeightPoint(val epochDay: Long, val kg: Double)

data class TrendPoint(val epochDay: Long, val kg: Double, val trendKg: Double)

/**
 * Smooths daily weigh-ins with an exponentially weighted moving average so water and food
 * swings do not hide the real direction. A gap of several days counts as several steps.
 */
object WeightTrend {
    const val DEFAULT_ALPHA = 0.1

    fun compute(points: List<WeightPoint>, alpha: Double = DEFAULT_ALPHA): List<TrendPoint> {
        require(alpha > 0 && alpha <= 1) { "alpha must be in (0, 1]" }
        val sorted = points.sortedBy { it.epochDay }
        if (sorted.isEmpty()) return emptyList()
        val result = ArrayList<TrendPoint>(sorted.size)
        var trend = sorted.first().kg
        var previousDay = sorted.first().epochDay
        result += TrendPoint(previousDay, trend, trend)
        for (p in sorted.drop(1)) {
            val gap = (p.epochDay - previousDay).coerceAtLeast(1)
            val effectiveAlpha = 1.0 - (1.0 - alpha).pow(gap.toDouble())
            trend += effectiveAlpha * (p.kg - trend)
            previousDay = p.epochDay
            result += TrendPoint(p.epochDay, p.kg, trend)
        }
        return result
    }

    /**
     * Rate of change of the trend in kg per week, from a least-squares line over the trend points
     * in the last [windowDays] days. Null when fewer than two points fall in the window.
     */
    fun weeklyRateKg(trend: List<TrendPoint>, windowDays: Int = 14): Double? {
        if (trend.isEmpty()) return null
        val lastDay = trend.maxOf { it.epochDay }
        val window = trend.filter { it.epochDay > lastDay - windowDays }
        if (window.size < 2) return null
        val xs = window.map { it.epochDay.toDouble() }
        val ys = window.map { it.trendKg }
        val meanX = xs.average()
        val meanY = ys.average()
        var num = 0.0
        var den = 0.0
        for (i in xs.indices) {
            num += (xs[i] - meanX) * (ys[i] - meanY)
            den += (xs[i] - meanX) * (xs[i] - meanX)
        }
        if (den == 0.0) return null
        return num / den * 7.0
    }
}
