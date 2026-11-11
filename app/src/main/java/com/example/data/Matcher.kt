package com.example.data

/**
 * Exact Kotlin port of `plan_matches` from `reference/sms_parse.py` and Section 4 of `APP_SIDE_APPROVAL_SPEC.md`.
 */
data class Deposit(
    val id: Long,
    val amountMinor: Long,
    val at: Long // epoch ms adjusted to server clock: receivedMs + serverOffsetMs
)

data class Item(
    val kind: String,      // "order" | "topup"
    val publicId: String,
    val dueMinor: Long,    // Rial amount expected to arrive by transfer
    val at: Long,          // created_ms on server
    val early: Boolean = false
)

object Matcher {
    /**
     * Pairs unused deposits with pending payments of the exact same amount.
     * Rules:
     * 1. Exact amount equality (`dueMinor == amountMinor`).
     * 2. Deposit can only pay an item created before or at the time the SMS arrived (`d.at >= i.at`).
     * 3. If `its.size <= ds.size` -> every waiting item is covered (`pairs += ds.zip(its)`).
     * 4. If `its.size > ds.size` -> ambiguous (`ambiguous += ds`), nothing approved automatically.
     */
    fun plan(
        deposits: List<Deposit>,
        items: List<Item>
    ): Pair<List<Pair<Deposit, Item>>, List<Deposit>> {
        val pairs = mutableListOf<Pair<Deposit, Item>>()
        val ambiguous = mutableListOf<Deposit>()
        for (amount in deposits.map { it.amountMinor }.toSortedSet()) {
            var ds = deposits.filter { it.amountMinor == amount }.sortedBy { it.at }   // قدیمی‌ترین اول
            var its = items.filter { it.dueMinor == amount }
            its = its.filter { i -> ds.any { d -> d.at >= i.at } }        // بعد از ساخته‌شدنش واریز شده
            ds = ds.filter { d -> its.any { i -> d.at >= i.at } }         // و واقعاً می‌تواند چیزی را بپردازد
            if (its.isEmpty()) continue
            its = its.sortedBy { it.at }
            if (its.size <= ds.size) {
                pairs += ds.zip(its)
            } else {
                ambiguous += ds
            }
        }
        return pairs to ambiguous
    }
}
