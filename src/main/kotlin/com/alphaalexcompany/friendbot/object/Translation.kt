package com.alphaalexcompany.friendbot.`object`

import com.vk.api.sdk.client.Lang

data class Translation(
    val id: Lang,
    val data: HashMap<String, String>
)
