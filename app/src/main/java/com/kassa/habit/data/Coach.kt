package com.kassa.habit.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class CoachMessage(val emoji: String, val title: String, val text: String)

data class Lesson(val emoji: String, val title: String, val body: String)

/**
 * The in-app coach: fixed, rule-based Persian messages (nothing is sent anywhere).
 * The tone is always the same: honest, warm, no blaming.
 */
object Coach {
    val triggers = listOf(
        "خستگی", "عصبانیت", "استرس", "بی‌حوصلگی", "ناراحتی", "تنهایی", "گرسنگی", "عجله",
        "کم‌خوابی", "گوشی و شبکه‌ها", "بعد از غذا", "جمع دوستان", "بحث و مخالفت", "حرفم قطع شد",
    )
    val skipReasons = listOf("وقت نداشتم", "خسته بودم", "فراموش کردم", "حوصله نداشتم", "بیمار بودم", "سفر یا مهمانی")
    val places = listOf("خانه", "محل کار", "بیرون", "مسیر", "مهمانی")
    val companions = listOf("تنها", "خانواده", "دوستان", "همکاران", "غریبه‌ها")

    private val tips = mapOf(
        "خستگی" to "تصمیم‌های سخت را به وقت خستگی نسپار؛ پیش از آن استراحت کوتاهی بگذار.",
        "عصبانیت" to "پیش از هر واکنش یک نفس عمیق بکش و تا ده بشمار.",
        "استرس" to "دو دقیقه آرام نفس بکش (بازدم بلندتر از دم) و بعد تصمیم بگیر.",
        "بی‌حوصلگی" to "یک جایگزین کوچک و آماده داشته باش: قدم زدن، چند صفحه کتاب، یک تماس.",
        "ناراحتی" to "حس را اسم‌گذاری کن («الان ناراحتم»). نوشتنش هم کمک می‌کند.",
        "تنهایی" to "به یک دوست پیام بده یا چند دقیقه از خانه بیرون برو.",
        "گرسنگی" to "یک خوراکی سالم دم دست بگذار تا گرسنگی برایت تصمیم نگیرد.",
        "عجله" to "چند دقیقه زودتر شروع کن؛ عجله جای فکر را تنگ می‌کند.",
        "کم‌خوابی" to "ساعت خواب ثابت، پایه‌ی خویشتن‌داری فرداست.",
        "گوشی و شبکه‌ها" to "گوشی را دور از دسترس بگذار یا اعلان‌های غیرضروری را خاموش کن.",
        "بعد از غذا" to "بلافاصله بعد از غذا جایگزینت را آماده کن؛ مثلاً چای یا آب.",
        "جمع دوستان" to "پیش از رفتن تصمیم بگیر چه می‌کنی و یک جمله‌ی «نه، ممنون» آماده داشته باش.",
        "بحث و مخالفت" to "اول حرف طرف مقابل را در یک جمله تکرار کن، بعد نظرت را بگو.",
        "حرفم قطع شد" to "بگو «اجازه بده حرفم تمام شود» و پیش از ادامه یک نفس بکش.",
        "وقت نداشتم" to "زمانش را به یک کار ثابت روزانه بچسبان، یا نسخه‌ی دو دقیقه‌ای را انجام بده.",
        "خسته بودم" to "آن را به ساعتی ببر که انرژی بیشتری داری، یا کوچک‌ترش کن.",
        "فراموش کردم" to "یادآور بگذار و وسایلش را جلوی چشم قرار بده.",
        "حوصله نداشتم" to "فقط شروعش کن: دو دقیقه. معمولاً شروع سخت‌ترین قسمت است.",
    )

    fun tipFor(trigger: String): String? = tips[trigger]

