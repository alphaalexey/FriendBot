package com.alphaalexcompany.friendbot.`object`

import com.vk.api.sdk.client.Lang

data class User(
    val id: Int,
    var age: Int = 0,
    var lang: Lang = Lang.RU,
    var level: UserLevel = UserLevel.USER,
    var command: InputCommand? = null,
    var subscription: Boolean = false,
    var stopped: Boolean = false
)
