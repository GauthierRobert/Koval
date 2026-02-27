import React, {useEffect, useRef, useState} from 'react';
import {
    Alert,
    FlatList,
    KeyboardAvoidingView,
    Modal,
    Platform,
    SafeAreaView,
    StyleSheet,
    Text,
    TouchableOpacity,
    View,
} from 'react-native';
import {StatusBar} from 'expo-status-bar';
import {Ionicons} from '@expo/vector-icons';
import {useStreamingChat} from '../../hooks/useStreamingChat';
import {MessageBubble} from '../../components/chat/MessageBubble';
import {ChatInput} from '../../components/chat/ChatInput';
import {theme} from '../../constants/theme';

const SUGGESTIONS = [
  'Create a 60-min FTP builder workout',
  'Plan my training for this week',
  'What should I do for recovery today?',
];

export default function ChatScreen() {
  const {
    messages,
    histories,
    activeChatId,
    isStreaming,
    error,
    sendMessage,
    loadHistory,
    loadHistories,
    newChat,
    removeHistory,
  } = useStreamingChat();

  const [showHistories, setShowHistories] = useState(false);
  const [prefill, setPrefill] = useState<string | undefined>();
  const listRef = useRef<FlatList>(null);

  useEffect(() => {
    loadHistories();
  }, []);

  useEffect(() => {
    if (messages.length > 0) {
      setTimeout(() => listRef.current?.scrollToEnd({ animated: true }), 80);
    }
  }, [messages.length]);

  function handleSelectHistory(id: string) {
    setShowHistories(false);
    loadHistory(id);
  }

  function handleDeleteHistory(id: string) {
    Alert.alert('Delete conversation?', 'This cannot be undone.', [
      { text: 'Cancel', style: 'cancel' },
      { text: 'Delete', style: 'destructive', onPress: () => removeHistory(id) },
    ]);
  }

  function handleSuggestion(text: string) {
    setPrefill(text);
  }

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar style="light" />

      {/* ── Sub-header ── */}
      <View style={styles.subHeader}>
        <TouchableOpacity
          style={styles.historyBtn}
          onPress={() => setShowHistories(true)}
          activeOpacity={0.75}
        >
          <Ionicons name="time-outline" size={15} color={theme.colors.textSecondary} />
          <Text style={styles.historyBtnText} numberOfLines={1}>
            {activeChatId ? 'Conversation loaded' : 'New conversation'}
          </Text>
          <Ionicons name="chevron-down" size={14} color={theme.colors.textMuted} style={styles.chevron} />
        </TouchableOpacity>

        <TouchableOpacity style={styles.newBtn} onPress={newChat} activeOpacity={0.75}>
          <Ionicons name="create-outline" size={19} color={theme.colors.primary} />
        </TouchableOpacity>
      </View>

      {/* ── Messages ── */}
      <KeyboardAvoidingView
        style={{ flex: 1 }}
        behavior={Platform.OS === 'ios' ? 'padding' : undefined}
        keyboardVerticalOffset={90}
      >
        <FlatList
          ref={listRef}
          data={messages}
          keyExtractor={m => m.id}
          renderItem={({ item }) => (
            <MessageBubble role={item.role} content={item.content} streaming={item.streaming} />
          )}
          contentContainerStyle={styles.messageList}
          ListEmptyComponent={
            <EmptyState onSuggest={handleSuggestion} isStreaming={isStreaming} />
          }
        />

        {error ? <Text style={styles.errorText}>{error}</Text> : null}

        <ChatInput onSend={sendMessage} disabled={isStreaming} prefill={prefill} />
      </KeyboardAvoidingView>

      {/* ── History modal ── */}
      <Modal visible={showHistories} animationType="slide" presentationStyle="pageSheet">
        <SafeAreaView style={styles.modal}>
          <View style={styles.modalHeader}>
            <Text style={styles.modalTitle}>Conversations</Text>
            <TouchableOpacity onPress={() => setShowHistories(false)} style={styles.closeBtn}>
              <Ionicons name="close" size={22} color={theme.colors.text} />
            </TouchableOpacity>
          </View>

          <FlatList
            data={histories}
            keyExtractor={h => h.id}
            renderItem={({ item }) => (
              <TouchableOpacity
                style={styles.historyRow}
                onPress={() => handleSelectHistory(item.id)}
                activeOpacity={0.7}
              >
                <View style={styles.historyIconWrap}>
                  <Ionicons name="chatbubble-ellipses-outline" size={17} color={theme.colors.primary} />
                </View>
                <Text style={styles.historyTitle} numberOfLines={1}>
                  {item.title ?? 'Conversation'}
                </Text>
                <TouchableOpacity
                  onPress={() => handleDeleteHistory(item.id)}
                  hitSlop={{ top: 8, bottom: 8, left: 8, right: 8 }}
                >
                  <Ionicons name="trash-outline" size={17} color={theme.colors.danger} />
                </TouchableOpacity>
              </TouchableOpacity>
            )}
            ListEmptyComponent={
              <Text style={styles.emptyModalText}>No conversations yet.</Text>
            }
          />
        </SafeAreaView>
      </Modal>
    </SafeAreaView>
  );
}

