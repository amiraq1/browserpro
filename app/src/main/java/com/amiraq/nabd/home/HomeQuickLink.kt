package com.amiraq.nabd.home

import java.util.UUID

data class HomeQuickLink(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val url: String,
    val createdAt: Long = System.currentTimeMillis()
)
