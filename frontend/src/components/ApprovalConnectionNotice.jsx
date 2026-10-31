/** 连接失败时保留原审批状态，并明确提供人工刷新，不让等待页伪装为实时更新。 */
export function ApprovalConnectionNotice({ state, enabled = true, onRefresh }) {
  if (!enabled || !['reconnecting', 'sync-failed', 'denied'].includes(state)) return null;
  const notice = state === 'denied' ? '审批状态订阅已停止，请确认当前账号权限。'
    : state === 'sync-failed' ? '审批状态读取失败，可刷新后重试。' : '审批状态暂未同步，正在重新连接。';
  return (
    <div className="alert" role="status">
      {notice}
      <button type="button" className="btn" onClick={onRefresh}>刷新状态</button>
    </div>
  );
}
