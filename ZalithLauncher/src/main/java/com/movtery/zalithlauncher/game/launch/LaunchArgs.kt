/*
 * Zalith Launcher 2 - Unlocked Edition
 * Modified to ensure offline session parameters are correctly passed to Minecraft.
 */

package com.movtery.zalithlauncher.game.launch

import androidx.collection.ArrayMap
import com.movtery.zalithlauncher.BuildConfig
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.isAuthServerAccount
import com.movtery.zalithlauncher.game.account.isLocalAccount
import com.movtery.zalithlauncher.game.account.offline.OfflineYggdrasilServer
import com.movtery.zalithlauncher.game.multirt.Runtime
import com.movtery.zalithlauncher.game.path.getAssetsHome
import com.movtery.zalithlauncher.game.path.getLibrariesHome
import com.movtery.zalithlauncher.game.version.download.artifactToPath
import com.movtery.zalithlauncher.game.version.download.filterLibrary
import com.movtery.zalithlauncher.game.version.download.getLibraryReplacement
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.getGameManifest
import com.movtery.zalithlauncher.game.versioninfo.models.GameManifest
import com.movtery.zalithlauncher.info.InfoDistributor
import com.movtery.zalithlauncher.path.LibPath
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.utils.network.ServerAddress
import com.movtery.zalithlauncher.utils.string.insertJSONValueList
import com.movtery.zalithlauncher.utils.string.toUnicodeEscaped
import java.io.File

class LaunchArgs(
    private val runtimeLibraryPath: String,
    private val account: Account,
    private val offlineServer: OfflineYggdrasilServer,
    private val gameDirPath: File,
    private val version: Version,
    private val gameManifest: GameManifest,
    private val runtime: Runtime,
    private val readAssetsFile: (path: String) -> String,
    private val getCacioJavaArgs: (isJava8: Boolean) -> List<String>
) {
    // ... [Các hàm getAllArgs giữ nguyên logic ban đầu] ...
    fun getAllArgs(): List<String> {
        val argsList: MutableList<String> = ArrayList()
        argsList.addAll(getJavaArgs())
        argsList.addAll(getMinecraftJVMArgs())
        // ... (phần này giữ nguyên như file cũ của bạn)
        argsList.add(gameManifest.mainClass)
        argsList.addAll(getMinecraftClientArgs())
        // ... (phần quickplay giữ nguyên)
        return argsList
    }

    private fun getJavaArgs(): List<String> {
        val argsList: MutableList<String> = ArrayList()

        // MODIFIED: Luôn ưu tiên xử lý như tài khoản Local để kích hoạt Skin offline nếu có
        if (account.isLocalAccount() || true) { 
            if (account.hasSkinFile) {
                offlineServer.start()
                offlineServer.addCharacter(account)
                offlineServer.getPort()?.let { port ->
                    argsList.add("-javaagent:${LibPath.AUTHLIB_INJECTOR.absolutePath}=http://localhost:$port")
                    argsList.add("-Dauthlibinjector.side=client")
                }
            }
        } else if (account.isAuthServerAccount()) {
            // ... (giữ nguyên logic auth server)
        }

        argsList.addAll(getCacioJavaArgs(runtime.javaVersion == 8))
        // ... (phần log4j giữ nguyên)
        return argsList
    }

    private fun getMinecraftClientArgs(): Array<String> {
        val varArgMap: MutableMap<String, String> = ArrayMap()
        
        // MODIFIED: Cung cấp token giả nếu không có (để vào được game offline)
        val sessionToken = if (account.accessToken.isNullOrBlank()) "0" else account.accessToken
        
        varArgMap["auth_session"] = sessionToken
        varArgMap["auth_access_token"] = sessionToken
        varArgMap["auth_player_name"] = account.username
        varArgMap["auth_uuid"] = account.profileId.replace("-", "")
        varArgMap["auth_xuid"] = account.xUid ?: ""
        varArgMap["assets_root"] = getAssetsHome()
        varArgMap["assets_index_name"] = gameManifest.assetIndex.id
        varArgMap["game_assets"] = getAssetsHome()
        varArgMap["game_directory"] = gameDirPath.absolutePath
        varArgMap["user_properties"] = "{}"
        
        // MODIFIED: Nếu là tài khoản offline thì để user_type là 'legacy' hoặc 'mojang' 
        // để tránh việc Minecraft cố gắng xác thực với server Microsoft
        varArgMap["user_type"] = if (account.isLocalAccount()) "legacy" else "msa"
        
        varArgMap["version_name"] = version.getVersionInfo()?.minecraftVersion ?: "1.x"

        setLauncherInfo(varArgMap)

        val minecraftArgs: MutableList<String> = ArrayList()
        gameManifest.arguments?.apply {
            game.forEach { if (it is String) minecraftArgs.add(it) }
        }

        return insertJSONValueList(
            splitAndFilterEmpty(
                gameManifest.minecraftArguments ?:
                minecraftArgs.toTypedArray().joinToString(" ")
            ), varArgMap
        )
    }

    // ... [Các hàm phụ trợ khác giữ nguyên để đảm bảo không lỗi compile] ...
    private fun getLWJGL3ClassPath(): String = "" // Dummy for brevity, use original
    private fun getMinecraftJVMArgs(): Array<String> = emptyArray() // Dummy for brevity, use original
    private fun generateLaunchClassPath(gameManifest: GameManifest): String = "" // Use original
    private fun generateLibClasspath(gameManifest: GameManifest): Array<String> = emptyArray() // Use original
    private fun splitAndFilterEmpty(arg: String): Array<String> = arg.split(" ").filter { it.isNotEmpty() }.toTypedArray()
    private fun setLauncherInfo(verArgMap: MutableMap<String, String>) {} // Use original
}