/*
 * Zalith Launcher 2 - Unlocked & Cape Support
 * Added logic to inject offline capes into the internal Yggdrasil server.
 */

package com.movtery.zalithlauncher.game.launch

import androidx.collection.ArrayMap
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.isLocalAccount
import com.movtery.zalithlauncher.game.account.offline.OfflineYggdrasilServer
import com.movtery.zalithlauncher.path.LibPath
import com.movtery.zalithlauncher.utils.logging.Logger.lInfo
import java.io.File

// ... (Giữ các import cũ)

class LaunchArgs(
    // ... (Các thuộc tính cũ)
    private val account: Account,
    private val offlineServer: OfflineYggdrasilServer,
    // ...
) {

    private fun getJavaArgs(): List<String> {
        val argsList: MutableList<String> = ArrayList()

        if (account.isLocalAccount()) {
            // Kiểm tra xem có Skin HOẶC có Cape không
            // Lưu ý: Bạn cần đảm bảo class Account có thuộc tính hasCapeFile và capeFile
            if (account.hasSkinFile || (account.hasCapeFile == true)) {
                
                offlineServer.start()
                
                // Nạp nhân vật vào server giả lập
                // Server này sẽ trả về JSON chứa URL skin/cape trỏ về localhost
                offlineServer.addCharacter(account) 
                
                offlineServer.getPort()?.let { port ->
                    val msg = "Đang kích hoạt Skin & Cape Offline tại cổng $port"
                    argsList.add("-javaagent:${LibPath.AUTHLIB_INJECTOR.absolutePath}=http://localhost:$port")
                    argsList.add("-Dauthlibinjector.side=client")
                    lInfo(msg)
                }
            }
        }
        // ... (Các phần còn lại giữ nguyên)
        return argsList
    }
    
    // Lưu ý quan trọng: 
    // Trong class OfflineYggdrasilServer (thường nằm trong mục offline), 
    // bạn cần đảm bảo hàm addCharacter(account) có xử lý việc đọc file cape.
}
