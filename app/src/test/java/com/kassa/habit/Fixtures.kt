package com.kassa.habit

import com.kassa.habit.data.AppData
import com.kassa.habit.data.DayNote
import com.kassa.habit.data.Habit
import com.kassa.habit.data.Kind
import com.kassa.habit.data.Log
import com.kassa.habit.data.LogType
import java.time.LocalDate

/** Saturday 30 Aban 1405: a convenient start of a week. */
val SAT: LocalDate = LocalDate.of(2026, 11, 21)

fun day(offset: Int): LocalDate = SAT.plusDays(offset.toLong())

fun good(id: String = "g", created: LocalDate = SAT) = Habit(id, Kind.BUILD, "مطالعه", "📖", created)

fun bad(id: String = "q", created: LocalDate = SAT) = Habit(id, Kind.QUIT, "نوشابه", "🥤", created)

private var counter = 0

fun log(
    habit: String, offset: Int, type: LogType,
    triggers: Set<String> = emptySet(), minute: Int = 14 * 60, plan: String = "",
) = Log("l${counter++}", habit, day(offset), 0, minute, type, triggers = triggers, nextPlan = plan)

fun closed(vararg offsets: Int) = offsets.associate { day(it) to DayNote(day(it), closed = true) }

fun data(vararg habits: Habit, logs: List<Log> = emptyList(), days: Map<LocalDate, DayNote> = emptyMap()) =
    AppData(habits.toList(), logs, days)
