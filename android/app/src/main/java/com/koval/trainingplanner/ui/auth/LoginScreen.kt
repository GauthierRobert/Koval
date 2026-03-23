package com.koval.trainingplanner.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koval.trainingplanner.BuildConfig
import com.koval.trainingplanner.domain.model.UserRole
import com.koval.trainingplanner.ui.theme.Background
import com.koval.trainingplanner.ui.theme.Border
import com.koval.trainingplanner.ui.theme.Danger
import com.koval.trainingplanner.ui.theme.Google
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.PrimaryMuted
import com.koval.trainingplanner.ui.theme.Strava
import com.koval.trainingplanner.ui.theme.Surface
import com.koval.trainingplanner.ui.theme.SurfaceElevated
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(viewModel: LoginViewModel) {
    val state by viewModel.authState.collectAsState()
    val context = LocalContext.current
    var devUserId by remember { mutableStateOf("dev-user-1") }
    var devRole by remember { mutableStateOf(UserRole.COACH) }
    var roleDropdownExpanded by remember { mutableStateOf(false) }

    if (state.isLoading && state.user == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Primary)
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Background),
    ) {
        // Top decorative glow
        Box(
            modifier = Modifier
                .size(300.dp)
                .align(Alignment.TopEnd)
                .background(
                    Brush.radialGradient(
                        colors = listOf(Primary.copy(alpha = 0.08f), Color.Transparent),
                        radius = 400f,
                    )
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            // App icon
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(PrimaryMuted),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.DirectionsBike,
                    contentDescription = null,
                    tint = Primary,
                    modifier = Modifier.size(40.dp),
                )
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "KOVAL",
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary,
                letterSpacing = 4.sp,
            )

            Text(
                text = "AI-powered training planner",
                fontSize = 15.sp,
                color = TextSecondary,
                modifier = Modifier.padding(top = 8.dp),
            )

            Spacer(Modifier.height(48.dp))

            // Strava button
            Button(
                onClick = { viewModel.loginWithStrava(context) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Strava),
                shape = RoundedCornerShape(14.dp),
                enabled = !state.isLoading,
            ) {
                Text("Connect with Strava", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(12.dp))

            // Google button
            Button(
                onClick = { viewModel.loginWithGoogle(context) },
                modifier = Modifier.fillMaxWidth().height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Google),
                shape = RoundedCornerShape(14.dp),
                enabled = !state.isLoading,
            ) {
                Text("Continue with Google", fontWeight = FontWeight.SemiBold)
            }

            // Error
            state.error?.let {
                Text(
                    text = it,
                    color = Danger,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }

            // Dev login (debug only)
            if (BuildConfig.DEV_LOGIN_ENABLED) {
                Spacer(Modifier.height(32.dp))

                Text(
                    text = "DEV LOGIN",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary,
                    letterSpacing = 2.sp,
                )

                Spacer(Modifier.height(12.dp))

                val fieldColors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = Border,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = Primary,
                    focusedContainerColor = Surface,
                    unfocusedContainerColor = Surface,
                )

                OutlinedTextField(
                    value = devUserId,
                    onValueChange = { devUserId = it },
                    label = { Text("User ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors,
                    shape = RoundedCornerShape(14.dp),
                    leadingIcon = {
                        Icon(Icons.Filled.Person, null, tint = TextSecondary)
                    },
                )

                Spacer(Modifier.height(8.dp))

                ExposedDropdownMenuBox(
                    expanded = roleDropdownExpanded,
                    onExpandedChange = { roleDropdownExpanded = it },
                ) {
                    OutlinedTextField(
                        value = devRole.name,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Role") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                        colors = fieldColors,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(roleDropdownExpanded) },
                    )
                    ExposedDropdownMenu(
                        expanded = roleDropdownExpanded,
                        onDismissRequest = { roleDropdownExpanded = false },
                        containerColor = SurfaceElevated,
                    ) {
                        UserRole.entries.forEach { role ->
                            DropdownMenuItem(
                                text = { Text(role.name, color = TextPrimary) },
                                onClick = {
                                    devRole = role
                                    roleDropdownExpanded = false
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                Button(
                    onClick = { viewModel.devLogin(devUserId.trim(), devRole) },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SurfaceElevated),
                    shape = RoundedCornerShape(14.dp),
                    enabled = devUserId.isNotBlank() && !state.isLoading,
                ) {
                    Text("Dev Login", color = Primary, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
