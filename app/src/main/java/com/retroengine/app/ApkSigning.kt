package com.retroengine.app

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import com.android.apksig.ApkSigner
import com.android.apksig.ApkVerifier
import java.io.File
import java.io.IOException
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.cert.X509Certificate
import java.util.Date
import javax.security.auth.x500.X500Principal

object ApkSigning {

    private const val ALIAS = "retro_engine_games"

    private fun signerConfig(): ApkSigner.SignerConfig {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        if (!ks.containsAlias(ALIAS)) {
            val now = System.currentTimeMillis()
            val gen = KeyPairGenerator.getInstance(KeyProperties.KEY_ALGORITHM_RSA, "AndroidKeyStore")
            gen.initialize(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_SIGN)
                    .setDigests(KeyProperties.DIGEST_SHA256)
                    .setSignaturePaddings(
                        KeyProperties.SIGNATURE_PADDING_RSA_PKCS1,
                        KeyProperties.SIGNATURE_PADDING_RSA_PSS
                    )
                    .setKeySize(2048)
                    .setCertificateSubject(X500Principal("CN=Retro Engine Games"))
                    .setCertificateNotBefore(Date(now - 24L * 3600 * 1000))
                    .setCertificateNotAfter(Date(now + 30L * 365 * 24 * 3600 * 1000))
                    .build()
            )
            gen.generateKeyPair()
        }
        val key = ks.getKey(ALIAS, null) as PrivateKey
        val cert = ks.getCertificate(ALIAS) as X509Certificate
        return ApkSigner.SignerConfig.Builder("retro-engine", key, listOf(cert)).build()
    }
    // Empreinte SHA-256 du certificat de signature, ou null si la cle n'existe pas encore
    fun fingerprint(): String? {
        return try {
            val ks = KeyStore.getInstance("AndroidKeyStore")
            ks.load(null)
            val cert = ks.getCertificate(ALIAS) ?: return null
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(cert.encoded)
            digest.joinToString(":") { "%02X".format(it) }
        } catch (e: Exception) {
            null
        }
    }    
    fun sign(input: File, output: File) {
        ApkSigner.Builder(listOf(signerConfig()))
            .setInputApk(input)
            .setOutputApk(output)
            .setMinSdkVersion(26)
            .setV1SigningEnabled(false)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            .build()
            .sign()
    }

    fun verify(apk: File) {
        val result = ApkVerifier.Builder(apk).setMinCheckedPlatformVersion(26).build().verify()
        if (!result.isVerified) {
            val details = result.errors.joinToString("; ") { it.toString() }
            throw IOException("Signature invalide : $details")
        }
    }
}
