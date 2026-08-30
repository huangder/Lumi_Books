package com.huangder.lumibooks.domain.model

enum class BookDeleteMode {
    LOCAL_ONLY,
    LOCAL_AND_CLOUD,
    FORCE_LOCAL_AND_CLOUD;

    val attemptsCloudDelete: Boolean
        get() = this != LOCAL_ONLY

    val cloudFailureBlocksLocalDelete: Boolean
        get() = this == LOCAL_AND_CLOUD

    val forcesLocalDelete: Boolean
        get() = this == FORCE_LOCAL_AND_CLOUD
}
