
package com.pfms.app.domain.usecase

import com.pfms.app.domain.model.Income
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Unit tests for the income summary calculation logic
 * used in IncomeViewModel: realized income, freelance
 * pending totals, crypto gain/loss, and the lkrAmount
 * helper that prevents the unconverted-USD fallback bug.
 *
 * These tests validate the same algorithms used inside
 * IncomeViewModel without needing coroutines or mocked
 * repositories.
 */
class IncomeSummaryCalculationTest {

    // ───────────────────────────────────────────────────
    // lkrAmount helper (mirrored from IncomeViewModel)
    // ───────────────────────────────────────────────────

    /**
     * Returns the LKR-equivalent amount for totals.
     * LKR entries → amountMinor.
     * USD entries with conversion → amountLkrMinor.
     * USD entries WITHOUT conversion → 0L.
     */
    private fun Income.lkrAmount(): Long = when {
        currency == "LKR" -> amountMinor
        amountLkrMinor != null -> amountLkrMinor
        else -> 0L
    }

    // ═══════════════════════════════════════════════════
    // 1. lkrAmount HELPER TESTS
    // ═══════════════════════════════════════════════════

    @Test
    fun `lkrAmount returns amountMinor for LKR entries`() {
        val income = Income(
            amountMinor = 5000000L,
            currency = "LKR"
        )
        assertEquals(5000000L, income.lkrAmount())
    }

    @Test
    fun `lkrAmount returns amountLkrMinor for converted USD`() {
        val income = Income(
            amountMinor = 10000L,    // 100 USD
            currency = "USD",
            amountLkrMinor = 3054200L // 30,542 LKR
        )
        assertEquals(3054200L, income.lkrAmount())
    }

    @Test
    fun `lkrAmount returns 0 for unconverted USD — prevents fallback bug`() {
        val income = Income(
            amountMinor = 10000L,    // 100 USD in cents
            currency = "USD",
            amountLkrMinor = null    // No conversion stored
        )
        // Previously this would have returned 10000L (treating
        // USD cents as LKR cents), which is the bug we fixed.
        assertEquals(0L, income.lkrAmount())
    }

    // ═══════════════════════════════════════════════════
    // 2. REALIZED INCOME CALCULATION TESTS
    // ═══════════════════════════════════════════════════

    private fun calculateRealizedIncome(records: List<Income>): Long {
        val nonCryptoRealized = records.filter {
            if (it.source == "Freelance") it.paymentStatus == "Received"
            else it.source != "Crypto"
        }.sumOf { it.lkrAmount() }

        val cryptoGains = records
            .filter { it.source == "Crypto" && it.cryptoType == "Gain" }
            .sumOf { it.lkrAmount() }

        val cryptoLosses = records
            .filter { it.source == "Crypto" && it.cryptoType == "Loss" }
            .sumOf { it.lkrAmount() }

        return nonCryptoRealized + cryptoGains - cryptoLosses
    }

    @Test
    fun `realized income includes salary`() {
        val records = listOf(
            Income(
                source = "Salary",
                amountMinor = 10000000L,
                currency = "LKR"
            )
        )
        assertEquals(10000000L, calculateRealizedIncome(records))
    }

    @Test
    fun `realized income excludes unpaid freelance`() {
        val records = listOf(
            Income(
                source = "Salary",
                amountMinor = 10000000L,
                currency = "LKR"
            ),
            Income(
                source = "Freelance",
                amountMinor = 5000000L,
                currency = "LKR",
                paymentStatus = "Expected",
                projectName = "Test Project"
            )
        )
        // Salary only — freelance Expected is excluded
        assertEquals(10000000L, calculateRealizedIncome(records))
    }

    @Test
    fun `realized income includes paid freelance`() {
        val records = listOf(
            Income(
                source = "Freelance",
                amountMinor = 5000000L,
                currency = "LKR",
                paymentStatus = "Received",
                projectName = "Test Project"
            )
        )
        assertEquals(5000000L, calculateRealizedIncome(records))
    }

