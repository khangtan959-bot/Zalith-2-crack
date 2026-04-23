/*
 * Zalith Launcher 2
 * Copyright (C) 2025 MovTery <movtery228@qq.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/gpl-3.0.txt>.
 */

package com.movtery.zalithlauncher.ui.screens.content

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.movtery.zalithlauncher.R
import com.movtery.zalithlauncher.context.COPY_LABEL_ACCOUNT_UUID
import com.movtery.zalithlauncher.game.account.Account
import com.movtery.zalithlauncher.game.account.AccountsManager
import com.movtery.zalithlauncher.game.account.auth_server.data.AuthServer
import com.movtery.zalithlauncher.game.account.isAuthServerAccount
import com.movtery.zalithlauncher.game.account.isMicrosoftLogging
import com.movtery.zalithlauncher.game.account.yggdrasil.PlayerProfile
import com.movtery.zalithlauncher.ui.base.BaseScreen
import com.movtery.zalithlauncher.ui.components.BackgroundCard
import com.movtery.zalithlauncher.ui.components.MarqueeText
import com.movtery.zalithlauncher.ui.components.ModelAnimation
import com.movtery.zalithlauncher.ui.components.PlayerSkin
import com.movtery.zalithlauncher.ui.components.ScalingActionButton
import com.movtery.zalithlauncher.ui.components.ScalingLabel
import com.movtery.zalithlauncher.ui.components.SimpleAlertDialog
import com.movtery.zalithlauncher.ui.components.SimpleEditDialog
import com.movtery.zalithlauncher.ui.components.SimpleListDialog
import com.movtery.zalithlauncher.ui.screens.NormalNavKey
import com.movtery.zalithlauncher.ui.screens.content.elements.AccountItem
import com.movtery.zalithlauncher.ui.screens.content.elements.AccountOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.AccountSkinOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.ChangeSkinDialog
import com.movtery.zalithlauncher.ui.screens.content.elements.LocalLoginDialog
import com.movtery.zalithlauncher.ui.screens.content.elements.LocalLoginOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.LoginMenuDialog
import com.movtery.zalithlauncher.ui.screens.content.elements.LoginMenuOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.MicrosoftLoginOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.MicrosoftLoginTipDialog
import com.movtery.zalithlauncher.ui.screens.content.elements.OtherLoginOperation
import com.movtery.zalithlauncher.ui.screens.content.elements.OtherServerLoginDialog
import com.movtery.zalithlauncher.ui.screens.content.elements.ServerOperation
import com.movtery.zalithlauncher.utils.animation.swapAnimateDpAsState
import com.movtery.zalithlauncher.utils.copyText
import com.movtery.zalithlauncher.utils.string.getMessageOrToString
import com.movtery.zalithlauncher.viewmodel.AccountManageEffect
import com.movtery.zalithlauncher.viewmodel.AccountManageIntent
import com.movtery.zalithlauncher.viewmodel.AccountManageViewModel
import com.movtery.zalithlauncher.viewmodel.ErrorViewModel
import com.movtery.zalithlauncher.viewmodel.LocalBackgroundViewModel
import com.movtery.zalithlauncher.viewmodel.ScreenBackStackViewModel

private data class AccountActions(
    val onIntent: (AccountManageIntent) -> Unit,
    val openLink: (url: String) -> Unit,
    val backToMainScreen: () -> Unit,
    val navigateToWeb: (url: String) -> Unit,
    val checkIfInWebScreen: () -> Boolean,
    val formatError: (Context, Throwable) -> String,
    val submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
)

enum class FirstLoginMenu {
    NONE, MICROSOFT, NORMAL
}

