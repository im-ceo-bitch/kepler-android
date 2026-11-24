package com.iumrah.beta.ui.prayer

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.GeomagneticField
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.view.Surface
import android.view.WindowManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.iumrah.beta.core.design.rememberIumrahForeground
import com.iumrah.beta.core.settings.AppLanguage
import com.iumrah.beta.domain.prayer.QiblaBearing
import com.iumrah.beta.ui.cupertino.CupertinoIcon
import com.iumrah.beta.ui.cupertino.CupertinoSymbol
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.math.*

/** A bounded, cancellable coarse-location request; not tied to the selected timetable city. */
internal suspend fun currentPrayerLocation(context: Context): Location? = withTimeoutOrNull(15_000L) {
    suspendCancellableCoroutine { continuation ->
        val manager = context.getSystemService(LocationManager::class.java)
        if (manager == null || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            continuation.resume(null); return@suspendCancellableCoroutine
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                runCatching { manager.removeUpdates(this) }
                if (continuation.isActive) continuation.resume(location)
            }
        }
        continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
        try {
            val provider = listOf(LocationManager.NETWORK_PROVIDER, LocationManager.GPS_PROVIDER).firstOrNull { manager.isProviderEnabled(it) }
            if (provider == null) { continuation.resume(null); return@suspendCancellableCoroutine }
            val cached = manager.getLastKnownLocation(provider)?.takeIf { System.currentTimeMillis() - it.time in 0..300_000L }
            if (cached != null) continuation.resume(cached)
            else manager.requestLocationUpdates(provider, 0L, 0f, listener, Looper.getMainLooper())
        } catch (_: Exception) { if (continuation.isActive) continuation.resume(null) }
    }
}

@Composable
internal fun PrayerQiblaCompass(language: AppLanguage) {
    val context = LocalContext.current
    val foreground = rememberIumrahForeground()
    val manager = remember(context) { context.getSystemService(SensorManager::class.java) }
    val sensor = remember(manager) { manager?.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR) }
    var request by remember { mutableIntStateOf(0) }
    var fix by remember { mutableStateOf<Location?>(null) }
    var heading by remember { mutableStateOf<Double?>(null) }
    var loading by remember { mutableStateOf(false) }
    var permission by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> permission = granted; if (granted) request++ }
    LaunchedEffect(permission, foreground, request) {
        fix = null
        if (permission && foreground) {
            loading = true
            try { fix = currentPrayerLocation(context) } finally { loading = false }
        }
    }
    DisposableEffect(manager, sensor, foreground) {
        heading = null
        val listener = object : SensorEventListener {
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) { if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) heading = null }
            override fun onSensorChanged(event: SensorEvent) {
                if (event.accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) { heading = null; return }
                val matrix = FloatArray(9); val oriented = FloatArray(9); val angles = FloatArray(3)
                SensorManager.getRotationMatrixFromVector(matrix, event.values)
                @Suppress("DEPRECATION")
                val rotation = context.getSystemService(WindowManager::class.java)?.defaultDisplay?.rotation ?: Surface.ROTATION_0
                val axes = when(rotation) {
                    Surface.ROTATION_90 -> SensorManager.AXIS_Y to SensorManager.AXIS_MINUS_X
                    Surface.ROTATION_180 -> SensorManager.AXIS_MINUS_X to SensorManager.AXIS_MINUS_Y
                    Surface.ROTATION_270 -> SensorManager.AXIS_MINUS_Y to SensorManager.AXIS_X
                    else -> SensorManager.AXIS_X to SensorManager.AXIS_Y
                }
                if (SensorManager.remapCoordinateSystem(matrix, axes.first, axes.second, oriented)) {
                    SensorManager.getOrientation(oriented, angles)
                    heading = Math.toDegrees(angles[0].toDouble()).takeIf { it.isFinite() }
                }
            }
        }
        if (foreground && manager != null && sensor != null) manager.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_UI)
        onDispose { manager?.unregisterListener(listener) }
    }
    val bearing = fix?.let { QiblaBearing.degrees(it.latitude, it.longitude) }
    val trueHeading = fix?.let { loc -> heading?.let { h -> h + GeomagneticField(loc.latitude.toFloat(), loc.longitude.toFloat(), loc.altitude.toFloat(), System.currentTimeMillis()).declination } }
    val relative = if (bearing != null && trueHeading != null) QiblaBearing.relative(bearing, trueHeading) else null
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(20.dp)) {
        Text(tr(language, "Кибла", "Qibla", "Qibla", "Қибла"), color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.SemiBold)
        Box(Modifier.size(255.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val radius = size.minDimension / 2f - 2.dp.toPx()
                drawCircle(Color.White.copy(alpha = .72f), radius = radius, style = Stroke(2.dp.toPx()))
                for (tick in 0 until 72) {
                    val theta = tick * PI / 36.0
                    val outer = radius - 5.dp.toPx()
                    val inner = outer - (if (tick % 6 == 0) 15.dp else 7.dp).toPx()
                    drawLine(Color.White.copy(alpha = if (tick % 6 == 0) .9f else .28f), center + Offset((sin(theta) * outer).toFloat(), (-cos(theta) * outer).toFloat()), center + Offset((sin(theta) * inner).toFloat(), (-cos(theta) * inner).toFloat()), strokeWidth = 1.dp.toPx())
                }
            }
            // Do not render a plausible direction when sensor or location is missing.
            relative?.let { CupertinoIcon(CupertinoSymbol.ArrowUp, tr(language, "Направление к Кибле", "Qibla direction", "Qibla yo‘nalishi", "Қибла йўналиши"), Modifier.size(85.dp).rotate(it.toFloat()), tint = Color.White) }
            if (relative == null) Text("—", color = Color.White.copy(alpha = .6f), fontSize = 32.sp)
        }
        when {
            !permission -> TextButton(onClick = { launcher.launch(Manifest.permission.ACCESS_COARSE_LOCATION) }) { Text(tr(language, "Разрешить геолокацию для Киблы", "Allow location for Qibla", "Qibla uchun joylashuvga ruxsat bering", "Қибла учун жойлашувга рухсат беринг"), color = Color.White) }
            sensor == null -> Text(tr(language, "На устройстве нет компаса", "Compass sensor unavailable", "Kompas mavjud emas", "Компас мавжуд эмас"), color = Color.White, textAlign = TextAlign.Center)
            loading -> Text(tr(language, "Определяем местоположение…", "Locating…", "Joylashuv aniqlanmoqda…", "Жойлашув аниқланмоқда…"), color = Color.White)
            fix == null -> TextButton(onClick = { request++ }) { Text(tr(language, "Повторить определение местоположения", "Retry location", "Qayta urinib ko‘ring", "Қайта уриниб кўринг"), color = Color.White) }
            relative == null -> Text(tr(language, "Направление недоступно. Проверьте калибровку компаса.", "Direction unavailable. Check compass calibration.", "Yo‘nalish mavjud emas. Kompasni tekshiring.", "Йўналиш мавжуд эмас. Компасни текширинг."), color = Color.White, textAlign = TextAlign.Center)
            else -> Text(tr(language, "Поверните телефон к Кибле", "Turn your phone toward the Qibla", "Telefoningizni Qiblaga qarating", "Телефонингизни Қиблага қаратинг"), color = Color.White, textAlign = TextAlign.Center)
        }
    }
}