    @Test
    fun `realized income adds crypto gains`() {
        val records = listOf(
            Income(
                source = "Salary",
                amountMinor = 10000000L,
                currency = "LKR"
            ),
            Income(
                source = "Crypto",
                amountMinor = 2000000L,
                currency = "LKR",
                cryptoType = "Gain",
                cryptoAsset = "BTC"
            )
        )
        // 10,000,000 + 2,000,000 = 12,000,000
        assertEquals(12000000L, calculateRealizedIncome(records))
    }

    @Test
    fun `realized income deducts crypto losses`() {
        val records = listOf(
            Income(
                source = "Salary",
                amountMinor = 10000000L,
                currency = "LKR"
            ),
            Income(
                source = "Crypto",
                amountMinor = 3000000L,
                currency = "LKR",
                cryptoType = "Loss",
                cryptoAsset = "ETH"
            )
        )
        // 10,000,000 - 3,000,000 = 7,000,000
        assertEquals(7000000L, calculateRealizedIncome(records))
    }

    @Test
    fun `realized income with mixed crypto gains and losses`() {
        val records = listOf(
            Income(
                source = "Salary",
                amountMinor = 10000000L,
                currency = "LKR"
            ),
            Income(
                source = "Crypto",
                amountMinor = 5000000L,
                currency = "LKR",
                cryptoType = "Gain",
                cryptoAsset = "BTC"
            ),
            Income(
                source = "Crypto",
                amountMinor = 2000000L,
                currency = "LKR",
                cryptoType = "Loss",
                cryptoAsset = "ETH"
            )
        )
        // 10,000,000 + 5,000,000 - 2,000,000 = 13,000,000
        assertEquals(13000000L, calculateRealizedIncome(records))
    }

    @Test
    fun `realized income excludes unconverted USD entries`() {
        val records = listOf(
            Income(
                source = "Salary",
                amountMinor = 10000000L,
                currency = "LKR"
            ),
            Income(
                source = "Freelance",
                amountMinor = 10000L,    // 100 USD cents
                currency = "USD",
                amountLkrMinor = null,   // No conversion
                paymentStatus = "Received",
                projectName = "USD Client"
            )
        )
        // Only LKR salary counts; unconverted USD = 0L
        assertEquals(10000000L, calculateRealizedIncome(records))
    }

    @Test
    fun `realized income includes converted USD entries`() {
        val records = listOf(
            Income(
                source = "Google AdSense",
                amountMinor = 10000L,         // 100 USD
                currency = "USD",
                amountLkrMinor = 3054200L      // Converted at 305.42
            )
        )
        assertEquals(3054200L, calculateRealizedIncome(records))
    }

    // ═══════════════════════════════════════════════════
    // 3. FREELANCE PENDING SUMMARY TESTS
    // ═══════════════════════════════════════════════════

    private fun calculatePendingFreelance(
        records: List<Income>
    ): Pair<Long, Int> {
        val pending = records.filter {
            it.source == "Freelance" &&
                    it.paymentStatus == "Expected"
        }
        val totalPending = pending.sumOf { it.lkrAmount() }
        return Pair(totalPending, pending.size)
    }

    @Test
    fun `pending freelance counts only Expected entries`() {
        val records = listOf(
            Income(
                source = "Freelance",
                amountMinor = 5000000L,
                currency = "LKR",
                paymentStatus = "Expected",
                projectName = "Project A"
            ),
            Income(
                source = "Freelance",
                amountMinor = 3000000L,
                currency = "LKR",
                paymentStatus = "Received",
                projectName = "Project B"
            )
        )
        val (total, count) = calculatePendingFreelance(records)
        assertEquals(5000000L, total)
        assertEquals(1, count)
    }

