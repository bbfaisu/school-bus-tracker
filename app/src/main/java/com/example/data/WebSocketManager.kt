package com.example.data

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.*
import org.json.JSONObject
import java.util.concurrent.TimeUnit

object WebSocketManager {
    private const val TAG = "WebSocketManager"
    
    // Using a highly reliable public secure WebSocket echo endpoint
    private const val WS_URL = "wss://ws.postman-echo.com/raw"

    private val client = OkHttpClient.Builder()
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .connectTimeout(10, TimeUnit.SECONDS)
        .build()

    private var webSocket: WebSocket? = null
    
    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    private val _incomingTelemetry = MutableStateFlow<String?>(null)
    val incomingTelemetry: StateFlow<String?> = _incomingTelemetry.asStateFlow()

    private var reconnectScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var isConnecting = false

    fun connect() {
        if (_isConnected.value || isConnecting) {
            Log.d(TAG, "Already connected or connection in progress.")
            return
        }

        isConnecting = true
        Log.d(TAG, "Connecting to WebSocket: $WS_URL")
        val request = Request.Builder().url(WS_URL).build()
        
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket Connection Open!")
                _isConnected.value = true
                isConnecting = false
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.d(TAG, "WebSocket RX message received: $text")
                _incomingTelemetry.value = text
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closing: $code / $reason")
                _isConnected.value = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "WebSocket Closed: $code / $reason")
                _isConnected.value = false
                this@WebSocketManager.webSocket = null
                isConnecting = false
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "WebSocket Failure: ${t.message}")
                _isConnected.value = false
                this@WebSocketManager.webSocket = null
                isConnecting = false
                
                // Attempt automatic reconnection after 4 seconds
                reconnectScope.launch {
                    delay(4000)
                    Log.d(TAG, "Retrying WebSocket connection...")
                    connect()
                }
            }
        })
    }

    fun disconnect() {
        reconnectScope.coroutineContext.cancelChildren()
        webSocket?.close(1000, "User requested disconnect")
        webSocket = null
        _isConnected.value = false
        isConnecting = false
        _incomingTelemetry.value = null
        Log.d(TAG, "WebSocket Disconnected and cleaned up.")
    }

    fun sendTelemetry(tripId: Int, lat: Double, lng: Double, speed: Double, isDeviated: Boolean) {
        val socket = webSocket
        if (socket != null && _isConnected.value) {
            try {
                val json = JSONObject().apply {
                    put("tripId", tripId)
                    put("latitude", lat)
                    put("longitude", lng)
                    put("speed", speed)
                    put("isDeviated", isDeviated)
                    put("timestamp", System.currentTimeMillis())
                }
                val payload = json.toString()
                Log.d(TAG, "WebSocket TX telemetry packet: $payload")
                socket.send(payload)
            } catch (e: Exception) {
                Log.e(TAG, "Error packaging/sending WebSocket telemetry: ${e.message}")
            }
        } else {
            Log.w(TAG, "WebSocket offline, buffering/skipping frame.")
        }
    }
}
