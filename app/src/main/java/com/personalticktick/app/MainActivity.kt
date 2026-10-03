package com.personalticktick.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.personalticktick.app.domain.TaskRepository
import com.personalticktick.app.ui.PersonalTickTickApp
import com.personalticktick.app.ui.TickTickViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val repository = (application as TickTickApplication).repository()
        setContent { PersonalTickTickApp(viewModel(factory = TickTickViewModelFactory(repository))) }
    }
}

private class TickTickViewModelFactory(private val repository: TaskRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return TickTickViewModel(repository) as T
    }
}
