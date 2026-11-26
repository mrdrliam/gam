package com.kassa.habit.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kassa.habit.notify.Reminders
import com.kassa.habit.widget.HabitWidget
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.LocalDate
import java.time.LocalDateTime
import java.util.UUID
import java.util.concurrent.Executors

/** Where a deep link (widget, shortcut, notification) wants the app to go. */
enum class DeepLink { QUICK_LOG, PAUSE, CLOSE_DAY, REVIEW }

/**
 * Single source of truth: Compose-observable state, persisted as one JSON file on the phone.
 * All changes happen on the main thread; only the file writing runs in the background.
 */
object Store {
    var data by mutableStateOf(AppData())
        private set

    /** The current logical day. */
    var today by mutableStateOf(LocalDate.now())
        private set

    /** Hour of the day (0..23), for the coach's greeting. */
    var hour by mutableIntStateOf(12)
        private set

    /** Bumped when the app comes to the foreground so permission-dependent UI refreshes. */
    var resumeTick by mutableIntStateOf(0)
        private set

    /** The data file could not be read; a copy of it was kept aside. */
    var loadFailed by mutableStateOf(false)
        private set

    var pendingLink by mutableStateOf<DeepLink?>(null)

    private var file: File? = null
    private var appContext: Context? = null
    private val io = Executors.newSingleThreadExecutor()

    fun init(context: Context) {
        if (file != null) return
        appContext = context.applicationContext
        file = File(context.applicationContext.filesDir, "habit.json")
        load()
        tick()
    }

    /** Re-reads the clock. */
    fun tick() {
        val now = LocalDateTime.now()
        today = logicalDate(now, data.settings.dayStartHour)
        hour = now.hour
    }

    fun onResume() {
        tick()
        resumeTick++
    }

    private fun newId() = UUID.randomUUID().toString()

    // ---------------------------------------------------------------- habits

    fun newHabit(kind: Kind, title: String, emoji: String): Habit =
        Habit(id = newId(), kind = kind, title = title, emoji = emoji, created = today)

    fun fromTemplate(t: Template): Habit = newHabit(t.kind, t.title, t.emoji).copy(
        why = t.why, cue = t.cue, tiny = t.tiny, plan = t.plan, replacement = t.replacement,
        schedule = listOf(DaysChange(today, AllDays, t.target)),
    )

    /** Adds the habit, or replaces the one with the same id. */
    fun saveHabit(habit: Habit) = mutate { d ->
        val exists = d.habits.any { it.id == habit.id }
        d.copy(habits = if (exists) d.habits.map { if (it.id == habit.id) habit else it } else d.habits + habit)
    }

    fun setArchived(id: String, archived: Boolean) = mutate { d ->
        d.copy(habits = d.habits.map {
            if (it.id != id) it else if (archived) it.archive(today) else it.restore(today)
        })
    }

    /** Removes the habit and its history. Returns what is needed to put it back. */
    fun deleteHabit(id: String): () -> Unit {
        val before = data
        val habit = before.habit(id) ?: return {}
        val logs = before.logsOf(id)
        val linked = before.habits.filter { it.replacementId == id }.map { it.id }.toSet()
        mutate { d ->
            d.copy(
                habits = d.habits.filterNot { it.id == id }.map { if (it.id in linked) it.copy(replacementId = null) else it },
                logs = d.logs.filterNot { it.habitId == id },
            )
        }
        return {
            mutate { d ->
                if (d.habit(id) != null) d else d.copy(
                    habits = d.habits.map { if (it.id in linked) it.copy(replacementId = id) else it } + habit,
                    logs = d.logs + logs,
                )
            }
        }
    }

    // ---------------------------------------------------------------- logs

    private fun entry(habitId: String, date: LocalDate, type: LogType): Log {
        val now = LocalDateTime.now()
        // the time of day is only known for things logged on the day itself
        val minute = if (date == today) now.hour * 60 + now.minute else -1
        return Log(newId(), habitId, date, System.currentTimeMillis(), minute, type)
    }

    /** Records one more slip or resisted urge. */
    fun addLog(habitId: String, date: LocalDate, type: LogType): Log {
        val log = entry(habitId, date, type)
        mutate { it.copy(logs = it.logs + log) }
        return log
    }

