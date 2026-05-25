export function formatTime(value) {
  return value ? new Date(value).toLocaleString('zh-CN') : '-';
}

export function formatBytes(value) {
  const bytes = Number(value || 0);
  if (bytes < 1024) {
    return `${bytes} B`;
  }
  if (bytes < 1024 * 1024) {
    return `${(bytes / 1024).toFixed(1)} KB`;
  }
  return `${(bytes / 1024 / 1024).toFixed(1)} MB`;
}

export function truncate(value, length = 120) {
  const text = String(value ?? '');
  return text.length > length ? `${text.slice(0, length)}...` : text;
}
