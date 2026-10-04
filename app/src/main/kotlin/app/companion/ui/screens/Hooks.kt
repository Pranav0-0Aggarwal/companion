package app.companion.ui.screens

import android.content.Context
import androidx.compose.runtime.Composable

@Composable
fun gmailLink(onResult: (Boolean) -> Unit): () -> Unit = { onResult(false) }

fun queueImport(c: Context) {}
