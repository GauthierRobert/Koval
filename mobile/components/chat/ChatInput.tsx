import React, { useState } from 'react';
import {
  View,
  TextInput,
  TouchableOpacity,
  StyleSheet,
  Animated,
} from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '../../constants/theme';

interface ChatInputProps {
  onSend: (text: string) => void;
  disabled?: boolean;
  /** Pre-fills the input (used by suggestion chips). */
  prefill?: string;
}

export function ChatInput({ onSend, disabled, prefill }: ChatInputProps) {
  const [text, setText] = useState(prefill ?? '');

  // Sync prefill when it changes externally
  React.useEffect(() => {
    if (prefill) setText(prefill);
  }, [prefill]);

  const canSend = text.trim().length > 0 && !disabled;

  function handleSend() {
    const trimmed = text.trim();
    if (!trimmed || disabled) return;
    onSend(trimmed);
    setText('');
  }

  return (
    <View style={styles.wrapper}>
      <View style={styles.container}>
        <TextInput
          style={styles.input}
          value={text}
          onChangeText={setText}
          placeholder="Ask your AI coach…"
          placeholderTextColor={theme.colors.textMuted}
          multiline
          maxLength={2000}
          editable={!disabled}
          selectionColor={theme.colors.primary}
        />
        <TouchableOpacity
          style={[styles.sendBtn, canSend && styles.sendBtnActive]}
          onPress={handleSend}
          disabled={!canSend}
          activeOpacity={0.8}
        >
          {disabled ? (
            <View style={styles.stopDot} />
          ) : (
            <Ionicons
              name="arrow-up"
              size={18}
              color={canSend ? '#fff' : theme.colors.textMuted}
            />
          )}
        </TouchableOpacity>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  wrapper: {
    paddingHorizontal: theme.spacing.md,
    paddingVertical: theme.spacing.sm,
    paddingBottom: theme.spacing.md,
    backgroundColor: theme.colors.background,
    borderTopWidth: 1,
    borderTopColor: theme.colors.border,
  },
  container: {
    flexDirection: 'row',
    alignItems: 'flex-end',
    backgroundColor: theme.colors.surface,
    borderRadius: theme.radius.xl,
    borderWidth: 1,
    borderColor: theme.colors.borderStrong,
    paddingLeft: theme.spacing.md,
    paddingRight: 6,
    paddingVertical: 6,
    gap: theme.spacing.xs,
  },
  input: {
    flex: 1,
    color: theme.colors.text,
    fontSize: theme.fontSize.md,
    lineHeight: 22,
    maxHeight: 110,
    paddingTop: 6,
    paddingBottom: 6,
  },
  sendBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: theme.colors.surfaceElevated,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  sendBtnActive: {
    backgroundColor: theme.colors.primary,
    ...theme.shadow.glow,
  },
  stopDot: {
    width: 10,
    height: 10,
    borderRadius: 2,
    backgroundColor: '#fff',
  },
});
