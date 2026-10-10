package com.personalticktick.app.binding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

sealed interface BindingScreenState {
    data object Welcome : BindingScreenState
    data object Connecting : BindingScreenState
    data object Syncing : BindingScreenState
    data class Expired(val message: String = "绑定码已失效") : BindingScreenState
    data class Failed(val message: String) : BindingScreenState
}

@Composable
fun ServerBindingApp(
    state: BindingScreenState,
    onCode: (String) -> Unit,
    onRetry: () -> Unit,
    onInvalidScan: () -> Unit
) {
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(onCode) ?: onInvalidScan()
    }
    val startScan = {
        scanner.launch(
            ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setPrompt("扫描知识背包中的服务器二维码")
                .setBeepEnabled(false)
                .setOrientationLocked(false)
        )
    }
    MaterialTheme(colorScheme = darkColorScheme(primary = Color(0xFF5B8DEF))) {
        Surface(Modifier.fillMaxSize(), color = Color(0xFF121315)) {
            Column(
                Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                when (state) {
                    BindingScreenState.Welcome -> {
                        Box(Modifier.size(66.dp).background(Color(0xFF5B8DEF), CircleShape), contentAlignment = Alignment.Center) {
                            Text("✓", color = Color.White, fontSize = 32.sp)
                        }
                        Spacer(Modifier.height(28.dp))
                        Text("连接你的滴答服务器", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(14.dp))
                        Text("在电脑上的知识背包中打开\n“滴答清单 → 绑定服务器”", color = Color(0xFFA7ABB3), textAlign = TextAlign.Center, lineHeight = 24.sp)
                        Spacer(Modifier.height(34.dp))
                        Button(onClick = startScan, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(12.dp)) {
                            Text("扫描服务器二维码")
                        }
                    }
                    BindingScreenState.Connecting -> ProgressContent("正在连接服务器", "请保持网络连接")
                    BindingScreenState.Syncing -> ProgressContent("服务器连接成功", "正在同步你的任务…")
                    is BindingScreenState.Expired -> ErrorContent("绑定码已失效", "请在知识背包中刷新二维码，然后重新扫描。", "重新扫描", startScan)
                    is BindingScreenState.Failed -> ErrorContent("无法连接服务器", state.message, "重新尝试", onRetry)
                }
            }
        }
    }
}

@Composable private fun ProgressContent(title: String, subtitle: String) {
    CircularProgressIndicator()
    Spacer(Modifier.height(28.dp))
    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(10.dp))
    Text(subtitle, color = Color(0xFFA7ABB3))
}

@Composable private fun ErrorContent(title: String, subtitle: String, action: String, click: () -> Unit) {
    Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    Spacer(Modifier.height(12.dp))
    Text(subtitle, color = Color(0xFFA7ABB3), textAlign = TextAlign.Center)
    Spacer(Modifier.height(30.dp))
    Button(onClick = click, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(action) }
}
