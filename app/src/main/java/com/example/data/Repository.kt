package com.example.data

import android.util.Log
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
    val allTrips: Flow<List<Trip>> = tripDao.getAllTrips()
    val allViolations: Flow<List<ViolationRecord>> = violationDao.getAllViolations()

    fun getStopsForRoute(routeId: Int): Flow<List<RouteStop>> = routeDao.getStopsForRoute(routeId)
    suspend fun getStopsForRouteSync(routeId: Int): List<RouteStop> = routeDao.getStopsForRouteSync(routeId)
    suspend fun getAllRoutesSync(): List<BusRoute> = routeDao.getAllRoutesSync()

    fun getTripById(tripId: Int): Flow<Trip?> = tripDao.getTripById(tripId)
    suspend fun getTripByIdSync(tripId: Int): Trip? = tripDao.getTripByIdSync(tripId)
    suspend fun getAnyActiveTrip(): Trip? = tripDao.getAnyActiveTrip()

    suspend fun getDriverByUsername(username: String): Driver? = driverDao.getDriverByUsername(username)
    suspend fun getDriverById(driverId: Int): Driver? = driverDao.getDriverById(driverId)

    suspend fun syncWithSupabase() {
        try {
            Log.d("BusTrackerRepository", "Starting background sync with Supabase...")
            // Pull new/updated Buses from Supabase and insert into Room
            val remoteBuses = SupabaseDbManager.fetchAllBuses()
            for (bus in remoteBuses) {
                busDao.insertBus(bus)
            }

            // Pull Routes
            val remoteRoutes = SupabaseDbManager.fetchAllRoutes()
            for (route in remoteRoutes) {
                routeDao.insertRoute(route)
            }

            // Pull Route Stops
            val remoteStops = SupabaseDbManager.fetchAllRouteStops()
            for (stop in remoteStops) {
                routeDao.insertStop(stop)
            }

            // Pull Drivers
            val remoteDrivers = SupabaseDbManager.fetchAllDrivers()
            for (driver in remoteDrivers) {
                driverDao.insertDriver(driver)
            }

            // Pull Trips
            val remoteTrips = SupabaseDbManager.fetchAllTrips()
            for (trip in remoteTrips) {
                tripDao.insertTrip(trip)
            }

            // Pull Violations
            val remoteViolations = SupabaseDbManager.fetchAllViolations()
            for (violation in remoteViolations) {
                violationDao.insertViolation(violation)
            }
            Log.d("BusTrackerRepository", "Synchronized successfully with Supabase!")
        } catch (e: Exception) {
            Log.e("BusTrackerRepository", "Failed to sync with Supabase: ${e.message}")
        }
    }

    suspend fun insertBus(bus: Bus) {
        busDao.insertBus(bus)
        try { SupabaseDbManager.pushBus(bus) } catch (e: Exception) { Log.e("Repository", "Push bus failed", e) }
    }

    suspend fun updateBus(bus: Bus) {
        busDao.updateBus(bus)
        try { SupabaseDbManager.pushBus(bus) } catch (e: Exception) { Log.e("Repository", "Push bus failed", e) }
    }

    suspend fun deleteBus(bus: Bus) {
        busDao.deleteBus(bus)
        try { SupabaseDbManager.deleteBus(bus) } catch (e: Exception) { Log.e("Repository", "Delete bus failed", e) }
    }

    suspend fun insertDriver(driver: Driver) {
        driverDao.insertDriver(driver)
        try { SupabaseDbManager.pushDriver(driver) } catch (e: Exception) { Log.e("Repository", "Push driver failed", e) }
    }

    suspend fun updateDriver(driver: Driver) {
        driverDao.updateDriver(driver)
        try { SupabaseDbManager.pushDriver(driver) } catch (e: Exception) { Log.e("Repository", "Push driver failed", e) }
    }

    suspend fun deleteDriver(driver: Driver) {
        driverDao.deleteDriver(driver)
        try { SupabaseDbManager.deleteDriver(driver) } catch (e: Exception) { Log.e("Repository", "Delete driver failed", e) }
    }

    suspend fun insertRoute(routeName: String, speedLimit: Double): Long {
        val route = BusRoute(routeName = routeName, speedLimit = speedLimit)
        val generatedId = routeDao.insertRoute(route)
        val routeWithId = route.copy(id = generatedId.toInt())
        try {
            val stops = routeDao.getStopsForRouteSync(generatedId.toInt())
            SupabaseDbManager.pushRoute(routeWithId, stops)
        } catch (e: Exception) { Log.e("Repository", "Push route failed", e) }
        return generatedId
    }

    suspend fun insertStop(stop: RouteStop) {
        routeDao.insertStop(stop)
        try {
            val route = routeDao.getAllRoutesSync().firstOrNull { it.id == stop.routeId }
            if (route != null) {
                val stops = routeDao.getStopsForRouteSync(route.id)
                SupabaseDbManager.pushRoute(route, stops)
            }
        } catch (e: Exception) { Log.e("Repository", "Push stop failed", e) }
    }

    suspend fun deleteRoute(route: BusRoute) {
        routeDao.deleteStopsForRoute(route.id)
        routeDao.deleteRoute(route)
        try { SupabaseDbManager.deleteRoute(route) } catch (e: Exception) { Log.e("Repository", "Delete route failed", e) }
    }

    suspend fun startTrip(driverId: Int, busId: Int, routeId: Int, startLat: Float, startLng: Float, isSimulation: Boolean): Long {
        val trip = Trip(
            driverId = driverId,
            busId = busId,
            routeId = routeId,
            status = "ACTIVE",
            startTime = System.currentTimeMillis(),
            currentLatitude = startLat,
            currentLongitude = startLng,
            currentSpeed = 0.0,
            currentStopIndex = 0,
            isSimulation = isSimulation
        )
        val generatedId = tripDao.insertTrip(trip)
        val tripWithId = trip.copy(id = generatedId.toInt())
        try { SupabaseDbManager.pushTrip(tripWithId) } catch (e: Exception) { Log.e("Repository", "Push trip failed", e) }
        return generatedId
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
        try { SupabaseDbManager.pushTrip(updated) } catch (e: Exception) { Log.e("Repository", "Push trip position failed", e) }
    }

    suspend fun completeTrip(tripId: Int) {
        val existing = tripDao.getTripByIdSync(tripId) ?: return
        val updated = existing.copy(
            status = "COMPLETED",
            endTime = System.currentTimeMillis()
        )
        tripDao.updateTrip(updated)
        try { SupabaseDbManager.pushTrip(updated) } catch (e: Exception) { Log.e("Repository", "Push complete trip failed", e) }
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
        try { SupabaseDbManager.pushViolation(record) } catch (e: Exception) { Log.e("Repository", "Push violation failed", e) }
    }

    suspend fun checkAndPrepopulate() {
        val buses = allBuses.first()
        if (buses.isEmpty()) {
            // Prepopulate Buses
            val bus1 = Bus(plateNumber = "JK01-4321", capacity = 40, model = "Tata Starbus M3")
            val bus2 = Bus(plateNumber = "JK01-9876", capacity = 18, model = "Force Traveller Pro")
            val bus3 = Bus(plateNumber = "JK01-1122", capacity = 32, model = "Eicher Skyline Pro")
            busDao.insertBus(bus1)
            busDao.insertBus(bus2)
            busDao.insertBus(bus3)

            // Prepopulate Routes
            val route1Id = routeDao.insertRoute(BusRoute(routeName = "Kashmir University - Lal Chowk Route", speedLimit = 40.0)).toInt()
            val route2Id = routeDao.insertRoute(BusRoute(routeName = "Dal Gate - Nishat - Shalimar Route", speedLimit = 50.0)).toInt()
            val route3Id = routeDao.insertRoute(BusRoute(routeName = "Women's College MA Road Ring Route", speedLimit = 45.0)).toInt()

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

            // Route 3 Stops (Women's College MA Road Ring Route)
            routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Women's College MA Road", latitude = 34.0754f, longitude = 74.8143f, sequenceOrder = 1, expectedArrivalMinutes = 0))
            routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Dalgate", latitude = 34.0784f, longitude = 74.8310f, sequenceOrder = 2, expectedArrivalMinutes = 6))
            routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Khayam Chowk", latitude = 34.0844f, longitude = 74.8235f, sequenceOrder = 3, expectedArrivalMinutes = 11))
            routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Khanyar Chowk", latitude = 34.0915f, longitude = 74.8210f, sequenceOrder = 4, expectedArrivalMinutes = 16))
            routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Bohri Kadal", latitude = 34.0965f, longitude = 74.8145f, sequenceOrder = 5, expectedArrivalMinutes = 21))
            routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Rajouri Kadal", latitude = 34.0990f, longitude = 74.8095f, sequenceOrder = 6, expectedArrivalMinutes = 26))
            routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Safa Kadal", latitude = 34.1020f, longitude = 74.7985f, sequenceOrder = 7, expectedArrivalMinutes = 32))
            routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Karan Nagar", latitude = 34.0885f, longitude = 74.7975f, sequenceOrder = 8, expectedArrivalMinutes = 38))
            routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Jahangir Chowk", latitude = 34.0735f, longitude = 74.8055f, sequenceOrder = 9, expectedArrivalMinutes = 44))
            routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Women's College MA Road Terminal", latitude = 34.0754f, longitude = 74.8143f, sequenceOrder = 10, expectedArrivalMinutes = 50))

            // Prepopulate Drivers
            val driver1 = Driver(name = "Mohammad Yusuf", licenseNumber = "DL-JK012019001", phoneNumber = "+91 94190 12345", username = "yusuf", password = "123", mappedBusId = 1, mappedRouteId = route1Id)
            val driver2 = Driver(name = "Ghulam Nabi", licenseNumber = "DL-JK022020551", phoneNumber = "+91 70061 54321", username = "nabi", password = "123", mappedBusId = 2, mappedRouteId = route2Id)
            val driver3 = Driver(name = "Farooq Ahmad", licenseNumber = "DL-JK032021882", phoneNumber = "+91 99060 98765", username = "farooq", password = "123", mappedBusId = 3, mappedRouteId = route3Id)
            driverDao.insertDriver(driver1)
            driverDao.insertDriver(driver2)
            driverDao.insertDriver(driver3)
        } else {
            // Also ensure it is present for already-seeded databases
            val existing = routeDao.getAllRoutesSync()
            val alreadyHasWomensRoute = existing.any { it.routeName == "Women's College MA Road Ring Route" }
            if (!alreadyHasWomensRoute) {
                val route3Id = routeDao.insertRoute(BusRoute(routeName = "Women's College MA Road Ring Route", speedLimit = 45.0)).toInt()
                
                routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Women's College MA Road", latitude = 34.0754f, longitude = 74.8143f, sequenceOrder = 1, expectedArrivalMinutes = 0))
                routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Dalgate", latitude = 34.0784f, longitude = 74.8310f, sequenceOrder = 2, expectedArrivalMinutes = 6))
                routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Khayam Chowk", latitude = 34.0844f, longitude = 74.8235f, sequenceOrder = 3, expectedArrivalMinutes = 11))
                routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Khanyar Chowk", latitude = 34.0915f, longitude = 74.8210f, sequenceOrder = 4, expectedArrivalMinutes = 16))
                routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Bohri Kadal", latitude = 34.0965f, longitude = 74.8145f, sequenceOrder = 5, expectedArrivalMinutes = 21))
                routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Rajouri Kadal", latitude = 34.0990f, longitude = 74.8095f, sequenceOrder = 6, expectedArrivalMinutes = 26))
                routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Safa Kadal", latitude = 34.1020f, longitude = 74.7985f, sequenceOrder = 7, expectedArrivalMinutes = 32))
                routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Karan Nagar", latitude = 34.0885f, longitude = 74.7975f, sequenceOrder = 8, expectedArrivalMinutes = 38))
                routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Jahangir Chowk", latitude = 34.0735f, longitude = 74.8055f, sequenceOrder = 9, expectedArrivalMinutes = 44))
                routeDao.insertStop(RouteStop(routeId = route3Id, stopName = "Women's College MA Road Terminal", latitude = 34.0754f, longitude = 74.8143f, sequenceOrder = 10, expectedArrivalMinutes = 50))

                val existingBuses = busDao.getBusesSync()
                val hasBus3 = existingBuses.any { it.plateNumber == "JK01-1122" }
                if (!hasBus3) {
                    busDao.insertBus(Bus(plateNumber = "JK01-1122", capacity = 32, model = "Eicher Skyline Pro"))
                }
                val freshBuses = busDao.getBusesSync()
                val bus3Id = freshBuses.firstOrNull { it.plateNumber == "JK01-1122" }?.id ?: 3

                val existingDriver = driverDao.getDriverByUsername("farooq")
                if (existingDriver == null) {
                    driverDao.insertDriver(Driver(name = "Farooq Ahmad", licenseNumber = "DL-JK032021882", phoneNumber = "+91 99060 98765", username = "farooq", password = "123", mappedBusId = bus3Id, mappedRouteId = route3Id))
                }
            }
        }
    }
}
