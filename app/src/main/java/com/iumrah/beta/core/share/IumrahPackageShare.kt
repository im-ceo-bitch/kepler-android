package com.iumrah.beta.core.share

import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.domain.pricing.PackageQuote
import com.iumrah.beta.models.hotel.StorefrontPackageSnapshot
import java.io.File
import java.io.FileOutputStream
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

object IumrahPackageShare {
    fun share(context: Context, snapshot: StorefrontPackageSnapshot, quote: PackageQuote?, language: AppLanguage) {
        val dir = File(context.cacheDir, "iumrah-share").apply { mkdirs() }
        val slug = snapshot.id.replace(Regex("[^A-Za-z0-9_-]"), "-").take(64)
        val image = File(dir, "iumrah-package-$slug.png")
        val pdf = File(dir, "iumrah-package-$slug.pdf")
        val link = "https://iumrah.app/flights/package/${snapshot.id}"
        renderImage(context, snapshot, quote, language, link, image)
        renderPdf(context, snapshot, quote, language, link, pdf)

        val imageUri = FileProvider.getUriForFile(context, "${context.packageName}.files", image)
        val pdfUri = FileProvider.getUriForFile(context, "${context.packageName}.files", pdf)
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = "*/*"
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(imageUri, pdfUri))
            putExtra(Intent.EXTRA_TEXT, message(snapshot, quote, language, link))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "iumrah"))
    }

    private fun message(snapshot: StorefrontPackageSnapshot, quote: PackageQuote?, language: AppLanguage, link: String): String {
        val hotel = snapshot.hotelName ?: "iumrah"
        val total = quote?.totalPackagePrice?.toDouble() ?: snapshot.totalPackagePrice
        val per = quote?.pricePerPerson?.toDouble() ?: snapshot.pricePerPerson
        val route = snapshot.routeSummary ?: listOfNotNull(snapshot.outbound?.let { "${it.origin} → ${it.destination}" }, snapshot.inbound?.let { "${it.origin} → ${it.destination}" }).joinToString(" · ")
        val days = snapshot.totalDays ?: 0
        val people = snapshot.configuration?.let { it.adults + it.children + it.infants } ?: 2
        val rooms = snapshot.configuration?.rooms ?: 1
        return when (language) {
            AppLanguage.RUSSIAN -> "Пакет Умры · iumrah Configurator\n$hotel · ${snapshot.tier.orEmpty()}\n$route\n${dateRange(snapshot, language)}\n$days дн. · $people паломн. · $rooms комн.\n${money(total)} за пакет · ${money(per)} на человека\n$link"
            AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Umrah package · iumrah Configurator\n$hotel · ${snapshot.tier.orEmpty()}\n$route\n${dateRange(snapshot, language)}\n$days days · $people pilgrims · $rooms rooms\n${money(total)} package · ${money(per)} per person\n$link"
            AppLanguage.UZBEK -> "Umra paketi · iumrah Configurator\n$hotel · ${snapshot.tier.orEmpty()}\n$route\n${dateRange(snapshot, language)}\n$days kun · $people ziyoratchi · $rooms xona\n${money(total)} paket · ${money(per)} kishi boshiga\n$link"
            AppLanguage.UZBEK_CYRILLIC -> "Умра пакети · iumrah Configurator\n$hotel · ${snapshot.tier.orEmpty()}\n$route\n${dateRange(snapshot, language)}\n$days кун · $people зиёратчи · $rooms хона\n${money(total)} пакет · ${money(per)} киши бошига\n$link"
        }
    }

    private fun renderImage(context: Context, snapshot: StorefrontPackageSnapshot, quote: PackageQuote?, language: AppLanguage, link: String, file: File) {
        val bitmap = android.graphics.Bitmap.createBitmap(1080, 1350, android.graphics.Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(Color.WHITE)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK
        canvas.drawRect(0f, 0f, 1080f, 310f, paint)
        draw(context, canvas, paint, "iumrah", 72f, 120f, 62f, Color.WHITE, true)
        draw(context, canvas, paint, "Configurator", 72f, 174f, 28f, 0xBFFFFFFF.toInt(), false)
        val route = snapshot.routeSummary ?: snapshot.outbound?.let { "${it.origin} → ${it.destination}" }.orEmpty()
        draw(context, canvas, paint, route, 72f, 250f, 34f, Color.WHITE, true)
        draw(context, canvas, paint, snapshot.hotelName ?: "iumrah", 72f, 430f, 51f, Color.BLACK, true)
        val people = snapshot.configuration?.let { it.adults + it.children + it.infants } ?: 2
        draw(context, canvas, paint, "${snapshot.tier.orEmpty()} · ${snapshot.totalDays ?: 0} days · $people pilgrims", 72f, 500f, 28f, Color.DKGRAY, false)
        draw(context, canvas, paint, dateRange(snapshot, language), 72f, 550f, 24f, Color.GRAY, false)
        draw(context, canvas, paint, money(quote?.pricePerPerson?.toDouble() ?: snapshot.pricePerPerson), 72f, 700f, 88f, Color.BLACK, true)
        draw(context, canvas, paint, when(language){ AppLanguage.RUSSIAN -> "на человека"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "per person"; AppLanguage.UZBEK -> "kishi boshiga"; AppLanguage.UZBEK_CYRILLIC -> "киши бошига" }, 72f, 750f, 27f, Color.GRAY, false)
        draw(context, canvas, paint, "${money(quote?.totalPackagePrice?.toDouble() ?: snapshot.totalPackagePrice)} · full package", 72f, 835f, 34f, Color.DKGRAY, true)
        paint.color = Color.BLACK
        canvas.drawRoundRect(72f, 1126f, 1008f, 1238f, 34f, 34f, paint)
        draw(context, canvas, paint, when(language){ AppLanguage.RUSSIAN -> "Открыть в iumrah"; AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> "Open in iumrah"; AppLanguage.UZBEK -> "iumrah’da ochish"; AppLanguage.UZBEK_CYRILLIC -> "iumrah’да очиш" }, 112f, 1194f, 34f, Color.WHITE, true)
        draw(context, canvas, paint, "iumrah.app", 72f, 1302f, 20f, Color.GRAY, false)
        FileOutputStream(file).use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    private fun renderPdf(context: Context, snapshot: StorefrontPackageSnapshot, quote: PackageQuote?, language: AppLanguage, link: String, file: File) {
        val doc = PdfDocument()
        val page = doc.startPage(PdfDocument.PageInfo.Builder(595, 842, 1).create())
        val canvas = page.canvas
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.BLACK
        canvas.drawRect(0f, 0f, 595f, 86f, paint)
        draw(context, canvas, paint, "iumrah", 42f, 58f, 26f, Color.WHITE, true)
        draw(context, canvas, paint, "CONFIGURATOR", 420f, 54f, 10f, 0xBFFFFFFF.toInt(), true)
        draw(context, canvas, paint, snapshot.hotelName ?: "Umrah package", 42f, 140f, 26f, Color.BLACK, true)
        draw(context, canvas, paint, snapshot.routeSummary ?: "${snapshot.originCode} → Saudi Arabia", 42f, 178f, 16f, Color.DKGRAY, false)
        draw(context, canvas, paint, dateRange(snapshot, language), 42f, 210f, 13f, Color.GRAY, false)
        draw(context, canvas, paint, money(quote?.totalPackagePrice?.toDouble() ?: snapshot.totalPackagePrice), 42f, 305f, 39f, Color.BLACK, true)
        draw(context, canvas, paint, "${money(quote?.pricePerPerson?.toDouble() ?: snapshot.pricePerPerson)} · per person", 42f, 344f, 17f, Color.DKGRAY, true)
        draw(context, canvas, paint, link, 42f, 735f, 10f, Color.GRAY, false)
        draw(context, canvas, paint, "Price and availability are rechecked before booking.", 42f, 785f, 10f, Color.GRAY, false)
        doc.finishPage(page)
        FileOutputStream(file).use { doc.writeTo(it) }
        doc.close()
    }

    private fun draw(context: Context, canvas: android.graphics.Canvas, paint: Paint, text: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean) {
        paint.textSize = size
        paint.color = color
        paint.typeface = androidx.core.content.res.ResourcesCompat.getFont(context, if (bold) com.iumrah.beta.R.font.lato_bold else com.iumrah.beta.R.font.lato_regular)
        canvas.drawText(text.take(90), x, y, paint)
    }

    private fun money(value: Double?): String = value?.let { "$${String.format(Locale.US, "%.0f", it)}" } ?: "—"

    private fun dateRange(snapshot: StorefrontPackageSnapshot, language: AppLanguage): String {
        fun format(raw: String?): String {
            if (raw.isNullOrBlank()) return "—"
            return runCatching {
                val locale = when(language){ AppLanguage.RUSSIAN -> Locale("ru"); AppLanguage.ENGLISH, AppLanguage.ARABIC, AppLanguage.TURKISH, AppLanguage.INDONESIAN, AppLanguage.MALAY -> language.locale; AppLanguage.UZBEK -> Locale.forLanguageTag("uz-Latn"); AppLanguage.UZBEK_CYRILLIC -> Locale.forLanguageTag("uz-Cyrl") }
                OffsetDateTime.parse(raw).format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
            }.getOrElse { raw.take(10) }
        }
        return "${format(snapshot.outbound?.departureAt ?: snapshot.startDate)} – ${format(snapshot.inbound?.departureAt ?: snapshot.endDate)}"
    }
}
