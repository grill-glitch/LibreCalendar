package org.librelab.calendar.data

/**
 * 事件颜色解析:
 * 优先级: 标签颜色 > 事件自定义颜色 > 标题 hash 动态取色 (同名同色)
 */
object EventColors {

    /** Material 300 系 16 色 (浅色, 深色主题下可读) */
    val PALETTE = listOf(
        0xFFEF9A9A.toInt(), 0xFFF48FB1.toInt(), 0xFFCE93D8.toInt(), 0xFFB39DDB.toInt(),
        0xFF9FA8DA.toInt(), 0xFF90CAF9.toInt(), 0xFF81D4FA.toInt(), 0xFF80DEEA.toInt(),
        0xFF80CBC4.toInt(), 0xFFA5D6A7.toInt(), 0xFFC5E1A5.toInt(), 0xFFE6EE9C.toInt(),
        0xFFFFF59D.toInt(), 0xFFFFE082.toInt(), 0xFFFFCC80.toInt(), 0xFFFFAB91.toInt(),
    )

    /** 标签颜色可用调色板 (含深色, 用于标签色块) */
    val LABEL_PALETTE = listOf(
        0xFFE53935.toInt(), 0xFFD81B60.toInt(), 0xFF8E24AA.toInt(), 0xFF5E35B1.toInt(),
        0xFF3949AB.toInt(), 0xFF1E88E5.toInt(), 0xFF039BE5.toInt(), 0xFF00ACC1.toInt(),
        0xFF00897B.toInt(), 0xFF43A047.toInt(), 0xFF7CB342.toInt(), 0xFFC0CA33.toInt(),
        0xFFFDD835.toInt(), 0xFFFFB300.toInt(), 0xFFFB8C00.toInt(), 0xFFF4511E.toInt(),
        0xFF6D4C41.toInt(), 0xFF546E7A.toInt(),
    )

    /** 标题 hash 动态色: 同一名称颜色固定 */
    fun dynamicColor(title: String): Int {
        val h = title.hashCode()
        val idx = Math.floorMod(h, PALETTE.size)
        return PALETTE[idx]
    }

    /** 解析事件最终颜色: 标签颜色 > 自定义颜色 > 动态 */
    fun resolve(event: CalendarEvent, label: Label?): Int =
        label?.color ?: event.customColor ?: dynamicColor(event.title)

    /** 解析并返回 Label 查找表: id -> Label */
    fun labelMap(labels: List<Label>): Map<Long, Label> = labels.associateBy { it.id }
}

/** 标签图标候选 (Material Symbols outlined 名 → 显示用文字), 与 fonts.google.com/icons 同源 */
object LabelIcons {
    /** 可用图标名 (扩充自 Material Symbols, 编辑弹窗中可按名称搜索) */
    val NAMES = listOf(
        "work", "home", "school", "restaurant", "flight",
        "fitness_center", "shopping_cart", "attach_money", "celebration", "favorite",
        "star", "pets", "directions_car", "sports_soccer", "music_note",
        "local_hospital", "account_balance", "phone_android", "emoji_events", "palette",
        "event", "date_range", "alarm", "coffee", "computer",
        "movie", "photo_camera", "train", "subway", "directions_bus",
        "local_cafe", "local_bar", "local_library", "local_mall", "local_movies",
        "local_pharmacy", "local_shipping", "local_taxi", "beach_access", "camping",
        "park", "eco", "forest", "volcano", "spa",
        "sports_basketball", "sports_tennis", "sports_esports", "sports_gym", "theater_comedy",
        "shopping_bag", "storefront", "payments", "savings", "credit_card",
        "celebration", "cake", "gift", "festival", "meeting_room",
        "construction", "build", "handyman", "electric_bolt", "water_drop",
    ).distinct()

    /** 名称 → 中文显示; 未收录的显示英文原名 */
    fun displayName(name: String?): String = when (name) {
        "work" -> "工作"; "home" -> "家"; "school" -> "学习"; "restaurant" -> "餐饮"
        "flight" -> "出行"; "fitness_center" -> "健身"; "shopping_cart" -> "购物"
        "attach_money" -> "财务"; "celebration" -> "庆祝"; "favorite" -> "重要"
        "star" -> "收藏"; "pets" -> "宠物"; "directions_car" -> "驾车"
        "sports_soccer" -> "足球"; "music_note" -> "音乐"; "local_hospital" -> "医疗"
        "account_balance" -> "银行"; "phone_android" -> "电话"; "emoji_events" -> "成就"
        "palette" -> "标签"; "event" -> "日程"; "date_range" -> "日期"; "alarm" -> "闹钟"
        "coffee" -> "咖啡"; "computer" -> "电脑"; "movie" -> "电影"; "photo_camera" -> "相机"
        "train" -> "火车"; "subway" -> "地铁"; "directions_bus" -> "公交"; "local_cafe" -> "咖啡馆"
        "local_bar" -> "酒吧"; "local_library" -> "图书馆"; "local_mall" -> "商场"; "local_movies" -> "影院"
        "local_pharmacy" -> "药店"; "local_shipping" -> "快递"; "local_taxi" -> "出租车"; "beach_access" -> "海滩"
        "camping" -> "露营"; "park" -> "公园"; "eco" -> "环保"; "forest" -> "森林"; "volcano" -> "火山"
        "spa" -> "水疗"; "sports_basketball" -> "篮球"; "sports_tennis" -> "网球"; "sports_esports" -> "电竞"
        "sports_gym" -> "健身"; "theater_comedy" -> "喜剧"; "shopping_bag" -> "购物袋"; "storefront" -> "店铺"
        "payments" -> "支付"; "savings" -> "储蓄"; "credit_card" -> "银行卡"; "cake" -> "蛋糕"
        "gift" -> "礼物"; "festival" -> "节日"; "meeting_room" -> "会议室"; "construction" -> "施工"
        "build" -> "建造"; "handyman" -> "维修"; "electric_bolt" -> "闪电"; "water_drop" -> "水滴"
        else -> name ?: "标签"
    }
}
