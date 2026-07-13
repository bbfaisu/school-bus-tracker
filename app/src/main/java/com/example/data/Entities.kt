package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "buses")
data class Bus(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val plateNumber: String,
    val capacity: Int,
    val model: String
)

@Entity(tableName = "drivers")
data class Driver(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val licenseNumber: String,
    val phoneNumber: String,
    val username: String,
    val password: String,
    val mappedBusId: Int? = null,
    val mappedRouteId: Int? = null
)

@Entity(tableName = "bus_routes")
data class BusRoute(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val routeName: String,
    val speedLimit: Double = 40.0 // km/h
)

@Entity(tableName = "route_stops")
data class RouteStop(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val routeId: Int,
    val stopName: String,
    val latitude: Float,
    val longitude: Float,
    val sequenceOrder: Int,
    val expectedArrivalMinutes: Int = 0 // Offset from start of trip
)

@Entity(tableName = "trips")
data class Trip(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val driverId: Int,
    val busId: Int,
    val routeId: Int,
    val status: String, // "ACTIVE", "COMPLETED"
    val startTime: Long,
    val endTime: Long? = null,
    val currentLatitude: Float,
    val currentLongitude: Float,
    val currentSpeed: Double,
    val currentStopIndex: Int = 0,
    val isDeviated: Boolean = false,
    val isOvertaking: Boolean = false
)

@Entity(tableName = "violation_records")
data class ViolationRecord(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val tripId: Int,
    val driverName: String,
    val busPlate: String,
    val routeName: String,
    val type: String, // "SPEED", "ROUTE_DEVIATION", "OVERTAKING", "PARENT_LATE"
    val timestamp: Long = System.currentTimeMillis(),
    val details: String,
    val value: Double = 0.0 // Value of infraction (e.g. speed, or parent late minutes)
)
