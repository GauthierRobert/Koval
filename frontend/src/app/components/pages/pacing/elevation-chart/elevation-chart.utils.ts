export function getSlopeColor(gradient: number): string {
  if (gradient > 12) return '#7c3aed';       // extreme climb — deep purple
  if (gradient > 8) return '#c026d3';        // very steep climb — red-purple
  if (gradient > 6) return '#dc2626';        // steep climb — red
  if (gradient > 3) return '#ea580c';        // moderate climb — red-orange
  if (gradient > 1) return '#f97316';        // slight climb — orange
  if (gradient >= -1) return '#a0a0a0';      // flat — grey
  if (gradient >= -3) return '#22c55e';      // slight descent — green
  if (gradient >= -6) return '#0d9488';      // moderate descent — teal
  if (gradient >= -10) return '#2563eb';     // steep descent — blue
  return '#1e3a5f';                          // very steep descent — dark blue
}

export function getSlopeFill(gradient: number): string {
  if (gradient > 12) return 'rgba(124, 58, 237, 0.35)';
  if (gradient > 8) return 'rgba(192, 38, 211, 0.3)';
  if (gradient > 6) return 'rgba(220, 38, 38, 0.3)';
  if (gradient > 3) return 'rgba(234, 88, 12, 0.25)';
  if (gradient > 1) return 'rgba(249, 115, 22, 0.2)';
  if (gradient >= -1) return 'rgba(160, 160, 160, 0.15)';
  if (gradient >= -3) return 'rgba(34, 197, 94, 0.2)';
  if (gradient >= -6) return 'rgba(13, 148, 136, 0.2)';
  if (gradient >= -10) return 'rgba(37, 99, 235, 0.25)';
  return 'rgba(30, 58, 95, 0.3)';
}

export function parsePaceSeconds(paceStr: string): number {
  const cleaned = paceStr.replace(' /km', '');
  const [min, sec] = cleaned.split(':').map(Number);
  return min * 60 + sec;
}

export const SLOPE_LEGEND: ReadonlyArray<{label: string; color: string}> = [
  {label: 'Steep ▲', color: '#dc2626'},
  {label: 'Climb', color: '#f97316'},
  {label: 'Flat', color: '#a0a0a0'},
  {label: 'Descent', color: '#22c55e'},
  {label: 'Steep ▼', color: '#2563eb'},
];