@Composable
fun AccountManageScreen(
    key: NormalNavKey.AccountManager,
    backStackViewModel: ScreenBackStackViewModel,
    backToMainScreen: () -> Unit,
    openLink: (url: String) -> Unit,
    submitError: (ErrorViewModel.ThrowableMessage) -> Unit,
    viewModel: AccountManageViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val loginUiState by viewModel.loginUiState.collectAsStateWithLifecycle()
    val profileUiState by viewModel.profileUiState.collectAsStateWithLifecycle()
    val operationUiState by viewModel.operationUiState.collectAsStateWithLifecycle()

    val actions = remember(
        viewModel,
        backToMainScreen,
        openLink,
        backStackViewModel,
        submitError
    ) {
        AccountActions(
            onIntent = viewModel::onIntent,
            openLink = openLink,
            backToMainScreen = backToMainScreen,
            navigateToWeb = { url -> backStackViewModel.mainScreen.backStack.navigateToWeb(url) },
            checkIfInWebScreen = { backStackViewModel.mainScreen.currentKey is NormalNavKey.WebScreen },
            formatError = { _, th -> viewModel.formatAccountError(th) },
            submitError = submitError,
        )
    }

    // --- INTEGRATED CAPE PICKER LOGIC ---
    val capePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { selectedUri ->
            profileUiState.currentAccount?.let { acc ->
                try {
                    val capeFile = acc.getCapeFile()
                    capeFile.parentFile?.mkdirs()
                    context.contentResolver.openInputStream(selectedUri)?.use { input ->
                        capeFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    Toast.makeText(context, "Cập nhật Cape thành công!", Toast.LENGTH_SHORT).show()
                    actions.onIntent(AccountManageIntent.RefreshAccount(acc))
                } catch (e: Exception) {
                    Toast.makeText(context, "Lỗi khi lưu Cape: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        when (key.loginMenu) {
            FirstLoginMenu.NONE -> {}
            FirstLoginMenu.MICROSOFT -> {
                actions.onIntent(AccountManageIntent.UpdateMicrosoftLoginOp(MicrosoftLoginOperation.Tip))
            }

            FirstLoginMenu.NORMAL -> {
                actions.onIntent(AccountManageIntent.UpdateLoginMenuOp(LoginMenuOperation.Login))
            }
        }

        viewModel.effect.collect { effect ->
            when (effect) {
                is AccountManageEffect.ShowError -> {
                    submitError(
                        ErrorViewModel.ThrowableMessage(
                            title = effect.title,
                            message = effect.message
                        )
                    )
                }

                is AccountManageEffect.ShowToast -> {
                    val message = if (effect.formatArgs.isEmpty()) {
                        context.getString(effect.messageRes)
                    } else {
                        context.getString(
                            effect.messageRes,
                            *effect.formatArgs.toTypedArray()
                        )
                    }
                    Toast.makeText(context, message, effect.duration).show()
                }
            }
        }
    }

    BaseScreen(screenKey = key, currentKey = backStackViewModel.mainScreen.currentKey) { isVisible ->
        Box(modifier = Modifier.fillMaxSize()) {
            AccountManageContent(
                isVisible = isVisible,
                loginUiState = loginUiState,
                profileUiState = profileUiState,
                operationUiState = operationUiState,
                actions = actions
            )

            // FAB for Offline Cape Management
            profileUiState.currentAccount?.let { acc ->
                if (acc.accountType == "Offline" || acc.accountType == null) {
                    FloatingActionButton(
                        onClick = { capePicker.launch("image/png") },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(32.dp),
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(imageVector = Icons.Default.Shield, contentDescription = "Cape")
                    }
                }
            }
        }
    }
}

@Composable
private fun AccountManageContent(
    isVisible: Boolean,
    loginUiState: AccountManageViewModel.LoginUiState,
    profileUiState: AccountManageViewModel.ProfileUiState,
    operationUiState: AccountManageViewModel.OperationUiState,
    actions: AccountActions
) {
    Row(modifier = Modifier.fillMaxSize()) {
        ActionsLayout(
            isVisible = isVisible,
            modifier = Modifier
                .fillMaxHeight()
                .padding(12.dp)
                .weight(3f),
            currentAccount = profileUiState.currentAccount,
            isOffline = profileUiState.isOffline,
            actions = actions
        )

        AccountsLayout(
            isVisible = isVisible,
            modifier = Modifier
                .fillMaxHeight()
                .padding(top = 12.dp, end = 12.dp, bottom = 12.dp)
                .weight(7f),
            accounts = profileUiState.accounts,
            currentAccount = profileUiState.currentAccount,
            isOffline = profileUiState.isOffline,
            accountOperation = operationUiState.accountOp,
            accountSkinOperation = operationUiState.accountSkinOp,
            accountSkinDialogState = operationUiState.accountSkinDialogState,
            accountCapes = profileUiState.accountCapeOpMap,
            actions = actions
        )
    }

    LoginMenuOperation(
        operation = loginUiState.menuOp,
        actions = actions,
        authServers = profileUiState.authServers
    )

    MicrosoftLoginOperation(
        operation = loginUiState.microsoftOp,
        actions = actions
    )

    LocalLoginOperation(
        operation = loginUiState.localOp,
        actions = actions
    )

    OtherLoginOperation(
        operation = loginUiState.otherOp,
        actions = actions
    )

    ServerTypeOperation(
        operation = operationUiState.serverOp,
        actions = actions
    )
}

@Composable
private fun ActionsLayout(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    currentAccount: Account?,
    isOffline: Boolean,
    actions: AccountActions
) {
    val xOffset by swapAnimateDpAsState(
        targetValue = (-40).dp,
        swapIn = isVisible,
        isHorizontal = true
    )

    Column(
        modifier = modifier
            .offset { IntOffset(x = xOffset.roundToPx(), y = 0) }
            .fillMaxHeight()
    ) {
        val refreshWardrobe by AccountsManager.refreshWardrobe.collectAsStateWithLifecycle()
        val accountSkin = remember(currentAccount, refreshWardrobe) {
            currentAccount?.getSkinFile()?.takeIf { it.exists() }
        }
        val accountCape = remember(currentAccount, refreshWardrobe) {
            currentAccount?.getCapeFile()?.takeIf { it.exists() }
        }
        val context = LocalContext.current
        val playerSkin = remember { PlayerSkin(context) }
        var pageFinished by remember { mutableStateOf(false) }

        DisposableEffect(Unit) {
            onDispose {
                playerSkin.destroy()
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    playerSkin.loadWebView(ctx) {
                        pageFinished = true
                        playerSkin.startAnim(ModelAnimation.NewIdle)
                        playerSkin.setAzimuthAndPitch(-35, 10)
                    }
                },
                update = {
                    if (pageFinished) {
                        runCatching {
                            accountSkin?.inputStream().use {
                                playerSkin.loadSkin(it, currentAccount?.skinModelType)
                            }
                        }
                        runCatching {
                            accountCape?.inputStream().use {
                                playerSkin.loadCape(it)
                            }
                        }
                    }
                }
            )

            if (!pageFinished) {
                CircularProgressIndicator()
            }
        }

        ScalingActionButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                if (isOffline) {
                    actions.onIntent(AccountManageIntent.UpdateMicrosoftLoginOp(MicrosoftLoginOperation.Tip))
                } else {
                    actions.onIntent(AccountManageIntent.UpdateLoginMenuOp(LoginMenuOperation.Login))
                }
            }
        ) {
            MarqueeText(text = stringResource(R.string.account_add_new_account))
        }
    }
}

