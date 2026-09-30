package app.pwhs.universalantisplit.engine.signature

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import app.pwhs.universalantisplit.domain.signature.AppSignatureInfo
import app.pwhs.universalantisplit.domain.signature.SignatureCertificateInfo
import com.android.apksig.ApkVerifier
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

object ApkSignatureReader {

    private const val SHA_256 = "SHA-256"
    private const val SHA_1 = "SHA-1"
    private const val MD5 = "MD5"

    /**
     * Reads and verifies the full signature of an APK file using Google's apksig library.
     */
    fun readFromApkFile(file: File): AppSignatureInfo {
        if (!file.exists()) {
            return AppSignatureInfo(
                verified = false,
                errors = listOf("File not found: ${file.absolutePath}")
            )
        }

        return try {
            val verifier = ApkVerifier.Builder(file)
                .setMinCheckedPlatformVersion(Build.VERSION.SDK_INT)
                .setMaxCheckedPlatformVersion(Build.VERSION.SDK_INT)
                .build()

            val result = verifier.verify()
            val certs = result.signerCertificates.map { formatCertificate(it) }
            val lineageCerts = runCatching {
                result.signingCertificateLineage?.certificatesInLineage.orEmpty()
            }.getOrDefault(emptyList()).map { formatCertificate(it) }

            val schemes = buildList {
                if (result.isVerifiedUsingV1Scheme) add("V1 (JAR)")
                if (result.isVerifiedUsingV2Scheme) add("V2 (Full APK)")
                if (result.isVerifiedUsingV3Scheme) add("V3 (Lineage)")
                if (result.isVerifiedUsingV31Scheme) add("V3.1")
                if (result.isVerifiedUsingV4Scheme) add("V4")
            }

            AppSignatureInfo(
                verified = result.isVerified,
                signerSha256Set = certs.mapTo(linkedSetOf()) { it.sha256 },
                certificates = certs,
                signingCertificateHistory = lineageCerts,
                hasMultipleSigners = certs.size > 1,
                verifiedSchemes = schemes,
                warnings = result.warnings.map { it.toString() },
                errors = result.errors.map { it.toString() }
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to verify APK signature: ${file.name}")
            AppSignatureInfo(
                verified = false,
                errors = listOf(e.localizedMessage ?: e::class.java.simpleName)
            )
        }
    }

    /**
     * Reads signatures of an installed package using Android PackageManager.
     */
    @Suppress("DEPRECATION")
    fun readFromInstalledPackage(context: Context, packageName: String): AppSignatureInfo? {
        return try {
            val packageManager = context.packageManager
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                PackageManager.GET_SIGNING_CERTIFICATES or PackageManager.MATCH_UNINSTALLED_PACKAGES
            } else {
                PackageManager.GET_SIGNATURES or PackageManager.MATCH_UNINSTALLED_PACKAGES
            }

            val packageInfo: PackageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong()))
            } else {
                packageManager.getPackageInfo(packageName, flags)
            }

            val rawSignatures = mutableListOf<Signature>()
            val historySignatures = mutableListOf<Signature>()
            var multipleSigners = false

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signingInfo = packageInfo.signingInfo
                if (signingInfo != null) {
                    if (signingInfo.hasMultipleSigners()) {
                        signingInfo.apkContentsSigners?.let { rawSignatures.addAll(it) }
                        multipleSigners = true
                    } else {
                        val history = signingInfo.signingCertificateHistory?.toList().orEmpty()
                        historySignatures.addAll(history)
                        val current = history.lastOrNull() ?: signingInfo.apkContentsSigners?.firstOrNull()
                        if (current != null) {
                            rawSignatures.add(current)
                        }
                    }
                }
            } else {
                packageInfo.signatures?.let { rawSignatures.addAll(it) }
                multipleSigners = rawSignatures.size > 1
            }

            if (rawSignatures.isEmpty()) return null

            val certs = rawSignatures.map { formatSignature(it) }
            val history = historySignatures.map { formatSignature(it) }

            AppSignatureInfo(
                verified = true,
                signerSha256Set = certs.mapTo(linkedSetOf()) { it.sha256 },
                certificates = certs,
                signingCertificateHistory = history,
                hasMultipleSigners = multipleSigners,
                verifiedSchemes = listOf("Android System Key")
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to read installed package signature: $packageName")
            null
        }
    }

    fun formatSignature(signature: Signature): SignatureCertificateInfo {
        return formatEncodedCertificate(signature.toByteArray())
    }

    fun formatCertificate(certificate: X509Certificate): SignatureCertificateInfo {
        return formatEncodedCertificate(certificate.encoded, certificate)
    }

    private fun formatEncodedCertificate(
        encoded: ByteArray,
        cachedCert: X509Certificate? = null
    ): SignatureCertificateInfo {
        val cert = cachedCert ?: runCatching {
            CertificateFactory.getInstance("X.509")
                .generateCertificate(ByteArrayInputStream(encoded)) as X509Certificate
        }.getOrNull()

        return SignatureCertificateInfo(
            sha256 = formatFingerprint(encoded, SHA_256),
            sha1 = formatFingerprint(encoded, SHA_1),
            md5 = formatFingerprint(encoded, MD5),
            subject = cert?.subjectX500Principal?.name ?: "Unknown",
            issuer = cert?.issuerX500Principal?.name ?: "Unknown",
            serialNumber = cert?.serialNumber?.toString(16)?.uppercase(Locale.US) ?: "Unknown",
            validFrom = cert?.notBefore?.formatDateUtc(),
            validUntil = cert?.notAfter?.formatDateUtc(),
            publicKeyAlgorithm = cert?.publicKey?.algorithm,
            signatureAlgorithm = cert?.sigAlgName
        )
    }

    private fun formatFingerprint(data: ByteArray, algorithm: String): String {
        val digest = MessageDigest.getInstance(algorithm)
        val hash = digest.digest(data)
        return hash.joinToString(":") { "%02X".format(it) }
    }

    private fun java.util.Date.formatDateUtc(): String {
        val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm:ss", Locale.getDefault())
        return formatter.format(this)
    }
}
