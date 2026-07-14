package com.example.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.sql.Connection
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.sql.ResultSet
import java.sql.Statement

object SupabaseDbManager {
    private const val TAG = "SupabaseDbManager"
    
    // Remote connection properties from User connection string:
    // postgresql://postgres:Baba@1361360@db.jabkspatgpuiuqhxxstq.supabase.co:5432/postgres
    private const val DB_URL = "jdbc:postgresql://db.jabkspatgpuiuqhxxstq.supabase.co:5432/postgres?sslmode=require"
    private const val DB_USER = "postgres"
    private const val DB_PASS = "Baba@1361360"

    private var isConnected = false

    init {
        // Load PostgreSQL JDBC driver
        try {
            Class.forName("org.postgresql.Driver")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load PostgreSQL driver", e)
        }
    }

    suspend fun testConnection(): Boolean = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            isConnected = conn != null && !conn.isClosed
            Log.d(TAG, "Successfully connected to Supabase PostgreSQL!")
            isConnected
        } catch (e: Exception) {
            Log.e(TAG, "Failed to connect to Supabase PostgreSQL: ${e.message}")
            isConnected = false
            false
        } finally {
            try { conn?.close() } catch (ignored: Exception) {}
        }
    }

    suspend fun initializeSchemaAndPrepopulate() = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        var stmt: Statement? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            stmt = conn.createStatement()

            Log.d(TAG, "Creating tables in Supabase if not exists...")

            // 1. buses
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS buses (
                    id SERIAL PRIMARY KEY,
                    plate_number VARCHAR(100) UNIQUE,
                    capacity INT,
                    model VARCHAR(100)
                );
            """.trimIndent())

            // 2. bus_routes
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS bus_routes (
                    id SERIAL PRIMARY KEY,
                    route_name VARCHAR(255) UNIQUE,
                    speed_limit DOUBLE PRECISION DEFAULT 40.0
                );
            """.trimIndent())

            // 3. route_stops
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS route_stops (
                    id SERIAL PRIMARY KEY,
                    route_id INT,
                    stop_name VARCHAR(255),
                    latitude REAL,
                    longitude REAL,
                    sequence_order INT,
                    expected_arrival_minutes INT DEFAULT 0
                );
            """.trimIndent())

            // 4. drivers
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS drivers (
                    id SERIAL PRIMARY KEY,
                    name VARCHAR(255),
                    license_number VARCHAR(100),
                    phone_number VARCHAR(100),
                    username VARCHAR(100) UNIQUE,
                    password VARCHAR(100),
                    mapped_bus_id INT,
                    mapped_route_id INT
                );
            """.trimIndent())

            // 5. trips
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS trips (
                    id INT PRIMARY KEY,
                    driver_id INT,
                    bus_id INT,
                    route_id INT,
                    status VARCHAR(50),
                    start_time BIGINT,
                    end_time BIGINT,
                    current_latitude REAL,
                    current_longitude REAL,
                    current_speed DOUBLE PRECISION,
                    current_stop_index INT DEFAULT 0,
                    is_deviated BOOLEAN DEFAULT FALSE,
                    is_overtaking BOOLEAN DEFAULT FALSE,
                    is_simulation BOOLEAN DEFAULT TRUE
                );
            """.trimIndent())

            try {
                stmt.execute("ALTER TABLE trips ADD COLUMN IF NOT EXISTS is_simulation BOOLEAN DEFAULT TRUE;")
            } catch (e: Exception) {
                Log.d(TAG, "ALTER TABLE trips column is_simulation addition handled: ${e.message}")
            }

            // 6. violation_records
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS violation_records (
                    id SERIAL PRIMARY KEY,
                    trip_id INT,
                    driver_name VARCHAR(255),
                    bus_plate VARCHAR(100),
                    route_name VARCHAR(255),
                    type VARCHAR(100),
                    timestamp BIGINT,
                    details TEXT,
                    value DOUBLE PRECISION DEFAULT 0.0
                );
            """.trimIndent())

            Log.d(TAG, "Tables validated successfully. Checking if data prepopulation is needed...")

            // Check if buses table is empty, if so, seed data
            val rs = stmt.executeQuery("SELECT COUNT(*) FROM buses")
            var count = 0
            if (rs.next()) {
                count = rs.getInt(1)
            }
            rs.close()

            if (count == 0) {
                Log.d(TAG, "Supabase tables are empty, inserting prepopulated data...")
                
                // Seed Buses
                stmt.execute("INSERT INTO buses (plate_number, capacity, model) VALUES ('JK01-4321', 40, 'Tata Starbus M3') ON CONFLICT DO NOTHING;")
                stmt.execute("INSERT INTO buses (plate_number, capacity, model) VALUES ('JK01-9876', 18, 'Force Traveller Pro') ON CONFLICT DO NOTHING;")
                stmt.execute("INSERT INTO buses (plate_number, capacity, model) VALUES ('JK01-1122', 32, 'Eicher Skyline Pro') ON CONFLICT DO NOTHING;")

                // Seed Routes
                stmt.execute("INSERT INTO bus_routes (route_name, speed_limit) VALUES ('Kashmir University - Lal Chowk Route', 40.0) ON CONFLICT DO NOTHING;")
                stmt.execute("INSERT INTO bus_routes (route_name, speed_limit) VALUES ('Dal Gate - Nishat - Shalimar Route', 50.0) ON CONFLICT DO NOTHING;")
                stmt.execute("INSERT INTO bus_routes (route_name, speed_limit) VALUES ('Women\'s College MA Road Ring Route', 45.0) ON CONFLICT DO NOTHING;")

                // Fetch route ids
                val rMap = mutableMapOf<String, Int>()
                val routeRs = stmt.executeQuery("SELECT id, route_name FROM bus_routes")
                while (routeRs.next()) {
                    rMap[routeRs.getString("route_name")] = routeRs.getInt("id")
                }
                routeRs.close()

                val r1Id = rMap["Kashmir University - Lal Chowk Route"] ?: 1
                val r2Id = rMap["Dal Gate - Nishat - Shalimar Route"] ?: 2
                val r3Id = rMap["Women's College MA Road Ring Route"] ?: 3

                // Route Stops
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r1Id, 'Lal Chowk Hub', 34.0722, 74.8115, 1, 0);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r1Id, 'Dal Gate Circle', 34.0784, 74.8310, 2, 8);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r1Id, 'Hazratbal Crossing', 34.1228, 74.8415, 3, 18);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r1Id, 'Kashmir University Terminal', 34.1275, 74.8375, 4, 22);")

                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r2Id, 'Dal Gate Main', 34.0784, 74.8310, 1, 0);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r2Id, 'Nehru Park Jetty', 34.0850, 74.8420, 2, 6);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r2Id, 'Nishat Mughal Garden', 34.1250, 74.8790, 3, 15);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r2Id, 'Shalimar Royal Terminal', 34.1505, 74.8720, 4, 22);")

                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r3Id, 'Women\'s College MA Road', 34.0754, 74.8143, 1, 0);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r3Id, 'Dalgate', 34.0784, 74.8310, 2, 6);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r3Id, 'Khayam Chowk', 34.0844, 74.8235, 3, 11);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r3Id, 'Khanyar Chowk', 34.0915, 74.8210, 4, 16);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r3Id, 'Bohri Kadal', 34.0965, 74.8145, 5, 21);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r3Id, 'Rajouri Kadal', 34.0990, 74.8095, 6, 26);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r3Id, 'Safa Kadal', 34.1020, 74.7985, 7, 32);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r3Id, 'Karan Nagar', 34.0885, 74.7975, 8, 38);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r3Id, 'Jahangir Chowk', 34.0735, 74.8055, 9, 44);")
                stmt.execute("INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) VALUES ($r3Id, 'Women\'s College MA Road Terminal', 34.0754, 74.8143, 10, 50);")

                // Fetch bus ids
                val bMap = mutableMapOf<String, Int>()
                val busRs = stmt.executeQuery("SELECT id, plate_number FROM buses")
                while (busRs.next()) {
                    bMap[busRs.getString("plate_number")] = busRs.getInt("id")
                }
                busRs.close()

                val b1Id = bMap["JK01-4321"] ?: 1
                val b2Id = bMap["JK01-9876"] ?: 2
                val b3Id = bMap["JK01-1122"] ?: 3

                // Seed Drivers
                stmt.execute("INSERT INTO drivers (name, license_number, phone_number, username, password, mapped_bus_id, mapped_route_id) VALUES ('Mohammad Yusuf', 'DL-JK012019001', '+91 94190 12345', 'yusuf', '123', $b1Id, $r1Id) ON CONFLICT DO NOTHING;")
                stmt.execute("INSERT INTO drivers (name, license_number, phone_number, username, password, mapped_bus_id, mapped_route_id) VALUES ('Ghulam Nabi', 'DL-JK022020551', '+91 70061 54321', 'nabi', '123', $b2Id, $r2Id) ON CONFLICT DO NOTHING;")
                stmt.execute("INSERT INTO drivers (name, license_number, phone_number, username, password, mapped_bus_id, mapped_route_id) VALUES ('Farooq Ahmad', 'DL-JK032021882', '+91 99060 98765', 'farooq', '123', $b3Id, $r3Id) ON CONFLICT DO NOTHING;")
                
                Log.d(TAG, "Prepopulated seed data in Supabase successfully!")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Schema creation error: ${e.message}", e)
        } finally {
            try { stmt?.close() } catch (ignored: Exception) {}
            try { conn?.close() } catch (ignored: Exception) {}
        }
    }

    // --- FETCH METHODS ---
    suspend fun fetchAllBuses(): List<Bus> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Bus>()
        var conn: Connection? = null
        var stmt: Statement? = null
        var rs: ResultSet? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            stmt = conn.createStatement()
            rs = stmt.executeQuery("SELECT * FROM buses ORDER BY id")
            while (rs.next()) {
                list.add(Bus(
                    id = rs.getInt("id"),
                    plateNumber = rs.getString("plate_number") ?: "",
                    capacity = rs.getInt("capacity"),
                    model = rs.getString("model") ?: ""
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching buses: ${e.message}")
        } finally {
            rs?.close()
            stmt?.close()
            conn?.close()
        }
        list
    }

    suspend fun fetchAllDrivers(): List<Driver> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Driver>()
        var conn: Connection? = null
        var stmt: Statement? = null
        var rs: ResultSet? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            stmt = conn.createStatement()
            rs = stmt.executeQuery("SELECT * FROM drivers ORDER BY id")
            while (rs.next()) {
                val busId = rs.getInt("mapped_bus_id").let { if (rs.wasNull()) null else it }
                val routeId = rs.getInt("mapped_route_id").let { if (rs.wasNull()) null else it }
                list.add(Driver(
                    id = rs.getInt("id"),
                    name = rs.getString("name") ?: "",
                    licenseNumber = rs.getString("license_number") ?: "",
                    phoneNumber = rs.getString("phone_number") ?: "",
                    username = rs.getString("username") ?: "",
                    password = rs.getString("password") ?: "",
                    mappedBusId = busId,
                    mappedRouteId = routeId
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching drivers: ${e.message}")
        } finally {
            rs?.close()
            stmt?.close()
            conn?.close()
        }
        list
    }

    suspend fun fetchAllRoutes(): List<BusRoute> = withContext(Dispatchers.IO) {
        val list = mutableListOf<BusRoute>()
        var conn: Connection? = null
        var stmt: Statement? = null
        var rs: ResultSet? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            stmt = conn.createStatement()
            rs = stmt.executeQuery("SELECT * FROM bus_routes ORDER BY id")
            while (rs.next()) {
                list.add(BusRoute(
                    id = rs.getInt("id"),
                    routeName = rs.getString("route_name") ?: "",
                    speedLimit = rs.getDouble("speed_limit")
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching routes: ${e.message}")
        } finally {
            rs?.close()
            stmt?.close()
            conn?.close()
        }
        list
    }

    suspend fun fetchAllRouteStops(): List<RouteStop> = withContext(Dispatchers.IO) {
        val list = mutableListOf<RouteStop>()
        var conn: Connection? = null
        var stmt: Statement? = null
        var rs: ResultSet? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            stmt = conn.createStatement()
            rs = stmt.executeQuery("SELECT * FROM route_stops ORDER BY route_id, sequence_order")
            while (rs.next()) {
                list.add(RouteStop(
                    id = rs.getInt("id"),
                    routeId = rs.getInt("route_id"),
                    stopName = rs.getString("stop_name") ?: "",
                    latitude = rs.getFloat("latitude"),
                    longitude = rs.getFloat("longitude"),
                    sequenceOrder = rs.getInt("sequence_order"),
                    expectedArrivalMinutes = rs.getInt("expected_arrival_minutes")
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching route stops: ${e.message}")
        } finally {
            rs?.close()
            stmt?.close()
            conn?.close()
        }
        list
    }

    suspend fun fetchAllTrips(): List<Trip> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Trip>()
        var conn: Connection? = null
        var stmt: Statement? = null
        var rs: ResultSet? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            stmt = conn.createStatement()
            rs = stmt.executeQuery("SELECT * FROM trips ORDER BY start_time DESC")
            while (rs.next()) {
                val endTime = rs.getLong("end_time").let { if (rs.wasNull() || it == 0L) null else it }
                val isSimulation = try { rs.getBoolean("is_simulation") } catch (e: Exception) { true }
                list.add(Trip(
                    id = rs.getInt("id"),
                    driverId = rs.getInt("driver_id"),
                    busId = rs.getInt("bus_id"),
                    routeId = rs.getInt("route_id"),
                    status = rs.getString("status") ?: "ACTIVE",
                    startTime = rs.getLong("start_time"),
                    endTime = endTime,
                    currentLatitude = rs.getFloat("current_latitude"),
                    currentLongitude = rs.getFloat("current_longitude"),
                    currentSpeed = rs.getDouble("current_speed"),
                    currentStopIndex = rs.getInt("current_stop_index"),
                    isDeviated = rs.getBoolean("is_deviated"),
                    isOvertaking = rs.getBoolean("is_overtaking"),
                    isSimulation = isSimulation
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching trips: ${e.message}")
        } finally {
            rs?.close()
            stmt?.close()
            conn?.close()
        }
        list
    }

    suspend fun fetchAllViolations(): List<ViolationRecord> = withContext(Dispatchers.IO) {
        val list = mutableListOf<ViolationRecord>()
        var conn: Connection? = null
        var stmt: Statement? = null
        var rs: ResultSet? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            stmt = conn.createStatement()
            rs = stmt.executeQuery("SELECT * FROM violation_records ORDER BY timestamp DESC")
            while (rs.next()) {
                list.add(ViolationRecord(
                    id = rs.getInt("id"),
                    tripId = rs.getInt("trip_id"),
                    driverName = rs.getString("driver_name") ?: "",
                    busPlate = rs.getString("bus_plate") ?: "",
                    routeName = rs.getString("route_name") ?: "",
                    type = rs.getString("type") ?: "SPEED",
                    timestamp = rs.getLong("timestamp"),
                    details = rs.getString("details") ?: "",
                    value = rs.getDouble("value")
                ))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error fetching violations: ${e.message}")
        } finally {
            rs?.close()
            stmt?.close()
            conn?.close()
        }
        list
    }

    // --- PUSH / WRITE METHODS ---
    suspend fun pushBus(bus: Bus) = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        var ps: PreparedStatement? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            ps = conn.prepareStatement("""
                INSERT INTO buses (id, plate_number, capacity, model) 
                VALUES (?, ?, ?, ?) 
                ON CONFLICT (plate_number) 
                DO UPDATE SET capacity = EXCLUDED.capacity, model = EXCLUDED.model;
            """.trimIndent())
            ps.setInt(1, bus.id)
            ps.setString(2, bus.plateNumber)
            ps.setInt(3, bus.capacity)
            ps.setString(4, bus.model)
            ps.executeUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing bus: ${e.message}")
        } finally {
            ps?.close()
            conn?.close()
        }
    }

    suspend fun deleteBus(bus: Bus) = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        var ps: PreparedStatement? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            ps = conn.prepareStatement("DELETE FROM buses WHERE plate_number = ?")
            ps.setString(1, bus.plateNumber)
            ps.executeUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting bus from Supabase: ${e.message}")
        } finally {
            ps?.close()
            conn?.close()
        }
    }

    suspend fun pushDriver(driver: Driver) = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        var ps: PreparedStatement? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            ps = conn.prepareStatement("""
                INSERT INTO drivers (name, license_number, phone_number, username, password, mapped_bus_id, mapped_route_id) 
                VALUES (?, ?, ?, ?, ?, ?, ?) 
                ON CONFLICT (username) 
                DO UPDATE SET name = EXCLUDED.name, license_number = EXCLUDED.license_number, 
                              phone_number = EXCLUDED.phone_number, password = EXCLUDED.password, 
                              mapped_bus_id = EXCLUDED.mapped_bus_id, mapped_route_id = EXCLUDED.mapped_route_id;
            """.trimIndent())
            ps.setString(1, driver.name)
            ps.setString(2, driver.licenseNumber)
            ps.setString(3, driver.phoneNumber)
            ps.setString(4, driver.username)
            ps.setString(5, driver.password)
            if (driver.mappedBusId != null) ps.setInt(6, driver.mappedBusId) else ps.setNull(6, java.sql.Types.INTEGER)
            if (driver.mappedRouteId != null) ps.setInt(7, driver.mappedRouteId) else ps.setNull(7, java.sql.Types.INTEGER)
            ps.executeUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing driver: ${e.message}")
        } finally {
            ps?.close()
            conn?.close()
        }
    }

    suspend fun deleteDriver(driver: Driver) = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        var ps: PreparedStatement? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            ps = conn.prepareStatement("DELETE FROM drivers WHERE username = ?")
            ps.setString(1, driver.username)
            ps.executeUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting driver: ${e.message}")
        } finally {
            ps?.close()
            conn?.close()
        }
    }

    suspend fun pushRoute(route: BusRoute, stops: List<RouteStop>): Int = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        var ps: PreparedStatement? = null
        var genId = route.id
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            // Insert or update Route
            ps = conn.prepareStatement("""
                INSERT INTO bus_routes (route_name, speed_limit) 
                VALUES (?, ?) 
                ON CONFLICT (route_name) 
                DO UPDATE SET speed_limit = EXCLUDED.speed_limit
                RETURNING id;
            """.trimIndent())
            ps.setString(1, route.routeName)
            ps.setDouble(2, route.speedLimit)
            val rs = ps.executeQuery()
            if (rs.next()) {
                genId = rs.getInt(1)
            }
            rs.close()
            ps.close()

            // Delete existing stops for this route and insert fresh ones
            ps = conn.prepareStatement("DELETE FROM route_stops WHERE route_id = ?")
            ps.setInt(1, genId)
            ps.executeUpdate()
            ps.close()

            // Batch insert stops
            ps = conn.prepareStatement("""
                INSERT INTO route_stops (route_id, stop_name, latitude, longitude, sequence_order, expected_arrival_minutes) 
                VALUES (?, ?, ?, ?, ?, ?);
            """.trimIndent())
            for (stop in stops) {
                ps.setInt(1, genId)
                ps.setString(2, stop.stopName)
                ps.setFloat(3, stop.latitude)
                ps.setFloat(4, stop.longitude)
                ps.setInt(5, stop.sequenceOrder)
                ps.setInt(6, stop.expectedArrivalMinutes)
                ps.addBatch()
            }
            ps.executeBatch()
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing route and stops: ${e.message}")
        } finally {
            ps?.close()
            conn?.close()
        }
        genId
    }

    suspend fun deleteRoute(route: BusRoute) = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        var ps: PreparedStatement? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            // Delete stops first
            ps = conn.prepareStatement("DELETE FROM route_stops WHERE route_id = ?")
            ps.setInt(1, route.id)
            ps.executeUpdate()
            ps.close()

            // Delete route
            ps = conn.prepareStatement("DELETE FROM bus_routes WHERE id = ?")
            ps.setInt(1, route.id)
            ps.executeUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Error deleting route from Supabase: ${e.message}")
        } finally {
            ps?.close()
            conn?.close()
        }
    }

    suspend fun pushTrip(trip: Trip) = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        var ps: PreparedStatement? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            ps = conn.prepareStatement("""
                INSERT INTO trips (id, driver_id, bus_id, route_id, status, start_time, end_time, 
                                  current_latitude, current_longitude, current_speed, current_stop_index, 
                                  is_deviated, is_overtaking, is_simulation) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) 
                ON CONFLICT (id) 
                DO UPDATE SET status = EXCLUDED.status, end_time = EXCLUDED.end_time, 
                              current_latitude = EXCLUDED.current_latitude, current_longitude = EXCLUDED.current_longitude, 
                              current_speed = EXCLUDED.current_speed, current_stop_index = EXCLUDED.current_stop_index, 
                              is_deviated = EXCLUDED.is_deviated, is_overtaking = EXCLUDED.is_overtaking,
                              is_simulation = EXCLUDED.is_simulation;
            """.trimIndent())
            ps.setInt(1, trip.id)
            ps.setInt(2, trip.driverId)
            ps.setInt(3, trip.busId)
            ps.setInt(4, trip.routeId)
            ps.setString(5, trip.status)
            ps.setLong(6, trip.startTime)
            if (trip.endTime != null) ps.setLong(7, trip.endTime) else ps.setNull(7, java.sql.Types.BIGINT)
            ps.setFloat(8, trip.currentLatitude)
            ps.setFloat(9, trip.currentLongitude)
            ps.setDouble(10, trip.currentSpeed)
            ps.setInt(11, trip.currentStopIndex)
            ps.setBoolean(12, trip.isDeviated)
            ps.setBoolean(13, trip.isOvertaking)
            ps.setBoolean(14, trip.isSimulation)
            ps.executeUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing trip ID ${trip.id}: ${e.message}")
        } finally {
            ps?.close()
            conn?.close()
        }
    }

    suspend fun pushViolation(violation: ViolationRecord) = withContext(Dispatchers.IO) {
        var conn: Connection? = null
        var ps: PreparedStatement? = null
        try {
            conn = DriverManager.getConnection(DB_URL, DB_USER, DB_PASS)
            ps = conn.prepareStatement("""
                INSERT INTO violation_records (trip_id, driver_name, bus_plate, route_name, type, timestamp, details, value) 
                VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING;
            """.trimIndent())
            ps.setInt(1, violation.tripId)
            ps.setString(2, violation.driverName)
            ps.setString(3, violation.busPlate)
            ps.setString(4, violation.routeName)
            ps.setString(5, violation.type)
            ps.setLong(6, violation.timestamp)
            ps.setString(7, violation.details)
            ps.setDouble(8, violation.value)
            ps.executeUpdate()
        } catch (e: Exception) {
            Log.e(TAG, "Error pushing violation: ${e.message}")
        } finally {
            ps?.close()
            conn?.close()
        }
    }
}
