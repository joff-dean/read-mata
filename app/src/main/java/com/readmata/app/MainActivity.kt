package com.readmata.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import com.readmata.app.ui.ReadMataApp

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: MainViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        viewModel = ViewModelProvider(this)[MainViewModel::class.java]
        setContent {
            ReadMataApp(viewModel.controller)
        }
    }

    override fun onStop() {
        viewModel.controller.pauseForBackground()
        super.onStop()
    }
}
