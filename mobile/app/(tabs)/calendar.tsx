import React, { useCallback, useEffect, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  RefreshControl,
  Alert,
  ActivityIndicator,
  SafeAreaView,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { Ionicons } from '@expo/vector-icons';
import {
  fetchSchedule,
  markCompleted,
  markSkipped,
  deleteScheduledWorkout,
  ScheduledWorkout,
} from '../../services/calendarService';
import { WorkoutCard } from '../../components/calendar/WorkoutCard';
import { theme } from '../../constants/theme';

const DAY_LABELS = ['M', 'T', 'W', 'T', 'F', 'S', 'S'];

function weekStart(date: Date): Date {
  const d = new Date(date);
  const day = d.getDay();
  d.setDate(d.getDate() + (day === 0 ? -6 : 1 - day));
  d.setHours(0, 0, 0, 0);
  return d;
}
function addDays(date: Date, days: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + days);
  return d;
}
function isoDate(d: Date): string {
  return d.toISOString().split('T')[0];
}
function formatHeader(start: Date, end: Date): string {
  const sameMonth = start.getMonth() === end.getMonth();
  const month = start.toLocaleDateString('en-US', { month: 'long' });
  const year = start.getFullYear();
  return sameMonth
    ? `${month} ${start.getDate()}–${end.getDate()}, ${year}`
    : `${start.toLocaleDateString('en-US', { month: 'short' })} ${start.getDate()} – ${end.toLocaleDateString('en-US', { month: 'short' })} ${end.getDate()}, ${year}`;
}

