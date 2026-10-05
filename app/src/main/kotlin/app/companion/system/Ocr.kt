package app.companion.system

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

object Ocr {
    suspend fun read(bitmap: Bitmap): String? = read { InputImage.fromBitmap(bitmap, 0) }

    suspend fun read(c: Context, uri: Uri): String? = read { InputImage.fromFilePath(c, uri) }

    private suspend fun read(image: () -> InputImage): String? = suspendCancellableCoroutine { k ->
        val r = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        k.invokeOnCancellation { r.close() }
        try {
            r.process(image())
                .addOnSuccessListener { if (k.isActive) k.resume(it.text.takeIf(String::isNotBlank)); r.close() }
                .addOnFailureListener { if (k.isActive) k.resume(null); r.close() }
        } catch (_: Exception) {
            r.close()
            if (k.isActive) k.resume(null)
        }
    }
}
