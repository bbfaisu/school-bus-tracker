package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.*
import com.example.viewmodel.TrackerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RtoScreen(
    viewModel: TrackerViewModel,
    modifier: Modifier = Modifier
) {
    var selectedRtoTab by remember { mutableStateOf(0) }
    val rtoTabs = listOf("Safety Spotlight", "Deep Reports")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
    ) {
        TabRow(
            selectedTabIndex = selectedRtoTab,
            containerColor = Color(0xFF1E1E1E),
            contentColor = Color(0xFF00E676), // Green RTO accent
            modifier = Modifier.fillMaxWidth()
        ) {
            rtoTabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedRtoTab == index,
                    onClick = { selectedRtoTab = index },
                    text = { Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .weight(1f)
        ) {
            when (selectedRtoTab) {
                0 -> RtoSpotlightContent(viewModel)
                1 -> ReportsScreen(viewModel, isRtoView = true)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RtoSpotlightContent(
    viewModel: TrackerViewModel,
    modifier: Modifier = Modifier
) {
    val violations by viewModel.allViolations.collectAsState()
    val buses by viewModel.allBuses.collectAsState()

    // Calculate RTO Fleet metrics dynamically
    val speedViolations = violations.count { it.type == "SPEED" }
    val routeViolations = violations.count { it.type == "ROUTE_DEVIATION" }
    val overtakingViolations = violations.count { it.type == "OVERTAKING" }
    val totalViolationsCount = speedViolations + routeViolations + overtakingViolations

    // Simple compliance score: Base 100%, subtract 5% per major violation (clamped to 50%)
    val complianceScore = (100 - totalViolationsCount * 6).coerceAtLeast(45)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
            .verticalScroll(androidx.compose.foundation.rememberScrollState())
    ) {
        // RTO Title Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.VerifiedUser,
                contentDescription = "RTO Kashmir",
                tint = Color(0xFF00E676),
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column {
                Text(
                    text = "RTO Kashmir Transport Portal",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                )
                Text(
                    text = "Department of Motor Vehicles • Safety Audit Console",
                    color = Color.Gray,
                    fontSize = 11.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Fleet Compliance Index Dashboard Card
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "KASHMIR SCHOOL FLEET COMPLIANCE SCORE",
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 11.sp,
                    letterSpacing = 1.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "$complianceScore%",
                            color = when {
                                complianceScore >= 85 -> Color(0xFF00E676)
                                complianceScore >= 70 -> Color(0xFFFFB300)
                                else -> Color(0xFFE53935)
                            },
                            fontWeight = FontWeight.Black,
                            fontSize = 42.sp,
                            lineHeight = 46.sp
                        )
                        Text(
                            text = when {
                                complianceScore >= 85 -> "EXCELLENT RATING"
                                complianceScore >= 70 -> "SATISFACTORY RATING"
                                else -> "CRITICAL SAFETY AUDIT REQ"
                            },
                            color = Color.LightGray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }

                    // Circular Progress representation
                    Box(
                        modifier = Modifier.size(80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { complianceScore.toFloat() / 100f },
                            modifier = Modifier.fillMaxSize(),
                            color = if (complianceScore >= 85) Color(0xFF00E676) else if (complianceScore >= 70) Color(0xFFFFB300) else Color(0xFFE53935),
                            strokeWidth = 8.dp,
                            trackColor = Color.DarkGray
                        )
                        Text(
                            text = "INDEX",
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold,
                            fontSize = 10.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Divider(color = Color.DarkGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(12.dp))

                // Stats breakdown
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Active Fleet", color = Color.Gray, fontSize = 11.sp)
                        Text("${buses.size} Buses", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Speed Alarms", color = Color.Gray, fontSize = 11.sp)
                        Text("$speedViolations Logged", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Route Devs", color = Color.Gray, fontSize = 11.sp)
                        Text("$routeViolations Logged", color = Color(0xFFF48FB1), fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // High Risk Flags Title
        Text(
            text = "High-Risk Spotlight Checks",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        // Process buses and identify any with more than 1 violation
        val highRiskBuses = buses.filter { bus ->
            violations.any { it.busPlate == bus.plateNumber }
        }

        if (highRiskBuses.isEmpty()) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Safe", tint = Color(0xFF00E676))
                    Spacer(modifier = Modifier.width(12.dp))
                    Text("Zero Vehicles Flagged as High Risk", color = Color.LightGray, fontSize = 13.sp)
                }
            }
        } else {
            highRiskBuses.forEach { bus ->
                val busViolations = violations.filter { it.busPlate == bus.plateNumber }
                val speedingCount = busViolations.count { it.type == "SPEED" }
                val deviationCount = busViolations.count { it.type == "ROUTE_DEVIATION" }
                val overtakeCount = busViolations.count { it.type == "OVERTAKING" }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1B1B)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE53935).copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.GppBad, contentDescription = "High Risk", tint = Color(0xFFE53935), modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = "ALERT ID: ${bus.plateNumber}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = bus.model, color = Color.Gray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Violations: $speedingCount Speeding • $deviationCount Route Deviations • $overtakeCount Overtaking",
                                color = Color(0xFFEF9A9A),
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Mandatory fitness checklist
        Text(
            text = "Regulatory Documents & Governor Checks",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 15.sp,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E1E)),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                buses.forEachIndexed { idx, bus ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(bus.plateNumber, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Text(bus.model, color = Color.Gray, fontSize = 11.sp)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            val active = idx == 0
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (active) Color(0xFF00E676).copy(alpha = 0.15f) else Color(0xFFFFB300).copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text(
                                    text = if (active) "Governor Calibrated" else "Calibration Pending",
                                    color = if (active) Color(0xFF00E676) else Color(0xFFFFB300),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                text = if (active) "Fitness OK (Expires Jul '27)" else "Fitness Expiring Next Month",
                                color = Color.Gray,
                                fontSize = 10.sp,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    if (idx < buses.size - 1) {
                        HorizontalDivider(color = Color.DarkGray, thickness = 0.5.dp, modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }
        }
    }
}