@Composable
private fun LoginMenuOperation(
    operation: LoginMenuOperation,
    actions: AccountActions,
    authServers: List<AuthServer>
) {
    if (operation is LoginMenuOperation.Login) {
        LoginMenuDialog(
            onDismissRequest = {
                actions.onIntent(AccountManageIntent.UpdateLoginMenuOp(LoginMenuOperation.None))
            },
            authServers = authServers,
            onMicrosoftLogin = {
                if (!isMicrosoftLogging()) {
                    actions.onIntent(AccountManageIntent.UpdateMicrosoftLoginOp(MicrosoftLoginOperation.Tip))
                }
            },
            onLocalLogin = {
                actions.onIntent(AccountManageIntent.UpdateLocalLoginOp(LocalLoginOperation.Edit))
            },
            onAuthServerLogin = {
                actions.onIntent(AccountManageIntent.UpdateOtherLoginOp(OtherLoginOperation.OnLogin(it)))
            },
            onAddAuthServer = {
                actions.onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.AddNew))
            },
            onDeleteAuthServer = {
                actions.onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.Delete(it)))
            }
        )
    }
}

@Composable
private fun MicrosoftLoginOperation(
    operation: MicrosoftLoginOperation,
    actions: AccountActions
) {
    if (operation is MicrosoftLoginOperation.Tip) {
        MicrosoftLoginTipDialog(
            onDismissRequest = {
                actions.onIntent(AccountManageIntent.UpdateMicrosoftLoginOp(MicrosoftLoginOperation.None))
            },
            onConfirm = {
                actions.onIntent(AccountManageIntent.UpdateMicrosoftLoginOp(MicrosoftLoginOperation.None))
                actions.onIntent(
                    AccountManageIntent.PerformMicrosoftLogin(
                        actions.navigateToWeb,
                        actions.backToMainScreen,
                        actions.checkIfInWebScreen
                    )
                )
            },
            openLink = actions.openLink
        )
    }
}

