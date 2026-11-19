package com.iumrah.beta.ui.booking

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import com.iumrah.beta.core.settings.AppLanguage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.withContext
import java.io.File

internal data class PrivateDocumentPreview(val title: String, val contentType: String, val bytes: ByteArray)
private data class DocumentPage(val bitmap: Bitmap, val count: Int)

private fun renderDocumentPage(context: Context, file: PrivateDocumentPreview, index: Int): DocumentPage {
    val mime = file.contentType.substringBefore(';').trim().lowercase()
    require(file.bytes.isNotEmpty()) { "EMPTY_DOCUMENT" }
    if (mime == "application/pdf") {
        val directory = File(context.cacheDir, "iumrah-private-documents").apply { mkdirs() }
        val temp = File.createTempFile("preview-", ".pdf", directory)
        try {
            temp.writeBytes(file.bytes)
            return ParcelFileDescriptor.open(temp, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                PdfRenderer(ParcelFileDescriptor.dup(descriptor.fileDescriptor)).use { renderer ->
                    require(renderer.pageCount > 0) { "EMPTY_PDF" }
                    renderer.openPage(index.coerceIn(0, renderer.pageCount - 1)).use { page ->
                        val scale = minOf(1800f / page.width.coerceAtLeast(1), 2400f / page.height.coerceAtLeast(1), 3f)
                        val bitmap = Bitmap.createBitmap((page.width * scale).toInt().coerceAtLeast(1), (page.height * scale).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(android.graphics.Color.WHITE)
                        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        DocumentPage(bitmap, renderer.pageCount)
                    }
                }
            }
        } finally { temp.delete() }
    }
    require(mime in setOf("image/png", "image/jpeg", "image/jpg", "image/webp")) { "UNSUPPORTED_DOCUMENT_TYPE" }
    val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(file.bytes, 0, file.bytes.size, options)
    require(options.outWidth > 0 && options.outHeight > 0) { "INVALID_DOCUMENT_IMAGE" }
    var sample = 1
    while (options.outWidth / sample > 2400 || options.outHeight / sample > 2400) sample *= 2
    options.inJustDecodeBounds = false
    options.inSampleSize = sample
    return DocumentPage(requireNotNull(BitmapFactory.decodeByteArray(file.bytes, 0, file.bytes.size, options)), 1)
}

/** Private media stays inside the app; PDF pages are rendered on demand. */
@Composable
internal fun PrivateTravelDocumentDialog(language: AppLanguage, file: PrivateDocumentPreview, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var page by remember(file) { mutableIntStateOf(0) }
    var count by remember(file) { mutableIntStateOf(1) }
    var rendered by remember(file) { mutableStateOf<Bitmap?>(null) }
    var error by remember(file) { mutableStateOf(false) }
    var retry by remember(file) { mutableIntStateOf(0) }
    LaunchedEffect(file, page, retry) {
        rendered = null; error = false
        try {
            val result = withContext(Dispatchers.IO) { renderDocumentPage(context, file, page) }
            count = result.count; rendered = result.bitmap
        } catch (e: CancellationException) { throw e }
        catch (_: Exception) { error = true }
    }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = false, securePolicy = SecureFlagPolicy.SecureOn)) {
        Surface(Modifier.fillMaxSize(), color = bookingIosPage()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(18.dp)) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(file.title, Modifier.weight(1f), style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = onDismiss) { Text(bookingText(language,"Готово","Done","Tayyor","Тайёр")) }
                }
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    val image = rendered
                    when {
                        image != null -> Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
                            Image(image.asImageBitmap(), contentDescription = file.title, modifier = Modifier.fillMaxWidth().aspectRatio(image.width.toFloat() / image.height.coerceAtLeast(1)).background(Color.White), contentScale = ContentScale.Fit)
                        }
                        error -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(bookingText(language,"Не удалось открыть документ","Could not open document","Hujjatni ochib bo‘lmadi","Ҳужжатни очиб бўлмади"))
                            TextButton(onClick = { retry++ }) { Text(bookingText(language,"Повторить","Retry","Qayta urinish","Қайта уриниш")) }
                        }
                        else -> CircularProgressIndicator()
                    }
                }
                if (count > 1) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    TextButton(enabled = page > 0, onClick = { page-- }) { Text(bookingText(language,"Назад","Previous","Orqaga","Орқага")) }
                    Text("${page + 1} / $count")
                    TextButton(enabled = page + 1 < count, onClick = { page++ }) { Text(bookingText(language,"Далее","Next","Keyingi","Кейинги")) }
                }
            }
        }
    }
}
