package com.personalticktick.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalticktick.app.binding.*
import com.personalticktick.app.ui.PersonalTickTickApp
import com.personalticktick.app.ui.TickTickViewModel
import com.personalticktick.app.ui.RepositoryUiGateway
import com.personalticktick.app.data.RoomTaskRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val database = com.personalticktick.app.data.TaskDatabase.get(applicationContext)
        val bindingStore = BindingStore(applicationContext)
        setContent {
            var activeBinding by remember {
                mutableStateOf(
                    BuildConfig.API_BASE_URL.takeIf { it.isNotBlank() }
                        ?.let { ServerBinding(it.trimEnd('/') + "/", BuildConfig.API_TOKEN) }
                        ?: bindingStore.read()
                )
            }
            var pendingBinding by remember { mutableStateOf(bindingStore.readPending()) }
            var showBinding by remember { mutableStateOf(activeBinding == null || pendingBinding != null) }
            var screen by remember { mutableStateOf<BindingScreenState>(if (pendingBinding != null) BindingScreenState.Syncing else BindingScreenState.Welcome) }
            var retryAction by remember { mutableStateOf<(() -> Unit)?>(null) }

            fun bootstrap(binding: ServerBinding) {
                lifecycleScope.launch {
                    screen = BindingScreenState.Syncing
                    try {
                        withContext(Dispatchers.IO) { ServerBootstrapper(database, ServerApiFactory.create(binding.baseUrl, binding.accessToken).sync).replaceFromServer() }
                        bindingStore.save(binding)
                        pendingBinding = null
                        activeBinding = binding
                        showBinding = false
                    } catch (error: Exception) {
                        screen = BindingScreenState.Failed(error.message ?: "请检查手机网络以及服务器状态。")
                    }
                }
            }

            LaunchedEffect(pendingBinding) {
                pendingBinding?.let { candidate ->
                    retryAction = { bootstrap(candidate) }
                    bootstrap(candidate)
                }
            }

            fun bind(raw: String) {
                val payload = try { BindingPayload.parse(raw) } catch (_: ExpiredBindingCode) {
                    screen = BindingScreenState.Expired(); return
                } catch (_: InvalidBindingCode) {
                    screen = BindingScreenState.Failed("这不是有效的服务器绑定码"); return
                }
                var sessionToken: String? = null
                val action: () -> Unit = action@{
                    if (screen == BindingScreenState.Connecting || screen == BindingScreenState.Syncing) return@action
                    screen = BindingScreenState.Connecting
                    lifecycleScope.launch {
                        try {
                            if (sessionToken == null) withContext(Dispatchers.IO) {
                                val exchange = ServerApiFactory.create(payload.baseUrl).binding.consume(ConsumeBindingRequest(payload.token))
                                check(exchange.ok)
                                sessionToken = exchange.accessToken
                                val candidate = ServerBinding(payload.baseUrl, exchange.accessToken)
                                bindingStore.savePending(candidate)
                                pendingBinding = candidate
                            }
                        } catch (error: Exception) {
                            screen = BindingScreenState.Failed(error.message ?: "请检查手机网络以及服务器状态。")
                        }
                    }
                }
                retryAction = action
                action()
            }

            if (showBinding) {
                ServerBindingApp(screen, ::bind, { retryAction?.invoke() }, { screen = BindingScreenState.Welcome })
            } else {
                val binding = checkNotNull(activeBinding)
                val factory = remember(binding) { object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        val repository = RoomTaskRepository(database.taskDao(), ServerApiFactory.create(binding.baseUrl, binding.accessToken).sync)
                        return TickTickViewModel(RepositoryUiGateway(repository)) as T
                    }
                } }
                PersonalTickTickApp(viewModel<TickTickViewModel>(key = binding.baseUrl, factory = factory), onRebind = {
                    screen = BindingScreenState.Welcome
                    retryAction = null
                    showBinding = true
                })
            }
        }
    }
}
