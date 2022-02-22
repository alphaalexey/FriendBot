package com.alphaalexcompany.friendbot.`object`

import org.bson.codecs.pojo.annotations.BsonId
import org.litote.kmongo.Id
import org.litote.kmongo.newId

data class Interest(
    val userId: Int,
    val theme: Theme,
    var data: String = "",
    @BsonId val id: Id<Interest> = newId()
)
