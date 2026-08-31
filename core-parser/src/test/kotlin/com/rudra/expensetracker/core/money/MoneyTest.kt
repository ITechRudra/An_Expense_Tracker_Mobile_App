package com.rudra.expensetracker.core.money

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class MoneyTest {

    @Test
    fun `parses plain and Indian-grouped amounts`() {
        assertThat(Money.parseOrNull("32.00")?.minorUnits).isEqualTo(3200L)
        assertThat(Money.parseOrNull("2000.00")?.minorUnits).isEqualTo(200_000L)
        assertThat(Money.parseOrNull("11213.98")?.minorUnits).isEqualTo(1_121_398L)
        assertThat(Money.parseOrNull("1,23,456.78")?.minorUnits).isEqualTo(12_345_678L)
        assertThat(Money.parseOrNull("45,000")?.minorUnits).isEqualTo(4_500_000L)
    }

    @Test
    fun `strips currency prefixes and sentence punctuation`() {
        assertThat(Money.parseOrNull("Rs 32.00.")?.minorUnits).isEqualTo(3200L)
        assertThat(Money.parseOrNull("INR 500")?.minorUnits).isEqualTo(50_000L)
        assertThat(Money.parseOrNull("₹1,250.50")?.minorUnits).isEqualTo(125_050L)
    }

    @Test
    fun `single decimal digit amounts are read as tenths not hundredths`() {
        assertThat(Money.parseOrNull("150.0")?.minorUnits).isEqualTo(15_000L)
        assertThat(Money.parseOrNull("150.5")?.minorUnits).isEqualTo(15_050L)
    }

    @Test
    fun `rejects tokens that are not clean amounts`() {
        assertThat(Money.parseOrNull("abc")).isNull()
        assertThat(Money.parseOrNull("")).isNull()
        assertThat(Money.parseOrNull("12.345")).isNull()
        assertThat(Money.parseOrNull("31-08-26")).isNull()
    }

    @Test
    fun `arithmetic stays exact across many additions`() {
        // 0.1 + 0.2 style drift would surface here if this were a Double.
        val total = generateSequence { Money(10) }.take(1000).fold(Money.ZERO) { a, b -> a + b }
        assertThat(total.minorUnits).isEqualTo(10_000L)
        assertThat(total.toBigDecimal().toPlainString()).isEqualTo("100.00")
    }

    @Test
    fun `percentOf is zero-safe`() {
        assertThat(Money(500).percentOf(Money.ZERO)).isEqualTo(0)
        assertThat(Money(2500).percentOf(Money(5000))).isEqualTo(50)
    }
}
