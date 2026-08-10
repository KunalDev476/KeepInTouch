package com.pragmaapps.keepintouch.domain.util

/** Simple Levenshtein-distance-based similarity, used only for the fuzzy-match tier. Pure/JVM-only. */
object NameSimilarity {

    /** Auto-attach threshold: a unique fuzzy match scoring at or above this is confident enough to act on. */
    const val AUTO_MATCH_THRESHOLD = 0.85

    /** Floor below which a fuzzy candidate isn't even offered for confirmation. */
    const val MIN_CANDIDATE_THRESHOLD = 0.6

    /** 1.0 = identical, 0.0 = completely different. Both inputs should already be normalized. */
    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val maxLen = maxOf(a.length, b.length)
        if (maxLen == 0) return 1.0
        val distance = levenshtein(a, b)
        return 1.0 - (distance.toDouble() / maxLen.toDouble())
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1,
                    dp[i][j - 1] + 1,
                    dp[i - 1][j - 1] + cost
                )
            }
        }
        return dp[a.length][b.length]
    }
}