    val lessons = listOf(
        Lesson("🗺️", "برنامه‌ی «اگر، آنگاه»", "به‌جای «بیشتر ورزش می‌کنم» بگو «اگر ساعت ۷ از کار برگشتم، ۱۵ دقیقه پیاده‌روی می‌کنم». پژوهش‌ها نشان داده‌اند مشخص کردن زمان و موقعیت، احتمال انجام کار را بالا می‌برد."),
        Lesson("👣", "کوچک شروع کن", "عادت اول باید آن‌قدر آسان باشد که در بدترین روز هم بتوانی انجامش دهی. دو صفحه کتاب از هیچ بهتر است و هویت «من اهل مطالعه‌ام» را زنده نگه می‌دارد."),
        Lesson("⛓️", "هیچ‌وقت دو بار پشت سر هم", "یک روز جا ماندن طبیعی است. مهم این است که روز بعد برگردی. برای همین زنجیره‌ی تو در این برنامه با یک روز خطا نمی‌شکند."),
        Lesson("🔁", "جایگزین کن، فقط حذف نکن", "هر رفتار معمولاً نیازی را برآورده می‌کند: استراحت، آرامش، توجه. اگر راه بهتری برای همان نیاز داشته باشی، ترک آسان‌تر می‌شود."),
        Lesson("🌊", "موج‌سواری روی وسوسه", "وسوسه مثل موج بالا می‌رود و فروکش می‌کند. لازم نیست با آن بجنگی؛ آن را ببین، نفس بکش و صبر کن تا رد شود. دکمه‌ی «مکث» برای همین است."),
        Lesson("🗣️", "مکث پیش از حرف زدن", "پیش از جواب دادن یک نفس بکش و سه چیز را بپرس: درست است؟ لازم است؟ الان وقتش است؟"),
        Lesson("🛑", "چهار حالِ پرخطر", "گرسنه، عصبانی، تنها یا خسته که هستی، تصمیم‌ها سخت‌تر می‌شوند. اول همان حال را درمان کن."),
        Lesson("🏠", "محیط را تغییر بده", "اراده محدود است. کار خوب را جلوی چشم و دم دست بگذار و کار ناخواسته را چند قدم دورتر. کتاب روی بالش، گوشی در اتاق دیگر."),
        Lesson("🌟", "هویت", "از خودت بپرس «آدمی که می‌خواهم باشم الان چه می‌کند؟» هر ثبت، یک رأی به همان آدم است."),
        Lesson("🤝", "با خودت مهربان باش", "سرزنش شدید معمولاً به «حالا که خراب شد، ولش کن» می‌رسد. مثل یک دوست خوب با خودت حرف بزن و بپرس دفعه‌ی بعد چه کمکی می‌کند."),
        Lesson("⏳", "صبور باش", "در یک پژوهش، خودکار شدن یک رفتار به‌طور میانه حدود ۶۶ روز طول کشید و تفاوت میان آدم‌ها بسیار زیاد بود. عجله لازم نیست."),
        Lesson("📝", "مرور هفتگی", "هفته‌ای یک بار نگاه کن چه چیزی جواب داد و چه چیزی مانع شد. فقط یک تغییر انتخاب کن؛ ده تغییر یعنی هیچ تغییر."),
    )

    private val daily = listOf(
        CoachMessage("🌤️", "امروز", "لازم نیست کامل باشی. کافی است کمی بهتر از دیروز باشی."),
        CoachMessage("🌟", "هویت", "هر انتخاب کوچک امروز، یک رأی به آدمی است که می‌خواهی بشوی."),
        CoachMessage("⏸️", "مکث", "میان محرک و واکنش یک فاصله هست. آزادی تو همان‌جاست."),
        CoachMessage("👣", "قدم کوچک", "اگر سخت به نظر می‌رسد، کوچک‌ترش کن؛ ولی حذفش نکن."),
        CoachMessage("👀", "آگاهی", "چیزی را که ثبت می‌کنی، بهتر می‌بینی؛ و چیزی را که می‌بینی، می‌توانی تغییر دهی."),
        CoachMessage("🏠", "محیط", "امروز یک مانع کوچک سر راه رفتار ناخواسته‌ات بگذار."),
        CoachMessage("🗺️", "برنامه", "سخت‌ترین موقعیت امروزت کدام است؟ از حالا بگو اگر پیش آمد چه می‌کنی."),
    )

