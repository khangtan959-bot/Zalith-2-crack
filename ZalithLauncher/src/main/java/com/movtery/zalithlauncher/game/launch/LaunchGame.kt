/*
 * Zalith Launcher 2 - Unlocked Edition
 * Modified to bypass network login/refresh and force offline mode.
 */

package com.movtery.zalithlauncher.game.launch

import android.content.Context
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.Task
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.isLocalAccount
import com.movtery.zalithlauncher.game.version.download.DownloadMode
import com.movtery.zalithlauncher.game.version.download.MinecraftDownloader
import com.movtery.zalithlauncher.game.version.installed.Version
import com.movtery.zalithlauncher.game.version.installed.VersionFolders
import com.movtery.zalithlauncher.game.version.mod.AllModReader
import com.movtery.zalithlauncher.ui.activities.runGame
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel

object LaunchGame {
    var isLaunching: Boolean = false
        private set

    fun launchGame(
        context: Context,
        version: Version,
        exitActivity: () -> Unit,
        submitError: (ErrorViewModel.ThrowableMessage) -> Unit
    ) {
        if (isLaunching) return
        val account = AccountsManager.currentAccountFlow.value ?: return
        isLaunching = true

        // MODIFIED: Luôn coi như không có mạng để kích hoạt chế độ Offline Login bên dưới
        // Điều này ngăn Launcher cố gắng liên hệ server xác thực
        val hasNetwork = false 

        val downloadTask = createDownloadTask(
            context = context,
            version = version,
            exitActivity = exitActivity,
            submitError = submitError
        )
        
        fun startDownloadTask() {
            // MODIFIED: Luôn ép sử dụng chế độ đăng nhập offline cho tài khoản hiện tại
            version.offlineAccountLogin = true
            TaskSystem.submitTask(downloadTask) { isLaunching = false }
        }

        // MODIFIED: Bỏ qua hoàn toàn loginTask để không bao giờ chạy trình làm mới tài khoản
        val loginTask: Task? = null

        if (loginTask != null) {
            TaskSystem.submitTask(loginTask)
        } else {
            // Ép đăng nhập offline cho mọi loại tài khoản (Microsoft, AuthServer, Local)
            version.offlineAccountLogin = true
            startDownloadTask()
        }
    }

    private fun createDownloadTask(
        context: Context,
        version: Version,
        exitActivity: () -> Unit,
        submitError: (ErrorViewModel.ThrowableMessage) -> Unit
    ): Task {
        return MinecraftDownloader(
            context = context,
            version = version.getVersionInfo()?.minecraftVersion ?: version.getVersionName(),
            customName = version.getVersionName(),
            verifyIntegrity = !version.skipGameIntegrityCheck(),
            mode = DownloadMode.VERIFY_AND_REPAIR,
            onCompletion = {
                checkEnableTouchProxy(version)
                runGame(context, version)
                exitActivity()
            },
            onError = { message ->
                submitError(
                    ErrorViewModel.ThrowableMessage(
                        title = context.getString(R.string.minecraft_download_failed),
                        message = message
                    )
                )
            }
        ).getDownloadTask()
    }

    private suspend fun checkEnableTouchProxy(version: Version) {
        val modsDir = VersionFolders.MOD.getDir(version.getGameDir())
        val reader = AllModReader(modsDir)
        for (mod in reader.readAllLocals()) {
            if (mod.id == "touchcontroller") {
                version.enableTouchProxy = true
                break
            }
        }
    }

    // MODIFIED: Hàm này giờ đây sẽ không làm gì cả để tránh lỗi xác thực mạng
    private fun createLoginTask(
        context: Context,
        hasNetwork: Boolean,
        account: Account,
        submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
        onFinally: () -> Unit
    ): Task? {
        // Luôn trả về null để bỏ qua bước kiểm tra tài khoản online
        return null
    }
}