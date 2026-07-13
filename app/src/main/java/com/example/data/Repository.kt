package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class BusTrackerRepository(private val database: AppDatabase) {

    private val busDao = database.busDao()
    private val driverDao = database.driverDao()
    private val routeDao = database.routeDao()
    private val tripDao = database.tripDao()
    private val violationDao = database.violationDao()

    val allBuses: Flow<List<Bus>> = busDao.getAllBuses()
    val allDrivers: Flow<List<Driver>> = driverDao.getAllDrivers()
    val allRoutes: Flow<List<BusRoute>> = routeDao.getAllRoutes()
    val activeTrips: Flow<List<Trip>> = tripDao.getActiveTrips()
    val allViolations: Flow<List<ViolationRecord>> = violationDao.getAllViolations()

    fun getStopsForRoute(routeId: Int): Flow<List<RouteStop>> = routeDao.getStopsForRoute(routeId)
    suspend fun getStopsForRouteSync(routeId: Int): List<RouteStop> = routeDao.getStopsForRouteSync(routeId)

    fun getTripById(tripId: Int): Flow<Trip?> = tripDao.getTripById(tripId)
    suspend fun getTripByIdSync(tripId: Int): Trip? = tripDao.getTripByIdSync(tripId)

    suspend fun getDriverByUsername(username: String): Driver? = driverDao.getDriverByUsername(username)

    suspend fun insertBus(bus: Bus) = busDao.insertBus(bus)
    suspend fun updateBus(bus: Bus) = busDao.updateBus(bus)
    suspend fun deleteBus(bus: Bus) = busDao.deleteBus(bus)

    suspend fun insertDriver(driver: Driver) = driverDao.insertDriver(driver)
    suspend fun updateDriver(driver: Driver) = driverDao.updateDriver(driver)
    suspend fun deleteDriver(driver: Driver) = driverDao.deleteDriver(driver)

    suspend fun insertRoute(routeName: String, speedLimit: Double): Long {
        val route = BusRoute(routeName = routeName, speedLimit = speedLimit)
        return routeDao.insertRoute(route)
    }

    suspend fun insertStop(stop: RouteStop) = routeDao.insertStop(stop)
    suspend fun deleteRoute(route: BusRoute) {
        routeDao.deleteStopsForRoute(route.id)
        routeDao.deleteRoute(route)
    }

    suspend fun startTrip(driverId: Int, busId: Int, routeId: Int, startLat: Float, startLng: Float): Long {
        val trip = Trip(
            driverId = driverId,
            busId = busId,
            routeId = routeId,
            status = "ACTIVE",
            startTime = System.currentTimeMillis(),
            currentLatitude = startLat,
            currentLongitude = startLng,
            currentSpeed = 0.0,
            currentStopIndex = 0
        )
        return tripDao.insertTrip(trip)
    }

    suspend fun updateTripPosition(tripId: Int, lat: Float, lng: Float, speed: Double, stopIndex: Int, isDeviated: Boolean, isOvertaking: Boolean) {
        val existing = tripDao.getTripByIdSync(tripId) ?: return
        val updated = existing.copy(
            currentLatitude = lat,
            currentLongitude = lng,
            currentSpeed = speed,
            currentStopIndex = stopIndex,
            isDeviated = isDeviated,
            isOvertaking = isOvertaking
        )
        tripDao.updateTrip(updated)
    }

    suspend fun completeTrip(tripId: Int) {
        val existing = tripDao.getTripByIdSync(tripId) ?: return
        val updated = existing.copy(
            status = "COMPLETED",
            endTime = System.currentTimeMillis()
        )
        tripDao.updateTrip(updated)
    }

    suspend fun logViolation(tripId: Int, driverName: String, busPlate: String, routeName: String, type: String, details: String, value: Double) {
        val record = ViolationRecord(
            tripId = tripId,
            driverName = driverName,
            busPlate = busPlate,
            routeName = routeName,
            type = type,
            details = details,
            value = value
        )
        violationDao.insertViolation(record)
    }

    suspend fun checkAndPrepopulate() {
        val buses = allBuses.first()
        if (buses.isEmpty()) {
            // Prepopulate Buses
            val bus1 = Bus(plateNumber = "JK01-4321", capacity = 40, model = "Tata Starbus M3")
            val bus2 = Bus(plateNumber = "JK01-9876", capacity = 18, model = "Force Traveller Pro")
            busDao.insertBus(bus1)
            busDao.insertBus(bus2)

            // Prepopulate Routes
            val route1Id = routeDao.insertRoute(BusRoute(routeName = "Kashmir University - Lal Chowk Route", speedLimit = 40.0)).toInt()
            val route2Id = routeDao.insertRoute(BusRoute(routeName = "Dal Gate - Nishat - Shalimar Route", speedLimit = 50.0)).toInt()

            // Prepopulate Route Stops
            // Route 1 Stops (Kashmir Univ - Lal Chowk)
            routeDao.insertStop(RouteStop(routeId = route1Id, stopName = "Lal Chowk Hub", latitude = 34.0722f, longitude = 74.8115f, sequenceOrder = 1, expectedArrivalMinutes = 0))
            routeDao.insertStop(RouteStop(routeId = route1Id, stopName = "Dal Gate Circle", latitude = 34.0784f, longitude = 74.8310f, sequenceOrder = 2, expectedArrivalMinutes = 8))
            routeDao.insertStop(RouteStop(routeId = route1Id, stopName = "Hazratbal Crossing", latitude = 34.1228f, longitude = 74.8415f, sequenceOrder = 3, expectedArrivalMinutes = 18))
            routeDao.insertStop(RouteStop(routeId = route1Id, stopName = "Kashmir University Terminal", latitude = 34.1275f, longitude = 74.8375f, sequenceOrder = 4, expectedArrivalMinutes = 22))

            // Route 2 Stops (Dal Gate - Nishat - Shalimar)
            routeDao.insertStop(RouteStop(routeId = route2Id, stopName = "Dal Gate Main", latitude = 34.0784f, longitude = 74.8310f, sequenceOrder = 1, expectedArrivalMinutes = 0))
            routeDao.insertStop(RouteStop(routeId = route2Id, stopName = "Nehru Park Jetty", latitude = 34.0850f, longitude = 74.8420f, sequenceOrder = 2, expectedArrivalMinutes = 6))
            routeDao.insertStop(RouteStop(routeId = route2Id, stopName = "Nishat Mughal Garden", latitude = 34.1250f, longitude = 74.8790f, sequenceOrder = 3, expectedArrivalMinutes = 15))
            routeDao.insertStop(RouteStop(routeId = route2Id, stopName = "Shalimar Royal Terminal", latitude = 34.1505f, longitude = 74.8720f, sequenceOrder = 4, expectedArrivalMinutes = 22))

            // Prepopulate Drivers
            val driver1 = Driver(name = "Mohammad Yusuf", licenseNumber = "DL-JK012019001", phoneNumber = "+91 94190 12345", username = "yusuf", password = "123", mappedBusId = 1, mappedRouteId = route1Id)
            val driver2 = Driver(name = "Ghulam Nabi", licenseNumber = "DL-JK022020551", phoneNumber = "+91 70061 54321", username = "nabi", password = "123", mappedBusId = 2, mappedRouteId = route2Id)
            driverDao.insertDriver(driver1)
            driverDao.insertDriver(driver2)
        }
    }
}
