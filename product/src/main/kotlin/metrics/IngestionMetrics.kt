package com.svebrant.metrics

import com.svebrant.repository.dto.ErrorDto
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

const val MAX_ERROR_SAMPLES = 5

class IngestionMetrics(
    val productsParsed: AtomicInteger = AtomicInteger(0),
    val productsIngested: AtomicInteger = AtomicInteger(0),
    val productsFailed: AtomicInteger = AtomicInteger(0),
    val productsDeduplicated: AtomicInteger = AtomicInteger(0),
    val discountsParsed: AtomicInteger = AtomicInteger(0),
    val discountsIngested: AtomicInteger = AtomicInteger(0),
    val discountsFailed: AtomicInteger = AtomicInteger(0),
    val discountsDeduplicated: AtomicInteger = AtomicInteger(0),
    val errorSamples: MutableList<ErrorDto> = Collections.synchronizedList(mutableListOf()),
) {
    fun snapshot(): MetricsSnapshot =
        MetricsSnapshot(
            productsParsed = productsParsed.get(),
            productsIngested = productsIngested.get(),
            productsFailed = productsFailed.get(),
            productsDeduplicated = productsDeduplicated.get(),
            discountsParsed = discountsParsed.get(),
            discountsIngested = discountsIngested.get(),
            discountsFailed = discountsFailed.get(),
            discountsDeduplicated = discountsDeduplicated.get(),
            errorSamples = synchronized(errorSamples) { errorSamples.toList() },
        )

    fun addErrorSample(
        fileName: String,
        lineNumber: Int,
        errorMessage: String,
        maxSamples: Int = 10,
    ) {
        synchronized(errorSamples) {
            if (errorSamples.size < maxSamples &&
                errorSamples.none { it.file == fileName && it.line == lineNumber }
            ) {
                errorSamples.add(
                    ErrorDto(
                        file = fileName,
                        line = lineNumber,
                        reason = errorMessage,
                    ),
                )
            }
        }
    }

    fun getSummary(isDryRun: Boolean = false): String {
        val dryRunPrefix = if (isDryRun) "[DRY RUN] " else ""
        return "$dryRunPrefix" +
            "Products: ${productsParsed.get()} parsed, " +
            "${productsIngested.get()} ingested, " +
            "${productsFailed.get()} failed, " +
            "${productsDeduplicated.get()} deduplicated - " +
            "Discounts: ${discountsParsed.get()} parsed, " +
            "${discountsIngested.get()} ingested, " +
            "${discountsFailed.get()} failed, " +
            "${discountsDeduplicated.get()} deduplicated"
    }

    data class MetricsSnapshot(
        val productsParsed: Int,
        val productsIngested: Int,
        val productsFailed: Int,
        val productsDeduplicated: Int,
        val discountsParsed: Int,
        val discountsIngested: Int,
        val discountsFailed: Int,
        val discountsDeduplicated: Int,
        val errorSamples: List<ErrorDto>,
    )
}
