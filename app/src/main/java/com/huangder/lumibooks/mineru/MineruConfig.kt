package com.huangder.lumibooks.mineru

enum class MineruMode(val key: String) {
    DISABLED("disabled"),
    AGENT("agent"),
    PRECISE("precise");

    companion object {
        fun fromKey(key: String?): MineruMode = entries.firstOrNull { it.key == key } ?: DISABLED
    }
}

object MineruConfig {
    const val CONSENT_VERSION = 1
    const val SERVICE_TERMS_URL =
        "https://webpub.shlab.tech/dps/opendatalab-web/odl_v5.1690/service.html"
    const val PRIVACY_POLICY_URL =
        "https://webpub.shlab.tech/dps/opendatalab-web/odl_v5.1690/privacy.html"
    const val API_LIMITS_URL = "https://mineru.net/apiManage/limit"
    const val API_MANAGEMENT_URL = "https://mineru.net/apiManage"
    const val MANUAL_WEB_URL = "https://mineru.net/"
}