    /**
     * Sets how a good habit went on [date] (null clears it). A once-a-day habit has one status per day.
     * For a counted habit DONE adds one more time and keeps the earlier ones.
     * Returns an action that restores what was there before.
     */
    fun setStatus(habitId: String, date: LocalDate, type: LogType?, triggers: Set<String> = emptySet()): () -> Unit {
        val counted = data.habit(habitId)?.countedOn(date) == true
        val old = replacedByStatus(data.logsOn(habitId, date), counted)
        val stored = if (counted && type == LogType.TINY) LogType.DONE else type
        val added = stored?.let { entry(habitId, date, it).copy(triggers = triggers) }
        val oldIds = old.map { it.id }.toSet()
        mutate { d -> d.copy(logs = d.logs.filterNot { it.id in oldIds } + listOfNotNull(added)) }
        return {
            mutate { d -> d.copy(logs = d.logs.filterNot { it.id == added?.id || it.id in oldIds } + old) }
        }
    }

    /** Takes back the latest time of a counted habit. Returns an undo, or null if there was none. */
    fun removeLastTime(habitId: String, date: LocalDate): (() -> Unit)? =
        data.logsOn(habitId, date).filter { it.isTime }.maxByOrNull { it.at }?.let { removeLog(it.id) }

    fun updateLog(log: Log) = mutate { d -> d.copy(logs = d.logs.map { if (it.id == log.id) log else it }) }

    fun removeLog(id: String): () -> Unit {
        val log = data.logs.firstOrNull { it.id == id } ?: return {}
        mutate { d -> d.copy(logs = d.logs.filterNot { it.id == id }) }
        return { mutate { d -> if (d.logs.any { it.id == id }) d else d.copy(logs = d.logs + log) } }
    }

    // ---------------------------------------------------------------- days and reviews

    fun closeDay(date: LocalDate, mood: Int, win: String, note: String) = mutate {
        it.copy(days = it.days + (date to DayNote(date, closed = true, mood = mood, win = win.trim(), note = note.trim())))
    }

    fun reopenDay(date: LocalDate) = mutate { d ->
        val old = d.days[date] ?: return@mutate d
        d.copy(days = d.days + (date to old.copy(closed = false)))
    }

    fun saveReview(review: Review) = mutate {
        val blank = review.wentWell.isBlank() && review.obstacle.isBlank() && review.change.isBlank() && review.rating == 0
        it.copy(
            reviews = if (blank) it.reviews - review.week
            else it.reviews + (review.week to review.copy(at = System.currentTimeMillis())),
        )
    }

    // ---------------------------------------------------------------- settings and backup

    fun updateSettings(transform: (Settings) -> Settings) {
        mutate { it.copy(settings = transform(it.settings)) }
        tick() // the start of the day may have changed
    }

    fun exportJson(): String = Codec.encode(data)

    /** Replaces all data with the backup. Returns false (and changes nothing) if it is not valid. */
    fun importJson(text: String): Boolean {
        val imported = try {
            Codec.decode(text)
        } catch (_: Exception) {
            return false
        }
        // the lock stays as it is on this phone: a file must not be able to switch it off
        val current = data.settings
        mutate { imported.copy(settings = imported.settings.copy(appLock = current.appLock, lockGraceSec = current.lockGraceSec)) }
        tick()
        return true
    }

    /** Erases habits and history; settings stay. */
    fun clearAll() = mutate { AppData(settings = it.settings.copy(seenBadges = emptySet())) }

    // ---------------------------------------------------------------- persistence

    private fun mutate(transform: (AppData) -> AppData) {
        val next = transform(data)
        if (next == data) return
        data = next
        save()
        appContext?.let {
            Reminders.schedule(it)
            Reminders.clearHandled(it)
            HabitWidget.updateAll(it)
        }
    }

    private fun save() {
        val f = file ?: return
        val snapshot = data // immutable, safe to hand to another thread
        io.execute {
            try {
                val tmp = File(f.parentFile, f.name + ".tmp")
                tmp.outputStream().use {
                    it.write(Codec.encode(snapshot).toByteArray(Charsets.UTF_8))
                    it.fd.sync()
                }
                Files.move(tmp.toPath(), f.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
            }
        }
    }

    /** Waits until pending writes are on disk (used by broadcast receivers before they finish). */
    fun flush() {
        try {
            io.submit {}.get()
        } catch (_: Exception) {
        }
    }

    private fun load() {
        val f = file ?: return
        if (!f.exists()) return
        try {
            data = Codec.decode(f.readText())
        } catch (_: Exception) {
            // keep the unreadable file: the next save would otherwise overwrite it for good
            try {
                f.copyTo(File(f.parentFile, "habit.json.broken-${System.currentTimeMillis()}"), overwrite = true)
            } catch (_: Exception) {
            }
            loadFailed = true
        }
    }
}
