/*
 * Zalith Launcher 2 - Unlocked Edition
 * Modified to allow Offline/Local accounts globally.
 */

package com.movtery.zalithlauncher.game.account

import android.content.Context
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.coroutine.Task
import com.movtery.zalithlauncher.coroutine.TaskSystem
import com.movtery.zalithlauncher.database.AppDatabase
import com.movtery.zalithlauncher.game.account.auth_server.data.AuthServer
import com.movtery.zalithlauncher.game.account.auth_server.data.AuthServerDao
import com.movtery.zalithlauncher.setting.AllSettings
import com.movtery.zalithlauncher.utils.logging.Logger.lError
import com.movtery.zalithlauncher.utils.logging.Logger.lInfo
import com.movtery.zalithlauncher.utils.network.isNetworkAvailable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.apache.commons.io.FileUtils
import java.util.concurrent.CopyOnWriteArrayList

object AccountsManager {
    private val scope = CoroutineScope(Dispatchers.IO)

    // Account related flows
    private val _accounts = CopyOnWriteArrayList<Account>()
    private val _accountsFlow = MutableStateFlow<List<Account>>(emptyList())
    val accountsFlow = _accountsFlow.asStateFlow()

    private val _currentAccountFlow = MutableStateFlow<Account?>(null)
    val currentAccountFlow = _currentAccountFlow.asStateFlow()

    // Auth Server related flows
    private val _authServers = CopyOnWriteArrayList<AuthServer>()
    private val _authServersFlow = MutableStateFlow<List<AuthServer>>(emptyList())
    val authServersFlow = _authServersFlow.asStateFlow()

    private val _refreshWardrobe = MutableStateFlow(false)
    /** Controls refreshing all account wardrobes */
    val refreshWardrobe = _refreshWardrobe.asStateFlow()

    // Modified: Always false to prevent offline blocking logic
    private val _isOffline = MutableStateFlow(false)
    val isOffline = _isOffline

    private lateinit var database: AppDatabase
    private lateinit var accountDao: AccountDao
    private lateinit var authServerDao: AuthServerDao

    /**
     * Initialize the account system
     */
    fun initialize(context: Context) {
        database = AppDatabase.getInstance(context)
        accountDao = database.accountDao()
        authServerDao = database.authServerDao()
    }

    /**
     * Reload accounts from database
     */
    fun reloadAccounts() {
        scope.launch {
            suspendReloadAccounts()
        }
    }

    /**
     * Refresh wardrobe state
     */
    fun refreshWardrobe() {
        _refreshWardrobe.update { !it }
    }

    private suspend fun suspendReloadAccounts() {
        val loadedAccounts = accountDao.getAllAccounts()
        _accounts.clear()
        _accounts.addAll(loadedAccounts)

        _accounts.sortWith(compareBy<Account>(
            { it.accountTypePriority() },
            { it.username },
        ))
        _accountsFlow.value = _accounts.toList()

        if (_accounts.isNotEmpty() && !isAccountExists(AllSettings.currentAccount.getValue())) {
            setCurrentAccount(_accounts[0])
        }

        refreshCurrentAccountState()

        lInfo("Loaded ${_accounts.size} accounts")
    }

    /**
     * Reload auth servers from database
     */
    fun reloadAuthServers() {
        scope.launch {
            val loadedServers = authServerDao.getAllServers()
            _authServers.clear()
            _authServers.addAll(loadedServers)

            _authServers.sortWith { o1, o2 -> o1.serverName.compareTo(o2.serverName) }
            _authServersFlow.value = _authServers.toList()

            lInfo("Loaded ${_authServers.size} auth servers")
        }
    }

    /**
     * Perform login operation
     */
    fun performLogin(
        context: Context,
        account: Account,
        onSuccess: suspend (Account, task: Task) -> Unit = { _, _ -> },
        onFailed: (th: Throwable) -> Unit = {}
    ) {
        val task = performLoginTask(context, account, onSuccess, onFailed)
        task?.let { TaskSystem.submitTask(it) }
    }

