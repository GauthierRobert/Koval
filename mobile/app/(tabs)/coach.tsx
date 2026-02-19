import React, { useCallback, useEffect, useState } from 'react';
import {
  View,
  Text,
  FlatList,
  StyleSheet,
  TouchableOpacity,
  ActivityIndicator,
  RefreshControl,
  Alert,
  ScrollView,
  SafeAreaView,
  Image,
} from 'react-native';
import { StatusBar } from 'expo-status-bar';
import { Ionicons } from '@expo/vector-icons';
import { useAuth } from '../../hooks/useAuth';
import {
  fetchAthletes,
  fetchAthleteSchedule,
  computeMetrics,
  coachMarkCompleted,
  coachMarkSkipped,
  Athlete,
  AthleteMetrics,
} from '../../services/coachService';
import { ScheduledWorkout } from '../../services/calendarService';
import { WorkoutCard } from '../../components/calendar/WorkoutCard';
import { theme } from '../../constants/theme';

// ── helpers ───────────────────────────────────────────────────────────────────

function weekStart(date: Date): Date {
  const d = new Date(date);
  const day = d.getDay();
  d.setDate(d.getDate() + (day === 0 ? -6 : 1 - day));
  d.setHours(0, 0, 0, 0);
  return d;
}
function addDays(date: Date, n: number): Date {
  const d = new Date(date);
  d.setDate(d.getDate() + n);
  return d;
}
function formatWeek(start: Date, end: Date) {
  return `${start.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })} – ${end.toLocaleDateString('en-US', { month: 'short', day: 'numeric' })}`;
}
function initials(name: string) {
  return name.split(' ').map(p => p[0]).join('').slice(0, 2).toUpperCase();
}

// ── MetricCard ────────────────────────────────────────────────────────────────

function MetricCard({
  label,
  value,
  color,
  icon,
}: {
  label: string;
  value: string | number;
  color: string;
  icon: string;
}) {
  return (
    <View style={[metricStyles.card, { borderTopColor: color }]}>
      <Ionicons name={icon as any} size={16} color={color} style={metricStyles.icon} />
      <Text style={[metricStyles.value, { color }]}>{value}</Text>
      <Text style={metricStyles.label}>{label}</Text>
    </View>
  );
}

const metricStyles = StyleSheet.create({
  card: {
    flex: 1,
    backgroundColor: theme.colors.surface,
    borderRadius: theme.radius.sm,
    paddingVertical: theme.spacing.sm,
    paddingHorizontal: theme.spacing.sm,
    alignItems: 'center',
    borderTopWidth: 2,
    ...theme.shadow.sm,
  },
  icon: { marginBottom: 2 },
  value: {
    fontSize: theme.fontSize.xxl,
    fontWeight: '800',
    lineHeight: 30,
  },
  label: {
    color: theme.colors.textMuted,
    fontSize: theme.fontSize.xs,
    marginTop: 1,
    textAlign: 'center',
    fontWeight: '600',
    letterSpacing: 0.3,
  },
});

// ── AthleteCard ───────────────────────────────────────────────────────────────

function AthleteCard({
  athlete,
  selected,
  onPress,
}: {
  athlete: Athlete;
  selected: boolean;
  onPress: () => void;
}) {
  return (
    <TouchableOpacity
      style={[styles.athleteCard, selected && styles.athleteCardSelected]}
      onPress={onPress}
      activeOpacity={0.75}
    >
      {/* Left accent bar when selected */}
      {selected && <View style={styles.athleteCardAccent} />}

      {athlete.profilePicture ? (
        <Image source={{ uri: athlete.profilePicture }} style={styles.avatar} />
      ) : (
        <View style={[styles.avatar, styles.avatarPlaceholder]}>
          <Text style={styles.avatarInitials}>{initials(athlete.displayName)}</Text>
        </View>
      )}

      <View style={styles.athleteCardInfo}>
        <Text style={styles.athleteName} numberOfLines={1}>
          {athlete.displayName}
        </Text>
        <View style={styles.athleteMetaRow}>
          {athlete.ftp && (
            <View style={styles.ftpChip}>
              <Ionicons name="flash" size={11} color={theme.colors.primary} />
              <Text style={styles.ftpChipText}>{athlete.ftp} W</Text>
            </View>
          )}
          {athlete.tags.map(t => (
            <View key={t} style={styles.tagChip}>
              <Text style={styles.tagChipText}>{t}</Text>
            </View>
          ))}
        </View>
      </View>

      <Ionicons
        name="chevron-forward"
        size={16}
        color={selected ? theme.colors.primary : theme.colors.textMuted}
      />
    </TouchableOpacity>
  );
}

