/*
 * Zalith Launcher 2 - Unlocked Edition
 * Added Offline Cape support for Ktor-based Yggdrasil Server.
 */

package com.movtery.zalithlauncher.game.account.offline

import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.wardrobe.SkinModelType
import com.movtery.zalithlauncher.info.InfoDistributor
import com.movtery.zalithlauncher.utils.logging.Logger.lDebug
import com.movtery.zalithlauncher.utils.logging.Logger.lError
import com.movtery.zalithlauncher.utils.logging.Logger.lInfo
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.ApplicationCall
import io.ktor.server.application.ApplicationStarted
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.cio.CIOApplicationEngine
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.header
import io.ktor.server.response.respond
import io.ktor.server.response.respondBytes
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingContext
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.jackhuang.hmcl.util.DigestUtils
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.Signature
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class OfflineYggdrasilServer(
    private val port: Int = 0,
    val serverName: String = "${InfoDistributor.LAUNCHER_IDENTIFIER}_Offline",
    val implementationName: String = InfoDistributor.LAUNCHER_SHORT_NAME,
    val implementationVersion: String = "1.0"
) {
    private val charactersByUuid = ConcurrentHashMap<String, Character>()
    private val charactersByName = ConcurrentHashMap<String, Character>()
    private val keyPair: KeyPair = KeyPairGenerator.getInstance("RSA").apply {
        initialize(2048)
    }.genKeyPair()

    private val serverStartedLatch = CountDownLatch(1)
    private var isServerRunning = false
    private var server: EmbeddedServer<CIOApplicationEngine, CIOApplicationEngine.Configuration>? = null

    fun start() {
        server = embeddedServer(CIO, port = port) {
            install(ContentNegotiation) {
                json(Json {
                    prettyPrint = true
                    isLenient = true
                    encodeDefaults = true
                })
            }

            routing {
                suspend fun RoutingContext.runCatched(block: suspend RoutingContext.() -> Unit) {
                    runCatching { block() }.onFailure { e -> lError("Internal server error", e) }
                }

                get("/") { runCatched { call.respondText(root(), ContentType.Application.Json) } }
                get("/status") { runCatched { call.respondText(status(), ContentType.Application.Json) } }
                post("/api/profiles/minecraft") { runCatched { call.respondText(profiles(call), ContentType.Application.Json) } }
                get("/sessionserver/session/minecraft/hasJoined") { runCatched { call.respondText(hasJoined(call), ContentType.Application.Json) } }
                post("/sessionserver/session/minecraft/join") { runCatched { call.respond(HttpStatusCode.NoContent) } }
                get("/sessionserver/session/minecraft/profile/{uuid}") { runCatched { call.respondText(profile(call), ContentType.Application.Json) } }
                get("/textures/{hash}") { runCatched { call.respond(texture(call)) } }
            }
        }.apply {
            monitor.subscribe(ApplicationStarted) { serverStartedLatch.countDown() }
        }

        server?.start(wait = false)
        if (serverStartedLatch.await(10L, TimeUnit.SECONDS)) {
            isServerRunning = true
        }
    }

    fun stop() {
        isServerRunning = false
        server?.stop(1000, 5000)
        serverStartedLatch.countDown()
    }

    fun getPort(): Int? {
        if (!isServerRunning) return null
        val engine = server?.engine ?: return null
        return runBlocking {
            try { engine.resolvedConnectors().firstOrNull()?.port } catch (_: Exception) { null }
        }
    }

    /**
     * MODIFIED: Thêm hỗ trợ load Cape từ file cục bộ
     */
    fun addCharacter(account: Account) {
        val skinFile = account.getSkinFile()
        val skinBytes = skinFile.takeIf { it.exists() }?.readBytes()
        val skinHash = skinBytes?.let { DigestUtils.digestToString("SHA-256", it) }

        // MỚI: Xử lý Cape
        val capeFile = account.getCapeFile()
        val capeBytes = capeFile.takeIf { it.exists() }?.readBytes()
        val capeHash = capeBytes?.let { DigestUtils.digestToString("SHA-256", it) }

        val character = Character(
            uuid = account.profileId.replace("-", ""),
            name = account.username,
            skin = LoadedSkin(
                skinHash = skinHash,
                skinBytes = skinBytes,
                model = account.skinModelType,
                capeHash = capeHash,    // Thêm vào đây
                capeBytes = capeBytes    // Thêm vào đây
            )
        )

        charactersByUuid[character.uuid.lowercase()] = character
        charactersByName[character.name.lowercase()] = character

        lInfo("Added character ${character.name} with skin hash=$skinHash, cape hash=$capeHash")
    }

    private fun PublicKey.toPEMPublicKey(): String {
        val base64Key = Base64.getEncoder().encodeToString(this.encoded)
        return "-----BEGIN PUBLIC KEY-----\n$base64Key\n-----END PUBLIC KEY-----"
    }

    private fun root(): String {
        return buildJsonObject {
            put("skinDomains", JsonArray(listOf(JsonPrimitive("127.0.0.1"), JsonPrimitive("localhost"))))
            put("meta", buildJsonObject {
                put("serverName", JsonPrimitive(serverName))
                put("implementationName", JsonPrimitive(implementationName))
                put("implementationVersion", JsonPrimitive(implementationVersion))
                put("feature.non_email_login", JsonPrimitive(true))
            })
            put("signaturePublickey", JsonPrimitive(keyPair.public.toPEMPublicKey()))
        }.toString()
    }

    private fun status(): String = buildJsonObject {
        put("user.count", JsonPrimitive(charactersByUuid.size))
        put("token.count", JsonPrimitive(0))
    }.toString()

    private suspend fun profiles(call: ApplicationCall): String {
        val names = call.receive<List<String>>()
        return buildJsonArray {
            names.distinct().mapNotNull { charactersByName[it.lowercase()] }.forEach { character ->
                add(buildJsonObject {
                    put("id", JsonPrimitive(character.uuid))
                    put("name", JsonPrimitive(character.name))
                })
            }
        }.toString()
    }

    private fun hasJoined(call: ApplicationCall): String {
        val username = call.request.queryParameters["username"] ?: return "{}"
        val character = charactersByName[username.lowercase()] ?: return "{}"
        return character.toCompleteResponse("http://localhost:${getPort()}", this::sign)
    }

    private fun profile(call: ApplicationCall): String {
        val uuid = call.parameters["uuid"] ?: return "{}"
        val character = charactersByUuid[uuid.lowercase()] ?: return "{}"
        return character.toCompleteResponse("http://localhost:${getPort()}", this::sign)
    }

    private suspend fun texture(call: ApplicationCall) {
        val hash = call.parameters["hash"] ?: return call.respond(HttpStatusCode.NotFound)
        
        val match = charactersByUuid.values.firstNotNullOfOrNull { char ->
            when (hash) {
                char.skin?.skinHash -> char.skin.skinBytes
                char.skin?.capeHash -> char.skin.capeBytes // Hỗ trợ trả về Cape bytes
                else -> null
            }
        }

        if (match != null) {
            call.response.header("Cache-Control", "max-age=2592000, public")
            call.response.header("Etag", "\"$hash\"")
            call.respondBytes(match, ContentType.Image.PNG)
        } else {
            call.respond(HttpStatusCode.NotFound)
        }
    }

    private fun sign(data: String): String {
        val signature = Signature.getInstance("SHA1withRSA")
        signature.initSign(keyPair.private)
        signature.update(data.toByteArray(Charsets.UTF_8))
        return Base64.getEncoder().encodeToString(signature.sign())
    }

    data class Character(
        val uuid: String,
        val name: String,
        val skin: LoadedSkin? = null
    ) {
        fun toCompleteResponse(rootUrl: String, signer: (String) -> String): String {
            val texturesObject = buildJsonObject {
                put("timestamp", JsonPrimitive(System.currentTimeMillis()))
                put("profileId", JsonPrimitive(uuid))
                put("profileName", JsonPrimitive(name))
                put("textures", buildJsonObject {
                    skin?.skinHash?.let { hash ->
                        put("SKIN", buildJsonObject {
                            put("url", JsonPrimitive("$rootUrl/textures/$hash"))
                            if (skin.model == SkinModelType.ALEX) {
                                put("metadata", buildJsonObject { put("model", JsonPrimitive("slim")) })
                            }
                        })
                    }
                    // MỚI: Thêm mục CAPE vào metadata trả về cho Minecraft
                    skin?.capeHash?.let { hash ->
                        put("CAPE", buildJsonObject {
                            put("url", JsonPrimitive("$rootUrl/textures/$hash"))
                        })
                    }
                })
            }

            val jsonString = Json.encodeToString(texturesObject)
            val encoded = Base64.getEncoder().encodeToString(jsonString.toByteArray(Charsets.UTF_8))

            return buildJsonObject {
                put("id", JsonPrimitive(uuid))
                put("name", JsonPrimitive(name))
                put("properties", buildJsonArray {
                    add(buildJsonObject {
                        put("name", JsonPrimitive("textures"))
                        put("value", JsonPrimitive(encoded))
                        put("signature", JsonPrimitive(signer(encoded)))
                    })
                })
            }.toString()
        }
    }
}

/**
 * MỚI: Cập nhật class LoadedSkin để lưu cả Cape
 */
data class LoadedSkin(
    val skinHash: String?,
    val skinBytes: ByteArray?,
    val model: SkinModelType,
    val capeHash: String? = null,
    val capeBytes: ByteArray? = null
)
