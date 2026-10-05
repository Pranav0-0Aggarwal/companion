package app.companion.ui

import kotlinx.coroutines.flow.MutableStateFlow

object Pick {
    val item = MutableStateFlow<Long?>(null)
}