    @Test
    fun `pending freelance excludes unconverted USD`() {
        val records = listOf(
            Income(
                source = "Freelance",
                amountMinor = 10000L,       // 100 USD
                currency = "USD",
                amountLkrMinor = null,      // Not converted
                paymentStatus = "Expected",
                projectName = "Foreign Client"
            )
        )
        val (total, count) = calculatePendingFreelance(records)
        // Total should be 0 (not 10000), count should still be 1
        assertEquals(0L, total)
        assertEquals(1, count)
    }

    // ═══════════════════════════════════════════════════
    // 4. CRYPTO SUMMARY TESTS
    // ═══════════════════════════════════════════════════

    private data class CryptoSummary(
        val gains: Long,
        val losses: Long,
        val net: Long,
        val ytdGains: Long,
        val ytdLosses: Long,
        val ytdNet: Long
    )

    private fun calculateCryptoSummary(
        records: List<Income>,
        currentYear: String = "2026"
    ): CryptoSummary {
        val cryptoRecords = records.filter { it.source == "Crypto" }
        val gains = cryptoRecords
            .filter { it.cryptoType == "Gain" }
            .sumOf { it.lkrAmount() }
        val losses = cryptoRecords
            .filter { it.cryptoType == "Loss" }
            .sumOf { it.lkrAmount() }

        val ytdRecords = cryptoRecords.filter {
            it.receivedDate.startsWith(currentYear)
        }
        val ytdGains = ytdRecords
            .filter { it.cryptoType == "Gain" }
            .sumOf { it.lkrAmount() }
        val ytdLosses = ytdRecords
            .filter { it.cryptoType == "Loss" }
            .sumOf { it.lkrAmount() }

        return CryptoSummary(
            gains = gains,
            losses = losses,
            net = gains - losses,
            ytdGains = ytdGains,
            ytdLosses = ytdLosses,
            ytdNet = ytdGains - ytdLosses
        )
    }

    @Test
    fun `crypto summary separates gains and losses`() {
        val records = listOf(
            Income(
                source = "Crypto",
                amountMinor = 5000000L,
                currency = "LKR",
                cryptoType = "Gain",
                cryptoAsset = "BTC",
                receivedDate = "2026-10-01"
            ),
            Income(
                source = "Crypto",
                amountMinor = 2000000L,
                currency = "LKR",
                cryptoType = "Loss",
                cryptoAsset = "ETH",
                receivedDate = "2026-10-05"
            )
        )
        val summary = calculateCryptoSummary(records)
        assertEquals(5000000L, summary.gains)
        assertEquals(2000000L, summary.losses)
        assertEquals(3000000L, summary.net)
    }

    @Test
    fun `crypto YTD filters by current year`() {
        val records = listOf(
            Income(
                source = "Crypto",
                amountMinor = 5000000L,
                currency = "LKR",
                cryptoType = "Gain",
                cryptoAsset = "BTC",
                receivedDate = "2026-06-15"
            ),
            Income(
                source = "Crypto",
                amountMinor = 3000000L,
                currency = "LKR",
                cryptoType = "Gain",
                cryptoAsset = "ETH",
                receivedDate = "2025-12-01"  // Previous year
            )
        )
        val summary = calculateCryptoSummary(records, currentYear = "2026")
        assertEquals(8000000L, summary.gains)     // All-time gains
        assertEquals(5000000L, summary.ytdGains)  // Only 2026
    }

    @Test
    fun `crypto summary with unconverted USD returns 0 for those`() {
        val records = listOf(
            Income(
                source = "Crypto",
                amountMinor = 10000L,       // 100 USD
                currency = "USD",
                amountLkrMinor = null,      // No conversion
                cryptoType = "Gain",
                cryptoAsset = "BTC",
                receivedDate = "2026-10-01"
            ),
            Income(
                source = "Crypto",
                amountMinor = 2000000L,
                currency = "LKR",
                cryptoType = "Loss",
                cryptoAsset = "ETH",
                receivedDate = "2026-10-05"
            )
        )
        val summary = calculateCryptoSummary(records)
        assertEquals(0L, summary.gains)          // Unconverted USD = 0
        assertEquals(2000000L, summary.losses)
        assertEquals(-2000000L, summary.net)     // 0 - 2,000,000
    }

