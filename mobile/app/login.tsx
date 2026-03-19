import React, {useState} from 'react';
import {ActivityIndicator, Alert, Image, StyleSheet, Text, TouchableOpacity, View,} from 'react-native';
import {StatusBar} from 'expo-status-bar';
import {AntDesign, Ionicons} from '@expo/vector-icons';
import {useAuth} from '../hooks/useAuth';
import {theme} from '../constants/theme';

export default function LoginScreen() {
  const { login, loginStrava } = useAuth();
  const [isLoading, setIsLoading] = useState<'google' | 'strava' | null>(null);

  async function handleGoogleLogin() {
    setIsLoading('google');
    try {
      await login();
    } catch (err) {
      Alert.alert('Sign-in failed', err instanceof Error ? err.message : 'Please try again.');
    } finally {
      setIsLoading(null);
    }
  }

  async function handleStravaLogin() {
    setIsLoading('strava');
    try {
      await loginStrava();
    } catch (err) {
      Alert.alert('Sign-in failed', err instanceof Error ? err.message : 'Please try again.');
    } finally {
      setIsLoading(null);
    }
  }

  return (
    <View style={styles.root}>
      <StatusBar style="light" />

      {/* ── Decorative background glows ── */}
      <View style={[styles.glow, styles.glowTopRight]} />
      <View style={[styles.glow, styles.glowBottomLeft]} />

      {/* ── Hero ── */}
      <View style={styles.hero}>
        <View style={styles.logoRing}>
          <Image
            source={require('../assets/icon.png')}
            style={styles.logo}
            resizeMode="contain"
          />
        </View>

        <Text style={styles.appName}>Koval</Text>
        <Text style={styles.tagline}>Your AI-powered training companion</Text>

        {/* Feature pills */}
        <View style={styles.pills}>
          {['AI Assistant', 'Calendar', 'Club Management'].map(label => (
            <View key={label} style={styles.pill}>
              <Ionicons name="checkmark-circle" size={13} color={theme.colors.primary} />
              <Text style={styles.pillText}>{label}</Text>
            </View>
          ))}
        </View>
      </View>

      {/* ── Actions ── */}
      <View style={styles.actions}>
        <TouchableOpacity
          style={[styles.stravaBtn, isLoading && styles.btnDisabled]}
          onPress={handleStravaLogin}
          disabled={!!isLoading}
          activeOpacity={0.85}
        >
          {isLoading === 'strava' ? (
            <ActivityIndicator color="#fff" />
          ) : (
            <>
              <Ionicons name="bicycle" size={20} color="#fff" />
              <Text style={styles.stravaBtnText}>Connect with Strava</Text>
            </>
          )}
        </TouchableOpacity>

        <View style={styles.divider}>
          <View style={styles.dividerLine} />
          <Text style={styles.dividerText}>or</Text>
          <View style={styles.dividerLine} />
        </View>

        <TouchableOpacity
          style={[styles.googleBtn, isLoading && styles.btnDisabled]}
          onPress={handleGoogleLogin}
          disabled={!!isLoading}
          activeOpacity={0.85}
        >
          {isLoading === 'google' ? (
            <ActivityIndicator color={theme.colors.text} />
          ) : (
            <>
              <AntDesign name="google" size={20} color="#fff" />
              <Text style={styles.googleBtnText}>Continue with Google</Text>
            </>
          )}
        </TouchableOpacity>

        <Text style={styles.legal}>
          By continuing you agree to our Terms of Service and Privacy Policy.
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  root: {
    flex: 1,
    backgroundColor: theme.colors.background,
    justifyContent: 'space-between',
    paddingBottom: 52,
    overflow: 'hidden',
  },

  // Decorative circles
  glow: {
    position: 'absolute',
    borderRadius: theme.radius.full,
  },
  glowTopRight: {
    width: 320,
    height: 320,
    top: -80,
    right: -80,
    backgroundColor: 'rgba(0,194,255,0.07)',
  },
  glowBottomLeft: {
    width: 260,
    height: 260,
    bottom: 40,
    left: -100,
    backgroundColor: 'rgba(100,80,255,0.07)',
  },

  // Hero
  hero: {
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
    paddingHorizontal: theme.spacing.xl,
    gap: theme.spacing.md,
  },
  logoRing: {
    width: 96,
    height: 96,
    borderRadius: 24,
    overflow: 'hidden',
    borderWidth: 1,
    borderColor: theme.colors.primaryMuted,
    ...theme.shadow.glow,
    marginBottom: theme.spacing.sm,
  },
  logo: {
    width: 96,
    height: 96,
  },
  appName: {
    color: theme.colors.text,
    fontSize: theme.fontSize.xxxl,
    fontWeight: '800',
    letterSpacing: -1,
  },
  tagline: {
    color: theme.colors.textSecondary,
    fontSize: theme.fontSize.md,
    textAlign: 'center',
    lineHeight: 22,
  },
  pills: {
    flexDirection: 'row',
    gap: theme.spacing.sm,
    marginTop: theme.spacing.md,
    flexWrap: 'wrap',
    justifyContent: 'center',
  },
  pill: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: 5,
    backgroundColor: theme.colors.surfaceElevated,
    borderRadius: theme.radius.full,
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  pillText: {
    color: theme.colors.textSecondary,
    fontSize: theme.fontSize.sm,
    fontWeight: '500',
  },

  // Actions
  actions: {
    paddingHorizontal: theme.spacing.xl,
    gap: theme.spacing.md,
  },
  divider: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: theme.spacing.sm,
  },
  dividerLine: {
    flex: 1,
    height: 1,
    backgroundColor: theme.colors.border,
  },
  dividerText: {
    color: theme.colors.textMuted,
    fontSize: theme.fontSize.sm,
    fontWeight: '500',
  },
  stravaBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: theme.spacing.sm,
    backgroundColor: '#fc4c02',
    borderRadius: theme.radius.md,
    height: 54,
    shadowColor: '#fc4c02',
    shadowOffset: { width: 0, height: 0 },
    shadowOpacity: 0.35,
    shadowRadius: 12,
    elevation: 8,
  },
  stravaBtnText: {
    color: '#fff',
    fontSize: theme.fontSize.lg,
    fontWeight: '700',
    letterSpacing: 0.2,
  },
  googleBtn: {
    flexDirection: 'row',
    alignItems: 'center',
    justifyContent: 'center',
    gap: theme.spacing.sm,
    backgroundColor: theme.colors.surfaceElevated,
    borderRadius: theme.radius.md,
    height: 54,
    borderWidth: 1,
    borderColor: theme.colors.border,
  },
  btnDisabled: {
    opacity: 0.55,
  },
  googleBtnText: {
    color: theme.colors.text,
    fontSize: theme.fontSize.lg,
    fontWeight: '700',
    letterSpacing: 0.2,
  },
  legal: {
    color: theme.colors.textMuted,
    fontSize: theme.fontSize.xs,
    textAlign: 'center',
    lineHeight: 17,
  },
});
