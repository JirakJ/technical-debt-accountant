package com.jakubjirak.debtaccountant.health

/**
 * Letter grade derived from a 0–100 [DebtHealth.score]. Higher scores (less
 * debt pressure) map to better grades. The thresholds are inclusive lower
 * bounds, so a score of exactly 90 is an [A].
 */
enum class DebtGrade(val letter: String, val label: String, val minScore: Int) {
    A("A", "Excellent", 90),
    B("B", "Good", 75),
    C("C", "Fair", 60),
    D("D", "At Risk", 40),
    F("F", "Critical", 0);

    companion object {
        /** Maps a 0–100 [score] to the highest grade whose threshold it meets. */
        fun fromScore(score: Int): DebtGrade =
            entries.firstOrNull { score >= it.minScore } ?: F
    }
}