    /** The one message shown on top of "today". */
    fun today(data: AppData, today: LocalDate, hour: Int): CoachMessage {
        val habits = data.activeHabits
        if (habits.isEmpty()) return CoachMessage("🌱", "شروع کنیم", "اولین رفتاری را که می‌خواهی بهتر کنی اضافه کن.")

        val last = lastActivity(data, today)
        if (last != null && ChronoUnit.DAYS.between(last, today) >= 3) return CoachMessage(
            "🤗", "خوش برگشتی", "لازم نیست چیزی را جبران کنی. از همین امروز ادامه بده؛ برگشتن خودش انضباط است.",
        )

        val slipped = habits.filter { it.kind == Kind.QUIT && countOn(data, it.id, today, LogType.SLIP) > 0 }
        if (slipped.isNotEmpty()) {
            val h = slipped.first()
            val plan = h.plan.ifBlank { h.replacement.ifBlank { "" } }
            return CoachMessage(
                "🤝", "ثبت شد؛ این یعنی حواست هست",
                if (plan.isBlank()) "لغزش داده است، نه حکم. از خودت بپرس دفعه‌ی بعد چه چیزی کمک می‌کند."
                else "لغزش داده است، نه حکم. برای دفعه‌ی بعد: $plan",
            )
        }

        val yesterday = today.minusDays(1)
        val missed = habits.firstOrNull {
            it.kind == Kind.BUILD && buildStatus(data, it, yesterday, today) == DayStatus.MISSED &&
                buildStatus(data, it, today, today) == DayStatus.PENDING
        }
        if (missed != null) return CoachMessage(
            "⛓️", "دو بار پشت سر هم نه",
            "دیروز «${missed.title}» جا ماند. " + when {
                missed.target > 1 -> "امروز حتی یک بارش هم حساب است."
                missed.tiny.isBlank() -> "امروز حتی نسخه‌ی کوچکش کافی است."
                else -> "امروز حتی نسخه‌ی کوچکش کافی است: ${missed.tiny}"
            },
        )

        val build = habits.filter { it.kind == Kind.BUILD && it.scheduledOn(today) }
        val allDone = build.isNotEmpty() && build.all {
            buildStatus(data, it, today, today).let { s -> s == DayStatus.DONE || s == DayStatus.TINY }
        }
        if ((hour >= 20 || hour < data.settings.dayStartHour) && !data.isClosed(today)) return CoachMessage(
            "🌙", "وقت بستن روز", "یک دقیقه نگاه کن امروز چطور گذشت و روزت را ببند.",
        )
        if (allDone) return CoachMessage("👏", "عالی بود", "همه‌ی عادت‌های امروزت انجام شد. همین لحظه را به خاطر بسپار.")

        return daily[Math.floorMod(today.toEpochDay(), daily.size.toLong()).toInt()]
    }
}

/** A ready-made behaviour to start from. */
data class Template(
    val key: String,
    val kind: Kind,
    val emoji: String,
    val title: String,
    val why: String = "",
    val cue: String = "",
    val tiny: String = "",
    val plan: String = "",
    val replacement: String = "",
    /** QUIT: key of the good habit offered as its replacement. */
    val pairedWith: String? = null,
    /** BUILD: times a day; more than 1 makes it a counted habit. */
    val target: Int = 1,
)

