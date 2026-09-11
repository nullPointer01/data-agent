import { Check, ChevronDown, Clock3, Copy, Fingerprint, ShieldAlert, X } from 'lucide-react';
import { Badge, ConfirmDialog, Field, LoadingState, Modal } from '../ui.jsx';
import { formatTime } from '../../utils/format.js';
import { useMemo, useState } from 'react';

function decisionTone(status) {
  if (status === 'APPROVED') return 'green';
  if (status === 'REJECTED' || status === 'EXPIRED' || status === 'CANCELLED') return 'red';
  return 'amber';
}

function ttlText(expiresAt, now) {
  const remaining = new Date(expiresAt).getTime() - now;
  if (!Number.isFinite(remaining) || remaining <= 0) return '已过期';
  const minutes = Math.ceil(remaining / 60000);
  if (minutes < 60) return `${minutes} 分钟`;
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`;
}

function userLabel(username, nickname, userId) {
  if (nickname && username) return `${nickname}（@${username}）`;
  return nickname || (username ? `@${username}` : userId || '未知用户');
}

function ApprovalIdentifier({ label, value, onCopy }) {
  return (
    <>
      <span>{label}</span>
      <div className="approval-identifier-value">
        <code className="mono">{value || '-'}</code>
        {value && (
          <button
            className="icon-button"
            type="button"
            title={`复制${label}`}
            aria-label={`复制${label}`}
            onClick={() => onCopy(value, label)}
          >
            <Copy size={14} />
          </button>
        )}
      </div>
    </>
  );
}

export function AgentApprovalDetail({ detail, loading, now, submitting, toast, onClose, onDecision }) {
  const [comment, setComment] = useState('');
  const [confirmAction, setConfirmAction] = useState('');
  const expired = detail ? new Date(detail.expiresAt).getTime() <= now : false;
  const pending = detail?.decisionStatus === 'PENDING' && !expired;
  const decisionLabel = confirmAction === 'approve' ? '批准执行' : '拒绝动作';
  const confirmation = useMemo(() => {
    if (!detail) return '';
    return confirmAction === 'approve'
      ? '批准后，系统会恢复本次运行并执行这一个不可修改的沙箱动作。'
      : '拒绝后，本次运行将结束且该动作不能再次执行。';
  }, [confirmAction, detail]);

  const copyIdentifier = async (value, label) => {
    try {
      await navigator.clipboard.writeText(value);
      toast?.(`${label}已复制`, 'success');
    } catch {
      toast?.('复制失败，请手动选择复制', 'error');
    }
  };

  return (
    <>
      <Modal title="动作审批详情" size="lg" onClose={onClose}>
        {loading || !detail ? <LoadingState label="加载审批详情" /> : (
          <div className="approval-detail">
            <div className="approval-detail-head">
              <div>
                <span className="approval-kicker"><ShieldAlert size={15} />人工确认动作</span>
                <h3>{detail.toolName}</h3>
              </div>
              <div className="approval-status-stack">
                <Badge tone={decisionTone(detail.decisionStatus)}>{detail.decisionStatus}</Badge>
                <Badge tone="gray">{detail.executionStatus}</Badge>
              </div>
            </div>

            <div className="approval-facts">
              <div><span>风险等级</span><strong>{detail.risk}</strong></div>
              <div><span>申请人</span><strong>{userLabel(detail.requesterUsername, detail.requesterNickname, detail.requesterUserId)}</strong></div>
              <div><span>申请时间</span><strong>{formatTime(detail.requestedAt)}</strong></div>
              <div><span>剩余时间</span><strong className={expired ? 'danger-text' : ''}><Clock3 size={14} />{ttlText(detail.expiresAt, now)}</strong></div>
            </div>

            <section className="approval-summary">
              <span>安全参数摘要</span>
              <pre>{detail.safeArgumentSummary || '{}'}</pre>
            </section>

            <div className="detail-grid approval-business-details">
              <span>审批人</span><span>{detail.reviewerUserId ? userLabel(detail.reviewerUsername, detail.reviewerNickname, detail.reviewerUserId) : '-'}</span>
              <span>审批时间</span><span>{formatTime(detail.decidedAt)}</span>
              <span>执行开始</span><span>{formatTime(detail.executionStartedAt)}</span>
              <span>执行结束</span><span>{formatTime(detail.executionCompletedAt)}</span>
              <span>审批备注</span><span>{detail.decisionComment || '-'}</span>
            </div>

            <details className="approval-audit-details">
              <summary>
                <span><Fingerprint size={15} />技术与审计信息</span>
                <span className="approval-audit-summary-meta">5 项标识<ChevronDown size={15} /></span>
              </summary>
              <div className="detail-grid approval-identifiers">
                <ApprovalIdentifier label="Approval ID" value={detail.approvalId} onCopy={copyIdentifier} />
                <ApprovalIdentifier label="Run ID" value={detail.runId} onCopy={copyIdentifier} />
                <ApprovalIdentifier label="Tool Call ID" value={detail.toolCallId} onCopy={copyIdentifier} />
                <ApprovalIdentifier label="申请人 ID" value={detail.requesterUserId} onCopy={copyIdentifier} />
                <ApprovalIdentifier label="审批人 ID" value={detail.reviewerUserId} onCopy={copyIdentifier} />
              </div>
            </details>

            {pending && (
              <div className="approval-decision-box">
                <Field label="审批备注（可选）">
                  <textarea
                    className="textarea"
                    rows={3}
                    maxLength={512}
                    value={comment}
                    onChange={(event) => setComment(event.target.value)}
                    placeholder="记录批准依据或拒绝原因"
                  />
                </Field>
                <div className="approval-actions">
                  <button className="btn danger" disabled={submitting} onClick={() => setConfirmAction('reject')}>
                    <X size={16} />拒绝
                  </button>
                  <button className="btn primary" disabled={submitting} onClick={() => setConfirmAction('approve')}>
                    <Check size={16} />批准执行
                  </button>
                </div>
              </div>
            )}
            {detail.decisionStatus === 'PENDING' && expired && (
              <div className="alert danger">审批已超过有效期，刷新列表后系统会显示最终过期状态。</div>
            )}
          </div>
        )}
      </Modal>
      {confirmAction && (
        <ConfirmDialog
          title={decisionLabel}
          message={confirmation}
          confirmText={decisionLabel}
          danger={confirmAction === 'reject'}
          onCancel={() => setConfirmAction('')}
          onConfirm={() => {
            const action = confirmAction;
            setConfirmAction('');
            onDecision(action, comment.trim());
          }}
        />
      )}
    </>
  );
}