function EmptyState({
  onSuggest,
  isStreaming,
}: {
  onSuggest: (text: string) => void;
  isStreaming: boolean;
}) {
  return (
    <View style={emptyStyles.container}>
      {/* Icon with glow */}
      <View style={emptyStyles.iconWrap}>
        <Ionicons name="sparkles" size={32} color={theme.colors.primary} />
      </View>

      <Text style={emptyStyles.title}>AI Coach</Text>
      <Text style={emptyStyles.subtitle}>
        Generate workouts, plan your week, or ask anything about your training.
      </Text>

      {/* Suggestion chips */}
      <View style={emptyStyles.chips}>
        {SUGGESTIONS.map(s => (
          <TouchableOpacity
            key={s}
            style={emptyStyles.chip}
            onPress={() => !isStreaming && onSuggest(s)}
            activeOpacity={0.7}
            disabled={isStreaming}
          >
            <Ionicons name="arrow-forward-circle-outline" size={15} color={theme.colors.primary} />
            <Text style={emptyStyles.chipText}>{s}</Text>
          </TouchableOpacity>
        ))}
      </View>
    </View>
  );
}

const emptyStyles = StyleSheet.create({
  container: {
    alignItems: 'center',
    paddingTop: 60,
    paddingHorizontal: theme.spacing.xl,
    gap: theme.spacing.md,
  },
  iconWrap: {
    width: 68,
    height: 68,
    borderRadius: 34,
    backgroundColor: theme.colors.primaryMuted,
    borderWidth: 1,
    borderColor: theme.colors.primary + '33',
    alignItems: 'center',
    justifyContent: 'center',
    ...theme.shadow.glow,
    marginBottom: theme.spacing.sm,
  },
  title: {
    color: theme.colors.text,
    fontSize: theme.fontSize.xxl,
    fontWeight: '700',
  },
  subtitle: {
    color: theme.colors.textSecondary,
    fontSize: theme.fontSize.md,
    textAlign: 'center',
    lineHeight: 22,
  },
  chips: {
    width: '100%',
    gap: theme.spacing.sm,
    marginTop: theme.spacing.sm,
  },
  chip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: theme.spacing.sm,
    backgroundColor: theme.colors.surface,
    borderRadius: theme.radius.md,
    borderWidth: 1,
    borderColor: theme.colors.border,
    paddingHorizontal: theme.spacing.md,
    paddingVertical: 12,
  },
  chipText: {
    color: theme.colors.text,
    fontSize: theme.fontSize.sm,
    flex: 1,
  },
});

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  // Sub-header
  subHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: theme.spacing.md,
    paddingVertical: theme.spacing.sm,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
    gap: theme.spacing.sm,
  },
  historyBtn: {
    flex: 1,
    flexDirection: 'row',
    alignItems: 'center',
    gap: 6,
    backgroundColor: theme.colors.surface,
    borderRadius: theme.radius.full,
    borderWidth: 1,
    borderColor: theme.colors.border,
    paddingHorizontal: theme.spacing.md,
    paddingVertical: 8,
  },
  historyBtnText: {
    color: theme.colors.textSecondary,
    fontSize: theme.fontSize.sm,
    flex: 1,
  },
  chevron: {
    marginLeft: 'auto',
  },
  newBtn: {
    width: 38,
    height: 38,
    borderRadius: 19,
    backgroundColor: theme.colors.surface,
    borderWidth: 1,
    borderColor: theme.colors.border,
    alignItems: 'center',
    justifyContent: 'center',
  },
  // Message list
  messageList: {
    flexGrow: 1,
    paddingVertical: theme.spacing.md,
  },
  errorText: {
    color: theme.colors.danger,
    fontSize: theme.fontSize.sm,
    textAlign: 'center',
    paddingHorizontal: theme.spacing.md,
    paddingBottom: 6,
  },
  // History modal
  modal: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  modalHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: theme.spacing.md,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
  },
  modalTitle: {
    color: theme.colors.text,
    fontSize: theme.fontSize.xl,
    fontWeight: '700',
  },
  closeBtn: {
    width: 34,
    height: 34,
    borderRadius: 17,
    backgroundColor: theme.colors.surfaceElevated,
    alignItems: 'center',
    justifyContent: 'center',
  },
  historyRow: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: theme.spacing.md,
    paddingVertical: 14,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
    gap: theme.spacing.sm,
  },
  historyIconWrap: {
    width: 34,
    height: 34,
    borderRadius: 10,
    backgroundColor: theme.colors.primaryMuted,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  historyTitle: {
    color: theme.colors.text,
    fontSize: theme.fontSize.md,
    flex: 1,
  },
  emptyModalText: {
    color: theme.colors.textSecondary,
    textAlign: 'center',
    marginTop: 48,
    fontSize: theme.fontSize.md,
  },
});
