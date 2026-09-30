package app.pwhs.universalantisplit.domain

data class KeystoreInfo(
    val alias: String,
    val algorithm: String,
    val sha256Fingerprint: String,
    val subject: String,
    val validUntil: String,
    val isCustom: Boolean = false,
)