export default function CalendarScreen() {
  const [weekOf, setWeekOf] = useState(() => weekStart(new Date()));
  const [workouts, setWorkouts] = useState<ScheduledWorkout[]>([]);
  const [loading, setLoading] = useState(false);
  const [refreshing, setRefreshing] = useState(false);
  const [selectedDay, setSelectedDay] = useState<string | null>(null);

  const weekEnd = addDays(weekOf, 6);
  const days = Array.from({ length: 7 }, (_, i) => addDays(weekOf, i));
  const today = isoDate(new Date());

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setWorkouts(await fetchSchedule(weekOf, weekEnd));
    } catch (err) {
      Alert.alert('Error', err instanceof Error ? err.message : 'Failed to load schedule');
    } finally {
      setLoading(false);
    }
  }, [weekOf]);

  useEffect(() => { setSelectedDay(null); load(); }, [weekOf]);

  async function onRefresh() {
    setRefreshing(true);
    await load();
    setRefreshing(false);
  }

  function getDayStatus(day: Date) {
    const iso = isoDate(day);
    const dayWorkouts = workouts.filter(w => w.scheduledDate === iso);
    if (!dayWorkouts.length) return null;
    const allDone = dayWorkouts.every(w => w.status !== 'PENDING');
    const hasCompleted = dayWorkouts.some(w => w.status === 'COMPLETED');
    return { count: dayWorkouts.length, allDone, hasCompleted };
  }

  const filteredWorkouts = selectedDay
    ? workouts.filter(w => w.scheduledDate === selectedDay)
    : workouts;
  const sortedWorkouts = [...filteredWorkouts].sort((a, b) =>
    a.scheduledDate.localeCompare(b.scheduledDate)
  );

  async function handleComplete(id: string) {
    try {
      const updated = await markCompleted(id);
      setWorkouts(prev => prev.map(w => (w.id === id ? updated : w)));
    } catch {
      Alert.alert('Error', 'Failed to mark as completed');
    }
  }
  async function handleSkip(id: string) {
    try {
      const updated = await markSkipped(id);
      setWorkouts(prev => prev.map(w => (w.id === id ? updated : w)));
    } catch {
      Alert.alert('Error', 'Failed to mark as skipped');
    }
  }
  async function handleDelete(id: string) {
    Alert.alert('Remove workout?', 'This will remove it from your schedule.', [
      { text: 'Cancel', style: 'cancel' },
      {
        text: 'Remove',
        style: 'destructive',
        onPress: async () => {
          try {
            await deleteScheduledWorkout(id);
            setWorkouts(prev => prev.filter(w => w.id !== id));
          } catch {
            Alert.alert('Error', 'Failed to delete workout');
          }
        },
      },
    ]);
  }

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar style="light" />

      {/* ── Week navigator ── */}
      <View style={styles.weekNav}>
        <TouchableOpacity onPress={() => setWeekOf(w => addDays(w, -7))} style={styles.navBtn}>
          <Ionicons name="chevron-back" size={20} color={theme.colors.text} />
        </TouchableOpacity>
        <View style={styles.weekLabelWrap}>
          <Text style={styles.weekLabel}>{formatHeader(weekOf, weekEnd)}</Text>
          {isoDate(weekOf) <= today && today <= isoDate(weekEnd) && (
            <View style={styles.thisWeekBadge}>
              <Text style={styles.thisWeekText}>This week</Text>
            </View>
          )}
        </View>
        <TouchableOpacity onPress={() => setWeekOf(w => addDays(w, 7))} style={styles.navBtn}>
          <Ionicons name="chevron-forward" size={20} color={theme.colors.text} />
        </TouchableOpacity>
      </View>

      {/* ── Day strip ── */}
      <View style={styles.dayStrip}>
        {days.map((day, i) => {
          const iso = isoDate(day);
          const isToday = iso === today;
          const isSelected = iso === selectedDay;
          const status = getDayStatus(day);

          return (
            <TouchableOpacity
              key={iso}
              style={styles.dayCell}
              onPress={() => setSelectedDay(isSelected ? null : iso)}
              activeOpacity={0.7}
            >
              <Text style={[styles.dayLabel, (isToday || isSelected) && styles.dayLabelActive]}>
                {DAY_LABELS[i]}
              </Text>

              {/* Number inside circle */}
              <View
                style={[
                  styles.dayNumberCircle,
                  isSelected && styles.dayNumberCircleSelected,
                  isToday && !isSelected && styles.dayNumberCircleToday,
                ]}
              >
                <Text
                  style={[
                    styles.dayNumber,
                    isToday && !isSelected && styles.dayNumberToday,
                    isSelected && styles.dayNumberSelected,
                  ]}
                >
                  {day.getDate()}
                </Text>
              </View>

              {/* Workout indicator dots */}
              {status ? (
                <View style={styles.dotRow}>
                  {Array.from({ length: Math.min(status.count, 3) }, (_, k) => (
                    <View
                      key={k}
                      style={[
                        styles.dot,
                        { backgroundColor: status.allDone ? theme.colors.success : theme.colors.primary },
                      ]}
                    />
                  ))}
                </View>
              ) : <View style={styles.dotPlaceholder} />}
            </TouchableOpacity>
          );
        })}
      </View>

      {/* ── Summary bar ── */}
      {!loading && workouts.length > 0 && (
        <View style={styles.summaryBar}>
          <Text style={styles.summaryText}>
            {filteredWorkouts.length} workout{filteredWorkouts.length !== 1 ? 's' : ''}
            {selectedDay ? ' on this day' : ' this week'}
          </Text>
          <Text style={styles.summaryDone}>
            {workouts.filter(w => w.status === 'COMPLETED').length} completed
          </Text>
        </View>
      )}

      {/* ── Workout list ── */}
      {loading ? (
        <View style={styles.loadingContainer}>
          <ActivityIndicator size="large" color={theme.colors.primary} />
        </View>
      ) : (
        <FlatList
          data={sortedWorkouts}
          keyExtractor={w => w.id}
          renderItem={({ item }) => (
            <WorkoutCard
              workout={item}
              onComplete={() => handleComplete(item.id)}
              onSkip={() => handleSkip(item.id)}
              onDelete={() => handleDelete(item.id)}
            />
          )}
          refreshControl={
            <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.colors.primary} />
          }
          contentContainerStyle={styles.listContent}
          ListEmptyComponent={
            <View style={styles.emptyState}>
              <Ionicons name="calendar-outline" size={44} color={theme.colors.textMuted} />
              <Text style={styles.emptyText}>
                {selectedDay ? 'No workouts on this day' : 'No workouts this week'}
              </Text>
            </View>
          }
        />
      )}
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },
  // Week nav
  weekNav: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: theme.spacing.sm,
    paddingVertical: 10,
  },
  navBtn: {
    width: 38,
    height: 38,
    alignItems: 'center',
    justifyContent: 'center',
    borderRadius: 19,
    backgroundColor: theme.colors.surface,
  },
  weekLabelWrap: {
    flex: 1,
    alignItems: 'center',
    gap: 4,
  },
  weekLabel: {
    color: theme.colors.text,
    fontSize: theme.fontSize.md,
    fontWeight: '700',
  },
  thisWeekBadge: {
    backgroundColor: theme.colors.primaryMuted,
    borderRadius: theme.radius.full,
    paddingHorizontal: 8,
    paddingVertical: 2,
  },
  thisWeekText: {
    color: theme.colors.primary,
    fontSize: theme.fontSize.xs,
    fontWeight: '600',
  },
  // Day strip
  dayStrip: {
    flexDirection: 'row',
    paddingHorizontal: theme.spacing.sm,
    paddingBottom: theme.spacing.sm,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
  },
  dayCell: {
    flex: 1,
    alignItems: 'center',
    gap: 3,
    paddingVertical: 4,
  },
  dayLabel: {
    color: theme.colors.textMuted,
    fontSize: 11,
    fontWeight: '600',
    letterSpacing: 0.5,
    textTransform: 'uppercase',
  },
  dayLabelActive: {
    color: theme.colors.primary,
  },
  dayNumberCircle: {
    width: 34,
    height: 34,
    borderRadius: 17,
    alignItems: 'center',
    justifyContent: 'center',
  },
  dayNumberCircleToday: {
    backgroundColor: theme.colors.primary,
  },
  dayNumberCircleSelected: {
    backgroundColor: theme.colors.surfaceHigh,
    borderWidth: 1.5,
    borderColor: theme.colors.primary,
  },
  dayNumber: {
    color: theme.colors.textSecondary,
    fontSize: theme.fontSize.md,
    fontWeight: '600',
  },
  dayNumberToday: {
    color: '#fff',
    fontWeight: '800',
  },
  dayNumberSelected: {
    color: theme.colors.primary,
    fontWeight: '800',
  },
  dotRow: {
    flexDirection: 'row',
    gap: 2,
    height: 6,
  },
  dotPlaceholder: {
    height: 6,
  },
  dot: {
    width: 5,
    height: 5,
    borderRadius: 3,
  },
  // Summary bar
  summaryBar: {
    flexDirection: 'row',
    justifyContent: 'space-between',
    paddingHorizontal: theme.spacing.md,
    paddingVertical: theme.spacing.sm,
  },
  summaryText: {
    color: theme.colors.textSecondary,
    fontSize: theme.fontSize.sm,
  },
  summaryDone: {
    color: theme.colors.success,
    fontSize: theme.fontSize.sm,
    fontWeight: '600',
  },
  // List
  listContent: {
    flexGrow: 1,
    paddingVertical: theme.spacing.sm,
    paddingBottom: theme.spacing.lg,
  },
  loadingContainer: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  emptyState: {
    alignItems: 'center',
    justifyContent: 'center',
    paddingTop: 64,
    gap: theme.spacing.md,
  },
  emptyText: {
    color: theme.colors.textSecondary,
    fontSize: theme.fontSize.md,
  },
});
