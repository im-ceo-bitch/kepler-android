package com.iumrah.beta.data.pricing

import com.iumrah.beta.core.network.APIClient
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.time.Duration
import java.util.Locale
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Same Worker-proxied CBU feed as iOS. Never used as an authoritative package price. */
class LocalFXRateService(private val api: APIClient = APIClient()) {
    @Serializable
    private data class CBUEntry(@SerialName("Ccy") val ccy: String, @SerialName("Rate") val rate: String)
    private val mutex = Mutex()
    private var cached: Map<String, BigDecimal> = emptyMap()
    private var cachedAt: Instant? = null

    suspend fun usd(amount: BigDecimal, rawCurrency: String): BigDecimal {
        require(amount >= BigDecimal.ZERO) { "Некорректная сумма компонента." }
        val currency = rawCurrency.uppercase(Locale.ROOT)
        if (currency == "USD") return amount
        return mutex.withLock {
            val now = Instant.now()
            val age = cachedAt?.let { Duration.between(it, now) }
            if (age == null || age.isNegative || age >= Duration.ofHours(6) || cached.size <= 1) {
                val rows: List<CBUEntry> = api.get("/api/services/fx", timeoutSeconds = 8)
                val next = rows.mapNotNull { row ->
                    val rate = row.rate.replace(" ", "").replace(',', '.').toBigDecimalOrNull()
                    rate?.takeIf { it.signum() > 0 }?.let { row.ccy.uppercase(Locale.ROOT) to it }
                }.toMap()
                check(next["USD"] != null) { "Курс валют временно недоступен. Повторите расчёт." }
                cached = next
                cachedAt = Instant.now()
            }
            val sourceUZS = cached[currency] ?: error("Не удалось пересчитать валюту $currency в USD.")
            val usdUZS = requireNotNull(cached["USD"])
            amount.multiply(sourceUZS, MathContext.DECIMAL128).divide(usdUZS, MathContext.DECIMAL128)
        }
    }
}
