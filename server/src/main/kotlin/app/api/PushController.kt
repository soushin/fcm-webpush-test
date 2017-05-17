package app.api

import app.config.AppProperties
import app.util.*
import com.fasterxml.jackson.annotation.JsonProperty
import okhttp3.MediaType
import okhttp3.OkHttpClient
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseEntity.ok
import org.springframework.web.bind.annotation.*
import java.security.interfaces.ECPublicKey
import java.util.*
import app.util.getLogger

/**
 *
 * @author nsoushi
 */
@RestController
@RequestMapping(value = "/push")
class PushController(val appProperties: AppProperties) {

    val log = getLogger(PushController::class)

    @CrossOrigin(origins = arrayOf("http://localhost"))
    @PostMapping
    fun post(@RequestBody req: Request): ResponseEntity<Boolean> {

        val registrationId = req.endpoint.replace(appProperties.serverUrl + "/", "")
        val payload  = objectMapper().writeValueAsString(Payload(req.message, req.tag, req.icon, req.url))
        val gcmData = GcmData.create(req.key, req.auth, payload)

        sendWebPush(registrationId, req, gcmData)

        return ok().json().body(true)
    }

    fun sendWebPush(registrationId: String, req: Request, gcmData: GcmData) {

        val body =  objectMapper().writeValueAsString(Response(gcmData.cipherText, listOf(registrationId)))

        val request = okhttp3.Request.Builder()
                .url(appProperties.serverUrl)
                .header("Content-Type", "application/json")
                .header("Encryption", gcmData.encryptionHeader)
                .header("Crypto-Key", gcmData.cryptoKeyHeader)
                .header("Content-Encoding", "aesgcm")
                .header("Authorization", "key=%s".format(appProperties.serverKey))
                .header("Ttl",(2*24*60*60).toString())
                .post(okhttp3.RequestBody.create(MediaType.parse("application/json"), body))
                .build()
        val client = OkHttpClient.Builder().build()
        val response = client.newCall(request).execute()

        return response.body().use { body ->
            log.info("response.code: %d".format(response.code()))
            log.info("response.body: %s".format(body.string()))
        }
    }

    data class GcmData(val cipherText: String, val encryptionHeader: String, val cryptoKeyHeader: String) {

        companion object {
            fun create(remoteKey: String, remoteAuth: String, payload: String): GcmData {

                val keyUtil = EllipticCurveKeyUtil();

                val serverKeys = keyUtil.generateServerKeyPair()

                val clientPublicKey = keyUtil.loadP256Dh(remoteKey)
                val clientAuth = Base64.getUrlDecoder().decode(remoteAuth)
                val salt = PushApiUtil.generateSalt()

                val sharedSecret = keyUtil.generateSharedSecret(serverKeys, clientPublicKey)
                val serverPublicKeyBytes = keyUtil.publicKeyToBytes(serverKeys.public as ECPublicKey)
                val clientPublicKeyBytes = keyUtil.publicKeyToBytes(clientPublicKey)
                val nonceInfo = PushApiUtil.generateInfo(serverPublicKeyBytes, clientPublicKeyBytes, Constants.NONCE)
                val contentEncryptionKeyInfo = PushApiUtil.generateInfo(serverPublicKeyBytes, clientPublicKeyBytes, Constants.AESGCM128)

                val cipherText = PushApiUtil.encryptPayload(payload, sharedSecret, salt, contentEncryptionKeyInfo, nonceInfo, clientAuth)
                val encryptionHeader = PushApiUtil.createEncryptionHeader(salt)
                val cryptoKeyHeader = PushApiUtil.createCryptoKeyHeader(serverPublicKeyBytes)

                return GcmData(cipherText, encryptionHeader, cryptoKeyHeader)
            }
        }
    }

    data class Payload(
            val body: String,
            val tag: String?,
            val icon: String,
            val url: String?)

    data class Response(
            @JsonProperty("raw_data") val rawData: String,
            @JsonProperty("registration_ids") val registrationIds: List<String>)

    data class Request(
            val endpoint: String,
            val key: String,
            val auth: String,
            val message: String,
            val tag: String?,
            val icon: String,
            val url:String?)
}