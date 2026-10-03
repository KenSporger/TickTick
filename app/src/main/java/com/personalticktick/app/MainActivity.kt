package com.personalticktick.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalticktick.app.ui.PersonalTickTickApp
import com.personalticktick.app.ui.TickTickViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PersonalTickTickApp(viewModel<TickTickViewModel>()) }
    }
}
