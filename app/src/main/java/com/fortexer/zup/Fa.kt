package com.fortexer.zup

/** Persian text helpers (digits, script detection, line wrapping). */
object Fa {

    private val digits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

    /** Converts ASCII digits to Persian digits. */
    fun d(s: String): String {
        var needs = false
        for (ch in s) if (ch in '0'..'9') { needs = true; break }
        if (!needs) return s
        val sb = StringBuilder(s.length)
        for (ch in s) sb.append(if (ch in '0'..'9') digits[ch - '0'] else ch)
        return sb.toString()
    }

    fun d(n: Int): String = d(n.toString())

    /** True when the text contains Arabic-script characters (so it needs the Persian font, no letter spacing). */
    fun hasArabic(s: String): Boolean {
        for (ch in s) if (ch in '\u0600'..'\u06FF' || ch in '\uFB50'..'\uFEFF') return true
        return false
    }

    /** True when the text contains Persian/Arabic LETTERS (digits alone stay left-to-right). */
    fun hasLetters(s: String): Boolean {
        for (ch in s) if (ch in '\u0600'..'\u065F' || ch in '\u066E'..'\u06D5' || ch in '\uFB50'..'\uFEFF') return true
        return false
    }

    // ---- UI strings ----
    const val TAGLINE = "با لمس صفحه جهت توپ را عوض کن، از موانع بگذر و کریستال جمع کن."
    const val PLAY = "شروع بازی"
    const val SHOP = "فروشگاه"
    const val SETTINGS = "تنظیمات"
    const val GUIDE = "راهنمای بازی"
    const val RECORDS = "امتیازات و رکوردها"
    const val EXIT = "خروج از بازی"
    const val MENU_GUIDE = "راهنما"
    const val MENU_RECORDS = "رکوردها"
    const val MENU_EXIT = "خروج"
    const val BEST_SCORE = "بهترین امتیاز"
    const val BEST = "رکورد"
    const val SCORE = "امتیاز"
    const val TAP_TO_FLIP = "برای تغییر جهت لمس کن"
    const val PAUSED = "توقف بازی"
    const val RESUME = "ادامه"
    const val RESTART = "شروع دوباره"
    const val MENU = "منوی اصلی"
    const val GAME_OVER = "بازی تمام شد"
    const val NEW_RECORD = "رکورد جدید!"
    const val RETRY = "تلاش دوباره"
    const val SKINS = "ظاهر توپ"
    const val TRAILS = "رد حرکت"
    const val EQUIPPED = "فعال است"
    const val UNEQUIP = "غیرفعال کن"
    const val EQUIP = "فعال کن"
    const val NOT_ENOUGH = "سکه کافی نیست"
    const val SOUND = "افکت‌های صوتی"
    const val MUSIC = "موسیقی"
    const val VIBRATION = "لرزش"
    const val FX = "کیفیت جلوه‌ها"
    const val FX_HIGH = "بالا"
    const val FX_LOW = "کم"
    const val RESET_SETTINGS = "بازگرداندن تنظیمات"
    const val RESET_SETTINGS_BTN = "بازگردانی"
    const val ERASE_DATA = "پاک کردن اطلاعات بازی"
    const val ERASE_BTN = "پاک کردن"
    const val VERSION = "نسخه"
    const val CONFIRM_TITLE = "همه اطلاعات بازی پاک شود؟"
    const val CONFIRM_BODY = "سکه‌ها، رکوردها، ظاهرهای خریداری‌شده و تنظیمات از بین می‌رود."
    const val CANCEL = "انصراف"
    const val ERASE_NOW = "پاک کن"
    const val ERASED = "اطلاعات بازی پاک شد"
    const val SETTINGS_RESET = "تنظیمات به حالت اولیه برگشت"
    const val CRYSTALS = "کریستال"
    const val BONUS = "جایزه"
    const val GAMES_PLAYED = "تعداد بازی‌ها"
    const val TOTAL_CRYSTALS = "کریستال‌های جمع‌شده"
    const val COINS = "سکه‌ها"
    const val SKINS_OWNED = "ظاهرهای خریداری‌شده"
    const val TRAILS_OWNED = "ردهای خریداری‌شده"
    const val MYKET_COMMENT = "امتیاز و نظر در مایکت"
    const val MYKET_MISSING = "برنامه مایکت روی این دستگاه نصب نیست"
    const val MYKET_FAILED = "باز کردن مایکت ممکن نشد"

    val skinNames = arrayOf("آبی نئونی", "هسته بنفش", "سبز سمی", "طلایی خورشیدی", "سرخ", "یخی", "کهکشان", "سایه")
    val trailNames = arrayOf("کلاسیک", "شعله آبی", "رد بنفش", "برقی", "رنگین‌کمان", "پلاسما")

    val guideTitles = arrayOf("حرکت توپ", "موانع و سرعت", "کریستال‌ها", "سکه و فروشگاه", "توقف بازی")
    val guideBodies = arrayOf(
        "توپ خودکار به چپ و راست می‌رود. با لمس صفحه، جهت حرکتش برعکس می‌شود.",
        "از شکاف بین موانع نئونی عبور کن؛ برخورد با مانع یعنی پایان بازی. هرچه امتیاز بالاتر برود، سرعت بیشتر می‌شود.",
        "هر کریستال ۵ امتیاز و ۳ سکه می‌دهد.",
        "در پایان هر بازی، به‌ازای هر ۲۰ امتیاز یک سکه جایزه می‌گیری. با سکه‌ها در فروشگاه، ظاهر توپ و رد حرکت را می‌خری.",
        "دکمه بالا سمت چپ بازی را متوقف می‌کند. پس از ادامه دادن، یک شمارش معکوس کوتاه نمایش داده می‌شود."
    )
}
