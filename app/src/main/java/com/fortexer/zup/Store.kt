package com.fortexer.zup

import android.content.Context
import kotlin.math.max
import kotlin.math.min

/** All player data lives here (SharedPreferences, fully local/offline). */
class Store(context: Context) {

    private val p = context.applicationContext.getSharedPreferences("zup_prefs", Context.MODE_PRIVATE)

    var best = 0
        private set
    var coins = 0
        private set
    var skin = 0
        private set
    var trail = 0
        private set
    var sound = true
        private set
    var music = true
        private set
    var vibration = true
        private set
    var fxHigh = true
        private set

    private val ownedSkins = HashSet<Int>()
    private val ownedTrails = HashSet<Int>()

    init {
        load()
    }

    private fun parse(s: String?, into: HashSet<Int>) {
        into.clear()
        into.add(0)
        if (s.isNullOrEmpty()) return
        for (part in s.split(",")) {
            val v = part.trim().toIntOrNull()
            if (v != null && v >= 0) into.add(v)
        }
    }

    private fun join(s: Set<Int>): String = s.joinToString(",")

    private fun load() {
        best = max(0, p.getInt("best", 0))
        coins = max(0, p.getInt("coins", 0))
        parse(p.getString("ownedSkins", null), ownedSkins)
        parse(p.getString("ownedTrails", null), ownedTrails)
        skin = p.getInt("skin", 0)
        if (skin < 0 || skin >= Cosmetics.SKIN_COUNT || !ownedSkins.contains(skin)) skin = 0
        trail = p.getInt("trail", 0)
        if (trail < 0 || trail >= Cosmetics.TRAIL_COUNT || !ownedTrails.contains(trail)) trail = 0
        sound = p.getBoolean("sound", true)
        music = p.getBoolean("music", true)
        vibration = p.getBoolean("vibration", true)
        fxHigh = p.getBoolean("fxHigh", true)
    }

    fun setBest(v: Int) {
        best = max(0, v)
        p.edit().putInt("best", best).apply()
    }

    fun addCoins(n: Int) {
        if (n <= 0) return
        coins = min(999_999, coins + n)
        p.edit().putInt("coins", coins).apply()
    }

    fun ownsSkin(i: Int) = ownedSkins.contains(i)
    fun ownsTrail(i: Int) = ownedTrails.contains(i)

    fun buySkin(i: Int): Boolean {
        if (i < 0 || i >= Cosmetics.SKIN_COUNT) return false
        if (ownedSkins.contains(i)) return true
        val price = Cosmetics.skinPrices[i]
        if (coins < price) return false
        coins -= price
        ownedSkins.add(i)
        p.edit().putInt("coins", coins).putString("ownedSkins", join(ownedSkins)).apply()
        return true
    }

    fun buyTrail(i: Int): Boolean {
        if (i < 0 || i >= Cosmetics.TRAIL_COUNT) return false
        if (ownedTrails.contains(i)) return true
        val price = Cosmetics.trailPrices[i]
        if (coins < price) return false
        coins -= price
        ownedTrails.add(i)
        p.edit().putInt("coins", coins).putString("ownedTrails", join(ownedTrails)).apply()
        return true
    }

    fun equipSkin(i: Int) {
        if (!ownedSkins.contains(i)) return
        skin = i
        p.edit().putInt("skin", i).apply()
    }

    fun equipTrail(i: Int) {
        if (!ownedTrails.contains(i)) return
        trail = i
        p.edit().putInt("trail", i).apply()
    }

    fun setSound(v: Boolean) { sound = v; p.edit().putBoolean("sound", v).apply() }
    fun setMusic(v: Boolean) { music = v; p.edit().putBoolean("music", v).apply() }
    fun setVibration(v: Boolean) { vibration = v; p.edit().putBoolean("vibration", v).apply() }
    fun setFx(v: Boolean) { fxHigh = v; p.edit().putBoolean("fxHigh", v).apply() }

    fun resetAll() {
        p.edit().clear().apply()
        load()
    }
}
