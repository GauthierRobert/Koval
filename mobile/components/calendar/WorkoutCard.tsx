import React from 'react';
import { View, Text, TouchableOpacity, StyleSheet } from 'react-native';
import { Ionicons } from '@expo/vector-icons';
import { theme } from '../../constants/theme';
import { SportIcon } from '../SportIcon';
import type { ScheduledWorkout } from '../../services/calendarService';

interface WorkoutCardProps {
  workout: ScheduledWorkout;
  onComplete: () => void;
  onSkip: () => void;
  onDelete: () => void;
}

function formatDuration(seconds?: number): string {
  if (!seconds) return '';
  const m = Math.floor(seconds / 60);
  if (m < 60) return `${m} min`;
  const h = Math.floor(m / 60);
  const rem = m % 60;
  return rem ? `${h}h ${rem}m` : `${h}h`;
}

const STATUS_CONFIG: Record<string, { color: string; bg: string; icon: string }> = {
  PENDING:   { color: theme.colors.textSecondary, bg: theme.colors.surfaceElevated, icon: 'time-outline' },
  COMPLETED: { color: theme.colors.success,       bg: theme.colors.successMuted,    icon: 'checkmark-circle' },
  SKIPPED:   { color: theme.colors.warning,       bg: theme.colors.warningMuted,    icon: 'play-skip-forward' },
};

const SPORT_BG: Record<string, string> = {
  CYCLING:  'rgba(0,194,255,0.12)',
  RUNNING:  'rgba(0,214,143,0.12)',
  SWIMMING: 'rgba(100,80,255,0.12)',
  BRICK:    'rgba(255,170,0,0.12)',
};

export function WorkoutCard({ workout, onComplete, onSkip, onDelete }: WorkoutCardProps) {
  const cfg = STATUS_CONFIG[workout.status] ?? STATUS_CONFIG.PENDING;
  const sportBg = workout.sportType ? (SPORT_BG[workout.sportType] ?? theme.colors.primaryMuted) : theme.colors.primaryMuted;
  const isPending = workout.status === 'PENDING';

  return (
    <View style={styles.card}>
      {/* Coloured left bar */}
      <View style={[styles.leftBar, { backgroundColor: cfg.color }]} />

      <View style={styles.body}>
        {/* Header row */}
        <View style={styles.header}>
          {/* Sport icon in tinted circle */}
          <View style={[styles.sportCircle, { backgroundColor: sportBg }]}>
            <SportIcon sport={workout.sportType} size={20} color={cfg.color === theme.colors.textSecondary ? theme.colors.primary : cfg.color} />
          </View>

          <View style={styles.titleBlock}>
            <Text style={styles.title} numberOfLines={1}>
              {workout.trainingTitle ?? 'Workout'}
            </Text>
            <Text style={styles.meta}>
              {formatDuration(workout.totalDurationSeconds)}
              {workout.tss ? `  ·  TSS ${workout.tss}` : ''}
            </Text>
          </View>

          {/* Status pill */}
          <View style={[styles.statusPill, { backgroundColor: cfg.bg }]}>
            <Ionicons name={cfg.icon as any} size={12} color={cfg.color} />
            <Text style={[styles.statusText, { color: cfg.color }]}>
              {workout.status}
            </Text>
          </View>
        </View>

        {/* Notes */}
        {workout.notes ? (
          <Text style={styles.notes} numberOfLines={2}>{workout.notes}</Text>
        ) : null}

        {/* Action row — only for pending */}
        {isPending && (
          <View style={styles.actions}>
            <TouchableOpacity style={[styles.actionBtn, styles.completeBtn]} onPress={onComplete} activeOpacity={0.8}>
              <Ionicons name="checkmark" size={15} color="#fff" />
              <Text style={styles.actionText}>Complete</Text>
            </TouchableOpacity>
            <TouchableOpacity style={[styles.actionBtn, styles.skipBtn]} onPress={onSkip} activeOpacity={0.8}>
              <Ionicons name="play-skip-forward" size={15} color="#fff" />
              <Text style={styles.actionText}>Skip</Text>
            </TouchableOpacity>
            <TouchableOpacity style={styles.deleteBtn} onPress={onDelete}>
              <Ionicons name="trash-outline" size={17} color={theme.colors.danger} />
            </TouchableOpacity>
          </View>
        )}
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  card: {
    flexDirection: 'row',
    backgroundColor: theme.colors.surface,
    borderRadius: theme.radius.md,
    marginVertical: theme.spacing.xs,
    marginHorizontal: theme.spacing.md,
    overflow: 'hidden',
    ...theme.shadow.sm,
  },
  leftBar: {
    width: 4,
    backgroundColor: theme.colors.textMuted,
  },
  body: {
    flex: 1,
    padding: theme.spacing.md,
    gap: theme.spacing.sm,
  },
  header: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: theme.spacing.sm,
  },
  sportCircle: {
    width: 40,
    height: 40,
    borderRadius: 12,
    alignItems: 'center',
    justifyContent: 'center',
    flexShrink: 0,
  },
  titleBlock: {
    flex: 1,
  },
  title: {
    color: theme.colors.text,
    fontSize: theme.fontSize.md,
    fontWeight: '600',
  },
  meta: {
    color: theme.colors.textSecondary,
    fontSize: theme.fontSize.sm,
    marginTop: 2,
  },
  statusPill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 4,
    borderRadius: theme.radius.full,
    paddingHorizontal: 8,
    paddingVertical: 4,
    flexShrink: 0,
  },
  statusText: {
    fontSize: theme.fontSize.xs,
    fontWeight: '700',
    letterSpacing: 0.4,
  },
  notes: {
    color: theme.colors.textSecondary,
    fontSize: theme.fontSize.sm,
    lineHeight: 19,
    fontStyle: 'italic',
    paddingLeft: 48, // align with title
  },
  actions: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: theme.spacing.sm,
    paddingLeft: 48,
    paddingTop: 2,
  },
  actionBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    paddingHorizontal: 14,
    paddingVertical: 6,
    borderRadius: theme.radius.full,
  },
  completeBtn: {
    backgroundColor: theme.colors.success,
  },
  skipBtn: {
    backgroundColor: theme.colors.warning,
  },
  actionText: {
    color: '#fff',
    fontSize: theme.fontSize.sm,
    fontWeight: '600',
  },
  deleteBtn: {
    marginLeft: 'auto',
    padding: theme.spacing.xs,
  },
});
