package com.koval.trainingplanner.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.koval.trainingplanner.ui.theme.Background
import com.koval.trainingplanner.ui.theme.GlassBorder
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.Surface
import com.koval.trainingplanner.ui.theme.TextMuted
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.TextSecondary

@Composable
fun CguDialog(
    isLoading: Boolean,
    onAccept: () -> Unit,
) {
    Dialog(
        onDismissRequest = { /* Cannot dismiss without accepting */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Surface, RoundedCornerShape(16.dp))
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "Terms of Service",
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Please accept the Terms of Service to continue",
                color = TextMuted,
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .background(Background, RoundedCornerShape(8.dp))
                    .padding(16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                CguSection("1. Acceptance",
                    "By creating an account and using Koval Training (\"the Service\"), you agree to these Terms of Service.")
                CguSection("2. Description",
                    "Koval Training is a training planning and analysis platform for cycling, running, swimming, and triathlon.")
                CguSection("3. User Accounts",
                    "You are responsible for maintaining the confidentiality of your account credentials.")
                CguSection("4. User Data",
                    "We collect and process your training data, performance metrics, and profile information to provide the Service. Your data is stored securely and is not shared with third parties without your consent.")
                CguSection("5. Acceptable Use",
                    "You agree not to misuse the Service, attempt unauthorized access, or use it for any illegal purpose.")
                CguSection("6. Limitation of Liability",
                    "The Service is provided \"as is\" without warranties. Always consult a medical professional before starting any training program.")
                CguSection("7. Modifications",
                    "We reserve the right to modify these terms. Continued use constitutes acceptance of updated terms.")
                CguSection("8. Contact",
                    "For questions, contact us at gauthier.robert2@gmail.com")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Primary,
                    disabledContainerColor = Primary.copy(alpha = 0.3f),
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Background,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text("Accept & Continue", color = Background, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun CguSection(title: String, content: String) {
    Spacer(modifier = Modifier.height(8.dp))
    Text(
        text = title,
        color = TextPrimary,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
    )
    Spacer(modifier = Modifier.height(2.dp))
    Text(
        text = content,
        color = TextSecondary,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )
}
