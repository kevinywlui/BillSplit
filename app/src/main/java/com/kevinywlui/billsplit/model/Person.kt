package com.kevinywlui.billsplit.model

import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
data class Person(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val venmoUsername: String = "",
    val avatarColorIndex: Int = 0
)