    @Test
    fun `crypto summary with converted USD includes conversion`() {
        val records = listOf(
            Income(
                source = "Crypto",
                amountMinor = 10000L,          // 100 USD
                currency = "USD",
                amountLkrMinor = 3054200L,     // Converted
                cryptoType = "Gain",
                cryptoAsset = "BTC",
                receivedDate = "2026-10-01"
            )
        )
        val summary = calculateCryptoSummary(records)
        assertEquals(3054200L, summary.gains)
        assertEquals(0L, summary.losses)
        assertEquals(3054200L, summary.net)
    }

    // ═══════════════════════════════════════════════════
    // 5. COMPREHENSIVE MIXED-SCENARIO TESTS
    // ═══════════════════════════════════════════════════

    @Test
    fun `full scenario with all income types`() {
        val records = listOf(
            // Salary: 100,000 LKR
            Income(
                source = "Salary",
                amountMinor = 10000000L,
                currency = "LKR",
                receivedDate = "2026-10-01"
            ),
            // Freelance Received: 50,000 LKR
            Income(
                source = "Freelance",
                amountMinor = 5000000L,
                currency = "LKR",
                paymentStatus = "Received",
                projectName = "Web App",
                receivedDate = "2026-10-05"
            ),
            // Freelance Expected: 30,000 LKR (excluded)
            Income(
                source = "Freelance",
                amountMinor = 3000000L,
                currency = "LKR",
                paymentStatus = "Expected",
                projectName = "Mobile App",
                receivedDate = "2026-10-10"
            ),
            // Crypto Gain: 20,000 LKR
            Income(
                source = "Crypto",
                amountMinor = 2000000L,
                currency = "LKR",
                cryptoType = "Gain",
                cryptoAsset = "BTC",
                receivedDate = "2026-10-08"
            ),
            // Crypto Loss: 5,000 LKR
            Income(
                source = "Crypto",
                amountMinor = 500000L,
                currency = "LKR",
                cryptoType = "Loss",
                cryptoAsset = "ETH",
                receivedDate = "2026-10-09"
            ),
            // Google AdSense USD (unconverted)
            Income(
                source = "Google AdSense",
                amountMinor = 5000L,        // 50 USD
                currency = "USD",
                amountLkrMinor = null,
                receivedDate = "2026-10-11"
            )
        )

        // Realized = Salary + Freelance Received + AdSense(0) + Gains - Losses
        //          = 10,000,000 + 5,000,000 + 0 + 2,000,000 - 500,000
        //          = 16,500,000
        assertEquals(16500000L, calculateRealizedIncome(records))

        // Pending freelance = 3,000,000 (1 record)
        val (pendingTotal, pendingCount) = calculatePendingFreelance(records)
        assertEquals(3000000L, pendingTotal)
        assertEquals(1, pendingCount)

        // Crypto summary
        val crypto = calculateCryptoSummary(records)
        assertEquals(2000000L, crypto.gains)
        assertEquals(500000L, crypto.losses)
        assertEquals(1500000L, crypto.net)
    }

    @Test
    fun `empty records returns all zeros`() {
        val records = emptyList<Income>()
        assertEquals(0L, calculateRealizedIncome(records))

        val (pendingTotal, pendingCount) = calculatePendingFreelance(records)
        assertEquals(0L, pendingTotal)
        assertEquals(0, pendingCount)

        val crypto = calculateCryptoSummary(records)
        assertEquals(0L, crypto.gains)
        assertEquals(0L, crypto.losses)
        assertEquals(0L, crypto.net)
    }
}
