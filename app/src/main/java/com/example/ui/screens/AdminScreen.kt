package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.ui.components.SimulatedMap
import com.example.viewmodel.TrackerViewModel
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    viewModel: TrackerViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Live Fleet", "Violations & Lateness", "Buses", "Drivers", "Routes", "Reports")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212)) // Dark mode background
    ) {
        // Tab row with safety-colored accents
        ScrollableTabRow(
            selectedTabIndex = selectedTab,
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color(0xFFFF9800), // Safety Orange Accent
            edgePadding = 16.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = { Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
                .padding(horizontal = 16.dp)
        ) {
            when (selectedTab) {
                0 -> LiveFleetTab(viewModel)
                1 -> ViolationsTab(viewModel)
                2 -> BusesTab(viewModel)
                3 -> DriversTab(viewModel)
                4 -> RoutesTab(viewModel)
                5 -> ReportsScreen(viewModel)
            }
        }
    }
}

@Composable
fun LiveFleetTab(viewModel: TrackerViewModel) {
    val activeTrips by viewModel.activeTrips.collectAsState()
    val buses by viewModel.allBuses.collectAsState()
    val drivers by viewModel.allDrivers.collectAsState()
    val routes by viewModel.allRoutes.collectAsState()

    if (activeTrips.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.DirectionsBus,
                    contentDescription = "No Bus Running",
                    tint = Color.Gray,
                    modifier = Modifier.size(72.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "No Active Trips at the Moment",
                    color = Color.LightGray,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Switch to 'Driver' role, login (e.g. yusuf/123), and start a trip to broadcast live tracking data.",
                    color = Color.Gray,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(activeTrips) { trip ->
                val bus = buses.find { it.id == trip.busId }
                val driver = drivers.find { it.id == trip.driverId }
                val route = routes.find { it.id == trip.routeId }
                val stopsFlow = remember(trip.routeId) { viewModel.getStopsForRouteFlow(trip.routeId) }
                val stops by stopsFlow.collectAsState(initial = emptyList())

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("fleet_item_${trip.id}")
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        // Title / Header
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = route?.routeName ?: "School Bus Route",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = "Driver: ${driver?.name ?: "Unknown"} • Bus: ${bus?.plateNumber ?: "Unknown"}",
                                    color = Color.Gray,
                                    fontSize = 13.sp
                                )
                            }

                            val isViolating = trip.currentSpeed > (route?.speedLimit ?: 40.0) || trip.isDeviated || trip.isOvertaking
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isViolating) Color(0xFFC62828) else Color(0xFF2E7D32))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (isViolating) "VIOLATION ALERT" else "TRACKING LIVE",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        // Render the Custom Canvas Map here!
                        SimulatedMap(trip = trip, stops = stops)

                        Spacer(modifier = Modifier.height(12.dp))

                        // Stats
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Current Speed", color = Color.Gray, fontSize = 11.sp)
                                Text(
                                    text = "${trip.currentSpeed.toInt()} km/h",
                                    color = if (trip.currentSpeed > (route?.speedLimit ?: 40.0)) Color(0xFFE53935) else Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Next Stop", color = Color.Gray, fontSize = 11.sp)
                                val stopsCount = stops.size
                                val nextStopName = stops.getOrNull(trip.currentStopIndex + 1)?.stopName ?: "Terminal"
                                Text(
                                    text = nextStopName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                Text("Distance to Next", color = Color.Gray, fontSize = 11.sp)
                                Text(
                                    text = viewModel.getDistanceToNextStop(trip, stops),
                                    color = Color(0xFFFF9800),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ViolationsTab(viewModel: TrackerViewModel) {
    val violations by viewModel.allViolations.collectAsState()
    val sdf = remember { SimpleDateFormat("hh:mm a (dd MMM)", Locale.getDefault()) }

    val speedCount = violations.count { it.type == "SPEED" }
    val devCount = violations.count { it.type == "ROUTE_DEVIATION" }
    val overtakeCount = violations.count { it.type == "OVERTAKING" }
    val parentLateLogs = violations.filter { it.type == "PARENT_LATE" }
    val parentLateCount = parentLateLogs.size
    val avgParentDelay = if (parentLateLogs.isEmpty()) 0.0 else parentLateLogs.map { it.value }.average()

    val mostDelayedStop = remember(parentLateLogs) {
        if (parentLateLogs.isEmpty()) "N/A"
        else {
            val stopCounts = parentLateLogs.map { log ->
                val capMatch = log.details.substringAfter("captured at ").substringBefore(":")
                val logMatch = log.details.substringAfter("logged at ").substringBefore(":")
                if (capMatch.length < log.details.length) capMatch
                else if (logMatch.length < log.details.length) logMatch
                else "Bus Stop"
            }.groupBy { it }
            stopCounts.maxByOrNull { it.value.size }?.key ?: "N/A"
        }
    }

    if (violations.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.Assessment,
                    contentDescription = "No Reports",
                    tint = Color.Gray,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("No violations or tardiness logs recorded yet.", color = Color.LightGray, fontSize = 15.sp)
            }
        }
    } else {
        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                Text(
                    text = "Transit Audit & Compliance Report",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                Text(
                    text = "A consolidated real-time summary of driver deviations, speed compliance levels, and parent arrival delays.",
                    color = Color.Gray,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            // Consolidated Summary Cards
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("TRAFFIC & SAFETY ALERTS", color = Color(0xFFFF9800), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "${speedCount + devCount + overtakeCount}",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 24.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Speeding: $speedCount\nRoute Devs: $devCount\nOvertakes: $overtakeCount",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF162326)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1.1f)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("PARENT DELAY METRICS", color = Color(0xFF00ACC1), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "$parentLateCount Instances",
                                color = Color.White,
                                fontWeight = FontWeight.Black,
                                fontSize = 20.sp
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Avg Delay: ${String.format("%.1f", avgParentDelay)} mins\nMost Delayed Stop:\n$mostDelayedStop",
                                color = Color.LightGray,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }
            }

            item {
                Divider(color = Color.DarkGray, thickness = 1.dp, modifier = Modifier.padding(vertical = 12.dp))
                Text(
                    text = "Raw Fleet Incident Log",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            items(violations) { record ->
                    val color = when (record.type) {
                        "SPEED" -> Color(0xFFE53935)
                        "ROUTE_DEVIATION" -> Color(0xFFD81B60)
                        "OVERTAKING" -> Color(0xFFFF8F00)
                        "PARENT_LATE" -> Color(0xFF00ACC1)
                        else -> Color.Gray
                    }

                    val icon = when (record.type) {
                        "SPEED" -> Icons.Default.Speed
                        "ROUTE_DEVIATION" -> Icons.Default.Navigation
                        "OVERTAKING" -> Icons.Default.Warning
                        "PARENT_LATE" -> Icons.Default.Schedule
                        else -> Icons.Default.Info
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(color.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(imageVector = icon, contentDescription = record.type, tint = color)
                            }

                            Spacer(modifier = Modifier.width(14.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = when (record.type) {
                                            "SPEED" -> "Speed Limit Violation"
                                            "ROUTE_DEVIATION" -> "Route Discipline Alert"
                                            "OVERTAKING" -> "Dangerous Overtaking"
                                            "PARENT_LATE" -> "Parent Lateness Log"
                                            else -> "Alert Logged"
                                        },
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 14.sp
                                    )
                                    Text(
                                        text = sdf.format(Date(record.timestamp)),
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                }

                                Text(
                                    text = "Driver: ${record.driverName} (${record.busPlate}) • Route: ${record.routeName}",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.padding(vertical = 2.dp)
                                )

                                Text(
                                    text = record.details,
                                    color = Color.Gray,
                                    fontSize = 12.sp,
                                    lineHeight = 16.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }

@Composable
fun BusesTab(viewModel: TrackerViewModel) {
    val buses by viewModel.allBuses.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    var plateNumber by remember { mutableStateOf("") }
    var capacity by remember { mutableStateOf("") }
    var model by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Registered Fleet", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.dp.value.sp)
            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                modifier = Modifier.testTag("add_bus_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Bus")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Bus", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(buses) { bus ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.DirectionsBus, contentDescription = "Bus", tint = Color(0xFFFF9800), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(bus.plateNumber, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                                Text("${bus.model} • ${bus.capacity} Seats", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                        IconButton(onClick = { viewModel.deleteBus(bus) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF222222),
            title = { Text("Add New Bus", color = Color.White) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextField(
                        value = plateNumber,
                        onValueChange = { plateNumber = it },
                        label = { Text("Plate Number (e.g. JK01-5544)") },
                        modifier = Modifier.fillMaxWidth().testTag("plate_input")
                    )
                    TextField(
                        value = model,
                        onValueChange = { model = it },
                        label = { Text("Bus Model / Make") },
                        modifier = Modifier.fillMaxWidth().testTag("model_input")
                    )
                    TextField(
                        value = capacity,
                        onValueChange = { capacity = it },
                        label = { Text("Seating Capacity") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.fillMaxWidth().testTag("capacity_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    onClick = {
                        val cap = capacity.toIntOrNull() ?: 30
                        if (plateNumber.isNotBlank() && model.isNotBlank()) {
                            viewModel.createBus(Bus(plateNumber = plateNumber, capacity = cap, model = model))
                            plateNumber = ""
                            model = ""
                            capacity = ""
                            showDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_bus_button")
                ) {
                    Text("Save", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun DriversTab(viewModel: TrackerViewModel) {
    val drivers by viewModel.allDrivers.collectAsState()
    val buses by viewModel.allBuses.collectAsState()
    val routes by viewModel.allRoutes.collectAsState()

    var showDialog by remember { mutableStateOf(false) }

    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var license by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var selectedBusId by remember { mutableStateOf<Int?>(null) }
    var selectedRouteId by remember { mutableStateOf<Int?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Registered Drivers", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                modifier = Modifier.testTag("add_driver_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Driver")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Driver", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(drivers) { driver ->
                val busMapped = buses.find { it.id == driver.mappedBusId }
                val routeMapped = routes.find { it.id == driver.mappedRouteId }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Person, contentDescription = "Driver", tint = Color(0xFFFF9800), modifier = Modifier.size(36.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(driver.name, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 16.sp)
                                Text("License: ${driver.licenseNumber} • Phone: ${driver.phoneNumber}", color = Color.LightGray, fontSize = 12.sp)
                                Text("Login: Username '${driver.username}' / Pass '${driver.password}'", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

                                if (busMapped != null || routeMapped != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Bus: ${busMapped?.plateNumber ?: "Unassigned"} • Route: ${routeMapped?.routeName ?: "Unassigned"}",
                                        color = Color(0xFFFF9800),
                                        fontWeight = FontWeight.Medium,
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.deleteDriver(driver) }) {
                            Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    }
                }
            }
        }
    }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF222222),
            title = { Text("Add New Driver Account", color = Color.White) },
            text = {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.verticalScroll(rememberScrollState()) // Enable scroll for dialog input
                ) {
                    TextField(value = name, onValueChange = { name = it }, label = { Text("Driver Full Name") }, modifier = Modifier.fillMaxWidth().testTag("driver_name_input"))
                    TextField(value = phone, onValueChange = { phone = it }, label = { Text("Phone Number") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), modifier = Modifier.fillMaxWidth().testTag("driver_phone_input"))
                    TextField(value = license, onValueChange = { license = it }, label = { Text("License Number") }, modifier = Modifier.fillMaxWidth().testTag("driver_license_input"))
                    TextField(value = username, onValueChange = { username = it }, label = { Text("Create Login Username") }, modifier = Modifier.fillMaxWidth().testTag("driver_username_input"))
                    TextField(value = password, onValueChange = { password = it }, label = { Text("Create Login Password") }, modifier = Modifier.fillMaxWidth().testTag("driver_password_input"))

                    // Simple Bus Mapping list selection
                    Text("Assign Bus:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    LazyColumn(modifier = Modifier.height(80.dp).fillMaxWidth().background(Color(0xFF1E1E1E)).padding(4.dp)) {
                        items(buses) { b ->
                            TextButton(
                                onClick = { selectedBusId = b.id },
                                colors = ButtonDefaults.textButtonColors(contentColor = if (selectedBusId == b.id) Color(0xFFFF9800) else Color.Gray)
                            ) {
                                Text("${if (selectedBusId == b.id) "✓ " else ""}${b.plateNumber} (${b.model})")
                            }
                        }
                    }

                    // Simple Route Mapping selection
                    Text("Assign Route:", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
                    LazyColumn(modifier = Modifier.height(80.dp).fillMaxWidth().background(Color(0xFF1E1E1E)).padding(4.dp)) {
                        items(routes) { r ->
                            TextButton(
                                onClick = { selectedRouteId = r.id },
                                colors = ButtonDefaults.textButtonColors(contentColor = if (selectedRouteId == r.id) Color(0xFFFF9800) else Color.Gray)
                            ) {
                                Text("${if (selectedRouteId == r.id) "✓ " else ""}${r.routeName}")
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    onClick = {
                        if (name.isNotBlank() && username.isNotBlank() && password.isNotBlank()) {
                            viewModel.createDriver(
                                Driver(
                                    name = name,
                                    phoneNumber = phone,
                                    licenseNumber = license,
                                    username = username,
                                    password = password,
                                    mappedBusId = selectedBusId,
                                    mappedRouteId = selectedRouteId
                                )
                            )
                            name = ""
                            phone = ""
                            license = ""
                            username = ""
                            password = ""
                            selectedBusId = null
                            selectedRouteId = null
                            showDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_driver_button")
                ) {
                    Text("Save Driver", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}

data class PendingStop(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val latitude: String,
    val longitude: String
)

@Composable
fun RoutesTab(viewModel: TrackerViewModel) {
    val routes by viewModel.allRoutes.collectAsState()
    var showDialog by remember { mutableStateOf(false) }

    var routeName by remember { mutableStateOf("") }
    var speedLimit by remember { mutableStateOf("40") }

    var pendingStops by remember {
        mutableStateOf(
            listOf(
                PendingStop(name = "Start Terminal", latitude = "34.0722", longitude = "74.8115"),
                PendingStop(name = "Middle Junction", latitude = "34.0900", longitude = "74.8300"),
                PendingStop(name = "Lake View Way", latitude = "34.1200", longitude = "74.8500"),
                PendingStop(name = "End School", latitude = "34.1450", longitude = "74.8600")
            )
        )
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Configured Bus Routes", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Button(
                onClick = { showDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                modifier = Modifier.testTag("add_route_button")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Add Route")
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add Route", fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            items(routes) { route ->
                val stopsFlow = remember(route.id) { viewModel.getStopsForRouteFlow(route.id) }
                val stops by stopsFlow.collectAsState(initial = emptyList())

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(imageVector = Icons.Default.Route, contentDescription = "Route", tint = Color(0xFFFF9800), modifier = Modifier.size(36.dp))
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(route.routeName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                                    Text("Speed Limit: ${route.speedLimit.toInt()} km/h • ${stops.size} Stops Configured", color = Color.LightGray, fontSize = 13.sp)
                                }
                            }
                            IconButton(onClick = { viewModel.deleteRoute(route) }) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                            }
                        }

                        if (stops.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Text("Route Stops Sequence:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            // Small list of sequence
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                             ) {
                                stops.forEachIndexed { index, stop ->
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF2C2C2C))
                                            .padding(horizontal = 8.dp, vertical = 6.dp)
                                    ) {
                                        Text(
                                            text = "${index + 1}. ${stop.stopName}",
                                            color = Color.White,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
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

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            containerColor = Color(0xFF222222),
            title = { Text("Add Route & Stops", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2D2D2D)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("Route Configuration", color = Color(0xFFFF9800), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            TextField(
                                value = routeName,
                                onValueChange = { routeName = it },
                                label = { Text("Route Name (e.g. Shalimar to Hazratbal)") },
                                modifier = Modifier.fillMaxWidth().testTag("route_name_input")
                            )
                            TextField(
                                value = speedLimit,
                                onValueChange = { speedLimit = it },
                                label = { Text("Speed Limit (km/h)") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth().testTag("speed_limit_input")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Route Stops Sequence (${pendingStops.size})", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        TextButton(
                            onClick = {
                                val lastStop = pendingStops.lastOrNull()
                                val nextLat = lastStop?.latitude?.toDoubleOrNull()?.let { it + 0.012 } ?: 34.0722
                                val nextLng = lastStop?.longitude?.toDoubleOrNull()?.let { it + 0.012 } ?: 74.8115
                                val nextNum = pendingStops.size + 1
                                pendingStops = pendingStops + PendingStop(
                                    name = "Stop $nextNum",
                                    latitude = (Math.round(nextLat * 10000.0) / 10000.0).toString(),
                                    longitude = (Math.round(nextLng * 10000.0) / 10000.0).toString()
                                )
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFFF9800))
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Add Stop", fontWeight = FontWeight.Bold)
                        }
                    }

                    pendingStops.forEachIndexed { index, stop ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF262626)),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Stop #${index + 1}",
                                        color = Color.LightGray,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    if (pendingStops.size > 2) {
                                        IconButton(
                                            onClick = { pendingStops = pendingStops.filter { it.id != stop.id } },
                                            modifier = Modifier.size(24.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Remove Stop",
                                                tint = Color.Red,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                }

                                TextField(
                                    value = stop.name,
                                    onValueChange = { newName ->
                                        pendingStops = pendingStops.map {
                                            if (it.id == stop.id) it.copy(name = newName) else it
                                        }
                                    },
                                    label = { Text("Stop Name") },
                                    modifier = Modifier.fillMaxWidth().height(56.dp)
                                )

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    TextField(
                                        value = stop.latitude,
                                        onValueChange = { newLat ->
                                            pendingStops = pendingStops.map {
                                                if (it.id == stop.id) it.copy(latitude = newLat) else it
                                            }
                                        },
                                        label = { Text("Latitude") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f).height(56.dp)
                                    )
                                    TextField(
                                        value = stop.longitude,
                                        onValueChange = { newLng ->
                                            pendingStops = pendingStops.map {
                                                if (it.id == stop.id) it.copy(longitude = newLng) else it
                                            }
                                        },
                                        label = { Text("Longitude") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        modifier = Modifier.weight(1f).height(56.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFF9800)),
                    onClick = {
                        val limit = speedLimit.toDoubleOrNull() ?: 40.0
                        if (routeName.isNotBlank() && pendingStops.isNotEmpty()) {
                            val stopsToSave = pendingStops.map {
                                val lat = it.latitude.toFloatOrNull() ?: 34.0837f
                                val lng = it.longitude.toFloatOrNull() ?: 74.7973f
                                it.name to (lat to lng)
                            }
                            viewModel.createRouteWithStops(routeName, limit, stopsToSave)
                            
                            routeName = ""
                            speedLimit = "40"
                            pendingStops = listOf(
                                PendingStop(name = "Start Terminal", latitude = "34.0722", longitude = "74.8115"),
                                PendingStop(name = "Middle Junction", latitude = "34.0900", longitude = "74.8300"),
                                PendingStop(name = "Lake View Way", latitude = "34.1200", longitude = "74.8500"),
                                PendingStop(name = "End School", latitude = "34.1450", longitude = "74.8600")
                            )
                            showDialog = false
                        }
                    },
                    modifier = Modifier.testTag("save_route_button")
                ) {
                    Text("Save Route & Stops", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }
}


