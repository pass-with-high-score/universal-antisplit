package app.pwhs.universalantisplit.engine.hook

import com.android.apksig.ApkVerifier
import timber.log.Timber
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.cert.Certificate
import java.util.jar.JarFile

object SignatureExtractor {

    /**
     * Extracts all original signing certificates from APK file (supporting v1, v2, v3 schemes).
     * Returns a list of DER-encoded certificate byte arrays.
     */
    fun extractCertificates(apkFile: File): List<ByteArray> {
        val certs = mutableListOf<ByteArray>()

        // 1. Primary: Google apksig ApkVerifier
        runCatching {
            val verifier = ApkVerifier.Builder(apkFile).build()
            val result = verifier.verify()
            for (signer in result.signerCertificates) {
                certs.add(signer.encoded)
            }
        }.onFailure {
            Timber.w(it, "ApkVerifier failed on ${apkFile.name}, trying JarFile fallback")
        }

        if (certs.isNotEmpty()) return certs

        // 2. Fallback: Standard JarFile (JAR v1 signature)
        runCatching {
            JarFile(apkFile).use { jar ->
                val entry = jar.getJarEntry("AndroidManifest.xml")
                if (entry != null) {
                    val buffer = ByteArray(8192)
                    jar.getInputStream(entry).use { stream ->
                        while (stream.read(buffer) != -1) {
                            // Exhaust stream to trigger certificate parsing
                        }
                    }
                    val jarCertificates: Array<Certificate>? = entry.certificates
                    if (jarCertificates != null) {
                        for (cert in jarCertificates) {
                            certs.add(cert.encoded)
                        }
                    }
                }
            }
        }.onFailure {
            Timber.e(it, "JarFile certificate extraction failed")
        }

        return certs
    }

    /**
     * Encodes a list of certificates into binary payload format expected by PmsHookApplication:
     * - 1 byte: number of certificates
     * - For each certificate: 4 bytes length (big-endian int), followed by raw certificate bytes.
     */
    fun encodeCertificates(certificates: List<ByteArray>): ByteArray {
        val baos = ByteArrayOutputStream()
        val dos = DataOutputStream(baos)
        dos.writeByte(certificates.size)
        for (cert in certificates) {
            dos.writeInt(cert.size)
            dos.write(cert)
        }
        dos.flush()
        return baos.toByteArray()
    }
}
