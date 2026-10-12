package com.iumrah.beta.core.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.RemoteViews
import com.iumrah.beta.MainActivity
import com.iumrah.beta.R
import com.iumrah.beta.models.booking.StoredBookingSession
import com.iumrah.beta.models.account.IumrahAccountProfile
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** Only non-secret, user-displayable summaries are copied to the widget process. */
object IumrahWidgetSync {
    private const val STORE = "iumrah.widget.snapshot.v1"

    fun sync(context: Context, sessions: List<StoredBookingSession>, account: IumrahAccountProfile?, language: String) {
        val active = sessions.filter { it.effectiveStatus.uppercase() !in setOf("CANCELLED", "COMPLETED") }
            .minByOrNull { parseDate(it.booking.input.startDate) ?: LocalDate.MAX }
        val editor = context.getSharedPreferences(STORE, Context.MODE_PRIVATE).edit()
        editor.putString("locale", language)
        editor.putString("booking_id", active?.id?.take(100).orEmpty())
        editor.putString("number", active?.displayBookingNumber.orEmpty())
        editor.putString("status", active?.effectiveStatus.orEmpty())
        editor.putString("origin", active?.booking?.input?.originCode.orEmpty())
        editor.putString("destination", active?.booking?.route?.outboundDestination?.takeIf { it.isNotBlank() } ?: active?.booking?.input?.arrivalAirportCode.orEmpty())
        editor.putString("date", active?.booking?.input?.startDate.orEmpty())
        editor.putString("date_end", active?.booking?.input?.endDate.orEmpty())
        editor.putString("guests", active?.booking?.input?.travelers?.totalPeople?.toString().orEmpty())
        editor.putString("name", account?.displayName?.ifBlank { listOf(account.firstName, account.lastName).filter { it.isNotBlank() }.joinToString(" ") }?.take(64).orEmpty())
        editor.putString("iumrah_id", account?.iumrahID?.take(96).orEmpty())
        editor.apply()
        refreshAll(context)
    }

