package com.huangder.lumibooks.domain.model

internal const val EARLY_DONOR_NOTICE_ID = "notice-20261112-early-donor-thanks"

internal data class ReceivedNotice(
    val id: String,
    val title: String,
    val message: String,
    val receivedAt: Long,
    val imageUrl: String? = null
)
