import { AlertCircle, CheckCircle2, ChevronLeft, ChevronRight, Loader2, Search, X } from 'lucide-react';
import { useEffect, useMemo, useState } from 'react';

export function PageHeader({ title, desc, actions }) {
  return (
    <div className="page-header">
      <div>
        <h1 className="page-title">{title}</h1>
        <p className="page-desc">{desc}</p>
      </div>
      <div className="toolbar">{actions}</div>
    </div>
  );
}

export function Metric({ label, value, hint }) {
  return (
    <div className="card metric-card">
      <div className="metric-label">{label}</div>
      <div className="metric-value">{value}</div>
      {hint && <div className="metric-hint">{hint}</div>}
    </div>
  );
}

export function Badge({ children, tone = 'blue' }) {
  return <span className={`badge ${tone}`}>{children}</span>;
}

export function LoadingState({ label = '加载中' }) {
  return (
    <div className="state-panel">
      <Loader2 className="spin" size={22} />
      <span>{label}</span>
    </div>
  );
}

export function EmptyState({ title = '暂无数据', desc = '当前筛选条件下没有可展示的数据。', actions }) {
  return (
    <div className="state-panel">
      <AlertCircle size={22} />
      <strong>{title}</strong>
      <span>{desc}</span>
      {actions && <div className="toolbar">{actions}</div>}
    </div>
  );
}

export function ToastHost({ toasts, onClose }) {
  return (
    <div className="toast-host">
      {toasts.map((toast) => (
        <div className={`toast ${toast.type || 'info'}`} key={toast.id}>
          {toast.type === 'success' ? <CheckCircle2 size={18} /> : <AlertCircle size={18} />}
          <span>{toast.message}</span>
          <button type="button" className="icon-button" onClick={() => onClose(toast.id)}><X size={16} /></button>
        </div>
      ))}
    </div>
  );
}

export function ConfirmDialog({ title, message, confirmText = '确认', danger, onCancel, onConfirm }) {
  return (
    <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onCancel(); }}>
      <div className="confirm-dialog">
        <h2>{title}</h2>
        <p>{message}</p>
        <div className="toolbar" style={{ justifyContent: 'flex-end' }}>
          <button className="btn" onClick={onCancel}>取消</button>
          <button className={`btn ${danger ? 'danger-fill' : 'primary'}`} onClick={onConfirm}>{confirmText}</button>
        </div>
      </div>
    </div>
  );
}

export function Modal({ title, children, actions, onClose, size = 'md' }) {
  return (
    <div className="modal-backdrop" onMouseDown={(event) => { if (event.target === event.currentTarget) onClose(); }}>
      <div className={`modal ${size}`}>
        <div className="page-header">
          <h2 className="page-title">{title}</h2>
          <button className="icon-button bordered" onClick={onClose}><X size={18} /></button>
        </div>
        {children}
        {actions && <div className="toolbar" style={{ justifyContent: 'flex-end', marginTop: 18 }}>{actions}</div>}
      </div>
    </div>
  );
}

export function ToolbarSearch({ value, onChange, placeholder = '搜索' }) {
  return (
    <label className="search-box">
      <Search size={16} />
      <input value={value} onChange={(event) => onChange(event.target.value)} placeholder={placeholder} />
    </label>
  );
}

export function usePagination(items, { initialPageSize = 10, keyField } = {}) {
  const safeItems = Array.isArray(items) ? items : [];
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(initialPageSize);
  const itemSignature = useMemo(() => safeItems.map((item, index) => {
    if (keyField && item?.[keyField] != null) {
      return item[keyField];
    }
    return item?.id ?? JSON.stringify(item) ?? index;
  }).join('\u001f'), [safeItems, keyField]);
  const totalPages = Math.max(1, Math.ceil(safeItems.length / pageSize));

  useEffect(() => { setPage(1); }, [itemSignature]);
  useEffect(() => { setPage((current) => Math.min(current, totalPages)); }, [totalPages]);

  const startIndex = (page - 1) * pageSize;
  return {
    page,
    pageSize,
    total: safeItems.length,
    pageItems: safeItems.slice(startIndex, startIndex + pageSize),
    setPage,
    setPageSize: (nextPageSize) => {
      setPageSize(Number(nextPageSize));
      setPage(1);
    }
  };
}

export function Pagination({ page, pageSize, total, onPageChange, onPageSizeChange, compact = false }) {
  if (total <= pageSize) {
    return null;
  }

  const totalPages = Math.max(1, Math.ceil(total / pageSize));
  const start = total === 0 ? 0 : (page - 1) * pageSize + 1;
  const end = Math.min(page * pageSize, total);

  return (
    <div className={`pagination ${compact ? 'compact' : ''}`} aria-label="分页导航">
      <span className="pagination-summary">第 {start}-{end} 条，共 {total} 条</span>
      <label className="pagination-size">
        <span>每页</span>
        <select value={pageSize} onChange={(event) => onPageSizeChange(event.target.value)} aria-label="每页条数">
          {[10, 20, 50].map((size) => <option value={size} key={size}>{size}</option>)}
        </select>
        <span>条</span>
      </label>
      <div className="pagination-actions">
        <button className="icon-button bordered" type="button" disabled={page <= 1} onClick={() => onPageChange(page - 1)} title="上一页" aria-label="上一页"><ChevronLeft size={16} /></button>
        <span>{page} / {totalPages}</span>
        <button className="icon-button bordered" type="button" disabled={page >= totalPages} onClick={() => onPageChange(page + 1)} title="下一页" aria-label="下一页"><ChevronRight size={16} /></button>
      </div>
    </div>
  );
}

export function DataTable({ columns, rows, rowKey, loading, empty, pageSize = 10 }) {
  const pagination = usePagination(rows, { initialPageSize: pageSize, keyField: rowKey });
  if (loading) {
    return <LoadingState />;
  }
  if (!rows || !rows.length) {
    return empty || <EmptyState />;
  }
  return (
    <div className="data-table-shell">
      <div className="table-wrap">
        <table style={{ minWidth: `${Math.max(520, columns.length * 110)}px` }}>
          <thead>
            <tr>{columns.map((column) => <th key={column.key}>{column.title}</th>)}</tr>
          </thead>
          <tbody>
            {pagination.pageItems.map((row, rowIndex) => (
              <tr key={rowKey ? row[rowKey] : rowIndex}>
                {columns.map((column) => (
                  <td key={column.key}>{column.render ? column.render(row) : row[column.key]}</td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <Pagination
        page={pagination.page}
        pageSize={pagination.pageSize}
        total={pagination.total}
        onPageChange={pagination.setPage}
        onPageSizeChange={pagination.setPageSize}
      />
    </div>
  );
}

export function Field({ label, children, span }) {
  return (
    <label className={span ? 'field span-2' : 'field'}>
      <span>{label}</span>
      {children}
    </label>
  );
}
