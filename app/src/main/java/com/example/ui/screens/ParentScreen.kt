package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.SimulatedMap
import com.example.viewmodel.TrackerViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ParentScreen(
    viewModel: TrackerViewModel,
    modifier: Modifier = Modifier
) {
    val routes by viewModel.allRoutes.collectAsState()
    val activeTrips by viewModel.activeTrips.collectAsState()
    val buses by viewModel.allBuses.collectAsState()
    val drivers by viewModel.allDrivers.collectAsState()
    val violations by viewModel.allViolations.collectAsState()

    var busSearchQuery by remember { mutableStateOf("") }

    // Match entered text against bus plate numbers (lenient, matching fragments, ignoring hyphens/spaces)
    val matchedBus = remember(busSearchQuery, buses) {
        if (busSearchQuery.isBlank()) null
        else buses.find {
            it.plateNumber.replace("-", "").replace(" ", "").contains(
                busSearchQuery.replace("-", "").replace(" ", ""),
                ignoreCase = true
            )
        }
    }

    // Resolve the route assigned to this bus (either from active trip or assigned driver)
    val resolvedRouteId = remember(matchedBus, activeTrips, drivers) {
        if (matchedBus == null) null
        else {
            val activeTrip = activeTrips.find { it.busId == matchedBus.id }
            if (activeTrip != null) {
                activeTrip.routeId
            } else {
                val driver = drivers.find { it.mappedBusId == matchedBus.id }
                driver?.mappedRouteId
            }
        }
    }

    val currentRoute = routes.find { it.id == resolvedRouteId }
    val activeTrip = activeTrips.find { it.busId == matchedBus?.id }

    val stopsFlow = remember(resolvedRouteId) {
        if (resolvedRouteId != null) {
            viewModel.getStopsForRouteFlow(resolvedRouteId)
        } else {
            flowOf(emptyList())
        }
    }
    val stops by stopsFlow.collectAsState(initial = emptyList())

    var parentStopId by remember(resolvedRouteId) { mutableStateOf<Int?>(null) }

    LaunchedEffect(stops) {
        if (parentStopId == null && stops.isNotEmpty()) {
            parentStopId = stops.getOrNull(1)?.id ?: stops.firstOrNull()?.id
        }
    }

    val selectedStop = remember(stops, parentStopId) {
        stops.find { it.id == parentStopId } ?: stops.getOrNull(1) ?: stops.firstOrNull()
    }

    var parentCheckedIn by remember(activeTrip?.id, selectedStop?.id) { mutableStateOf(false) }
    var busArrivalTime by remember(activeTrip?.id, selectedStop?.id) { mutableStateOf<Long?>(null) }
    var secondsSinceBusArrival by remember { mutableStateOf(0) }
    var loggedDelayVal by remember(activeTrip?.id, selectedStop?.id) { mutableStateOf<Double?>(null) }

    val selectedStopIndex = remember(stops, selectedStop) {
        stops.indexOfFirst { it.id == selectedStop?.id }
    }
    val isBusAtSelectedStop = activeTrip != null && activeTrip.currentStopIndex == selectedStopIndex

    LaunchedEffect(isBusAtSelectedStop, parentCheckedIn) {
        if (isBusAtSelectedStop && !parentCheckedIn) {
            if (busArrivalTime == null) {
                busArrivalTime = System.currentTimeMillis()
            }
            while (true) {
                val arrival = busArrivalTime ?: System.currentTimeMillis()
                secondsSinceBusArrival = ((System.currentTimeMillis() - arrival) / 1000).toInt()
                delay(1000)
            }
        } else if (!isBusAtSelectedStop) {
            secondsSinceBusArrival = 0
            busArrivalTime = null
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        // Parent Screen Title
        Text(
            text = "Track Child's School Bus",
            color = Color.White,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = "Enter your child's bus plate number below to track the vehicle's location, compliance speed, and schedule details in real time.",
            color = Color.Gray,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Bus Number Search Input Field
        OutlinedTextField(
            value = busSearchQuery,
            onValueChange = { busSearchQuery = it },
            label = { Text("Enter Bus Plate / Fleet Number") },
            placeholder = { Text("e.g. JK01-4321 or 4321") },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (matchedBus != null) Color(0xFFFF9800) else Color.Gray
                )
            },
            trailingIcon = {
                if (busSearchQuery.isNotEmpty()) {
                    IconButton(onClick = { busSearchQuery = "" }) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Clear Search", tint = Color.Gray)
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFF9800),
                unfocusedBorderColor = Color.DarkGray,
                focusedLabelColor = Color(0xFFFF9800),
                unfocusedLabelColor = Color.Gray,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .testTag("bus_number_input")
        )

        if (busSearchQuery.isEmpty()) {
            // Suggestion view for quick-selection
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Available Fleet Vehicles",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
            Text(
                text = "Tap any bus from the registered Kashmiri school fleet below to track it immediately.",
                color = Color.Gray,
                fontSize = 11.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                itemsIndexed(buses) { _, bus ->
                    val isBusActive = activeTrips.any { it.busId == bus.id }
                    val busDriver = drivers.find { it.mappedBusId == bus.id }
                    val busRoute = routes.find { it.id == (activeTrips.find { it.busId == bus.id }?.routeId ?: busDriver?.mappedRouteId) }

                    Card(
                        onClick = { busSearchQuery = bus.plateNumber },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("suggested_bus_${bus.plateNumber}")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isBusActive) Color(0xFF1B5E20) else Color(0xFF2C2C2C)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsBus,
                                        contentDescription = "Bus Icon",
                                        tint = if (isBusActive) Color(0xFF4CAF50) else Color.Gray,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        text = bus.plateNumber,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = "${bus.model} • ${busRoute?.routeName ?: "No Route"}",
                                        color = Color.Gray,
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Status Badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isBusActive) Color(0xFF1B5E20).copy(alpha = 0.4f) else Color(0xFF2C2C2C))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isBusActive) "ACTIVE" else "OFF-DUTY",
                                    color = if (isBusActive) Color(0xFF81C784) else Color.Gray,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        } else if (matchedBus == null) {
            // Bus search query entered but no matching vehicle found in database
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "No results",
                        tint = Color.Gray,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "No Bus Found Matching \"$busSearchQuery\"",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Check the license plate spelling and try again. Example valid inputs: \"4321\" or \"JK01-9876\".",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center
                    )
                }
            }
        } else {
            // Valid matching bus is found
            val matchedDriver = drivers.find { it.mappedBusId == matchedBus.id }

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Bus Details & Status Summary Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsBus,
                                        contentDescription = "Bus",
                                        tint = Color(0xFFFF9800),
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "FLEET VEHICLE DETAILS",
                                        color = Color(0xFFFF9800),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = matchedBus.plateNumber,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "${matchedBus.model} • Cap: ${matchedBus.capacity}",
                                    color = Color.LightGray,
                                    fontSize = 12.sp
                                )
                                if (matchedDriver != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Driver: ${matchedDriver.name} (${matchedDriver.phoneNumber})",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Dynamic state badge
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (activeTrip != null) Color(0xFF1B5E20) else Color(0xFF2C2C2C))
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = if (activeTrip != null) "LIVE NOW" else "OFF-DUTY",
                                    color = if (activeTrip != null) Color.Green else Color.Gray,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                if (activeTrip == null) {
                    // Bus is off-duty
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A1A)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBus,
                                    contentDescription = "Off Duty",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = "This Bus is Currently Off-Duty",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Once the assigned driver starts the active school trip, real-time map telemetry and speed warnings will show here.",
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                } else {
                    // Bus is live, show map & telemetry
                    val etas = viewModel.calculateETAs(activeTrip, stops, activeTrip.currentSpeed)
                    val distanceToNext = viewModel.getDistanceToNextStop(activeTrip, stops)

                    item {
                        SimulatedMap(trip = activeTrip, stops = stops)
                    }

                    // Telemetry Row
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Live Speed", color = Color.Gray, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Row(verticalAlignment = Alignment.Bottom) {
                                        Text(
                                            text = "${activeTrip.currentSpeed.toInt()}",
                                            color = if (activeTrip.currentSpeed > (currentRoute?.speedLimit ?: 40.0)) Color(0xFFE53935) else Color(0xFFFF9800),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 20.sp
                                        )
                                        Text(" km/h", color = Color.Gray, fontSize = 12.sp)
                                    }
                                }
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1.2f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text("Distance to Next Stop", color = Color.Gray, fontSize = 11.sp)
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = distanceToNext,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }
                        }
                    }

                    // My Stop Portal & Parent Delay Metric Card
                    val parentDelayMinutes = secondsSinceBusArrival * 0.5
                    val hasArrivedPassed = activeTrip.currentStopIndex > selectedStopIndex

                    item {
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    parentCheckedIn && (loggedDelayVal ?: 0.0) > 0.0 -> Color(0xFF2C1B1B)
                                    parentCheckedIn -> Color(0xFF1B5E20)
                                    isBusAtSelectedStop -> Color(0xFF2C1B1B)
                                    else -> Color(0xFF1E1E1E)
                                }
                            ),
                            border = if (isBusAtSelectedStop && !parentCheckedIn) {
                                androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f))
                            } else null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("parent_stop_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Home,
                                            contentDescription = "My Stop",
                                            tint = if (isBusAtSelectedStop && !parentCheckedIn) Color(0xFFE53935) else Color(0xFFFF9800),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "MY BUS STOP PORTAL",
                                            color = if (isBusAtSelectedStop && !parentCheckedIn) Color(0xFFE53935) else Color(0xFFFF9800),
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            letterSpacing = 0.5.sp
                                        )
                                    }

                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(
                                                when {
                                                    parentCheckedIn && (loggedDelayVal ?: 0.0) > 0.0 -> Color(0xFFE53935).copy(alpha = 0.15f)
                                                    parentCheckedIn -> Color(0xFF00E676).copy(alpha = 0.15f)
                                                    isBusAtSelectedStop -> Color(0xFFE53935).copy(alpha = 0.15f)
                                                    else -> Color.DarkGray
                                                }
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = when {
                                                parentCheckedIn && (loggedDelayVal ?: 0.0) > 0.0 -> "LATE"
                                                parentCheckedIn -> "ON TIME"
                                                isBusAtSelectedStop -> "BUS AT STOP"
                                                else -> "WAITING"
                                            },
                                            color = when {
                                                parentCheckedIn && (loggedDelayVal ?: 0.0) > 0.0 -> Color(0xFFEF5350)
                                                parentCheckedIn -> Color(0xFF00E676)
                                                isBusAtSelectedStop -> Color(0xFFEF5350)
                                                else -> Color.LightGray
                                            },
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 10.sp
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(10.dp))

                                Text(
                                    text = "Designated Stop: ${selectedStop?.stopName ?: "Not Selected"}",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )

                                Text(
                                    text = "Tip: Tap any stop card in the timeline below to change your stop.",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp)
                                )

                                Spacer(modifier = Modifier.height(12.dp))

                                if (!parentCheckedIn) {
                                    if (isBusAtSelectedStop) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text(
                                                text = "Bus is at your stop! Calculating delay...",
                                                color = Color.LightGray,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "PARENT DELAY: ${String.format("%.1f", parentDelayMinutes)} mins",
                                                color = Color(0xFFE53935),
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 18.sp,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                            Button(
                                                onClick = {
                                                    val finalDelay = parentDelayMinutes
                                                    if (finalDelay > 0.1) {
                                                        loggedDelayVal = finalDelay
                                                        viewModel.logParentDelay(activeTrip.id, selectedStop?.stopName ?: "Stop", finalDelay)
                                                    } else {
                                                        loggedDelayVal = 0.0
                                                    }
                                                    parentCheckedIn = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                                                modifier = Modifier.fillMaxWidth().testTag("parent_check_in_btn")
                                            ) {
                                                Text("I've Arrived • Check In", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    } else if (hasArrivedPassed) {
                                        Column(modifier = Modifier.fillMaxWidth()) {
                                            Text(
                                                text = "The bus has already passed your stop and you were not checked in.",
                                                color = Color.Gray,
                                                fontSize = 12.sp
                                            )
                                            Spacer(modifier = Modifier.height(8.dp))
                                            Button(
                                                onClick = {
                                                    val finalDelay = 5.0
                                                    loggedDelayVal = finalDelay
                                                    viewModel.logParentDelay(activeTrip.id, selectedStop?.stopName ?: "Stop", finalDelay)
                                                    parentCheckedIn = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC62828)),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text("Record Delayed Arrival (Missed Bus)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                loggedDelayVal = 0.0
                                                parentCheckedIn = true
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E7D32)),
                                            modifier = Modifier.fillMaxWidth().testTag("parent_check_in_btn")
                                        ) {
                                            Text("I am at the Stop (Pre-Check)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        }
                                    }
                                } else {
                                    val delayVal = loggedDelayVal ?: 0.0
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Icon(
                                            imageVector = if (delayVal > 0.0) Icons.Default.Warning else Icons.Default.CheckCircle,
                                            contentDescription = "Status",
                                            tint = if (delayVal > 0.0) Color(0xFFEF5350) else Color(0xFF00E676),
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                text = if (delayVal > 0.0) "Logged Delayed Arrival" else "Checked In Safely",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 14.sp
                                            )
                                            Text(
                                                text = if (delayVal > 0.0) "Parent tardiness logged: Delayed by ${String.format("%.1f", delayVal)} mins" else "You checked in on time before the bus left.",
                                                color = Color.LightGray,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Deviation / Speeding warnings
                    val isSpeeding = activeTrip.currentSpeed > (currentRoute?.speedLimit ?: 40.0)
                    if (isSpeeding || activeTrip.isDeviated) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1B1B)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f)),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(imageVector = Icons.Default.Warning, contentDescription = "Alarm", tint = Color(0xFFE53935))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text(
                                            text = if (activeTrip.isDeviated) "ROUTE DEVIATION ALARM" else "OVERSPEEDING WARNING",
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Text(
                                            text = if (activeTrip.isDeviated) "The bus is currently off its designated route path." else "The bus speed is above the school-sanctioned safety threshold.",
                                            color = Color.LightGray,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Scheduled Route and Stops Timeline
                if (currentRoute != null && stops.isNotEmpty()) {
                    item {
                        Text(
                            text = "Route: ${currentRoute.routeName}",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    itemsIndexed(stops) { index, stop ->
                        val isPassed = activeTrip != null && index < activeTrip.currentStopIndex
                        val isCurrent = activeTrip != null && index == activeTrip.currentStopIndex
                        val etaText = if (activeTrip != null) {
                            viewModel.calculateETAs(activeTrip, stops, activeTrip.currentSpeed)[index] ?: "Calculating..."
                        } else {
                            "+${stop.expectedArrivalMinutes} mins"
                        }

                        // See if parent has a lateness log at this stop
                        val hasLatenessLog = activeTrip?.let { trip ->
                            violations.find {
                                it.tripId == trip.id &&
                                        it.type == "PARENT_LATE" &&
                                        it.details.contains(stop.stopName)
                            }
                        }

                        val isMyStop = selectedStop?.id == stop.id
                        Card(
                            onClick = { parentStopId = stop.id },
                            colors = CardDefaults.cardColors(
                                containerColor = when {
                                    isMyStop -> Color(0xFF2C1C0D) // Subtle Amber highlight
                                    isCurrent -> Color(0xFF16252E)
                                    else -> Color(0xFF1A1A1A)
                                }
                            ),
                            border = if (isMyStop) androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFF9800).copy(alpha = 0.5f)) else null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth().testTag("stop_item_${stop.id}")
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Icon(
                                            imageVector = when {
                                                isPassed -> Icons.Default.CheckCircle
                                                isCurrent -> Icons.Default.RadioButtonChecked
                                                else -> Icons.Default.RadioButtonUnchecked
                                            },
                                            contentDescription = "Status",
                                            tint = when {
                                                isPassed -> Color(0xFF4CAF50)
                                                isCurrent -> Color(0xFFFF9800)
                                                else -> Color.Gray
                                            },
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text(
                                            text = stop.stopName,
                                            color = if (isCurrent) Color.White else Color.LightGray,
                                            fontWeight = if (isCurrent || isMyStop) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 14.sp,
                                            maxLines = 1
                                        )
                                        if (isMyStop) {
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Box(
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(4.dp))
                                                    .background(Color(0xFFFF9800).copy(alpha = 0.15f))
                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                            ) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Icon(
                                                        imageVector = Icons.Default.Home,
                                                        contentDescription = "My Stop",
                                                        tint = Color(0xFFFF9800),
                                                        modifier = Modifier.size(10.dp)
                                                    )
                                                    Spacer(modifier = Modifier.width(4.dp))
                                                    Text(
                                                        text = "My Stop",
                                                        color = Color(0xFFFF9800),
                                                        fontWeight = FontWeight.Bold,
                                                        fontSize = 9.sp
                                                    )
                                                }
                                            }
                                        }
                                    }
 
                                    Text(
                                        text = if (isPassed) "Passed" else if (isCurrent) "Bus Arrived" else "ETA: $etaText",
                                        color = if (isCurrent) Color(0xFFFF9800) else Color.Gray,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                }

                                if (hasLatenessLog != null) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(Color(0xFF2C1B1B))
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Schedule,
                                            contentDescription = "Late",
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(
                                            text = "Parent tardiness logged: Delayed by ${String.format("%.1f", hasLatenessLog.value)} mins",
                                            color = Color(0xFFEF9A9A),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
