const EM_DASH = '—';
const MIDDOT = '·';

export function formatSessionTime(dateStr: string | null | undefined): string {
  if (!dateStr) return '';
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return '';
  const day = d.toLocaleDateString('en-US', {weekday: 'short', month: 'short', day: 'numeric'});
  const time = d.toLocaleTimeString('en-US', {hour: '2-digit', minute: '2-digit'});
  return `${day} ${MIDDOT} ${time}`;
}

export function formatGoalDate(dateStr: string | null | undefined): string {
  if (!dateStr) return EM_DASH;
  const d = new Date(dateStr);
  if (isNaN(d.getTime())) return EM_DASH;
  return d.toLocaleDateString('en-US', {weekday: 'short', month: 'short', day: 'numeric'});
}

export function getDaysUntil(dateStr: string | null | undefined): number {
  if (!dateStr) return 0;
  const ts = new Date(dateStr).getTime();
  if (isNaN(ts)) return 0;
  return Math.max(0, Math.ceil((ts - Date.now()) / 86400000));
}
