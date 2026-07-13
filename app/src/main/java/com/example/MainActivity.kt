package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.AdminScreen
import com.example.ui.screens.DriverScreen
import com.example.ui.screens.ParentScreen
import com.example.ui.screens.RtoScreen
import com.example.ui.theme.MyApplicationTheme
import com.example.viewmodel.TrackerViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val viewModel: TrackerViewModel = viewModel()
                MainAppShell(viewModel)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainAppShell(viewModel: TrackerViewModel) {
    val currentRole by viewModel.currentRole.collectAsState()
    val activeTripId by viewModel.activeTripId.collectAsState()

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212)),
        topBar = {
            Column(
                modifier = Modifier
                    .background(Color(0xFF1E1E1E))
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                // Main Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFFFF9800)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBus,
                                contentDescription = "Bus Logo",
                                tint = Color.Black,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Text(
                                text = "School Bus Tracking",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Text(
                                text = "Kashmir Safety Initiative",
                                color = Color(0xFFFF9800),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                letterSpacing = 0.5.sp
                            )
                        }
                    }

                    // Simulated live status indicator
                    if (activeTripId != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF1B5E20))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.Green)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "LIVE SIM",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF333333))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color.Gray)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "IDLE",
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Segmented Selector for Role Switcher
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF121212))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val roles = listOf("Admin", "Driver", "Parent", "RTO")
                    roles.forEach { role ->
                        val isSelected = currentRole == role
                        val tintColor = when (role) {
                            "Admin" -> Color(0xFFFF9800)
                            "Driver" -> Color(0xFF2196F3)
                            "Parent" -> Color(0xFF4CAF50)
                            "RTO" -> Color(0xFFE91E63)
                            else -> Color.White
                        }

                        Button(
                            onClick = { viewModel.switchRole(role) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSelected) tintColor.copy(alpha = 0.2f) else Color.Transparent,
                                contentColor = if (isSelected) tintColor else Color.Gray
                            ),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(0.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("role_tab_$role")
                        ) {
                            Text(
                                text = role,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF121212))
        ) {
            when (currentRole) {
                "Admin" -> AdminScreen(viewModel = viewModel)
                "Driver" -> DriverScreen(viewModel = viewModel)
                "Parent" -> ParentScreen(viewModel = viewModel)
                "RTO" -> RtoScreen(viewModel = viewModel)
            }
        }
    }
}
