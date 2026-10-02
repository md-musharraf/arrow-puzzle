package com.example.arrowpuzzle.data

import android.content.Context
import android.content.SharedPreferences

class PreferencesManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences("arrow_puzzle_prefs", Context.MODE_PRIVATE)

    var highestUnlockedLevel: Int
        get() = prefs.getInt(KEY_UNLOCKED_LEVEL, 1)
        set(value) = prefs.edit().putInt(KEY_UNLOCKED_LEVEL, value.coerceAtLeast(highestUnlockedLevel)).apply()

    var isSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOUND_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_SOUND_ENABLED, value).apply()

    var isHapticsEnabled: Boolean
        get() = prefs.getBoolean(KEY_HAPTICS_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_HAPTICS_ENABLED, value).apply()

    var endlessHighScore: Int
        get() = prefs.getInt(KEY_ENDLESS_HIGH_SCORE, 0)
        set(value) = prefs.edit().putInt(KEY_ENDLESS_HIGH_SCORE, value.coerceAtLeast(endlessHighScore)).apply()

    var availableHints: Int
        get() = prefs.getInt(KEY_HINTS, 5)
        set(value) = prefs.edit().putInt(KEY_HINTS, value.coerceAtLeast(0)).apply()

    fun getLevelStars(levelNum: Int): Int {
        return prefs.getInt("${KEY_STARS_PREFIX}$levelNum", 0)
    }

    fun setLevelStars(levelNum: Int, stars: Int) {
        val current = getLevelStars(levelNum)
        val earned = stars.coerceIn(0, 3)
        if (earned > current) {
            prefs.edit()
                .putInt("${KEY_STARS_PREFIX}$levelNum", earned)
                .putInt(KEY_TOTAL_STARS, totalStars + (earned - current))
                .apply()
        }
    }

    /**
     * Running total, kept as the stars are earned.
     *
     * The campaign has no last level, so summing every level played to get this figure was a
     * main-thread loop whose length grew without bound as a player progressed — a menu that got
     * measurably slower the further someone got.
     */
    val totalStars: Int
        get() {
            if (!prefs.contains(KEY_TOTAL_STARS)) migrateTotalStars()
            return prefs.getInt(KEY_TOTAL_STARS, 0)
        }

    /**
     * Backfills the total for someone who was already playing before it was kept.
     *
     * Runs once: the old per-level keys are the only record of what they have earned, so the sum
     * has to happen one final time before the counter can take over.
     */
    private fun migrateTotalStars() {
        var total = 0
        for (level in 1..highestUnlockedLevel) total += getLevelStars(level)
        prefs.edit().putInt(KEY_TOTAL_STARS, total).apply()
    }

    // --- Daily puzzle streak ---

    val dailyStreak: Int
        get() = prefs.getInt(KEY_DAILY_STREAK, 0)

    private val lastDailyDay: Long
        get() = prefs.getLong(KEY_DAILY_DAY, Long.MIN_VALUE)

    fun isDailySolved(day: Long): Boolean = lastDailyDay == day

    /** Extends the streak only when yesterday's puzzle was the previous one solved. */
    fun recordDailySolved(day: Long) {
        if (lastDailyDay == day) return
        val streak = if (lastDailyDay == day - 1) dailyStreak + 1 else 1
        prefs.edit().putInt(KEY_DAILY_STREAK, streak).putLong(KEY_DAILY_DAY, day).apply()
    }

    fun resetAllProgress() {
        prefs.edit().clear().apply()
    }

    companion object {
        private const val KEY_UNLOCKED_LEVEL = "key_unlocked_level"
        private const val KEY_SOUND_ENABLED = "key_sound_enabled"
        private const val KEY_HAPTICS_ENABLED = "key_haptics_enabled"
        private const val KEY_ENDLESS_HIGH_SCORE = "key_endless_high_score"
        private const val KEY_HINTS = "key_available_hints"
        private const val KEY_STARS_PREFIX = "key_level_stars_"
        private const val KEY_TOTAL_STARS = "key_total_stars"
        private const val KEY_DAILY_STREAK = "key_daily_streak"
        private const val KEY_DAILY_DAY = "key_daily_day"
    }
}