@Composable
private fun LocalLoginOperation(
    operation: LocalLoginOperation,
    actions: AccountActions
) {
    when (operation) {
        is LocalLoginOperation.Edit -> {
            LocalLoginDialog(
                onDismissRequest = {
                    actions.onIntent(AccountManageIntent.UpdateLocalLoginOp(LocalLoginOperation.None))
                },
                onConfirm = { inv, name, uuid ->
                    actions.onIntent(
                        AccountManageIntent.UpdateLocalLoginOp(
                            if (inv) LocalLoginOperation.Alert(name, uuid)
                            else LocalLoginOperation.Create(name, uuid)
                        )
                    )
                },
                openLink = actions.openLink
            )
        }

        is LocalLoginOperation.Create -> {
            LaunchedEffect(operation) {
                actions.onIntent(
                    AccountManageIntent.CreateLocalAccount(
                        operation.userName,
                        operation.userUUID
                    )
                )
            }
        }

        is LocalLoginOperation.Alert -> {
            SimpleAlertDialog(
                title = stringResource(R.string.account_supporting_username_invalid_title),
                text = {
                    Text(text = stringResource(R.string.account_supporting_username_invalid_local_message_hint1))
                },
                confirmText = stringResource(R.string.account_supporting_username_invalid_still_use),
                onConfirm = {
                    actions.onIntent(
                        AccountManageIntent.UpdateLocalLoginOp(
                            LocalLoginOperation.Create(
                                operation.userName,
                                operation.userUUID
                            )
                        )
                    )
                },
                onCancel = {
                    actions.onIntent(AccountManageIntent.UpdateLocalLoginOp(LocalLoginOperation.None))
                }
            )
        }

        is LocalLoginOperation.None -> {}
    }
}

@Composable
private fun OtherLoginOperation(
    operation: OtherLoginOperation,
    actions: AccountActions
) {
    val context = LocalContext.current
    val loggingInFailedTitle = stringResource(R.string.account_logging_in_failed)

    when (operation) {
        is OtherLoginOperation.OnLogin -> {
            OtherServerLoginDialog(
                server = operation.server,
                onRegisterClick = {
                    actions.openLink(it)
                    actions.onIntent(AccountManageIntent.UpdateOtherLoginOp(OtherLoginOperation.None))
                },
                onDismissRequest = {
                    actions.onIntent(AccountManageIntent.UpdateOtherLoginOp(OtherLoginOperation.None))
                },
                onConfirm = { e, p ->
                    actions.onIntent(AccountManageIntent.UpdateOtherLoginOp(OtherLoginOperation.None))
                    actions.onIntent(
                        AccountManageIntent.LoginWithOtherServer(
                            operation.server,
                            e,
                            p
                        )
                    )
                }
            )
        }

        is OtherLoginOperation.OnFailed -> {
            val message = actions.formatError(context, operation.th)
            LaunchedEffect(operation) {
                actions.submitError(
                    ErrorViewModel.ThrowableMessage(
                        title = loggingInFailedTitle,
                        message = message
                    )
                )
                actions.onIntent(AccountManageIntent.UpdateOtherLoginOp(OtherLoginOperation.None))
            }
        }

        is OtherLoginOperation.SelectRole -> {
            SimpleListDialog(
                title = stringResource(R.string.account_other_login_select_role),
                items = operation.profiles,
                itemTextProvider = { it.name },
                onItemSelected = {
                    operation.selected(it)
                },
                onDismissRequest = {
                    actions.onIntent(AccountManageIntent.UpdateOtherLoginOp(OtherLoginOperation.None))
                }
            )
        }

        is OtherLoginOperation.None -> {}
    }
}