    fun refreshAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        listOf(
            ActiveTripWidget::class.java, BookingStatusWidget::class.java,
            PlannedUmrahWidget::class.java, IdentityWidget::class.java, UmrahCountdownWidget::class.java,
        ).forEach { type ->
            val component = ComponentName(context, type)
            val ids = manager.getAppWidgetIds(component)
            ids.forEach { id -> render(context, manager, id, type) }
        }
    }

    internal fun render(context: Context, manager: AppWidgetManager, widgetId: Int, receiver: Class<*>) {
        val prefs = context.getSharedPreferences(STORE, Context.MODE_PRIVATE)
        val plan = context.getSharedPreferences("iumrah.planned-trip.v1", Context.MODE_PRIVATE)
        val locale = prefs.getString("locale", plan.getString("language", "uz")) ?: "uz"
        val number = prefs.getString("number", "").orEmpty()
        val status = prefs.getString("status", "").orEmpty()
        val code = prefs.getString("origin", "").orEmpty()
        val destination = prefs.getString("destination", "").orEmpty()
        val date = parseDate(prefs.getString("date", null))
        val end = parseDate(prefs.getString("date_end", null))
        val plannedStart = parseDate(plan.getString("start", null))
        val title = plan.getString("title", "Umrah")?.take(80).orEmpty()
        val views = RemoteViews(context.packageName, R.layout.iumrah_widget_card)
        val (heading, main, subtitle, link) = when (receiver) {
            ActiveTripWidget::class.java -> Quad(
                tr(locale, "Активная поездка", "Active trip", "Faol safar", "Фаол сафар"),
                if (number.isNotBlank()) number else "iumrah",
                if (code.isNotBlank() && destination.isNotBlank()) "$code  →  $destination" else tr(locale,"Нет активной поездки","No active trip","Faol safar yo‘q","Фаол сафар йўқ"),
                if (prefs.getString("booking_id", "").isNullOrBlank()) "bookings" else "booking/${Uri.encode(prefs.getString("booking_id", ""))}",
            )
            BookingStatusWidget::class.java -> Quad(
                tr(locale,"Бронирование","Booking status","Bron holati","Брон ҳолати"),
                if (number.isNotBlank()) number else "iumrah",
                statusText(status, locale),
                if (prefs.getString("booking_id", "").isNullOrBlank()) "bookings" else "booking/${Uri.encode(prefs.getString("booking_id", ""))}",
            )
            PlannedUmrahWidget::class.java -> Quad(
                tr(locale,"План умры","Umrah plan","Umra rejasi","Умра режаси"),
                if (plannedStart != null) title else "iumrah",
                plannedStart?.toString() ?: tr(locale,"Создайте план поездки","Create your trip plan","Safar rejasini tuzing","Сафар режасини тузинг"),
                "planned",
            )
            IdentityWidget::class.java -> Quad(
                "iumrah ID", prefs.getString("name", "").orEmpty().ifBlank { "iumrah" },
                prefs.getString("iumrah_id", "").orEmpty().ifBlank { tr(locale,"Откройте профиль","Open your profile","Profilni oching","Профилни очинг") },
                "account",
            )
            else -> {
                val target = date ?: plannedStart
                val days = target?.let { ChronoUnit.DAYS.between(LocalDate.now(), it).coerceAtLeast(0) }
                Quad(
                    tr(locale,"До умры","Umrah countdown","Umragacha","Умрагача"),
                    days?.toString() ?: "—",
                    if (days != null) tr(locale,"дней до начала","days to go","kun qoldi","кун қолди") else tr(locale,"Укажите дату поездки","Choose a trip date","Safar sanasini tanlang","Сафар санасини танланг"),
                    if (date != null) "bookings" else "planned",
                )
            }
        }
        views.setTextViewText(R.id.widget_heading, heading)
        views.setTextViewText(R.id.widget_main, main)
        views.setTextViewText(R.id.widget_subtitle, subtitle)
        views.setTextViewText(R.id.widget_brand, "iumrah")
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("iumrah://widget/$link")
            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(context, widgetId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        views.setOnClickPendingIntent(R.id.widget_root, pending)
        manager.updateAppWidget(widgetId, views)
    }

    private data class Quad(val title: String, val value: String, val detail: String, val link: String)
    private fun parseDate(raw: String?): LocalDate? = runCatching { LocalDate.parse(raw?.take(10) ?: "") }.getOrNull()
    private fun statusText(status: String, locale: String): String = when (status.uppercase()) {
        "AVAILABILITY_CHECK" -> tr(locale,"Проверка наличия","Checking availability","Mavjudlik tekshirilmoqda","Мавжудлик текширилмоқда")
        "PAYMENT_PENDING" -> tr(locale,"Ожидается оплата","Awaiting payment","To‘lov kutilmoqda","Тўлов кутилмоқда")
        "BOOKING_CONFIRMED" -> tr(locale,"Бронирование подтверждено","Booking confirmed","Bron tasdiqlandi","Брон тасдиқланди")
        "READY_TO_TRAVEL" -> tr(locale,"Документы готовы","Ready to travel","Safarga tayyor","Сафарга тайёр")
        "IN_TRIP" -> tr(locale,"В поездке","On trip","Safarda","Сафарда")
        else -> tr(locale,"Нет активной заявки","No active booking","Faol bron yo‘q","Фаол брон йўқ")
    }
    private fun tr(lang: String, ru: String, en: String, uz: String, uzCyrl: String): String = when(lang) {
        "ru" -> ru; "en" -> en; "uz-cyrl" -> uzCyrl; else -> uz
    }
}

abstract class IumrahWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        appWidgetIds.forEach { IumrahWidgetSync.render(context, appWidgetManager, it, javaClass) }
    }
    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        if (intent.action == Intent.ACTION_DATE_CHANGED || intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            IumrahWidgetSync.refreshAll(context)
        }
    }
}
class ActiveTripWidget : IumrahWidgetProvider()
class BookingStatusWidget : IumrahWidgetProvider()
class PlannedUmrahWidget : IumrahWidgetProvider()
class IdentityWidget : IumrahWidgetProvider()
class UmrahCountdownWidget : IumrahWidgetProvider()
