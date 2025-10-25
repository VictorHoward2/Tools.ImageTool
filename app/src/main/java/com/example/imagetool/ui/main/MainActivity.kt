package com.example.imagetool.ui.main

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.*
import com.example.imagetool.ui.crop.CropScreen
import com.example.imagetool.ui.crop.CropViewModel
import com.example.imagetool.ui.theme.PhotoStitcherTheme

class MainActivity : ComponentActivity() {

    private val mainViewModel: MainViewModel by viewModels()
    private val cropViewModel: CropViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            var currentScreen by remember { mutableStateOf<Screen>(Screen.Main) }

            PhotoStitcherTheme {
                when (val screen = currentScreen) {
                    is Screen.Main -> {
                        MainScreen(
                            viewModel = mainViewModel,
                            onCropImage = {
                                cropViewModel.loadBitmapForCrop(it)
                                currentScreen = Screen.Crop(it)
                            }
                        )
                    }
                    is Screen.Crop -> {
                        CropScreen(
                            viewModel = cropViewModel,
                            onBack = { currentScreen = Screen.Main }
                        )
                    }
                }
            }
        }
    }
}

sealed class Screen {
    object Main : Screen()
    data class Crop(val imageUri: Uri) : Screen()
}
