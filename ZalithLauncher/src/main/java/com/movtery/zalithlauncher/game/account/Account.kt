/*
 * Zalith Launcher 2 - Unlocked Edition
 * Modified to support Local Skin and Cape management.
 */

package com.movtery.zalithlauncher.game.account

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.movtery.zalithlauncher.game.account.wardrobe.CapeFileDownloader
import com.movtery.zalithlauncher.game.account.wardrobe.SkinFileDownloader
import com.movtery.zalithlauncher.game.account.wardrobe.SkinModelType
import com.movtery.zalithlauncher.game.account.wardrobe.getLocalUUIDWithSkinModel
import com.movtery.zalithlauncher.path.PathManager
import com.movtery.zalithlauncher.utils.logging.Logger.lError
import com.movtery.zalithlauncher.utils.logging.Logger.lInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.withContext
import org.apache.commons.io.FileUtils
import java.io.File
import java.util.UUID

@Entity(tableName = "accounts")
data class Account(
    @PrimaryKey
    val uniqueUUID: String = UUID.randomUUID().toString().lowercase(),
    var accessToken: String = "0",
    var expiresAt: Long = 0L,
    var clientToken: String = "0",
    var username: String = "Steve",
    var profileId: String = getLocalUUIDWithSkinModel(username, SkinModelType.NONE),
    var refreshToken: String = "0",
    var xUid: String? = null,
    var otherBaseUrl: String? = null,
    var otherAccount: String? = null,
    var otherPassword: String? = null,
    var accountType: String? = null,
    var skinModelType: SkinModelType = SkinModelType.NONE
) {
    // MODIFIED: Kiểm tra skin cục bộ
    val hasSkinFile: Boolean
        get() = getSkinFile().exists()

    // MODIFIED: Kiểm tra áo choàng (cape) cục bộ
    val hasCapeFile: Boolean
        get() = getCapeFile().exists()

    fun getSkinFile() = File(PathManager.DIR_ACCOUNT_SKIN, "$uniqueUUID.png")

    fun getCapeFile() = File(PathManager.DIR_ACCOUNT_CAPE, "$uniqueUUID.png")

    /**
     * Tải và cập nhật skin/cape từ server Yggdrasil (nếu có mạng)
     */
    suspend fun downloadYggdrasil() = withContext(Dispatchers.IO) {
        val baseUrl = when {
            isMicrosoftAccount() -> "https://sessionserver.mojang.com"
            isAuthServerAccount() -> otherBaseUrl!!.removeSuffix("/") + "/sessionserver/"
            else -> null
        }
        baseUrl?.let { url ->
            listOf(
                async { updateSkin(url) },
                async { updateCape(url) }
            ).joinAll()
        }
    }

    private suspend fun updateSkin(url: String) {
        val skinFile = getSkinFile()
        // Đảm bảo thư mục tồn tại
        if (!skinFile.parentFile!!.exists()) skinFile.parentFile!!.mkdirs()
        
        if (skinFile.exists()) FileUtils.deleteQuietly(skinFile)

        runCatching {
            SkinFileDownloader().download(url, skinFile, profileId) { modelType ->
                this.skinModelType = modelType
            }
            lInfo("Cập nhật skin thành công")
        }.onFailure { e ->
            lError("Không thể cập nhật skin", e)
        }
        AccountsManager.refreshWardrobe()
    }

    private suspend fun updateCape(url: String) {
        val capeFile = getCapeFile()
        // Đảm bảo thư mục tồn tại
        if (!capeFile.parentFile!!.exists()) capeFile.parentFile!!.mkdirs()

        if (capeFile.exists()) FileUtils.deleteQuietly(capeFile)

        runCatching {
            CapeFileDownloader().download(url, capeFile, profileId)
            lInfo("Cập nhật cape thành công")
        }.onFailure { e ->
            lError("Không thể cập nhật cape", e)
        }
        AccountsManager.refreshWardrobe()
    }
}
