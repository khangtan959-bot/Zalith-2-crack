/*
 * Zalith Launcher 2 - Unlocked & Fixed Classpath
 * This version restores the original library loading logic while keeping the offline bypass.
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
import com.movtery.zalithlauncher.utils.logging.Logger.lDebug
import com.movtery.zalithlauncher.utils.logging.Logger.lInfo
import com.movtery.zalithlauncher.utils.logging.Logger.lWarning
import com.movtery.zalithlauncher.utils.string.insertJSONValueList
import com.movtery.zalithlauncher.utils.string.isNotEmptyOrBlank
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
    fun getAllArgs(): List<String> {
        val argsList: MutableList<String> = ArrayList()
        argsList.addAll(getJavaArgs())
        argsList.addAll(getMinecraftJVMArgs())
        // Native plugins
        argsList.add(gameManifest.mainClass)
        argsList.addAll(getMinecraftClientArgs())
        return argsList
    }

    private fun getJavaArgs(): List<String> {
        val argsList: MutableList<String> = ArrayList()
        // Offline Skin logic
        if (account.isLocalAccount()) {
            if (account.hasSkinFile) {
                offlineServer.start()
                offlineServer.addCharacter(account)
                offlineServer.getPort()?.let { port ->
                    argsList.add("-javaagent:${LibPath.AUTHLIB_INJECTOR.absolutePath}=http://localhost:$port")
                    argsList.add("-Dauthlibinjector.side=client")
                }
            }
        } else if (account.isAuthServerAccount()) {
            argsList.add("-javaagent:${LibPath.AUTHLIB_INJECTOR.absolutePath}=${account.otherBaseUrl}")
            argsList.add("-Dauthlibinjector.side=client")
        }
        argsList.addAll(getCacioJavaArgs(runtime.javaVersion == 8))
        argsList.add("-Dminecraft.client.jar=${version.getClientJar().absolutePath}")
        return argsList
    }

    private fun getMinecraftJVMArgs(): Array<String> {
        val manifest = getGameManifest(version, true)
        val varArgMap: MutableMap<String, String> = ArrayMap()
        
        // PHẦN QUAN TRỌNG: Khôi phục lại classpath đầy đủ
        val launchClassPath = "${getLWJGL3ClassPath()}:${generateLaunchClassPath(manifest)}"
        var hasClasspathInArgs = false

        varArgMap["classpath_separator"] = ":"
        varArgMap["library_directory"] = getLibrariesHome()
        varArgMap["version_name"] = manifest.id
        varArgMap["natives_directory"] = runtimeLibraryPath
        setLauncherInfo(varArgMap)

        val rawJvmArgs = manifest.arguments?.jvm?.mapNotNull { arg ->
            if (arg is String) {
                if (arg == "\${classpath}") {
                    hasClasspathInArgs = true
                    launchClassPath
                } else arg
            } else null
        }?.toTypedArray() ?: emptyArray()

        val replacedArgs = insertJSONValueList(rawJvmArgs, varArgMap)
        return if (hasClasspathInArgs) replacedArgs else replacedArgs + arrayOf("-cp", launchClassPath)
    }

    private fun generateLaunchClassPath(gameManifest: GameManifest): String {
        val classpathList = mutableListOf<String>()
        val libs = generateLibClasspath(gameManifest)
        
        for (jarFile in libs) {
            if (File(jarFile).exists()) classpathList.add(jarFile)
        }
        
        val clientJar = version.getClientJar()
        if (clientJar.exists()) classpathList.add(clientJar.absolutePath)
        
        return classpathList.joinToString(":")
    }

    private fun generateLibClasspath(gameManifest: GameManifest): Array<String> {
        val libs = LinkedHashMap<GameManifest.Library, String>()
        for (libItem in gameManifest.libraries) {
            if (!(GameManifest.Rule.checkRules(libItem.rules) && !libItem.isNative)) continue
            val path = libItem.progressLibrary() ?: continue
            libs[libItem] = getLibrariesHome() + "/" + path
        }
        return libs.values.toTypedArray()
    }

    private fun GameManifest.Library.progressLibrary(): String? {
        if (filterLibrary()) return null
        var path = artifactToPath(this)
        val nameParts = name.split(":")
        val versionPart = nameParts.getOrNull(2)?.split(".")
        if (versionPart != null) {
            getLibraryReplacement(name, versionPart)?.let { path = it.newPath }
        }
        return path
    }

    private fun getMinecraftClientArgs(): Array<String> {
        val varArgMap: MutableMap<String, String> = ArrayMap()
        val token = if (account.accessToken.isNullOrBlank()) "0" else account.accessToken
        
        varArgMap["auth_session"] = token
        varArgMap["auth_access_token"] = token
        varArgMap["auth_player_name"] = account.username
        varArgMap["auth_uuid"] = account.profileId.replace("-", "")
        varArgMap["auth_xuid"] = account.xUid ?: ""
        varArgMap["assets_root"] = getAssetsHome()
        varArgMap["assets_index_name"] = gameManifest.assetIndex.id
        varArgMap["game_assets"] = getAssetsHome()
        varArgMap["game_directory"] = gameDirPath.absolutePath
        varArgMap["user_properties"] = "{}"
        varArgMap["user_type"] = "legacy" // Force legacy for local accounts
        varArgMap["version_name"] = version.getVersionInfo()?.minecraftVersion ?: "1.21.1"

        setLauncherInfo(varArgMap)

        val minecraftArgs: MutableList<String> = ArrayList()
        gameManifest.arguments?.game?.forEach { if (it is String) minecraftArgs.add(it) }

        return insertJSONValueList(
            (gameManifest.minecraftArguments ?: minecraftArgs.joinToString(" ")).split(" ").filter { it.isNotEmpty() }.toTypedArray(),
            varArgMap
        )
    }

    private fun getLWJGL3ClassPath(): String =
        File(PathManager.DIR_COMPONENTS, "lwjgl3")
            .listFiles { file -> file.name.endsWith(".jar") }
            ?.joinToString(":") { it.absolutePath }
            ?: ""

    private fun setLauncherInfo(verArgMap: MutableMap<String, String>) {
        verArgMap["launcher_name"] = InfoDistributor.LAUNCHER_NAME
        verArgMap["launcher_version"] = BuildConfig.VERSION_NAME
        verArgMap["version_type"] = version.getCustomInfo().takeIf { it.isNotEmptyOrBlank() } ?: gameManifest.type
    }
}
