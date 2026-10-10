package com.personalticktick.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalticktick.app.binding.*
import com.personalticktick.app.ui.PersonalTickTickApp
import com.personalticktick.app.ui.TickTickViewModel
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
                        ?.let { ServerBinding(it.trimEnd('/') + "/") }
                        ?: bindingStore.read()
                )
            }
            var showBinding by remember { mutableStateOf(activeBinding == null) }
            var screen by remember { mutableStateOf<BindingScreenState>(BindingScreenState.Welcome) }
            var retryAction by remember { mutableStateOf<(() -> Unit)?>(null) }

            fun bind(raw: String) {
                val payload = try { BindingPayload.parse(raw) } catch (_: ExpiredBindingCode) {
                    screen = BindingScreenState.Expired(); return
                } catch (_: InvalidBindingCode) {
                    screen = BindingScreenState.Failed("这不是有效的服务器绑定码"); return
                }
                var tokenConsumed = false
                val action: () -> Unit = action@{
                    if (screen == BindingScreenState.Connecting || screen == BindingScreenState.Syncing) return@action
                    screen = BindingScreenState.Connecting
                    lifecycleScope.launch {
                        try {
                            val apis = withContext(Dispatchers.IO) { ServerApiFactory.create(payload.baseUrl) }
                            if (!tokenConsumed) withContext(Dispatchers.IO) {
                                check(apis.binding.consume(ConsumeBindingRequest(payload.token)).ok)
                                tokenConsumed = true
                            }
                            screen = BindingScreenState.Syncing
                            withContext(Dispatchers.IO) { ServerBootstrapper(database, apis.sync).replaceFromServer() }
                            bindingStore.save(ServerBinding(payload.baseUrl))
                            activeBinding = ServerBinding(payload.baseUrl)
                            showBinding = false
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
                PersonalTickTickApp(viewModel<TickTickViewModel>(), onRebind = {
                    screen = BindingScreenState.Welcome
                    retryAction = null
                    showBinding = true
                })
            }
        }
    }
}
