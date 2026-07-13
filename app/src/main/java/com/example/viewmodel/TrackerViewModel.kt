package com.example.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlin.math.*

class TrackerViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BusTrackerRepository

    init {
        val database = AppDatabase.getDatabase(application)
        repository = BusTrackerRepository(database)
        viewModelScope.launch {
            repository.checkAndPrepopulate()
            checkForActiveTripAndResume()
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
                    _tripResumed.value = true
                    
                    startSimulation(activeTrip.id, stops)
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

    private var simulationProgress = 0 // 0 to 10 between stops
    private var lastOverspeedLogged = 0L
    private var lastDeviationLogged = 0L

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

            val startLat = stops.first().latitude
            val startLng = stops.first().longitude

            val tripId = repository.startTrip(driverId, busId, routeId, startLat, startLng).toInt()
            _activeTripId.value = tripId
            _tripResumed.value = false

            // Reset simulation control states
            _simSpeedSlider.value = 35.0
            _simIsDeviated.value = false
            _waitingAtStop.value = false
            _parentLatenessTimerActive.value = false
            _parentLatenessSeconds.value = 0
            simulationProgress = 0

            startSimulation(tripId, stops)
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
            while (true) {
                delay(1000) // Simulation tick every second
                val trip = repository.getTripByIdSync(tripId) ?: break
                _currentTrip.value = trip

                if (_waitingAtStop.value) {
                    if (_parentLatenessTimerActive.value) {
                        _parentLatenessSeconds.value += 1
                    }
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

                // Update database
                repository.updateTripPosition(
                    tripId = tripId,
                    lat = nextLat,
                    lng = nextLng,
                    speed = currentSpeed,
                    stopIndex = currIndex,
                    isDeviated = _simIsDeviated.value,
                    isOvertaking = trip.isOvertaking
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
            }
        }
    }

    fun stopSimulation() {
        simulationJob?.cancel()
        simulationJob = null
        _activeTripId.value = null
        _currentTrip.value = null
        _routeStops.value = emptyList()
        _waitingAtStop.value = false
        _parentLatenessTimerActive.value = false
        _parentLatenessSeconds.value = 0
        simulationProgress = 0
        _tripResumed.value = false
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
}
