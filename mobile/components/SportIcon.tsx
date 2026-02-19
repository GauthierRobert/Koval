import React from 'react';
import { MaterialCommunityIcons } from '@expo/vector-icons';
import type { SportType } from '../services/calendarService';

interface SportIconProps {
  sport?: SportType | null;
  size?: number;
  color?: string;
}

const ICON_MAP: Record<string, keyof typeof MaterialCommunityIcons.glyphMap> = {
  CYCLING: 'bike',
  RUNNING: 'run',
  SWIMMING: 'swim',
  BRICK: 'biathlon',
};

export function SportIcon({ sport, size = 20, color = '#00b4d8' }: SportIconProps) {
  const iconName = sport ? (ICON_MAP[sport] ?? 'dumbbell') : 'dumbbell';
  return <MaterialCommunityIcons name={iconName} size={size} color={color} />;
}
