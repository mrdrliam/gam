package com.kassa.habit.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** The one JSON format used both for the data file on the phone and for backup files. */
object Codec {
    private const val APP = "habit"
    private const val VERSION = 1

    fun encode(data: AppData): String {
        val root = JSONObject()
        root.put("app", APP)
        root.put("v", VERSION)
        root.put("settings", settingsToJson(data.settings))
        root.put("habits", array(data.habits.map(::habitToJson)))
        root.put("logs", array(data.logs.map(::logToJson)))
        root.put("days", array(data.days.values.sortedBy { it.date }.map {
            JSONObject().put("date", it.date.toString()).put("closed", it.closed).put("mood", it.mood)
                .put("win", it.win).put("note", it.note)
        }))
        root.put("reviews", array(data.reviews.values.sortedBy { it.week }.map {
            JSONObject().put("week", it.week.toString()).put("wentWell", it.wentWell).put("obstacle", it.obstacle)
                .put("change", it.change).put("rating", it.rating).put("at", it.at)
        }))
        return root.toString()
    }

    /** Throws if [text] is not a file of this app or is damaged. */
    fun decode(text: String): AppData {
        val root = JSONObject(text)
        require(root.getString("app") == APP) { "not a backup of this app" }
        require(root.getInt("v") <= VERSION) { "made by a newer version" }
        val habits = root.getJSONArray("habits").objects().map(::habitFromJson)
        val ids = habits.mapTo(HashSet()) { it.id }
        val logs = root.getJSONArray("logs").objects().map(::logFromJson).filter { it.habitId in ids }
        val days = root.optJSONArray("days").objects().map {
            DayNote(
                LocalDate.parse(it.getString("date")), it.optBoolean("closed"), it.optInt("mood").coerceIn(0, 5),
                it.optString("win"), it.optString("note"),
            )
        }.associateBy { it.date }
        val reviews = root.optJSONArray("reviews").objects().map {
            Review(
                LocalDate.parse(it.getString("week")), it.optString("wentWell"), it.optString("obstacle"),
                it.optString("change"), it.optInt("rating").coerceIn(0, 5), it.optLong("at"),
            )
        }.associateBy { it.week }
        return AppData(habits, logs, days, reviews, settingsFromJson(root.optJSONObject("settings") ?: JSONObject()))
    }

