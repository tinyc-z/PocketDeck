package com.pocketdeck.launcher.lan

import java.util.Locale
import kotlin.random.Random

/**
 * 为局域网发现生成可爱、好记的随机设备昵称（用户可随时自定义修改）。
 */
object DeviceNames {
    private val ZH_NAMES = listOf(
        "软糖橘猫", "像素狐狸", "抹茶企鹅", "草莓水獭",
        "闪电柴犬", "芋泥仓鼠", "云朵兔兔", "气泡海豹",
        "菠萝刺猬", "团子浣熊", "星光考拉", "蜜桃羊驼",
        "奶盖柯基", "布丁松鼠", "薄荷小鹿", "芝士熊猫",
        "焦糖小熊", "蓝莓海豚", "糯米柴柴", "蜜瓜鸭鸭",
    )

    private val EN_NAMES = listOf(
        "Mochi Shiba", "Pixel Fox", "Boba Cat", "Cozy Otter",
        "Matcha Penguin", "Cloud Bunny", "Berry Panda", "Sunny Corgi",
        "Peach Alpaca", "Mint Deer", "Caramel Bear", "Cheese Koala",
        "Yuzu Hedgehog", "Bubble Seal", "Honey Raccoon", "Starry Hamster",
    )

    fun randomCuteName(): String {
        val lang = Locale.getDefault().language.lowercase()
        val pool = if (lang.startsWith("zh")) ZH_NAMES else EN_NAMES
        return pool[Random.nextInt(pool.size)]
    }
}