    /**
     * Get login task
     */
    fun performLoginTask(
        context: Context,
        account: Account,
        onSuccess: suspend (Account, task: Task) -> Unit = { _, _ -> },
        onFailed: (th: Throwable) -> Unit = {},
        onFinally: () -> Unit = {}
    ): Task? =
        when {
            account.isNoLoginRequired() -> null
            account.isAuthServerAccount() -> {
                otherLogin(context = context, account = account, onSuccess = onSuccess, onFailed = onFailed, onFinally = onFinally)
            }
            account.isMicrosoftAccount() -> {
                microsoftRefresh(account = account, onSuccess = onSuccess, onFailed = onFailed, onFinally = onFinally)
            }
            else -> null
        }

    /**
     * Refresh an account
     */
    fun refreshAccount(
        context: Context,
        account: Account,
        onFailed: (th: Throwable) -> Unit = {},
    ) {
        if (isNetworkAvailable(context)) {
            performLogin(
                context = context,
                account = account,
                onSuccess = { account, task ->
                    task.updateMessage(R.string.account_logging_in_saving)
                    account.downloadYggdrasil()
                    suspendSaveAccount(account)
                },
                onFailed = onFailed
            )
        }
    }

    /**
     * Get the currently selected account
     */
    private fun getCurrentAccount(): Account? {
        return _accounts.find {
            it.uniqueUUID == AllSettings.currentAccount.getValue()
        } ?: _accounts.firstOrNull()
    }

    /**
     * Set and save current account
     */
    fun setCurrentAccount(account: Account) {
        AllSettings.currentAccount.save(account.uniqueUUID)
        refreshCurrentAccountState()
    }

    /**
     * MODIFIED: Bypasses the Greater China and Microsoft account check.
     * Allows any account to be set as the current account.
     */
    private fun refreshCurrentAccountState() {
        val currentAccount = getCurrentAccount()
        // Always set isOffline to false to unlock functionality
        val isOfflineStatus = false 
        
        _currentAccountFlow.update {
            // Never return null, always allow the current account
            currentAccount
        }
        _isOffline.update { isOfflineStatus }
    }

    /**
     * Save account to DB
     */
    fun saveAccount(account: Account) {
        scope.launch {
            suspendSaveAccount(account)
        }
    }

    /**
     * Save account to DB (suspend)
     */
    suspend fun suspendSaveAccount(account: Account) {
        runCatching {
            accountDao.saveAccount(account)
            lInfo("Saved account: ${account.username}")
        }.onFailure { e ->
            lError("Failed to save account: ${account.username}", e)
        }
        suspendReloadAccounts()
    }

    /**
     * Delete account from DB
     */
    fun deleteAccount(account: Account) {
        scope.launch {
            accountDao.deleteAccount(account)
            val skinFile = account.getSkinFile()
            FileUtils.deleteQuietly(skinFile)
            suspendReloadAccounts()
        }
    }

    /**
     * Save auth server to DB
     */
    suspend fun saveAuthServer(server: AuthServer) {
        runCatching {
            authServerDao.saveServer(server)
            lInfo("Saved auth server: ${server.serverName} -> ${server.baseUrl}")
        }.onFailure { e ->
            lError("Failed to save auth server: ${server.serverName}", e)
        }
        reloadAuthServers()
    }

    /**
     * Delete auth server from DB
     */
    fun deleteAuthServer(server: AuthServer) {
        scope.launch {
            authServerDao.deleteServer(server)
            reloadAuthServers()
        }
    }

    /**
     * MODIFIED: Always returns true to bypass checks requiring a Microsoft account.
     */
    fun hasMicrosoftAccount(): Boolean = true

    /**
     * Load account by profileId
     */
    fun loadFromProfileID(
        profileId: String,
        accountType: String? = null
    ): Account? =
        _accounts.find { it.profileId == profileId && it.accountType == accountType }

    /**
     * Check if account exists
     */
    fun isAccountExists(uniqueUUID: String): Boolean {
        return uniqueUUID.isNotEmpty() && _accounts.any { it.uniqueUUID == uniqueUUID }
    }

    /**
     * Check if auth server exists
     */
    fun isAuthServerExists(baseUrl: String): Boolean {
        return baseUrl.isNotEmpty() && _authServers.any { it.baseUrl == baseUrl }
    }
}