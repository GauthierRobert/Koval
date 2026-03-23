package com.koval.trainingplanner.ui.chat

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.koval.trainingplanner.ui.chat.components.ChatInput
import com.koval.trainingplanner.ui.chat.components.MessageBubble
import com.koval.trainingplanner.ui.chat.components.SuggestionChips
import com.koval.trainingplanner.ui.theme.Background
import com.koval.trainingplanner.ui.theme.Border
import com.koval.trainingplanner.ui.theme.Danger
import com.koval.trainingplanner.ui.theme.Primary
import com.koval.trainingplanner.ui.theme.PrimaryMuted
import com.koval.trainingplanner.ui.theme.SurfaceElevated
import com.koval.trainingplanner.ui.theme.TextMuted
import com.koval.trainingplanner.ui.theme.TextPrimary
import com.koval.trainingplanner.ui.theme.TextSecondary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    var showHistory by remember { mutableStateOf(false) }

    // Auto-scroll to bottom on new messages
    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                viewModel.loadHistories()
                showHistory = true
            }) {
                Icon(Icons.Filled.History, "History", tint = TextSecondary)
            }
            Text(
                text = if (state.activeChatId != null) "Conversation loaded" else "New conversation",
                color = TextSecondary,
                fontSize = 13.sp,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
            )
            IconButton(onClick = { viewModel.newChat() }) {
                Icon(Icons.Filled.Add, "New chat", tint = Primary)
            }
        }

        HorizontalDivider(color = Border, thickness = 0.5.dp)

        // Messages or empty state
        Box(modifier = Modifier.weight(1f)) {
            if (state.messages.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = PrimaryMuted,
                        modifier = Modifier.size(64.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.AutoAwesome,
                                null,
                                tint = Primary,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("AI Assistant", color = TextPrimary, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "Create workouts, plan training, and get coaching advice",
                        color = TextSecondary,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                    SuggestionChips(onSuggestionClick = { viewModel.sendMessage(it) })
                }
            } else {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize().padding(vertical = 8.dp),
                ) {
                    items(state.messages, key = { it.id }) { message ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = if (message.role == "user") Arrangement.End else Arrangement.Start,
                        ) {
                            MessageBubble(
                                role = message.role,
                                content = message.content,
                                isStreaming = message.isStreaming,
                            )
                        }
                    }
                }
            }
        }

        // Error
        state.error?.let {
            Text(
                text = it,
                color = Danger,
                fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp),
                maxLines = 2,
            )
        }

        HorizontalDivider(color = Border, thickness = 0.5.dp)

        // Input
        ChatInput(
            onSend = { viewModel.sendMessage(it) },
            isDisabled = state.isStreaming,
        )
    }

    // History bottom sheet
    if (showHistory) {
        ModalBottomSheet(
            onDismissRequest = { showHistory = false },
            sheetState = rememberModalBottomSheetState(),
            containerColor = SurfaceElevated,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Conversations",
                    color = TextPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
                if (state.histories.isEmpty()) {
                    Text(
                        "No conversations yet.",
                        color = TextMuted,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
                        textAlign = TextAlign.Center,
                    )
                } else {
                    state.histories.forEach { history ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                Text(
                                    text = history.title ?: "Conversation",
                                    color = TextPrimary,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            IconButton(
                                onClick = {
                                    viewModel.loadHistory(history.id)
                                    showHistory = false
                                },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Filled.History, "Load", tint = Primary, modifier = Modifier.size(18.dp))
                            }
                            Spacer(Modifier.width(4.dp))
                            IconButton(
                                onClick = { viewModel.deleteHistory(history.id) },
                                modifier = Modifier.size(32.dp),
                            ) {
                                Icon(Icons.Filled.Delete, "Delete", tint = Danger, modifier = Modifier.size(18.dp))
                            }
                        }
                        HorizontalDivider(color = Border, thickness = 0.5.dp)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }
        }
    }
}
