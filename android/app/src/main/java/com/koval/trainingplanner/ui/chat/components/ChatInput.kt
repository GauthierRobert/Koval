package com.koval.trainingplanner.ui.chat.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.koval.trainingplanner.ui.theme.Border
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.Surface
import com.koval.trainingplanner.ui.theme.TextMuted
import com.koval.trainingplanner.ui.theme.TextPrimary

@Composable
fun ChatInput(
    onSend: (String) -> Unit,
    isDisabled: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var text by remember { mutableStateOf("") }
    val canSend = text.isNotBlank() && !isDisabled

    Row(
        modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { if (it.length <= 2000) text = it },
            modifier = Modifier.weight(1f).heightIn(max = 110.dp),
            placeholder = { Text("Ask your AI assistant\u2026", color = TextMuted) },
            shape = RoundedCornerShape(22.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = Border,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary,
                cursorColor = Primary,
                focusedContainerColor = Surface,
                unfocusedContainerColor = Surface,
            ),
            maxLines = 5,
            enabled = !isDisabled,
        )

        IconButton(
            onClick = {
                if (canSend) {
                    onSend(text)
                    text = ""
                }
            },
            enabled = canSend,
            modifier = Modifier
                .padding(start = 8.dp)
                .size(48.dp),
            colors = IconButtonDefaults.iconButtonColors(
                containerColor = if (canSend) Primary else TextMuted.copy(alpha = 0.3f),
            ),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (canSend) TextPrimary else TextMuted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
