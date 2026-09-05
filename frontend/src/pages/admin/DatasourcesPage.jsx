import { Eye, PlugZap } from 'lucide-react';
import { useState } from 'react';
import { ResourcePage } from '../../components/admin/ResourcePage.jsx';
import { Modal } from '../../components/ui.jsx';

export function DatasourcesPage({ api, toast }) {
  const [preview, setPreview] = useState(null);
  const [schema, setSchema] = useState(null);

  const openPreview = async (item) => {
    const res = await api.get(`/api/v1/datasources/preview/${item.datasourceId}?limit=20`);
    setPreview({ datasource: item, data: res.preview });
  };

  const openSchema = async (item) => {
    const res = await api.get(`/api/v1/datasources/schema/${item.datasourceId}`);
    setSchema({ datasource: item, data: res.schema });
  };

  return (
    <>
      <ResourcePage
        api={api}
        toast={toast}
        title="数据源管理"
        desc="配置数据库、HTTP JSON/CSV/Text 等外部数据连接器"
        listUrl="/api/v1/datasources/list"
        listKey="datasources"
        idKey="datasourceId"
        createUrl="/api/v1/datasources/add"
        updateUrl={(id) => `/api/v1/datasources/update/${id}`}
        toggleUrl={(id) => `/api/v1/datasources/toggle/${id}`}
        deleteUrl={(id) => `/api/v1/datasources/delete/${id}`}
        fields={[
          ['name', '名称'],
          ['type', '类型', 'select', [{ value: 'mysql', label: 'MySQL' }, { value: 'postgresql', label: 'PostgreSQL' }, { value: 'clickhouse', label: 'ClickHouse' }, { value: 'http', label: 'HTTP' }]],
          ['host', '主机'],
          ['port', '端口', 'number'],
          ['dbName', '数据库'],
          ['username', '用户名'],
          ['password', '密码', 'password'],
          ['description', '描述'],
          ['enabled', '启用', 'checkbox']
        ]}
        visibleFields={['name', 'type', 'host', 'dbName']}
        extraRowAction={(item) => <>
          <button className="btn" onClick={() => api.post(`/api/v1/datasources/test/${item.datasourceId}`, {}).then((res) => toast(res.message || (res.success ? '连接成功' : '连接失败'), res.success ? 'success' : 'error'))}><PlugZap size={16} />测试</button>
          <button className="btn" onClick={() => openSchema(item)}><Eye size={16} />Schema</button>
          <button className="btn" onClick={() => openPreview(item)}>预览</button>
        </>}
      />
      {schema && <Modal title={`Schema - ${schema.datasource.name}`} onClose={() => setSchema(null)} size="lg"><pre className="code-panel">{JSON.stringify(schema.data, null, 2)}</pre></Modal>}
      {preview && <Modal title={`数据预览 - ${preview.datasource.name}`} onClose={() => setPreview(null)} size="lg"><pre className="code-panel">{JSON.stringify(preview.data, null, 2)}</pre></Modal>}
    </>
  );
}