@Composable
private fun ServerTypeOperation(
    operation: ServerOperation,
    actions: AccountActions
) {
    when (operation) {
        is ServerOperation.AddNew -> {
            var url by rememberSaveable { mutableStateOf("") }

            SimpleEditDialog(
                title = stringResource(R.string.account_add_new_server),
                value = url,
                onValueChange = {
                    url = it.trim()
                },
                label = {
                    Text(text = stringResource(R.string.account_label_server_url))
                },
                onDismissRequest = {
                    actions.onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.None))
                },
                onConfirm = {
                    if (url.isNotEmpty()) {
                        actions.onIntent(AccountManageIntent.AddServer(url))
                    }
                }
            )
        }

        is ServerOperation.Delete -> {
            SimpleAlertDialog(
                title = stringResource(R.string.account_other_login_delete_server_title),
                text = stringResource(
                    R.string.account_other_login_delete_server_message,
                    operation.server.serverName
                ),
                onDismiss = {
                    actions.onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.None))
                },
                onConfirm = {
                    actions.onIntent(AccountManageIntent.DeleteServer(operation.server))
                }
            )
        }

        is ServerOperation.OnThrowable -> {
            val title = stringResource(R.string.account_other_login_adding_failure)
            val message = operation.throwable.getMessageOrToString()
            LaunchedEffect(operation) {
                actions.submitError(
                    ErrorViewModel.ThrowableMessage(
                        title = title,
                        message = message
                    )
                )
                actions.onIntent(AccountManageIntent.UpdateServerOp(ServerOperation.None))
            }
        }

        is ServerOperation.None -> {}
    }
}

@Composable
private fun AccountsLayout(
    isVisible: Boolean,
    modifier: Modifier = Modifier,
    accounts: List<Account>,
    currentAccount: Account?,
    isOffline: Boolean,
    accountOperation: AccountOperation,
    accountSkinOperation: AccountSkinOperation,
    accountSkinDialogState: AccountManageViewModel.AccountSkinDialogState,
    accountCapes: Map<String, List<PlayerProfile.Cape>>,
    actions: AccountActions
) {
    val yOffset by swapAnimateDpAsState(
        targetValue = (-40).dp,
        swapIn = isVisible
    )
    val context = LocalContext.current

    AccountOperation(
        operation = accountOperation,
        actions = actions
    )

    AccountSkinOperation(
        accountSkinOperation = accountSkinOperation,
        skinDialogState = accountSkinDialogState,
        accountCapes = accountCapes,
        actions = actions
    )

    BackgroundCard(
        modifier = modifier.offset {
            IntOffset(0, yOffset.roundToPx())
        },
        shape = MaterialTheme.shapes.extraLarge
    ) {
        if (accounts.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.extraLarge),
                contentPadding = PaddingValues(12.dp, 6.dp)
            ) {
                items(accounts, key = { it.uniqueUUID }) { account ->
                    AccountItem(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        currentAccount = currentAccount,
                        account = account,
                        enabled = !isOffline,
                        onSelected = {
                            AccountsManager.setCurrentAccount(it)
                        },
                        openChangeSkinDialog = {
                            if (!account.isAuthServerAccount()) {
                                actions.onIntent(
                                    AccountManageIntent.UpdateAccountSkinOp(
                                        AccountSkinOperation.ChangeSkin(account)
                                    )
                                )
                            }
                        },
                        onRefreshClick = {
                            actions.onIntent(AccountManageIntent.RefreshAccount(account))
                        },
                        onCopyUUID = {
                            copyText(COPY_LABEL_ACCOUNT_UUID, account.profileId, context, false)
                        },
                        onDeleteClick = {
                            actions.onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.Delete(account)))
                        }
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                ScalingLabel(text = stringResource(R.string.account_no_account))
            }
        }
    }
}

