import React, { useEffect, useRef } from 'react';
import { View, Text, StyleSheet, Animated } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '../../constants/theme';

interface MessageBubbleProps {
  role: 'user' | 'assistant';
  content: string;
  streaming?: boolean;
}

/** Three pulsing dots shown while the assistant is thinking. */
function ThinkingDots() {
  const dots = [useRef(new Animated.Value(0.3)).current,
                useRef(new Animated.Value(0.3)).current,
                useRef(new Animated.Value(0.3)).current];

  useEffect(() => {
    const anims = dots.map((dot, i) =>
      Animated.loop(
        Animated.sequence([
          Animated.delay(i * 160),
          Animated.timing(dot, { toValue: 1, duration: 300, useNativeDriver: true }),
          Animated.timing(dot, { toValue: 0.3, duration: 300, useNativeDriver: true }),
          Animated.delay((2 - i) * 160),
        ])
      )
    );
    anims.forEach(a => a.start());
    return () => anims.forEach(a => a.stop());
  }, []);

  return (
    <View style={dotStyles.row}>
      {dots.map((opacity, i) => (
        <Animated.View key={i} style={[dotStyles.dot, { opacity }]} />
      ))}
    </View>
  );
}

const dotStyles = StyleSheet.create({
  row: { flexDirection: 'row', alignItems: 'center', gap: 5, padding: 4 },
  dot: { width: 7, height: 7, borderRadius: 4, backgroundColor: theme.colors.primary },
});

export function MessageBubble({ role, content, streaming }: MessageBubbleProps) {
  const isUser = role === 'user';
  const showThinking = streaming && !content;

  return (
    <View style={[styles.row, isUser ? styles.rowUser : styles.rowAssistant]}>
      {/* AI avatar for assistant messages */}
      {!isUser && (
        <View style={styles.aiAvatar}>
          <Ionicons name="sparkles" size={14} color={theme.colors.primary} />
        </View>
      )}

      <View
        style={[
          styles.bubble,
          isUser ? styles.bubbleUser : styles.bubbleAssistant,
          isUser && styles.bubbleUserShadow,
        ]}
      >
        {showThinking ? (
          <ThinkingDots />
        ) : (
          <Text style={[styles.text, isUser ? styles.textUser : styles.textAssistant]}>
            {content}
            {streaming && content ? (
              <Text style={styles.cursor}> ▌</Text>
            ) : null}
          </Text>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  row: {
    marginVertical: 3,
    marginHorizontal: theme.spacing.md,
    flexDirection: 'row',
    alignItems: 'flex-end',
    gap: theme.spacing.sm,
  },
  rowUser: {
    justifyContent: 'flex-end',
  },
  rowAssistant: {
    justifyContent: 'flex-start',
  },

  aiAvatar: {
    width: 28,
    height: 28,
    borderRadius: 14,
    backgroundColor: theme.colors.primaryMuted,
    borderWidth: 1,
    borderColor: theme.colors.primary + '44',
    alignItems: 'center',
    justifyContent: 'center',
    marginBottom: 2,
    flexShrink: 0,
  },

  bubble: {
    maxWidth: '82%',
    paddingHorizontal: theme.spacing.md,
    paddingVertical: 10,
    borderRadius: theme.radius.lg,
  },
  bubbleUser: {
    backgroundColor: theme.colors.userBubble,
    borderWidth: 1,
    borderColor: theme.colors.userBubbleBright,
    borderBottomRightRadius: theme.radius.xs,
  },
  bubbleUserShadow: {
    ...theme.shadow.sm,
  },
  bubbleAssistant: {
    backgroundColor: theme.colors.assistantBubble,
    borderWidth: 1,
    borderColor: theme.colors.border,
    borderBottomLeftRadius: theme.radius.xs,
  },

  text: {
    fontSize: theme.fontSize.md,
    lineHeight: 23,
  },
  textUser: {
    color: theme.colors.text,
  },
  textAssistant: {
    color: theme.colors.text,
  },
  cursor: {
    color: theme.colors.primary,
  },
});
