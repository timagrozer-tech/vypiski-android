package ru.scanner291.app

import androidx.activity.ComponentActivity
import dagger.hilt.android.AndroidEntryPoint

/** Хост для Compose-тестов, которым нужны hiltViewModel(). Только debug. */
@AndroidEntryPoint
class HiltTestActivity : ComponentActivity()
