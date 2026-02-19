export const theme = {
  colors: {
    // ── Backgrounds ─────────────────────────────────────────────────────────
    background: '#09090f',
    surface: '#111119',
    surfaceElevated: '#18182a',
    surfaceHigh: '#1f1f35',
    overlay: 'rgba(0,0,0,0.65)',

    // ── Borders ──────────────────────────────────────────────────────────────
    border: '#1e1e30',
    borderStrong: '#2a2a40',

    // ── Brand ────────────────────────────────────────────────────────────────
    primary: '#00c2ff',
    primaryDark: '#0096cc',
    primaryMuted: 'rgba(0,194,255,0.12)',
    primaryLight: '#66daff',

    // ── Semantic ─────────────────────────────────────────────────────────────
    success: '#00d68f',
    successMuted: 'rgba(0,214,143,0.12)',
    warning: '#ffaa00',
    warningMuted: 'rgba(255,170,0,0.12)',
    danger: '#ff4d6a',
    dangerMuted: 'rgba(255,77,106,0.12)',

    // ── Text ─────────────────────────────────────────────────────────────────
    text: '#eeeef8',
    textSecondary: '#8080a0',
    textMuted: '#404060',

    // ── Chat ─────────────────────────────────────────────────────────────────
    userBubble: '#162340',
    userBubbleBright: '#1e3056',
    assistantBubble: '#16162a',

    // ── Schedule status ───────────────────────────────────────────────────────
    pending: '#404060',
    completed: '#00d68f',
    skipped: '#ffaa00',
  },
  spacing: {
    xs: 4,
    sm: 8,
    md: 16,
    lg: 24,
    xl: 32,
    xxl: 48,
  },
  radius: {
    xs: 4,
    sm: 8,
    md: 14,
    lg: 22,
    xl: 28,
    full: 9999,
  },
  fontSize: {
    xs: 11,
    sm: 13,
    md: 15,
    lg: 17,
    xl: 20,
    xxl: 26,
    xxxl: 34,
  },
  // Shared shadow tokens
  shadow: {
    sm: {
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 2 },
      shadowOpacity: 0.4,
      shadowRadius: 4,
      elevation: 3,
    },
    md: {
      shadowColor: '#000',
      shadowOffset: { width: 0, height: 4 },
      shadowOpacity: 0.5,
      shadowRadius: 10,
      elevation: 6,
    },
    glow: {
      shadowColor: '#00c2ff',
      shadowOffset: { width: 0, height: 0 },
      shadowOpacity: 0.35,
      shadowRadius: 12,
      elevation: 8,
    },
  },
} as const;

export type Theme = typeof theme;
