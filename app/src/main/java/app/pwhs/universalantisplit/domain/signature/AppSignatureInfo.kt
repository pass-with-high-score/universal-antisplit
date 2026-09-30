package app.pwhs.universalantisplit.domain.signature

data class AppSignatureInfo(
    val verified: Boolean,
    val signerSha256Set: Set<String> = emptySet(),
    val certificates: List<SignatureCertificateInfo> = emptyList(),
    val signingCertificateHistory: List<SignatureCertificateInfo> = emptyList(),
    val hasMultipleSigners: Boolean = false,
    val verifiedSchemes: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
) {
    val primaryCertificate: SignatureCertificateInfo?
        get() = certificates.firstOrNull()

    val primarySha256: String?
        get() = primaryCertificate?.sha256 ?: signerSha256Set.firstOrNull()

    val schemesDisplay: String
        get() = if (verifiedSchemes.isEmpty()) "None" else verifiedSchemes.joinToString(", ")

    val hasV1: Boolean get() = verifiedSchemes.any { it.startsWith("V1") }
    val hasV2: Boolean get() = verifiedSchemes.any { it.startsWith("V2") }
    val hasV3: Boolean get() = verifiedSchemes.any { it.startsWith("V3") }
    val hasV4: Boolean get() = verifiedSchemes.any { it.startsWith("V4") }
}

data class SignatureCertificateInfo(
    val sha256: String,
    val sha1: String,
    val md5: String,
    val subject: String,
    val issuer: String,
    val serialNumber: String,
    val validFrom: String?,
    val validUntil: String?,
    val publicKeyAlgorithm: String?,
    val signatureAlgorithm: String?,
)