    private fun array(items: List<Any>) = JSONArray().also { a -> items.forEach { a.put(it) } }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).map { getJSONObject(it) }

    private fun JSONArray?.strings(): Set<String> =
        if (this == null) emptySet() else (0 until length()).mapTo(LinkedHashSet()) { getString(it) }

    private fun JSONArray?.ints(): Set<Int> =
        if (this == null) emptySet() else (0 until length()).mapTo(LinkedHashSet()) { getInt(it) }

    private fun JSONObject.intOrNull(key: String): Int? = if (!has(key) || isNull(key)) null else getInt(key)
    private fun JSONObject.textOrNull(key: String): String? = if (!has(key) || isNull(key)) null else getString(key)

    private fun habitToJson(h: Habit) = JSONObject()
        .put("id", h.id).put("kind", h.kind.name).put("title", h.title).put("emoji", h.emoji)
        .put("created", h.created.toString()).put("why", h.why).put("cue", h.cue).put("tiny", h.tiny)
        .put("plan", h.plan).put("replacement", h.replacement)
        .put("replacementId", h.replacementId ?: JSONObject.NULL)
        .put("schedule", array(h.schedule.map {
            JSONObject().put("from", it.from.toString()).put("days", array(it.days.sorted()))
                .apply { if (it.target > 1) put("target", it.target) }
        }))
        .put("weeklyLimit", h.weeklyLimit ?: JSONObject.NULL)
        .put("remindAt", h.remindAt ?: JSONObject.NULL)
        .put("archivedOn", h.archivedOn?.toString() ?: JSONObject.NULL)

    private fun habitFromJson(o: JSONObject): Habit {
        val created = LocalDate.parse(o.getString("created"))
        val schedule = o.optJSONArray("schedule").objects()
            .map {
                DaysChange(
                    LocalDate.parse(it.getString("from")),
                    it.optJSONArray("days").ints().filter { d -> d in 0..6 }.toSet(),
                    it.optInt("target", 1).coerceIn(1, 99),
                )
            }
            .sortedBy { it.from }
        return Habit(
            id = o.getString("id"),
            kind = Kind.valueOf(o.getString("kind")),
            title = o.getString("title"),
            emoji = o.optString("emoji", "🎯"),
            created = created,
            why = o.optString("why"),
            cue = o.optString("cue"),
            tiny = o.optString("tiny"),
            plan = o.optString("plan"),
            replacement = o.optString("replacement"),
            replacementId = o.textOrNull("replacementId"),
            schedule = schedule.ifEmpty { listOf(DaysChange(created, AllDays)) },
            weeklyLimit = o.intOrNull("weeklyLimit"),
            remindAt = o.intOrNull("remindAt")?.coerceIn(0, 1439),
            archivedOn = o.textOrNull("archivedOn")?.let(LocalDate::parse),
        )
    }

    private fun logToJson(l: Log) = JSONObject()
        .put("id", l.id).put("habit", l.habitId).put("date", l.date.toString()).put("at", l.at)
        .put("minute", l.minute).put("type", l.type.name).apply {
            // empty details are left out to keep the file small
            if (l.mood != 0) put("mood", l.mood)
            if (l.triggers.isNotEmpty()) put("triggers", array(l.triggers.toList()))
            if (l.place.isNotEmpty()) put("place", l.place)
            if (l.people.isNotEmpty()) put("people", l.people)
            if (l.note.isNotEmpty()) put("note", l.note)
            if (l.nextPlan.isNotEmpty()) put("nextPlan", l.nextPlan)
        }

    private fun logFromJson(o: JSONObject) = Log(
        id = o.getString("id"),
        habitId = o.getString("habit"),
        date = LocalDate.parse(o.getString("date")),
        at = o.optLong("at"),
        minute = o.optInt("minute", -1),
        type = LogType.valueOf(o.getString("type")),
        mood = o.optInt("mood").coerceIn(0, 5),
        triggers = o.optJSONArray("triggers").strings(),
        place = o.optString("place"),
        people = o.optString("people"),
        note = o.optString("note"),
        nextPlan = o.optString("nextPlan"),
    )

    // an alarm that is switched off is stored as -1, so that "missing" can still mean "default"
    private fun settingsToJson(s: Settings) = JSONObject()
        .put("name", s.name).put("theme", s.theme).put("dayStartHour", s.dayStartHour).put("notify", s.notify)
        .put("eveningAt", s.eveningAt ?: -1).put("reviewAt", s.reviewAt ?: -1)
        .put("appLock", s.appLock).put("lockGraceSec", s.lockGraceSec).put("onboarded", s.onboarded)
        .put("seenBadges", array(s.seenBadges.sorted()))

    private fun settingsFromJson(o: JSONObject): Settings {
        val d = Settings()
        fun alarm(key: String, default: Int?): Int? {
            val v = o.optInt(key, default ?: -1)
            return if (v < 0) null else v.coerceAtMost(1439)
        }
        return Settings(
            name = o.optString("name", d.name),
            theme = o.optInt("theme", d.theme).coerceIn(0, 2),
            dayStartHour = o.optInt("dayStartHour", d.dayStartHour).coerceIn(0, 6),
            notify = o.optBoolean("notify", d.notify),
            eveningAt = alarm("eveningAt", d.eveningAt),
            reviewAt = alarm("reviewAt", d.reviewAt),
            appLock = o.optBoolean("appLock", d.appLock),
            lockGraceSec = o.optInt("lockGraceSec", d.lockGraceSec).coerceIn(5, 3600),
            onboarded = o.optBoolean("onboarded", d.onboarded),
            seenBadges = o.optJSONArray("seenBadges").strings(),
        )
    }
}
