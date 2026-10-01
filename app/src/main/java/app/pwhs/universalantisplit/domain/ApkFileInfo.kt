package app.pwhs.universalantisplit.domain

data class ApkFileInfo(
    val versionName: String?,
    val versionCode: Long,
    val minSdk: Int,
    val targetSdk: Int,
    val abis: List<String>,
)
