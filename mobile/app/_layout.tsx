import React, {useEffect, useRef} from 'react';
import {Stack, useRouter, useSegments} from 'expo-router';
import {AuthProvider, useAuth} from '../hooks/useAuth';
import {ActivityIndicator, View} from 'react-native';
import {theme} from '../constants/theme';
import {
  registerForPushNotifications,
  addNotificationResponseListener,
} from '../services/notificationService';
import * as Notifications from 'expo-notifications';

function RootLayoutInner() {
  const { user, loading } = useAuth();
  const segments = useSegments();
  const router = useRouter();
  const notificationResponseListener = useRef<Notifications.EventSubscription | null>(null);

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

  // Register for push notifications after authentication
  useEffect(() => {
    if (!user) return;

    registerForPushNotifications();

    notificationResponseListener.current = addNotificationResponseListener((response) => {
      const data = response.notification.request.content.data as Record<string, string>;
      const type = data?.type;

      if (type === 'TRAINING_ASSIGNED') {
        router.push('/(tabs)/calendar');
      } else if (type === 'SESSION_CREATED') {
        router.push('/(tabs)/calendar');
      }
    });

    return () => {
      notificationResponseListener.current?.remove();
    };
  }, [user]);

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
