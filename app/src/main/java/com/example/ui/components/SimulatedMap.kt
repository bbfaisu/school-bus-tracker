package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.RouteStop
import com.example.data.Trip
import kotlin.math.sin

// Real Google Maps SDK and Compose Wrapper Imports
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.rememberMarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.google.maps.android.compose.Polyline
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.MapProperties
import com.google.maps.android.compose.MapType


@OptIn(ExperimentalTextApi::class)
@Composable
fun SimulatedMap(
    trip: Trip?,
    stops: List<RouteStop>,
    modifier: Modifier = Modifier,
    driverLocation: LatLng? = null
) {
    val textMeasurer = rememberTextMeasurer()

    // Pulsing animation for speeding or deviation alerts
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 10f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulse"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    var useRealMap by remember { mutableStateOf(true) }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(280.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xFF1E262A)) // Custom alpine slate dark theme
            .testTag("simulated_map_container")
    ) {
        if (stops.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No Route Data Configured",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            if (useRealMap) {
                RealGoogleMap(trip = trip, stops = stops, modifier = Modifier.fillMaxSize(), driverLocation = driverLocation)
            } else {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                val width = size.width
                val height = size.height
                val padding = 60f

                // Coordinate bounds scaling
                val minLat = stops.map { it.latitude }.minOrNull() ?: 34.0f
                val maxLat = stops.map { it.latitude }.maxOrNull() ?: 34.2f
                val minLng = stops.map { it.longitude }.minOrNull() ?: 74.8f
                val maxLng = stops.map { it.longitude }.maxOrNull() ?: 75.0f

                val latRange = (maxLat - minLat).coerceAtLeast(0.0001f)
                val lngRange = (maxLng - minLng).coerceAtLeast(0.0001f)

                fun getCanvasCoords(lat: Float, lng: Float): Offset {
                    val x = padding + (lng - minLng) / lngRange * (width - 2 * padding)
                    val y = padding + (maxLat - lat) / latRange * (height - 2 * padding)
                    return Offset(x, y)
                }

                // --- DECORATION: Srinagar Dal Lake / Mountain Elements ---
                // Draw a beautiful watery lake on one side for Srinagar aesthetic
                val lakePath = Path().apply {
                    moveTo(width * 0.4f, height * 0.2f)
                    quadraticTo(width * 0.6f, height * 0.1f, width * 0.85f, height * 0.35f)
                    quadraticTo(width * 0.95f, height * 0.65f, width * 0.7f, height * 0.85f)
                    quadraticTo(width * 0.45f, height * 0.9f, width * 0.3f, height * 0.6f)
                    close()
                }
                drawPath(lakePath, color = Color(0xFF165C73).copy(alpha = 0.3f))

                // Lake text label
                drawText(
                    textMeasurer = textMeasurer,
                    text = "DAL LAKE",
                    topLeft = Offset(width * 0.55f, height * 0.45f),
                    style = TextStyle(
                        color = Color(0xFF165C73),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                )

                // Mountains line decoration at top
                val mountainPath = Path().apply {
                    moveTo(0f, height * 0.15f)
                    lineTo(width * 0.2f, height * 0.05f)
                    lineTo(width * 0.4f, height * 0.18f)
                    lineTo(width * 0.55f, height * 0.08f)
                    lineTo(width * 0.75f, height * 0.22f)
                    lineTo(width, height * 0.1f)
                }
                drawPath(
                    mountainPath,
                    color = Color.Gray.copy(alpha = 0.12f),
                    style = Stroke(width = 3f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f)))
                )

                // --- ROUTE PATHS ---
                val stopPoints = stops.map { getCanvasCoords(it.latitude, it.longitude) }

                // Connect stops with a thick route line
                for (i in 0 until stopPoints.size - 1) {
                    drawLine(
                        color = Color(0xFF293F4A),
                        start = stopPoints[i],
                        end = stopPoints[i + 1],
                        strokeWidth = 14f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = Color(0xFF3893C4), // Bright cyan path
                        start = stopPoints[i],
                        end = stopPoints[i + 1],
                        strokeWidth = 6f,
                        cap = StrokeCap.Round
                    )
                }

                // --- STOPS PINS ---
                stops.forEachIndexed { index, stop ->
                    val coords = stopPoints[index]

                    // Outer white border for contrast
                    drawCircle(
                        color = Color.White,
                        radius = 13f,
                        center = coords
                    )
                    // Inner solid black dot
                    drawCircle(
                        color = Color.Black,
                        radius = 9f,
                        center = coords
                    )

                    // Text labels for stops
                    val labelOffset = when {
                        coords.y > height * 0.8f -> Offset(-30f, -48f)
                        else -> Offset(-30f, 18f)
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = "${index + 1}. ${stop.stopName.substringBefore(" ")}",
                        topLeft = coords + labelOffset,
                        style = TextStyle(
                            color = Color(0xFFE0E0E0),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            background = Color(0xFF1E262A).copy(alpha = 0.7f)
                        )
                    )
                }

                // --- LIVE BUS POSITION ---
                if (trip != null) {
                    val busCoords = getCanvasCoords(trip.currentLatitude, trip.currentLongitude)

                    // Draw dynamic connection from route to deviated position if deviated
                    if (trip.isDeviated) {
                        // Find closest stop to draw deviation indicator
                        val currentStopCoords = stopPoints.getOrNull(trip.currentStopIndex) ?: stopPoints.first()
                        drawLine(
                            color = Color(0xFFE53935),
                            start = currentStopCoords,
                            end = busCoords,
                            strokeWidth = 4f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))
                        )

                        // Deviation Warning wave
                        drawCircle(
                            color = Color(0xFFE53935).copy(alpha = pulseAlpha),
                            radius = pulseRadius * 1.2f,
                            center = busCoords
                        )
                    }

                    // Overspeeding feedback
                    val speedLimit = 40.0 // Default limit
                    if (trip.currentSpeed > speedLimit) {
                        // Red pulse
                        drawCircle(
                            color = Color(0xFFE53935).copy(alpha = pulseAlpha),
                            radius = pulseRadius,
                            center = busCoords
                        )
                    }

                    // Overtaking Warning
                    if (trip.isOvertaking) {
                        drawCircle(
                            color = Color(0xFFFFB300).copy(alpha = pulseAlpha),
                            radius = pulseRadius * 1.5f,
                            center = busCoords
                        )
                    }

                    // Bus Glow
                    drawCircle(
                        color = Color(0xFFFF9800).copy(alpha = 0.35f),
                        radius = 24f,
                        center = busCoords
                    )

                    // Main Bus indicator
                    drawCircle(
                        color = Color(0xFFFFC107), // Golden school-bus yellow
                        radius = 14f,
                        center = busCoords
                    )

                    drawCircle(
                        color = Color.Black,
                        radius = 6f,
                        center = busCoords
                    )

                    drawCircle(
                        color = Color.White,
                        radius = 2.5f,
                        center = busCoords
                    )
                }
            }
        }
    }

        // Live labels / overlay
        if (trip != null) {
            val hasViolation = trip.currentSpeed > 40.0 || trip.isDeviated || trip.isOvertaking
            Row(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        if (hasViolation) Color(0xFFD32F2F).copy(alpha = 0.9f)
                        else Color(0xFF388E3C).copy(alpha = 0.9f)
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (hasViolation) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = "Alert",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (trip.isDeviated) "ROUTE DEVIATION" else if (trip.isOvertaking) "DANGEROUS OVERTAKING" else "OVERSPEEDING",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Text(
                        text = "ROUTE COMPLIANT",
                        color = Color.White,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Map Mode Toggle overlay
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color.Black.copy(alpha = 0.75f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Button(
                onClick = { useRealMap = false },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (!useRealMap) Color(0xFFFF9800) else Color.Transparent,
                    contentColor = if (!useRealMap) Color.Black else Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp).testTag("simulated_map_toggle")
            ) {
                Text("SIMULATED", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
            Button(
                onClick = { useRealMap = true },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (useRealMap) Color(0xFFFF9800) else Color.Transparent,
                    contentColor = if (useRealMap) Color.Black else Color.White
                ),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(32.dp).testTag("real_map_toggle")
            ) {
                Text("REAL GOOGLE MAP", fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun RealGoogleMap(
    trip: Trip?,
    stops: List<RouteStop>,
    modifier: Modifier = Modifier,
    driverLocation: LatLng? = null
) {
    val srinagarDefault = LatLng(34.0837, 74.7973)
    val cameraPositionState = rememberCameraPositionState {
        val initialLatLng = if (trip != null) {
            LatLng(trip.currentLatitude.toDouble(), trip.currentLongitude.toDouble())
        } else if (driverLocation != null) {
            driverLocation
        } else if (stops.isNotEmpty()) {
            LatLng(stops.first().latitude.toDouble(), stops.first().longitude.toDouble())
        } else {
            srinagarDefault
        }
        position = CameraPosition.fromLatLngZoom(initialLatLng, 13f)
    }

    val targetLat = trip?.currentLatitude ?: (stops.firstOrNull()?.latitude ?: 34.0837f)
    val targetLng = trip?.currentLongitude ?: (stops.firstOrNull()?.longitude ?: 74.7973f)

    // Smoothly animate marker coordinate transitions
    val animatedLat by animateFloatAsState(
        targetValue = targetLat,
        animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
        label = "busLatitude"
    )
    val animatedLng by animateFloatAsState(
        targetValue = targetLng,
        animationSpec = tween(durationMillis = 2000, easing = LinearEasing),
        label = "busLongitude"
    )

    // Move camera smoothly as bus animated coordinates update
    LaunchedEffect(animatedLat, animatedLng) {
        if (trip != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(
                    LatLng(animatedLat.toDouble(), animatedLng.toDouble())
                )
            )
        }
    }

    // Move camera smoothly if driverLocation changes before the trip starts
    LaunchedEffect(driverLocation) {
        if (trip == null && driverLocation != null) {
            cameraPositionState.animate(
                CameraUpdateFactory.newLatLng(driverLocation)
            )
        }
    }

    val uiSettings = remember {
        MapUiSettings(
            zoomControlsEnabled = true,
            myLocationButtonEnabled = false,
            mapToolbarEnabled = true
        )
    }
    val mapProperties = remember {
        MapProperties(
            mapType = MapType.NORMAL
        )
    }

    Box(modifier = modifier.fillMaxSize()) {
        GoogleMap(
            modifier = Modifier.fillMaxSize(),
            cameraPositionState = cameraPositionState,
            uiSettings = uiSettings,
            properties = mapProperties
        ) {
            // Draw Route Polyline
            val pathPoints = remember(stops) {
                stops.map { LatLng(it.latitude.toDouble(), it.longitude.toDouble()) }
            }
            if (pathPoints.isNotEmpty()) {
                Polyline(
                    points = pathPoints,
                    color = Color(0xFF1976D2),
                    width = 8f
                )
            }

            val blackDotIcon = remember { createBlackDotIcon() }

            // Draw Stop Markers
            stops.forEachIndexed { index, stop ->
                val stopLatLng = LatLng(stop.latitude.toDouble(), stop.longitude.toDouble())
                Marker(
                    state = rememberMarkerState(position = stopLatLng),
                    title = "${index + 1}. ${stop.stopName}",
                    snippet = "Stop Latitude: ${stop.latitude}, Longitude: ${stop.longitude}",
                    icon = blackDotIcon
                )
            }

            // Draw pre-trip driver phone marker if trip is null and driverLocation is not null
            if (trip == null && driverLocation != null) {
                Marker(
                    state = rememberMarkerState(position = driverLocation),
                    title = "Driver's Current Location (Phone)",
                    snippet = "Proceed to the first stop (Black Dot) to start route",
                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE)
                )
            }

            // Draw active bus Marker with smooth animated position
            if (trip != null) {
                val animatedBusLatLng = LatLng(animatedLat.toDouble(), animatedLng.toDouble())
                val markerState = rememberMarkerState(position = animatedBusLatLng)
                
                // Keep marker state in sync with animated position
                LaunchedEffect(animatedBusLatLng) {
                    markerState.position = animatedBusLatLng
                }

                val busIcon = remember<BitmapDescriptor> { createBusIcon() }
                Marker(
                    state = markerState,
                    title = "Active Bus ID: ${trip.busId}",
                    snippet = "Speed: ${trip.currentSpeed.toInt()} km/h",
                    icon = busIcon
                )
            }
        }

        // Overlay message if key is the placeholder
        val isPlaceholder = remember {
            com.example.BuildConfig.MAPS_API_KEY.isEmpty() || 
            com.example.BuildConfig.MAPS_API_KEY == "YOUR_MAPS_API_KEY"
        }
        if (isPlaceholder) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.8f))
                    .padding(8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Demo Key Active: Set MAPS_API_KEY in AI Studio Secrets panel to load real satellite data.",
                    color = Color(0xFFFFB300),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun createBusIcon(): BitmapDescriptor {
    val size = 96
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    // Outer circle shadow/glow
    paint.color = 0x40000000
    canvas.drawCircle(size / 2f, size / 2f + 3f, size / 2f - 3f, paint)

    // Outer circular container: School Bus Yellow
    paint.color = 0xFFFFC107.toInt()
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)

    // Inner dark circle for contrast
    paint.color = 0xFF1E262A.toInt()
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 10f, paint)

    val cx = size / 2f
    val cy = size / 2f

    // Draw wheels
    paint.color = 0xFF121212.toInt()
    canvas.drawRoundRect(cx - 14f, cy + 10f, cx - 8f, cy + 18f, 3f, 3f, paint)
    canvas.drawRoundRect(cx + 8f, cy + 10f, cx + 14f, cy + 18f, 3f, 3f, paint)

    // Bumper
    paint.color = 0xFF757575.toInt()
    canvas.drawRoundRect(cx - 16f, cy + 8f, cx + 16f, cy + 12f, 2f, 2f, paint)

    // Bus body: School Bus Yellow
    paint.color = 0xFFFFC107.toInt()
    canvas.drawRoundRect(cx - 16f, cy - 14f, cx + 16f, cy + 8f, 6f, 6f, paint)

    // Bus roof cap
    paint.color = 0xFFFFD54F.toInt()
    canvas.drawRoundRect(cx - 13f, cy - 14f, cx + 13f, cy - 9f, 3f, 3f, paint)

    // Front windshield window
    paint.color = 0xFF212121.toInt()
    canvas.drawRoundRect(cx - 12f, cy - 7f, cx + 12f, cy + 1f, 3f, 3f, paint)

    paint.color = 0xFF00E5FF.toInt()
    canvas.drawRoundRect(cx - 10f, cy - 5f, cx + 10f, cy - 1f, 2f, 2f, paint)

    // Headlights
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(cx - 9f, cy + 5f, 3.5f, paint)
    canvas.drawCircle(cx + 9f, cy + 5f, 3.5f, paint)

    paint.color = 0xFFFFEB3B.toInt()
    canvas.drawCircle(cx - 9f, cy + 5f, 2f, paint)
    canvas.drawCircle(cx + 9f, cy + 5f, 2f, paint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

private fun createBlackDotIcon(): BitmapDescriptor {
    val size = 48
    val bitmap = android.graphics.Bitmap.createBitmap(size, size, android.graphics.Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)

    // Outer circle white border for visibility and contrast on dark backgrounds or maps
    paint.color = 0xFFFFFFFF.toInt()
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 4f, paint)

    // Inner solid black circle
    paint.color = 0xFF000000.toInt()
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 10f, paint)

    return BitmapDescriptorFactory.fromBitmap(bitmap)
}