@Composable
private fun AccountSkinOperation(
    accountSkinOperation: AccountSkinOperation,
    skinDialogState: AccountManageViewModel.AccountSkinDialogState,
    accountCapes: Map<String, List<PlayerProfile.Cape>>,
    actions: AccountActions
) {
    if (accountSkinOperation is AccountSkinOperation.ChangeSkin) {
        val account = accountSkinOperation.account

        ChangeSkinDialog(
            account = account,
            availableCapes = accountCapes[account.uniqueUUID] ?: emptyList(),
            skinState = skinDialogState.pendingSkinData,
            onSkinStateChange = {
                actions.onIntent(AccountManageIntent.UpdatePendingSkinData(it))
            },
            capeState = skinDialogState.pendingCapeData,
            onCapeStateChange = {
                actions.onIntent(AccountManageIntent.UpdatePendingCapeData(it))
            },
            isImportingSkin = skinDialogState.importingSkin,
            onSkinPicked = {
                actions.onIntent(AccountManageIntent.OnSkinPicked(it))
            },
            onDismissRequest = {
                actions.onIntent(AccountManageIntent.ResetAccountSkinDialogState)
                actions.onIntent(AccountManageIntent.UpdateAccountSkinOp(AccountSkinOperation.None))
            },
            onResetSkin = {
                actions.onIntent(AccountManageIntent.ResetSkin(account))
            },
            onFetchCapes = {
                actions.onIntent(AccountManageIntent.FetchMicrosoftCapes(account))
            },
            onApplySkin = { f, m ->
                actions.onIntent(AccountManageIntent.ApplySkin(account, f, m))
            },
            onApplyCape = {
                actions.onIntent(AccountManageIntent.ApplyMicrosoftCape(account, it))
            }
        )
    }
}

@Composable
private fun AccountOperation(
    operation: AccountOperation,
    actions: AccountActions
) {
    val context = LocalContext.current
    val loggingInFailedTitle = stringResource(R.string.account_logging_in_failed)

    when (operation) {
        is AccountOperation.Delete -> {
            SimpleAlertDialog(
                title = stringResource(R.string.account_delete_title),
                text = stringResource(R.string.account_delete_message, operation.account.username),
                onConfirm = {
                    actions.onIntent(AccountManageIntent.DeleteAccount(operation.account))
                },
                onDismiss = {
                    actions.onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
                }
            )
        }

        is AccountOperation.OnFailed -> {
            val message = actions.formatError(context, operation.th)
            LaunchedEffect(operation) {
                actions.submitError(
                    ErrorViewModel.ThrowableMessage(
                        title = loggingInFailedTitle,
                        message = message
                    )
                )
                actions.onIntent(AccountManageIntent.UpdateAccountOp(AccountOperation.None))
            }
        }

        is AccountOperation.None -> {}
    }
}

@Preview(showBackground = true, widthDp = 800, heightDp = 480)
@Composable
private fun AccountManageContentPreview() {
    CompositionLocalProvider(LocalBackgroundViewModel provides null) {
        MaterialTheme {
            Surface {
                AccountManageContent(
                    isVisible = true,
                    loginUiState = AccountManageViewModel.LoginUiState(),
                    profileUiState = AccountManageViewModel.ProfileUiState(),
                    operationUiState = AccountManageViewModel.OperationUiState(),
                    actions = AccountActions(
                        onIntent = {},
                        openLink = {},
                        backToMainScreen = {},
                        navigateToWeb = {},
                        checkIfInWebScreen = { false },
                        formatError = { _, _ -> "" },
                        submitError = {},
                    )
                )
            }
        }
    }
}
