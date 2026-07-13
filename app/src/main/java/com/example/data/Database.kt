package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface BusDao {
    @Query("SELECT * FROM buses")
    fun getAllBuses(): Flow<List<Bus>>

    @Query("SELECT * FROM buses")
    suspend fun getBusesSync(): List<Bus>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBus(bus: Bus)

    @Update
    suspend fun updateBus(bus: Bus)

    @Delete
    suspend fun deleteBus(bus: Bus)
}

@Dao
interface DriverDao {
    @Query("SELECT * FROM drivers")
    fun getAllDrivers(): Flow<List<Driver>>

    @Query("SELECT * FROM drivers WHERE username = :username LIMIT 1")
    suspend fun getDriverByUsername(username: String): Driver?

    @Query("SELECT * FROM drivers WHERE id = :driverId LIMIT 1")
    suspend fun getDriverById(driverId: Int): Driver?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDriver(driver: Driver)

    @Update
    suspend fun updateDriver(driver: Driver)

    @Delete
    suspend fun deleteDriver(driver: Driver)
}

@Dao
interface RouteDao {
    @Query("SELECT * FROM bus_routes")
    fun getAllRoutes(): Flow<List<BusRoute>>

    @Query("SELECT * FROM bus_routes")
    suspend fun getAllRoutesSync(): List<BusRoute>

    @Query("SELECT * FROM route_stops WHERE routeId = :routeId ORDER BY sequenceOrder ASC")
    fun getStopsForRoute(routeId: Int): Flow<List<RouteStop>>

    @Query("SELECT * FROM route_stops WHERE routeId = :routeId ORDER BY sequenceOrder ASC")
    suspend fun getStopsForRouteSync(routeId: Int): List<RouteStop>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: BusRoute): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertStop(stop: RouteStop)

    @Query("DELETE FROM route_stops WHERE routeId = :routeId")
    suspend fun deleteStopsForRoute(routeId: Int)

    @Delete
    suspend fun deleteRoute(route: BusRoute)
}

@Dao
interface TripDao {
    @Query("SELECT * FROM trips WHERE status = 'ACTIVE'")
    fun getActiveTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun getAllTrips(): Flow<List<Trip>>

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    fun getTripById(tripId: Int): Flow<Trip?>

    @Query("SELECT * FROM trips WHERE id = :tripId LIMIT 1")
    suspend fun getTripByIdSync(tripId: Int): Trip?

    @Query("SELECT * FROM trips WHERE status = 'ACTIVE' LIMIT 1")
    suspend fun getAnyActiveTrip(): Trip?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrip(trip: Trip): Long

    @Update
    suspend fun updateTrip(trip: Trip)
}

@Dao
interface ViolationDao {
    @Query("SELECT * FROM violation_records ORDER BY timestamp DESC")
    fun getAllViolations(): Flow<List<ViolationRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertViolation(violation: ViolationRecord)
}

@Database(
    entities = [Bus::class, Driver::class, BusRoute::class, RouteStop::class, Trip::class, ViolationRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun busDao(): BusDao
    abstract fun driverDao(): DriverDao
    abstract fun routeDao(): RouteDao
    abstract fun tripDao(): TripDao
    abstract fun violationDao(): ViolationDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: android.content.Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "safe_school_bus_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