// ── CompletionBar ─────────────────────────────────────────────────────────────

function CompletionBar({ rate }: { rate: number }) {
  return (
    <View style={barStyles.container}>
      <View style={barStyles.track}>
        <View style={[barStyles.fill, { width: `${Math.min(rate, 100)}%` }]} />
      </View>
      <Text style={barStyles.label}>{rate}% completion</Text>
    </View>
  );
}

const barStyles = StyleSheet.create({
  container: { gap: 4 },
  track: {
    height: 6,
    backgroundColor: theme.colors.surfaceHigh,
    borderRadius: 3,
    overflow: 'hidden',
  },
  fill: {
    height: '100%',
    backgroundColor: theme.colors.success,
    borderRadius: 3,
  },
  label: {
    color: theme.colors.textSecondary,
    fontSize: theme.fontSize.xs,
    fontWeight: '600',
  },
});

// ── Main screen ───────────────────────────────────────────────────────────────

export default function CoachScreen() {
  const { user } = useAuth();

  const [athletes, setAthletes] = useState<Athlete[]>([]);
  const [loadingAthletes, setLoadingAthletes] = useState(false);
  const [refreshing, setRefreshing] = useState(false);

  const [selectedAthlete, setSelectedAthlete] = useState<Athlete | null>(null);
  const [weekOf, setWeekOf] = useState(() => weekStart(new Date()));
  const [schedule, setSchedule] = useState<ScheduledWorkout[]>([]);
  const [loadingSchedule, setLoadingSchedule] = useState(false);
  const [metrics, setMetrics] = useState<AthleteMetrics | null>(null);

  if (user?.role !== 'COACH') {
    return (
      <SafeAreaView style={styles.container}>
        <View style={styles.notCoach}>
          <View style={styles.notCoachIcon}>
            <Ionicons name="people-outline" size={36} color={theme.colors.primary} />
          </View>
          <Text style={styles.notCoachTitle}>Coach view</Text>
          <Text style={styles.notCoachSub}>
            Switch your role to Coach from the web app to unlock this section.
          </Text>
        </View>
      </SafeAreaView>
    );
  }

  // ── Data loading ─────────────────────────────────────────────────────────────

  const loadAthletes = useCallback(async () => {
    setLoadingAthletes(true);
    try {
      const data = await fetchAthletes();
      setAthletes(data);
      if (data.length > 0 && !selectedAthlete) setSelectedAthlete(data[0]);
    } catch (err) {
      Alert.alert('Error', err instanceof Error ? err.message : 'Failed to load athletes');
    } finally {
      setLoadingAthletes(false);
    }
  }, []);

  const loadSchedule = useCallback(async () => {
    if (!selectedAthlete) return;
    setLoadingSchedule(true);
    try {
      const end = addDays(weekOf, 6);
      const data = await fetchAthleteSchedule(selectedAthlete.id, weekOf, end);
      setSchedule(data);
      setMetrics(computeMetrics(data));
    } catch (err) {
      Alert.alert('Error', err instanceof Error ? err.message : 'Failed to load schedule');
    } finally {
      setLoadingSchedule(false);
    }
  }, [selectedAthlete, weekOf]);

  useEffect(() => { loadAthletes(); }, []);
  useEffect(() => { loadSchedule(); }, [selectedAthlete, weekOf]);

  async function onRefresh() {
    setRefreshing(true);
    await Promise.all([loadAthletes(), loadSchedule()]);
    setRefreshing(false);
  }

  // ── Workout actions ───────────────────────────────────────────────────────────

  function patchSchedule(id: string, updated: ScheduledWorkout) {
    const next = schedule.map(w => (w.id === id ? updated : w));
    setSchedule(next);
    setMetrics(computeMetrics(next));
  }

  async function handleComplete(id: string) {
    try { patchSchedule(id, await coachMarkCompleted(id)); }
    catch { Alert.alert('Error', 'Failed to mark as completed'); }
  }
  async function handleSkip(id: string) {
    try { patchSchedule(id, await coachMarkSkipped(id)); }
    catch { Alert.alert('Error', 'Failed to mark as skipped'); }
  }
  function handleDelete(_id: string) {
    Alert.alert('Not available', 'Use the web app to remove assigned workouts.');
  }

  // ── Render ────────────────────────────────────────────────────────────────────

  const weekEnd = addDays(weekOf, 6);
  const sortedSchedule = [...schedule].sort((a, b) =>
    a.scheduledDate.localeCompare(b.scheduledDate)
  );

  return (
    <SafeAreaView style={styles.container}>
      <StatusBar style="light" />

      <ScrollView
        refreshControl={
          <RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor={theme.colors.primary} />
        }
        stickyHeaderIndices={[0]}
      >
        {/* ── Athlete list (sticky header) ── */}
        <View style={styles.athleteSection}>
          <Text style={styles.sectionTitle}>Athletes</Text>
          {loadingAthletes ? (
            <ActivityIndicator color={theme.colors.primary} style={{ padding: theme.spacing.md }} />
          ) : athletes.length === 0 ? (
            <View style={styles.emptyAthletes}>
              <Ionicons name="person-add-outline" size={28} color={theme.colors.textMuted} />
              <Text style={styles.emptyAthletesText}>
                No athletes yet. Share an invite code from the web app.
              </Text>
            </View>
          ) : (
            <FlatList
              data={athletes}
              keyExtractor={a => a.id}
              renderItem={({ item }) => (
                <AthleteCard
                  athlete={item}
                  selected={selectedAthlete?.id === item.id}
                  onPress={() => setSelectedAthlete(item)}
                />
              )}
              scrollEnabled={false}
              ItemSeparatorComponent={() => <View style={styles.separator} />}
            />
          )}
        </View>

        {/* ── Selected athlete panel ── */}
        {selectedAthlete && (
          <>
            {/* Athlete header */}
            <View style={styles.detailHeader}>
              {selectedAthlete.profilePicture ? (
                <Image source={{ uri: selectedAthlete.profilePicture }} style={styles.detailAvatar} />
              ) : (
                <View style={[styles.detailAvatar, styles.avatarPlaceholder]}>
                  <Text style={styles.detailInitials}>{initials(selectedAthlete.displayName)}</Text>
                </View>
              )}
              <View style={styles.detailInfo}>
                <Text style={styles.detailName}>{selectedAthlete.displayName}</Text>
                {selectedAthlete.tags.length > 0 && (
                  <View style={styles.tagRow}>
                    {selectedAthlete.tags.map(t => (
                      <View key={t} style={styles.tagChip}>
                        <Text style={styles.tagChipText}>{t}</Text>
                      </View>
                    ))}
                  </View>
                )}
              </View>
            </View>

            {/* Metrics */}
            {metrics && (
              <View style={styles.metricsSection}>
                <Text style={styles.metricsSectionLabel}>THIS WEEK</Text>
                <View style={styles.metricsRow}>
                  <MetricCard label="Total" value={metrics.total} color={theme.colors.textSecondary} icon="calendar-outline" />
                  <View style={{ width: theme.spacing.xs }} />
                  <MetricCard label="Done" value={metrics.completed} color={theme.colors.success} icon="checkmark-circle-outline" />
                  <View style={{ width: theme.spacing.xs }} />
                  <MetricCard label="Skipped" value={metrics.skipped} color={theme.colors.warning} icon="play-skip-forward-outline" />
                  <View style={{ width: theme.spacing.xs }} />
                  <MetricCard label="Pending" value={metrics.pending} color={theme.colors.primary} icon="time-outline" />
                </View>
                {metrics.total > 0 && <CompletionBar rate={metrics.completionRate} />}
              </View>
            )}

            {/* Week navigator */}
            <View style={styles.weekNav}>
              <TouchableOpacity
                onPress={() => setWeekOf(w => addDays(w, -7))}
                style={styles.navBtn}
                activeOpacity={0.7}
              >
                <Ionicons name="chevron-back" size={18} color={theme.colors.text} />
              </TouchableOpacity>
              <Text style={styles.weekLabel}>{formatWeek(weekOf, weekEnd)}</Text>
              <TouchableOpacity
                onPress={() => setWeekOf(w => addDays(w, 7))}
                style={styles.navBtn}
                activeOpacity={0.7}
              >
                <Ionicons name="chevron-forward" size={18} color={theme.colors.text} />
              </TouchableOpacity>
            </View>

            {/* Schedule */}
            {loadingSchedule ? (
              <ActivityIndicator color={theme.colors.primary} style={{ paddingVertical: theme.spacing.xl }} />
            ) : sortedSchedule.length === 0 ? (
              <View style={styles.emptySchedule}>
                <Ionicons name="calendar-outline" size={36} color={theme.colors.textMuted} />
                <Text style={styles.emptyScheduleText}>No workouts scheduled</Text>
              </View>
            ) : (
              sortedSchedule.map(w => (
                <WorkoutCard
                  key={w.id}
                  workout={w}
                  onComplete={() => handleComplete(w.id)}
                  onSkip={() => handleSkip(w.id)}
                  onDelete={() => handleDelete(w.id)}
                />
              ))
            )}
          </>
        )}
      </ScrollView>
    </SafeAreaView>
  );
}

