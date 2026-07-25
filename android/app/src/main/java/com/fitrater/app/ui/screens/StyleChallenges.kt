package com.fitrater.app.ui.screens

import java.time.LocalDate
import java.time.temporal.WeekFields
import java.util.Locale

/** Deterministic weekly challenges cycled by ISO week-of-year. */
object StyleChallenges {
    val list: List<String> = listOf(
        "Try monochrome for a day.",
        "Add one accessory you never wear.",
        "Layer two tones from the same family.",
        "Ground your fit with brown shoes.",
        "One vintage piece, one modern.",
        "Wear a color you rarely reach for.",
        "Cuff your sleeves.",
        "Skip black for a week.",
        "Match your bag to your belt.",
        "Try a shirt tucked in properly.",
        "Wear texture you can feel from across the room.",
        "Fewer buttons open, more discipline.",
    )

    fun current(date: LocalDate = LocalDate.now()): String {
        val week = date.get(WeekFields.ISO.weekOfWeekBasedYear())
        return list[((week % list.size) + list.size) % list.size]
    }
}
