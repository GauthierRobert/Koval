import React from 'react';
import { Tabs } from 'expo-router';
import { Ionicons } from '@expo/vector-icons';
import { Platform } from 'react-native';
import { theme } from '../../constants/theme';

export default function TabsLayout() {
  return (
    <Tabs
      screenOptions={{
        headerStyle: {
          backgroundColor: theme.colors.surface,
          borderBottomWidth: 0,
          shadowOpacity: 0,
          elevation: 0,
        },
        headerTintColor: theme.colors.text,
        headerTitleStyle: {
          fontWeight: '700',
          fontSize: theme.fontSize.lg,
          color: theme.colors.text,
        },
        headerShadowVisible: false,
        tabBarStyle: {
          backgroundColor: theme.colors.surface,
          borderTopColor: theme.colors.border,
          borderTopWidth: 1,
          paddingTop: 6,
          height: Platform.OS === 'ios' ? 84 : 62,
        },
        tabBarActiveTintColor: theme.colors.primary,
        tabBarInactiveTintColor: theme.colors.textMuted,
        tabBarLabelStyle: {
          fontSize: 11,
          fontWeight: '600',
          marginBottom: Platform.OS === 'ios' ? 0 : 4,
        },
      }}
    >
      <Tabs.Screen
        name="chat"
        options={{
          title: 'AI Coach',
          tabBarLabel: 'Chat',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="sparkles-outline" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="calendar"
        options={{
          title: 'My Schedule',
          tabBarLabel: 'Calendar',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="calendar-outline" size={size} color={color} />
          ),
        }}
      />
      <Tabs.Screen
        name="coach"
        options={{
          title: 'My Athletes',
          tabBarLabel: 'Coach',
          tabBarIcon: ({ color, size }) => (
            <Ionicons name="people-outline" size={size} color={color} />
          ),
        }}
      />
    </Tabs>
  );
}