// ── Styles ────────────────────────────────────────────────────────────────────

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: theme.colors.background,
  },

  // Not-coach guard
  notCoach: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    padding: theme.spacing.xl,
    gap: theme.spacing.md,
  },
  notCoachIcon: {
    width: 72,
    height: 72,
    borderRadius: 36,
    backgroundColor: theme.colors.primaryMuted,
    alignItems: 'center',
    justifyContent: 'center',
    borderWidth: 1,
    borderColor: theme.colors.primary + '33',
  },
  notCoachTitle: {
    color: theme.colors.text,
    fontSize: theme.fontSize.xl,
    fontWeight: '700',
  },
  notCoachSub: {
    color: theme.colors.textSecondary,
    fontSize: theme.fontSize.md,
    textAlign: 'center',
    lineHeight: 22,
  },

  // Athlete section
  athleteSection: {
    backgroundColor: theme.colors.surface,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
    paddingBottom: theme.spacing.sm,
  },
  sectionTitle: {
    color: theme.colors.textMuted,
    fontSize: theme.fontSize.xs,
    fontWeight: '700',
    letterSpacing: 1,
    paddingHorizontal: theme.spacing.md,
    paddingTop: theme.spacing.md,
    paddingBottom: theme.spacing.sm,
  },
  emptyAthletes: {
    alignItems: 'center',
    padding: theme.spacing.lg,
    gap: theme.spacing.sm,
  },
  emptyAthletesText: {
    color: theme.colors.textSecondary,
    fontSize: theme.fontSize.sm,
    textAlign: 'center',
  },
  separator: {
    height: 1,
    backgroundColor: theme.colors.border,
    marginLeft: 64 + theme.spacing.md,
  },

  // Athlete card
  athleteCard: {
    flexDirection: 'row',
    alignItems: 'center',
    paddingHorizontal: theme.spacing.md,
    paddingVertical: 10,
    gap: theme.spacing.sm,
  },
  athleteCardSelected: {
    backgroundColor: theme.colors.surfaceElevated,
  },
  athleteCardAccent: {
    position: 'absolute',
    left: 0,
    top: 8,
    bottom: 8,
    width: 3,
    borderRadius: 2,
    backgroundColor: theme.colors.primary,
  },
  avatar: {
    width: 44,
    height: 44,
    borderRadius: 22,
    flexShrink: 0,
  },
  avatarPlaceholder: {
    backgroundColor: theme.colors.surfaceHigh,
    borderWidth: 1,
    borderColor: theme.colors.borderStrong,
    alignItems: 'center',
    justifyContent: 'center',
  },
  avatarInitials: {
    color: theme.colors.primary,
    fontSize: theme.fontSize.md,
    fontWeight: '800',
  },
  athleteCardInfo: { flex: 1 },
  athleteName: {
    color: theme.colors.text,
    fontSize: theme.fontSize.md,
    fontWeight: '600',
  },
  athleteMetaRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 4,
    marginTop: 4,
  },
  ftpChip: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 3,
    backgroundColor: theme.colors.primaryMuted,
    borderRadius: theme.radius.full,
    paddingHorizontal: 7,
    paddingVertical: 2,
  },
  ftpChipText: {
    color: theme.colors.primary,
    fontSize: theme.fontSize.xs,
    fontWeight: '700',
  },
  tagChip: {
    backgroundColor: theme.colors.surfaceHigh,
    borderRadius: theme.radius.full,
    paddingHorizontal: 7,
    paddingVertical: 2,
  },
  tagChipText: {
    color: theme.colors.textSecondary,
    fontSize: theme.fontSize.xs,
    fontWeight: '500',
  },

  // Athlete detail header
  detailHeader: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: theme.spacing.md,
    padding: theme.spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
    backgroundColor: theme.colors.surface,
  },
  detailAvatar: {
    width: 56,
    height: 56,
    borderRadius: 28,
  },
  detailInitials: {
    color: theme.colors.primary,
    fontSize: theme.fontSize.xxl,
    fontWeight: '800',
  },
  detailInfo: { flex: 1 },
  detailName: {
    color: theme.colors.text,
    fontSize: theme.fontSize.xl,
    fontWeight: '700',
  },
  tagRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 4,
    marginTop: 4,
  },

  // Metrics
  metricsSection: {
    padding: theme.spacing.md,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
    gap: theme.spacing.md,
  },
  metricsSectionLabel: {
    color: theme.colors.textMuted,
    fontSize: theme.fontSize.xs,
    fontWeight: '700',
    letterSpacing: 1,
  },
  metricsRow: {
    flexDirection: 'row',
  },

  // Week nav
  weekNav: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'space-between',
    paddingHorizontal: theme.spacing.md,
    paddingVertical: 12,
    borderBottomWidth: 1,
    borderBottomColor: theme.colors.border,
  },
  navBtn: {
    width: 36,
    height: 36,
    borderRadius: 18,
    backgroundColor: theme.colors.surface,
    alignItems: 'center',
    justifyContent: 'center',
  },
  weekLabel: {
    color: theme.colors.text,
    fontSize: theme.fontSize.md,
    fontWeight: '600',
  },

  // Empty schedule
  emptySchedule: {
    alignItems: 'center',
    paddingVertical: theme.spacing.xl,
    gap: theme.spacing.sm,
  },
  emptyScheduleText: {
    color: theme.colors.textSecondary,
    fontSize: theme.fontSize.md,
  },
});
