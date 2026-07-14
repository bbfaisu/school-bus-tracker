package com.example.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import com.google.android.gms.location.*
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.*

class TrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BusTrackerRepository
    private var fusedLocationClient: FusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(application)
    private var locationCallback: LocationCallback? = null

    private val _supabaseConnected = MutableStateFlow(false)
    val supabaseConnected: StateFlow<Boolean> = _supabaseConnected.asStateFlow()

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BusTrackerRepository(database)
        viewModelScope.launch {
            // 1. Initial connection & schema setup on background threads
            val connected = SupabaseDbManager.testConnection()
            _supabaseConnected.value = connected
            if (connected) {
                SupabaseDbManager.initializeSchemaAndPrepopulate()
                repository.syncWithSupabase()
            }

            // 2. Local check & prepopulate fallback + active trip resumption
            repository.checkAndPrepopulate()
            checkForActiveTripAndResume()

            // 3. Periodic Background Sync Loop (runs every 3 seconds to pull live updates from remote)
            launch {
                while (true) {
                    delay(3000)
                    val connStatus = SupabaseDbManager.testConnection()
                    _supabaseConnected.value = connStatus
                    if (connStatus) {
                        repository.syncWithSupabase()
                    }
                }
            }

            // 4. WebSocket live stream listener (sub-second telemetry updates from cloud)
            launch {
                WebSocketManager.incomingTelemetry.collect { telemetryStr ->
                    if (telemetryStr != null) {
                        try {
                            val json = org.json.JSONObject(telemetryStr)
                            val tId = json.getInt("tripId")
                            val lat = json.getDouble("latitude").toFloat()
                            val lng = json.getDouble("longitude").toFloat()
                            val speed = json.getDouble("speed")
                            val isDeviated = json.getBoolean("isDeviated")
                            
                            val trip = repository.getTripByIdSync(tId)
                            if (trip != null) {
                                val currIndex = trip.currentStopIndex
                                val stops = _routeStops.value
                                
                                var arrivedIndex = currIndex
                                
                                if (stops.isNotEmpty() && currIndex < stops.size - 1) {
                                    val nextStop = stops[currIndex + 1]
                                    val dist = calculateDistance(lat.toDouble(), lng.toDouble(), nextStop.latitude.toDouble(), nextStop.longitude.toDouble())
                                    if (dist < 0.05) { // within 50 meters, we consider it arrived!
                                        arrivedIndex = currIndex + 1
                                        _waitingAtStop.value = true
                                        simulationProgress = 0
                                    }
                                }
                                
                                repository.updateTripPosition(
                                    tripId = tId,
                                    lat = lat,
                                    lng = lng,
                                    speed = speed,
                                    stopIndex = arrivedIndex,
                                    isDeviated = isDeviated,
                                    isOvertaking = trip.isOvertaking
                                )
                                
                                _currentTrip.value = repository.getTripByIdSync(tId)
                            }
                        } catch (e: Exception) {
                            Log.e("TrackerViewModel", "WebSocket live tracking frame parse error: ${e.message}")
                        }
                    }
                }
            }
        }
    }

    private fun checkForActiveTripAndResume() {
        viewModelScope.launch {
            val activeTrip = repository.getAnyActiveTrip()
            if (activeTrip != null) {
                val driver = repository.getDriverById(activeTrip.driverId)
                if (driver != null) {
                    _loggedInDriver.value = driver
                    _currentRole.value = "Driver"
                    
                    val stops = repository.getStopsForRouteSync(activeTrip.routeId)
                    _routeStops.value = stops
                    _activeTripId.value = activeTrip.id
                    _currentTrip.value = activeTrip
                    
                    _simSpeedSlider.value = activeTrip.currentSpeed
                    _simIsDeviated.value = activeTrip.isDeviated
                    _isSimulationMode.value = activeTrip.isSimulation
                    if (activeTrip.isSimulation) {
                        WebSocketManager.disconnect()
                    } else {
                        WebSocketManager.connect()
                    }
                    _tripResumed.value = true
                    
                    if (activeTrip.isSimulation) {
                        startSimulation(activeTrip.id, stops)
                    } else {
                        startRealGpsTracking(activeTrip.id)
                    }
                }
            }
        }
    }

    // Role state
    private val _currentRole = MutableStateFlow("Admin") // "Admin", "Driver", "Parent", "RTO"
    val currentRole: StateFlow<String> = _currentRole.asStateFlow()

    fun switchRole(role: String) {
        _currentRole.value = role
    }

    // Driver login session
    private val _loggedInDriver = MutableStateFlow<Driver?>(null)
    val loggedInDriver: StateFlow<Driver?> = _loggedInDriver.asStateFlow()

    private val _loginError = MutableStateFlow<String?>(null)
    val loginError: StateFlow<String?> = _loginError.asStateFlow()

    fun loginDriver(username: String, password: String) {
        viewModelScope.launch {
            val driver = repository.getDriverByUsername(username)
            if (driver != null && driver.password == password) {
                _loggedInDriver.value = driver
                _loginError.value = null
                checkForActiveTripAndResume()
            } else {
                _loginError.value = "Invalid username or password"
            }
        }
    }

    fun logoutDriver() {
        _loggedInDriver.value = null
        stopSimulation()
    }

    fun updateDriverAssignment(busId: Int, routeId: Int) {
        val currentDriver = _loggedInDriver.value ?: return
        val updatedDriver = currentDriver.copy(mappedBusId = busId, mappedRouteId = routeId)
        viewModelScope.launch {
            repository.updateDriver(updatedDriver)
            _loggedInDriver.value = updatedDriver
        }
    }

    // DB flows
    val allBuses: StateFlow<List<Bus>> = repository.allBuses.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allDrivers: StateFlow<List<Driver>> = repository.allDrivers.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allRoutes: StateFlow<List<BusRoute>> = repository.allRoutes.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val activeTrips: StateFlow<List<Trip>> = repository.activeTrips.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allTrips: StateFlow<List<Trip>> = repository.allTrips.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val allViolations: StateFlow<List<ViolationRecord>> = repository.allViolations.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Admin Creation states
    fun createDriver(driver: Driver) = viewModelScope.launch { repository.insertDriver(driver) }
    fun updateDriver(driver: Driver) = viewModelScope.launch { repository.updateDriver(driver) }
    fun deleteDriver(driver: Driver) = viewModelScope.launch { repository.deleteDriver(driver) }

    fun createBus(bus: Bus) = viewModelScope.launch { repository.insertBus(bus) }
    fun deleteBus(bus: Bus) = viewModelScope.launch { repository.deleteBus(bus) }

    fun createRouteWithStops(routeName: String, speedLimit: Double, stops: List<Pair<String, Pair<Float, Float>>>) = viewModelScope.launch {
        val routeId = repository.insertRoute(routeName, speedLimit).toInt()
        stops.forEachIndexed { index, pair ->
            val stop = RouteStop(
                routeId = routeId,
                stopName = pair.first,
                latitude = pair.second.first,
                longitude = pair.second.second,
                sequenceOrder = index + 1,
                expectedArrivalMinutes = index * 7 // Estimate 7 min per stop
            )
            repository.insertStop(stop)
        }
    }

    fun deleteRoute(route: BusRoute) = viewModelScope.launch { repository.deleteRoute(route) }

    // Pre-trip checklist
    private val _checklist = MutableStateFlow(mapOf(
        "Brakes & Steering Checked" to false,
        "Lights & Indicators Working" to false,
        "Tyre Pressure OK" to false,
        "First Aid & Emergency Kit Present" to false,
        "Emergency Exit Clear" to false
    ))
    val checklist: StateFlow<Map<String, Boolean>> = _checklist.asStateFlow()

    fun toggleChecklistItem(item: String) {
        val current = _checklist.value.toMutableMap()
        current[item] = !(current[item] ?: false)
        _checklist.value = current
    }

    fun resetChecklist() {
        _checklist.value = _checklist.value.mapValues { false }
    }

    // Active Simulation states
    private var simulationJob: Job? = null
    private val _activeTripId = MutableStateFlow<Int?>(null)
    val activeTripId: StateFlow<Int?> = _activeTripId.asStateFlow()

    private val _tripResumed = MutableStateFlow(false)
    val tripResumed: StateFlow<Boolean> = _tripResumed.asStateFlow()

    private val _currentTrip = MutableStateFlow<Trip?>(null)
    val currentTrip: StateFlow<Trip?> = _currentTrip.asStateFlow()

    private val _routeStops = MutableStateFlow<List<RouteStop>>(emptyList())
    val routeStops: StateFlow<List<RouteStop>> = _routeStops.asStateFlow()

    // Interactive Driver Controls during simulation
    private val _simSpeedSlider = MutableStateFlow(30.0) // km/h
    val simSpeedSlider: StateFlow<Double> = _simSpeedSlider.asStateFlow()

    private val _simIsDeviated = MutableStateFlow(false)
    val simIsDeviated: StateFlow<Boolean> = _simIsDeviated.asStateFlow()

    private val _waitingAtStop = MutableStateFlow(false)
    val waitingAtStop: StateFlow<Boolean> = _waitingAtStop.asStateFlow()

    private val _parentLatenessTimerActive = MutableStateFlow(false)
    val parentLatenessTimerActive: StateFlow<Boolean> = _parentLatenessTimerActive.asStateFlow()

    private val _parentLatenessSeconds = MutableStateFlow(0)
    val parentLatenessSeconds: StateFlow<Int> = _parentLatenessSeconds.asStateFlow()

    private val _isSimulationMode = MutableStateFlow(true)
    val isSimulationMode: StateFlow<Boolean> = _isSimulationMode.asStateFlow()

    private val _preTripDriverLocation = MutableStateFlow<LatLng?>(null)
    val preTripDriverLocation: StateFlow<LatLng?> = _preTripDriverLocation.asStateFlow()

    private val _tripStartError = MutableStateFlow<String?>(null)
    val tripStartError: StateFlow<String?> = _tripStartError.asStateFlow()

    fun clearTripStartError() {
        _tripStartError.value = null
    }

    val distanceToFirstStopKm: StateFlow<Double?> = combine(_preTripDriverLocation, _routeStops) { loc, stops ->
        if (loc == null || stops.isEmpty()) {
            null
        } else {
            val firstStop = stops.first()
            calculateDistance(loc.latitude, loc.longitude, firstStop.latitude.toDouble(), firstStop.longitude.toDouble())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val isNearFirstStop: StateFlow<Boolean> = distanceToFirstStopKm.map { dist ->
        if (dist == null) true
        else dist <= 0.15
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    private var simulationProgress = 0 // 0 to 10 between stops
    private var lastOverspeedLogged = 0L
    private var lastDeviationLogged = 0L
    private var lastOvertakingLogged = 0L

    fun setSimulationMode(enabled: Boolean) {
        _isSimulationMode.value = enabled
        val activeId = _activeTripId.value
        if (activeId != null) {
            viewModelScope.launch {
                val stops = _routeStops.value
                if (enabled) {
                    stopRealGpsTracking()
                    WebSocketManager.disconnect()
                    startSimulation(activeId, stops)
                } else {
                    simulationJob?.cancel()
                    simulationJob = null
                    WebSocketManager.connect()
                    startRealGpsTracking(activeId)
                }
            }
        } else {
            if (enabled) {
                stopPreTripRealLocationTracking()
                WebSocketManager.disconnect()
                val stops = _routeStops.value
                if (stops.isNotEmpty()) {
                    val firstStop = stops.first()
                    _preTripDriverLocation.value = LatLng(
                        firstStop.latitude.toDouble() + 0.012,
                        firstStop.longitude.toDouble() + 0.012
                    )
                }
            } else {
                WebSocketManager.connect()
                startPreTripRealLocationTracking()
            }
        }
    }

    fun initializePreTripLocation(routeId: Int) {
        viewModelScope.launch {
            val stops = repository.getStopsForRouteSync(routeId)
            _routeStops.value = stops
            if (stops.isNotEmpty()) {
                val firstStop = stops.first()
                if (_isSimulationMode.value) {
                    _preTripDriverLocation.value = LatLng(
                        firstStop.latitude.toDouble() + 0.012,
                        firstStop.longitude.toDouble() + 0.012
                    )
                } else {
                    startPreTripRealLocationTracking()
                }
            }
        }
    }

    fun simulateMoveToFirstStop() {
        val stops = _routeStops.value
        if (stops.isNotEmpty()) {
            val firstStop = stops.first()
            _preTripDriverLocation.value = LatLng(
                firstStop.latitude.toDouble(),
                firstStop.longitude.toDouble()
            )
        }
    }

    private var preTripLocationCallback: LocationCallback? = null

    fun startPreTripRealLocationTracking() {
        stopPreTripRealLocationTracking()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 2000L).apply {
            setMinUpdateIntervalMillis(2000L)
        }.build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                if (!_isSimulationMode.value) {
                    _preTripDriverLocation.value = LatLng(location.latitude, location.longitude)
                }
            }
        }
        preTripLocationCallback = callback
        try {
            fusedLocationClient.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())
        } catch (e: SecurityException) {
            Log.e("TrackerViewModel", "Pre-trip Location Access Error: ${e.message}")
        }
    }

    fun stopPreTripRealLocationTracking() {
        preTripLocationCallback?.let {
            try {
                fusedLocationClient.removeLocationUpdates(it)
            } catch (e: Exception) {
                Log.e("TrackerViewModel", "Error removing pre-trip location updates: ${e.message}")
            }
            preTripLocationCallback = null
        }
    }

    override fun onCleared() {
        super.onCleared()
        stopRealGpsTracking()
        stopPreTripRealLocationTracking()
        WebSocketManager.disconnect()
    }

    fun setSimSpeed(speed: Double) {
        _simSpeedSlider.value = speed
    }

    fun setSimDeviated(deviated: Boolean) {
        _simIsDeviated.value = deviated
    }

    fun getStopsForRouteFlow(routeId: Int): Flow<List<RouteStop>> {
        return repository.getStopsForRoute(routeId)
    }

    fun startTrip(driverId: Int, busId: Int, routeId: Int) {
        viewModelScope.launch {
            val stops = repository.getStopsForRouteSync(routeId)
            if (stops.isEmpty()) return@launch
            _routeStops.value = stops

            // Distance verification check: Driver must be near the first stop (within 150m)
            val firstStop = stops.first()
            val driverLoc = _preTripDriverLocation.value
            if (driverLoc != null) {
                val dist = calculateDistance(
                    driverLoc.latitude,
                    driverLoc.longitude,
                    firstStop.latitude.toDouble(),
                    firstStop.longitude.toDouble()
                )
                if (dist > 0.15) { // more than 150 meters away
                    val stopName = firstStop.stopName
                    val formattedDist = String.format(java.util.Locale.US, "%.2f", dist)
                    _tripStartError.value = "Cannot Start Trip: You are $formattedDist km away from '$stopName' (First Stop). Please proceed to the first stop first to begin the trip."
                    return@launch
                }
            }

            // Successful start - clean up pre-trip tracking
            stopPreTripRealLocationTracking()
            _tripStartError.value = null

            val startLat = stops.first().latitude
            val startLng = stops.first().longitude

            val tripId = repository.startTrip(driverId, busId, routeId, startLat, startLng, _isSimulationMode.value).toInt()
            _activeTripId.value = tripId
            _tripResumed.value = false

            // Reset simulation control states
            _simSpeedSlider.value = 35.0
            _simIsDeviated.value = false
            _waitingAtStop.value = false
            _parentLatenessTimerActive.value = false
            _parentLatenessSeconds.value = 0
            simulationProgress = 0

            // If we are starting a real trip, open WebSocket
            if (!_isSimulationMode.value) {
                WebSocketManager.connect()
                startRealGpsTracking(tripId)
            } else {
                startSimulation(tripId, stops)
            }
        }
    }

    fun completeTrip() {
        val tripId = _activeTripId.value ?: return
        viewModelScope.launch {
            repository.completeTrip(tripId)
            stopSimulation()
        }
    }

    private fun startSimulation(tripId: Int, stops: List<RouteStop>) {
        simulationJob?.cancel()
        simulationJob = viewModelScope.launch {
            var lastTickSpeed = _simSpeedSlider.value
            while (true) {
                delay(1000) // Simulation tick every second
                val trip = repository.getTripByIdSync(tripId) ?: break
                _currentTrip.value = trip

                if (_waitingAtStop.value) {
                    if (_parentLatenessTimerActive.value) {
                        _parentLatenessSeconds.value += 1
                    }
                    lastTickSpeed = 0.0 // Reset speed baseline at stop
                    continue
                }

                // If trip is ACTIVE and we are not waiting at a stop, move!
                val currIndex = trip.currentStopIndex
                if (currIndex >= stops.size - 1) {
                    // Reached the end of route! Waiting for manual completion or trigger complete automatically
                    continue
                }

                val currentStop = stops[currIndex]
                val nextStop = stops[currIndex + 1]

                simulationProgress += 1

                // Calculate next coordinates
                var nextLat: Float
                var nextLng: Float

                if (_simIsDeviated.value) {
                    // Simulate route deviation: pull coordinates off course
                    val offsetLat = 0.008f * sin(simulationProgress.toFloat() / 2f)
                    val offsetLng = 0.008f * cos(simulationProgress.toFloat() / 2f)
                    val lerpLat = currentStop.latitude + (nextStop.latitude - currentStop.latitude) * (simulationProgress.toFloat() / 10f)
                    val lerpLng = currentStop.longitude + (nextStop.longitude - currentStop.longitude) * (simulationProgress.toFloat() / 10f)
                    nextLat = lerpLat + offsetLat
                    nextLng = lerpLng + offsetLng

                    // Log deviation violation once every 10 seconds of continuous deviation
                    val now = System.currentTimeMillis()
                    if (now - lastDeviationLogged > 10000L) {
                        lastDeviationLogged = now
                        logSimulationViolation(tripId, "ROUTE_DEVIATION", "Route deviation alert: Bus has left the specified path between ${currentStop.stopName} and ${nextStop.stopName}", 1.0)
                    }
                } else {
                    // Smooth linear interpolation along the route
                    nextLat = currentStop.latitude + (nextStop.latitude - currentStop.latitude) * (simulationProgress.toFloat() / 10f)
                    nextLng = currentStop.longitude + (nextStop.longitude - currentStop.longitude) * (simulationProgress.toFloat() / 10f)
                }

                // Check speed limits & log speeding
                val currentSpeed = _simSpeedSlider.value
                val route = allRoutes.value.find { it.id == trip.routeId }
                val speedLimit = route?.speedLimit ?: 40.0
                if (currentSpeed > speedLimit) {
                    val now = System.currentTimeMillis()
                    if (now - lastOverspeedLogged > 10000L) { // Log speeding once every 10 seconds
                        lastOverspeedLogged = now
                        logSimulationViolation(tripId, "SPEED", "Speed limit violation: Bus traveling at ${currentSpeed.toInt()} km/h (Limit: ${speedLimit.toInt()} km/h)", currentSpeed)
                    }
                }

                // Catch sudden speed increase (acceleration) / overtaking
                val speedDelta = currentSpeed - lastTickSpeed
                var isOvertakingCurrentTick = trip.isOvertaking

                if (speedDelta >= 12.0 && lastTickSpeed > 5.0) { // Sudden acceleration of 12+ km/h in 1 sec while moving
                    val now = System.currentTimeMillis()
                    if (now - lastOvertakingLogged > 10000L) {
                        lastOvertakingLogged = now
                        isOvertakingCurrentTick = true
                        
                        viewModelScope.launch {
                            val stopName = currentStop.stopName
                            logSimulationViolation(
                                tripId = tripId,
                                type = "OVERTAKING",
                                details = "Dangerous sudden speed increase & overtaking caught! Speed surged from ${lastTickSpeed.toInt()} to ${currentSpeed.toInt()} km/h (+${speedDelta.toInt()} km/h/s) near $stopName",
                                value = currentSpeed
                            )

                            // Clear overtaking flag after 4 seconds
                            delay(4000)
                            val cleanTrip = repository.getTripByIdSync(tripId)
                            if (cleanTrip != null && cleanTrip.status == "ACTIVE") {
                                repository.updateTripPosition(
                                    tripId = tripId,
                                    lat = cleanTrip.currentLatitude,
                                    lng = cleanTrip.currentLongitude,
                                    speed = _simSpeedSlider.value,
                                    stopIndex = cleanTrip.currentStopIndex,
                                    isDeviated = cleanTrip.isDeviated,
                                    isOvertaking = false
                                )
                            }
                        }
                    }
                }
                lastTickSpeed = currentSpeed

                if (_isSimulationMode.value) {
                    // --- SIMULATION MODE: Update locally immediately ---
                    repository.updateTripPosition(
                        tripId = tripId,
                        lat = nextLat,
                        lng = nextLng,
                        speed = currentSpeed,
                        stopIndex = currIndex,
                        isDeviated = _simIsDeviated.value,
                        isOvertaking = isOvertakingCurrentTick
                    )

                    // If progress reaches 10, we arrived at the next stop!
                    if (simulationProgress >= 10) {
                        // Arrived at next stop!
                        val arrivedIndex = currIndex + 1
                        repository.updateTripPosition(
                            tripId = tripId,
                            lat = nextStop.latitude,
                            lng = nextStop.longitude,
                            speed = 0.0,
                            stopIndex = arrivedIndex,
                            isDeviated = _simIsDeviated.value,
                            isOvertaking = false
                        )
                        simulationProgress = 0
                        _waitingAtStop.value = true
                    }
                } else {
                    // --- REAL MODE: Stream over OkHttp WebSocket ---
                    if (simulationProgress >= 10) {
                        WebSocketManager.sendTelemetry(
                            tripId = tripId,
                            lat = nextStop.latitude.toDouble(),
                            lng = nextStop.longitude.toDouble(),
                            speed = 0.0,
                            isDeviated = _simIsDeviated.value
                        )
                    } else {
                        WebSocketManager.sendTelemetry(
                            tripId = tripId,
                            lat = nextLat.toDouble(),
                            lng = nextLng.toDouble(),
                            speed = currentSpeed,
                            isDeviated = _simIsDeviated.value
                        )
                    }
                }
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        stopRealGpsTracking()
        _activeTripId.value = null
        _currentTrip.value = null
        _routeStops.value = emptyList()
        _waitingAtStop.value = false
        _parentLatenessTimerActive.value = false
        _parentLatenessSeconds.value = 0
        simulationProgress = 0
        _tripResumed.value = false
        WebSocketManager.disconnect()
    }

    // Trigger parent lateness stop
    fun triggerParentLatenessStart() {
        _parentLatenessTimerActive.value = true
        _parentLatenessSeconds.value = 0
    }

    fun parentArrivedAndResume() {
        val tripId = _activeTripId.value ?: return
        val currentTripValue = _currentTrip.value ?: return
        val stops = _routeStops.value
        val currentIdx = currentTripValue.currentStopIndex

        viewModelScope.launch {
            if (_parentLatenessTimerActive.value) {
                val latenessSec = _parentLatenessSeconds.value
                if (latenessSec > 2) { // Only log if genuinely delayed by a few simulated seconds
                    val latenessMin = (latenessSec * 0.5) // Let's scale 1 second of real time to 0.5 minutes of simulation
                    val stopName = stops.getOrNull(currentIdx)?.stopName ?: "Bus Stop"
                    logSimulationViolation(
                        tripId = tripId,
                        type = "PARENT_LATE",
                        details = "Parent tardiness captured at $stopName: Delayed by ${String.format("%.1f", latenessMin)} mins",
                        value = latenessMin
                    )
                }
            }

            _parentLatenessTimerActive.value = false
            _parentLatenessSeconds.value = 0
            _waitingAtStop.value = false // Resume moving!
            simulationProgress = 0
        }
    }

    // Trigger instant overtaking violation
    fun triggerOvertakingViolation() {
        val tripId = _activeTripId.value ?: return
        val currentTripValue = _currentTrip.value ?: return
        val stops = _routeStops.value
        val currentIdx = currentTripValue.currentStopIndex
        val stopName = stops.getOrNull(currentIdx)?.stopName ?: "Route Stop"

        viewModelScope.launch {
            // Momentarily turn on overtaking flag
            repository.updateTripPosition(
                tripId = tripId,
                lat = currentTripValue.currentLatitude,
                lng = currentTripValue.currentLongitude,
                speed = currentTripValue.currentSpeed + 15, // speed surge during overtaking
                stopIndex = currentIdx,
                isDeviated = currentTripValue.isDeviated,
                isOvertaking = true
            )

            logSimulationViolation(
                tripId = tripId,
                type = "OVERTAKING",
                details = "Dangerous overtaking violation logged near $stopName: Speed surged under unstable lane discipline",
                value = currentTripValue.currentSpeed + 15
            )

            // Reset back after 2 seconds
            delay(2000)
            val updatedTrip = repository.getTripByIdSync(tripId)
            if (updatedTrip != null && updatedTrip.status == "ACTIVE") {
                repository.updateTripPosition(
                    tripId = tripId,
                    lat = updatedTrip.currentLatitude,
                    lng = updatedTrip.currentLongitude,
                    speed = _simSpeedSlider.value,
                    stopIndex = updatedTrip.currentStopIndex,
                    isDeviated = updatedTrip.isDeviated,
                    isOvertaking = false
                )
            }
        }
    }

    private suspend fun logSimulationViolation(tripId: Int, type: String, details: String, value: Double) {
        val trip = repository.getTripByIdSync(tripId) ?: return
        val driver = allDrivers.value.find { it.id == trip.driverId }
        val driverName = driver?.name ?: "Unknown Driver"
        val bus = allBuses.value.find { it.id == trip.busId }
        val busPlate = bus?.plateNumber ?: "Unknown Plate"
        val route = allRoutes.value.find { it.id == trip.routeId }
        val routeName = route?.routeName ?: "Unknown Route"

        repository.logViolation(
            tripId = tripId,
            driverName = driverName,
            busPlate = busPlate,
            routeName = routeName,
            type = type,
            details = details,
            value = value
        )
    }

    fun logParentDelay(tripId: Int, stopName: String, delayMinutes: Double) {
        viewModelScope.launch {
            logSimulationViolation(
                tripId = tripId,
                type = "PARENT_LATE",
                details = "Parent tardiness captured at $stopName: Delayed by ${String.format("%.1f", delayMinutes)} mins",
                value = delayMinutes
            )
        }
    }

    // Calculate real-time estimated time of arrival (ETA) for each stop
    fun calculateETAs(trip: Trip, stops: List<RouteStop>, currentSpeedKmh: Double): Map<Int, String> {
        val etas = mutableMapOf<Int, String>()
        val currIdx = trip.currentStopIndex
        val speed = if (currentSpeedKmh <= 5) 30.0 else currentSpeedKmh // Use 30km/h default if bus is stationary

        var accumulativeDistanceKm = 0.0
        var prevLat = trip.currentLatitude.toDouble()
        var prevLng = trip.currentLongitude.toDouble()

        for (i in currIdx until stops.size) {
            val stop = stops[i]
            if (i == currIdx) {
                etas[i] = "Arrived"
                continue
            }

            // Haversine formula to compute distance
            val dLat = Math.toRadians((stop.latitude - prevLat))
            val dLng = Math.toRadians((stop.longitude - prevLng))
            val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(prevLat)) * cos(Math.toRadians(stop.latitude.toDouble())) * sin(dLng / 2).pow(2)
            val c = 2 * atan2(sqrt(a), sqrt(1 - a))
            val dist = 6371.0 * c // Earth radius in km

            accumulativeDistanceKm += dist
            prevLat = stop.latitude.toDouble()
            prevLng = stop.longitude.toDouble()

            // Time = Distance / Speed
            val timeHours = accumulativeDistanceKm / speed
            val timeMinutes = (timeHours * 60.0).roundToInt()
            
            etas[i] = if (timeMinutes <= 0) "1 min" else "$timeMinutes mins"
        }

        // For preceding stops, they are completed
        for (i in 0 until currIdx) {
            etas[i] = "Passed"
        }

        return etas
    }

    // Dynamic distance calculation helper
    fun getDistanceToNextStop(trip: Trip, stops: List<RouteStop>): String {
        val currIdx = trip.currentStopIndex
        if (currIdx >= stops.size - 1) return "Arrived at Terminal"
        val nextStop = stops[currIdx + 1]

        val dLat = Math.toRadians((nextStop.latitude - trip.currentLatitude).toDouble())
        val dLng = Math.toRadians((nextStop.longitude - trip.currentLongitude).toDouble())
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(trip.currentLatitude.toDouble())) * cos(Math.toRadians(nextStop.latitude.toDouble())) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        val dist = 6371.0 * c // distance in km

        return if (dist < 1.0) "${(dist * 1000).toInt()} meters" else String.format("%.2f km", dist)
    }

    private fun calculateDistance(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2).pow(2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return 6371.0 * c // in km
    }

    private fun startRealGpsTracking(tripId: Int) {
        stopRealGpsTracking()
        val request = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 1000L).apply {
            setMinUpdateIntervalMillis(1000L)
        }.build()

        val callback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation ?: return
                val lat = location.latitude
                val lng = location.longitude
                val speed = location.speed * 3.6 // Convert m/s to km/h
                
                Log.d("TrackerViewModel", "Real GPS Position Update: lat=$lat, lng=$lng, speed=$speed")

                viewModelScope.launch {
                    val trip = repository.getTripByIdSync(tripId) ?: return@launch
                    val stops = _routeStops.value
                    val currIndex = trip.currentStopIndex
                    
                    var arrivedIndex = currIndex
                    if (stops.isNotEmpty() && currIndex < stops.size - 1) {
                        val nextStop = stops[currIndex + 1]
                        val dist = calculateDistance(lat, lng, nextStop.latitude.toDouble(), nextStop.longitude.toDouble())
                        if (dist < 0.05) { // within 50 meters
                            arrivedIndex = currIndex + 1
                            _waitingAtStop.value = true
                        }
                    }
                    
                    val isDeviated = checkIfRealLocationDeviated(lat, lng, stops)

                    // 1. Update our Room Database & Supabase immediately
                    repository.updateTripPosition(
                        tripId = tripId,
                        lat = lat.toFloat(),
                        lng = lng.toFloat(),
                        speed = speed,
                        stopIndex = arrivedIndex,
                        isDeviated = isDeviated,
                        isOvertaking = trip.isOvertaking
                    )

                    // 2. Stream telemetry over OkHttp WebSocket (which mirrors it/echoes it back)
                    WebSocketManager.sendTelemetry(
                        tripId = tripId,
                        lat = lat,
                        lng = lng,
                        speed = speed,
                        isDeviated = isDeviated
                    )

                    _currentTrip.value = repository.getTripByIdSync(tripId)
                }
            }
        }
        
        locationCallback = callback
        try {
            fusedLocationClient.requestLocationUpdates(request, callback, android.os.Looper.getMainLooper())
            Log.d("TrackerViewModel", "Successfully requested location updates from device GPS.")
        } catch (e: SecurityException) {
            Log.e("TrackerViewModel", "Location permission missing: ${e.message}")
        }
    }

    private fun stopRealGpsTracking() {
        locationCallback?.let { callback ->
            try {
                fusedLocationClient.removeLocationUpdates(callback)
                Log.d("TrackerViewModel", "Successfully stopped device GPS tracking.")
            } catch (e: Exception) {
                Log.e("TrackerViewModel", "Error stopping location updates: ${e.message}")
            }
            locationCallback = null
        }
    }

    private fun checkIfRealLocationDeviated(lat: Double, lng: Double, stops: List<RouteStop>): Boolean {
        if (stops.isEmpty()) return false
        var minDistance = Double.MAX_VALUE
        for (stop in stops) {
            val dist = calculateDistance(lat, lng, stop.latitude.toDouble(), stop.longitude.toDouble())
            if (dist < minDistance) {
                minDistance = dist
            }
        }
        return minDistance > 0.5 // 500 meters off-route
    }
}