object Templates {
    val all = listOf(
        Template("pause", Kind.BUILD, "⏸️", "مکث پیش از حرف زدن", why = "تا حرفی بزنم که بعدش پشیمان نشوم.", cue = "پیش از جواب دادن در گفت‌وگوهای مهم", tiny = "یک بار امروز، یک نفس پیش از جواب", target = 3),
        Template("water", Kind.BUILD, "💧", "نوشیدن آب", why = "برای سلامتی و انرژی بیشتر.", cue = "بعد از بیدار شدن و همراه هر وعده", tiny = "یک لیوان", target = 8),
        Template("walk", Kind.BUILD, "🚶", "پیاده‌روی", why = "برای بدن سالم‌تر و ذهن آرام‌تر.", cue = "بعد از کار", tiny = "۵ دقیقه"),
        Template("read", Kind.BUILD, "📖", "مطالعه", why = "تا هر روز چیزی یاد بگیرم.", cue = "پیش از خواب", tiny = "۲ صفحه"),
        Template("breath", Kind.BUILD, "🧘", "تنفس و آرامش", why = "تا کمتر از روی هیجان رفتار کنم.", cue = "صبح، پیش از دیدن گوشی", tiny = "۵ نفس آرام"),
        Template("exercise", Kind.BUILD, "🏋️", "ورزش", cue = "ساعت مشخصی از روز", tiny = "۱۰ حرکت"),
        Template("sleep", Kind.BUILD, "😴", "خواب به‌موقع", why = "تا فردا سرحال و صبورتر باشم.", cue = "ساعت ۲۳", tiny = "گوشی را بیرون از تخت بگذارم"),
        Template("journal", Kind.BUILD, "✍️", "نوشتن روزانه", cue = "پیش از خواب", tiny = "یک جمله"),

        Template("speak", Kind.QUIT, "🗣️", "حرف زدن بدون فکر", why = "تا دل کسی را نرنجانم و پشیمان نشوم.", cue = "وقتی عصبانی‌ام یا حرفم قطع می‌شود", plan = "اگر خواستم فوری جواب بدهم، اول یک نفس عمیق می‌کشم.", replacement = "مکث و گوش دادن", pairedWith = "pause"),
        Template("drink", Kind.QUIT, "🥤", "نوشیدنی ناسالم", why = "برای سلامتی بدنم.", cue = "بعد از غذا و وقت خستگی", plan = "اگر هوس نوشیدنی کردم، اول یک لیوان آب می‌خورم.", replacement = "آب یا چای", pairedWith = "water"),
        Template("phone", Kind.QUIT, "📱", "گوشی بی‌هدف", why = "تا وقتم دست خودم باشد.", cue = "وقت بی‌حوصلگی و پیش از خواب", plan = "اگر بی‌دلیل گوشی را برداشتم، آن را می‌گذارم و ۳ نفس می‌کشم.", replacement = "چند صفحه کتاب", pairedWith = "read"),
        Template("anger", Kind.QUIT, "😠", "پرخاش و عصبانیت", why = "تا رابطه‌هایم آرام‌تر باشد.", cue = "بحث و فشار کاری", plan = "اگر داغ شدم، چند دقیقه از موقعیت فاصله می‌گیرم.", replacement = "تنفس آرام", pairedWith = "breath"),
        Template("delay", Kind.QUIT, "⏰", "عقب انداختن کارها", why = "تا استرس لحظه‌ی آخر نداشته باشم.", cue = "کارهای بزرگ و مبهم", plan = "اگر خواستم عقب بیندازم، فقط ۲ دقیقه شروعش می‌کنم."),
        Template("late", Kind.QUIT, "🌙", "دیر خوابیدن", cue = "شب‌ها با گوشی یا فیلم", plan = "اگر ساعت ۲۳ شد، صفحه‌ها را خاموش می‌کنم.", replacement = "خواب به‌موقع", pairedWith = "sleep"),
        Template("snack", Kind.QUIT, "🍟", "تنقلات ناسالم", cue = "وقت تماشای فیلم یا کلافگی", plan = "اگر هوس کردم، میوه می‌خورم.", replacement = "میوه"),
        Template("gossip", Kind.QUIT, "🤐", "غیبت و قضاوت", why = "تا درباره‌ی دیگران منصف باشم.", cue = "جمع دوستان و همکاران", plan = "اگر بحث به غیبت رفت، موضوع را عوض می‌کنم.", replacement = "گفتن یک نکته‌ی خوب"),
    )

    fun byKey(key: String?): Template? = all.firstOrNull { it.key == key }

    val emojis = listOf(
        "🎯", "⏸️", "💧", "🚶", "📖", "🧘", "🏋️", "😴", "✍️", "🥗", "🍎", "🛁", "🙏", "📞", "💰", "🛏️",
        "🗣️", "🥤", "📱", "😠", "⏰", "🌙", "🍟", "🤐", "🚬", "🍩", "☕", "🎮", "🛒", "😟",
    )
}
