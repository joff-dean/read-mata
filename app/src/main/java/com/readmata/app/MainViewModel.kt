package com.readmata.app

import android.app.Application
import androidx.lifecycle.AndroidViewModel

class MainViewModel(application: Application) : AndroidViewModel(application) {
    val controller = MainController(application.applicationContext)

    override fun onCleared() {
        controller.close()
        super.onCleared()
    }
}
