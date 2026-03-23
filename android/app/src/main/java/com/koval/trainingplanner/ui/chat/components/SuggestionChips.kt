package com.koval.trainingplanner.ui.chat.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.koval.trainingplanner.ui.theme.Border
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.TextSecondary

private val suggestions = listOf(
    "Create a 60-min FTP builder workout",
    "Plan my training for this week",
    "What should I do for recovery today?",
)

@Composable
fun SuggestionChips(
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        suggestions.forEach { suggestion ->
            OutlinedButton(
                onClick = { onSuggestionClick(suggestion) },
                shape = RoundedCornerShape(14.dp),
                border = ButtonDefaults.outlinedButtonBorder(true).copy(
                    brush = androidx.compose.ui.graphics.SolidColor(Border)
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = suggestion,
                    color = TextSecondary,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
