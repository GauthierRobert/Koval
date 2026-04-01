export interface SseEvent {
  eventType: string;
  data: string;
}

export function parseSseBuffer(buffer: string): { events: SseEvent[]; remaining: string } {
  buffer = buffer.replace(/\r\n/g, '\n').replace(/\r/g, '\n');
  const chunks = buffer.split('\n\n');
  const remaining = chunks.pop() || '';
  const events: SseEvent[] = [];

  for (const chunk of chunks) {
    if (!chunk.trim()) continue;

    const lines = chunk.split('\n');
    let eventType = '';
    const dataLines: string[] = [];

    for (const line of lines) {
      if (line.startsWith('event:')) {
        eventType = line.substring(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.substring(5));
      } else if (line.startsWith('id:') || line.startsWith('retry:') || line.startsWith(':')) {
        continue;
      }
    }

    if (eventType || dataLines.length) {
      events.push({ eventType, data: dataLines.join('\n') });
    }
  }

  return { events, remaining };
}

export function parseRemainingBuffer(buffer: string): SseEvent | null {
  if (!buffer.trim()) return null;

  const lines = buffer.split('\n');
  let eventType = '';
  const dataLines: string[] = [];

  for (const line of lines) {
    if (line.startsWith('event:')) eventType = line.substring(6).trim();
    else if (line.startsWith('data:')) dataLines.push(line.substring(5));
  }

  const data = dataLines.join('\n');
  if (eventType && data) {
    return { eventType, data };
  }
  return null;
}
