import React from 'react';
import { Stack, useRouter, useSegments } from 'expo-router';
import { useEffect } from 'react';
import { AuthProvider, useAuth } from '../hooks/useAuth';
import { ActivityIndicator, View } from 'react-native';
import { theme } from '../constants/theme';

function RootLayoutInner() {
  const { user, loading } = useAuth();
  const segments = useSegments();
  const router = useRouter();

  useEffect(() => {
    if (loading) return;

    const inTabsGroup = segments[0] === '(tabs)';

    if (!user && inTabsGroup) {
      // Redirect to login if not authenticated
      router.replace('/login');
    } else if (user && !inTabsGroup) {
      // Redirect to tabs if authenticated
      router.replace('/(tabs)/chat');
    }
  }, [user, loading, segments]);

  if (loading) {
    return (
      <View style={{ flex: 1, backgroundColor: theme.colors.background, alignItems: 'center', justifyContent: 'center' }}>
        <ActivityIndicator size="large" color={theme.colors.primary} />
      </View>
    );
  }

  return (
    <Stack screenOptions={{ headerShown: false }}>
      <Stack.Screen name="login" />
      <Stack.Screen name="(tabs)" />
    </Stack>
  );
}

export default function RootLayout() {
  return (
    <AuthProvider>
      <RootLayoutInner />
    </AuthProvider>
  );
}
