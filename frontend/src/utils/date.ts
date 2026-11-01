export function parseUtcDate(iso: string | null | undefined): Date {
  if (!iso) return new Date();
  const hasTimezone = iso.endsWith('Z') || /[+-]\d{2}(:\d{2})?$/.test(iso);
  return new Date(hasTimezone ? iso : `${iso}Z`);
}

export function formatNotificationTime(iso: string | null | undefined): string {
  if (!iso) {
    return new Date().toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }
  const date = parseUtcDate(iso);
  const now = new Date();
  const isToday = date.toDateString() === now.toDateString();
  if (isToday) {
    return date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
  }
  return `${date.toLocaleDateString([], { month: 'short', day: 'numeric' })}, ${date.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}`;
}

