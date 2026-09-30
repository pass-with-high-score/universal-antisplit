package app.pwhs.universalantisplit.engine.signer

import android.content.Context
import com.android.apksig.ApkSigner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import app.pwhs.universalantisplit.domain.KeystoreInfo
import timber.log.Timber
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.MessageDigest
import java.security.PrivateKey
import java.security.Security
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manages on-device cryptographic key generation and APK signing
 * using Google's official `apksig` library and BouncyCastle.
 */
class ApkSignerManager(private val context: Context) {

    companion object {
        private const val KEYSTORE_FILENAME = "universal_antisplit.p12"
        private const val KEY_ALIAS = "UniversalAntiSplit"
        private val KEY_PASSWORD = "UniversalAntiSplitPassword".toCharArray()
        private const val CERT_VALIDITY_DAYS = 365 * 30 // 30 years

        init {
            runCatching {
                Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
                Security.insertProviderAt(BouncyCastleProvider(), 1)
            }
        }
    }

    private var cachedPrivateKey: PrivateKey? = null
    private var cachedCertificate: X509Certificate? = null

    /**
     * Signs the given [inputApk] and writes the signed result to [outputApk].
     */
    suspend fun signApk(
        inputApk: File,
        outputApk: File,
        v1Enabled: Boolean = true,
        v2Enabled: Boolean = true,
        v3Enabled: Boolean = true,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(inputApk.exists()) { "Input APK does not exist: ${inputApk.absolutePath}" }
            if (outputApk.exists()) {
                outputApk.delete()
            }
            outputApk.parentFile?.mkdirs()

            val (privateKey, certificate) = getOrCreateSigningCredentials()

            val signerConfig = ApkSigner.SignerConfig.Builder(
                KEY_ALIAS,
                privateKey,
                listOf(certificate)
            ).build()

            val apkSigner = ApkSigner.Builder(listOf(signerConfig))
                .setInputApk(inputApk)
                .setOutputApk(outputApk)
                .setV1SigningEnabled(v1Enabled)
                .setV2SigningEnabled(v2Enabled)
                .setV3SigningEnabled(v3Enabled)
                .setMinSdkVersion(21)
                .setCreatedBy("Universal Anti-Split")
                .build()

            Timber.i("Signing APK: ${inputApk.name} -> ${outputApk.name} with schemes v1=$v1Enabled, v2=$v2Enabled, v3=$v3Enabled")
            apkSigner.sign()
            Timber.i("Successfully signed APK: ${outputApk.absolutePath} (${outputApk.length()} bytes)")

            outputApk
        }.onFailure { error ->
            Timber.e(error, "Failed to sign APK: ${inputApk.absolutePath}")
        }
    }

    @Synchronized
    private fun getOrCreateSigningCredentials(): Pair<PrivateKey, X509Certificate> {
        val priv = cachedPrivateKey
        val cert = cachedCertificate
        if (priv != null && cert != null) {
            return Pair(priv, cert)
        }

        val keystoreFile = File(context.filesDir, "keystore/$KEYSTORE_FILENAME")
        keystoreFile.parentFile?.mkdirs()

        val keyStore = KeyStore.getInstance("PKCS12")

        if (keystoreFile.exists() && keystoreFile.length() > 0) {
            try {
                FileInputStream(keystoreFile).use { fis ->
                    keyStore.load(fis, KEY_PASSWORD)
                }
                val loadedKey = keyStore.getKey(KEY_ALIAS, KEY_PASSWORD) as? PrivateKey
                val loadedCert = keyStore.getCertificate(KEY_ALIAS) as? X509Certificate
                if (loadedKey != null && loadedCert != null) {
                    cachedPrivateKey = loadedKey
                    cachedCertificate = loadedCert
                    return Pair(loadedKey, loadedCert)
                }
            } catch (e: Exception) {
                Timber.w(e, "Existing keystore corrupted, generating new keypair")
                keystoreFile.delete()
            }
        }

        // Generate new 2048-bit RSA KeyPair and self-signed certificate
        val keyPair = generateRsaKeyPair()
        val certificate = generateSelfSignedCertificate(keyPair)

        keyStore.load(null, KEY_PASSWORD)
        keyStore.setKeyEntry(
            KEY_ALIAS,
            keyPair.private,
            KEY_PASSWORD,
            arrayOf(certificate)
        )

        FileOutputStream(keystoreFile).use { fos ->
            keyStore.store(fos, KEY_PASSWORD)
        }

        cachedPrivateKey = keyPair.private
        cachedCertificate = certificate
        Timber.i("Generated and saved new PKCS12 Keystore to ${keystoreFile.absolutePath}")

        return Pair(keyPair.private, certificate)
    }

    private fun generateRsaKeyPair(): KeyPair {
        val keyGen = KeyPairGenerator.getInstance("RSA")
        keyGen.initialize(2048)
        return keyGen.generateKeyPair()
    }

    private fun generateSelfSignedCertificate(keyPair: KeyPair): X509Certificate {
        val now = System.currentTimeMillis()
        val startDate = Date(now - 24 * 60 * 60 * 1000L) // Yesterday
        val endDate = Date(now + CERT_VALIDITY_DAYS * 24 * 60 * 60 * 1000L)

        val subject = X500Name("CN=Universal Anti-Split, O=Universal Anti-Split, C=VN")
        val serialNumber = BigInteger.valueOf(now)

        val certBuilder = JcaX509v3CertificateBuilder(
            subject,
            serialNumber,
            startDate,
            endDate,
            subject,
            keyPair.public
        )

        val signer = JcaContentSignerBuilder("SHA256withRSA")
            .build(keyPair.private)

        val certHolder = certBuilder.build(signer)
        return JcaX509CertificateConverter()
            .getCertificate(certHolder)
    }

    suspend fun getKeystoreInfo(): KeystoreInfo = withContext(Dispatchers.IO) {
        val (_, cert) = getOrCreateSigningCredentials()
        val customMarker = File(context.filesDir, "keystore/is_custom_keystore")
        val sha256 = getCertificateFingerprint(cert)
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
        KeystoreInfo(
            alias = KEY_ALIAS,
            algorithm = "${cert.publicKey.algorithm} (${cert.sigAlgName})",
            sha256Fingerprint = sha256,
            subject = cert.subjectX500Principal.name,
            validUntil = dateFormat.format(cert.notAfter),
            isCustom = customMarker.exists()
        )
    }

    suspend fun exportKeystore(outputStream: OutputStream): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            getOrCreateSigningCredentials()
            val keystoreFile = File(context.filesDir, "keystore/$KEYSTORE_FILENAME")
            FileInputStream(keystoreFile).use { input ->
                input.copyTo(outputStream)
            }
            outputStream.flush()
        }
    }

    suspend fun importKeystore(
        inputStream: InputStream,
        password: String? = null,
        alias: String? = null
    ): Result<KeystoreInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = inputStream.readBytes()
            require(bytes.isNotEmpty()) { "Keystore file is empty" }

            val passChars = if (!password.isNullOrEmpty()) password.toCharArray() else KEY_PASSWORD

            var loadedKeyStore: KeyStore? = null
            val types = listOf("PKCS12", "BKS", "JKS")
            for (type in types) {
                try {
                    val ks = KeyStore.getInstance(type)
                    ByteArrayInputStream(bytes).use { bais ->
                        ks.load(bais, passChars)
                    }
                    loadedKeyStore = ks
                    break
                } catch (_: Exception) {
                    // Try next format
                }
            }

            val ks = loadedKeyStore
                ?: throw IllegalArgumentException("Could not decrypt or parse keystore with provided password")

            val targetAlias = if (!alias.isNullOrBlank() && ks.containsAlias(alias)) {
                alias
            } else {
                ks.aliases().asSequence().firstOrNull { ks.isKeyEntry(it) }
                    ?: throw IllegalArgumentException("No key entries found in keystore")
            }

            val key = ks.getKey(targetAlias, passChars) as? PrivateKey
                ?: throw IllegalArgumentException("Private key not found for alias: $targetAlias")
            val certChain = ks.getCertificateChain(targetAlias)
                ?: arrayOf(ks.getCertificate(targetAlias) ?: throw IllegalArgumentException("Certificate not found for alias: $targetAlias"))

            val destKs = KeyStore.getInstance("PKCS12")
            destKs.load(null, KEY_PASSWORD)
            destKs.setKeyEntry(KEY_ALIAS, key, KEY_PASSWORD, certChain)

            val keystoreFile = File(context.filesDir, "keystore/$KEYSTORE_FILENAME")
            keystoreFile.parentFile?.mkdirs()
            FileOutputStream(keystoreFile).use { fos ->
                destKs.store(fos, KEY_PASSWORD)
            }

            File(context.filesDir, "keystore/is_custom_keystore").createNewFile()

            cachedPrivateKey = key
            cachedCertificate = certChain[0] as X509Certificate

            getKeystoreInfo()
        }
    }

    suspend fun resetToDefaultKeystore(): Result<KeystoreInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val keystoreFile = File(context.filesDir, "keystore/$KEYSTORE_FILENAME")
            if (keystoreFile.exists()) {
                keystoreFile.delete()
            }
            File(context.filesDir, "keystore/is_custom_keystore").delete()
            cachedPrivateKey = null
            cachedCertificate = null
            getOrCreateSigningCredentials()
            getKeystoreInfo()
        }
    }

    private fun getCertificateFingerprint(cert: X509Certificate): String {
        val md = MessageDigest.getInstance("SHA-256")
        val digest = md.digest(cert.encoded)
        return digest.joinToString(":") { "%02X".format(it) }
    }
}
