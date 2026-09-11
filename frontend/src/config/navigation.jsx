import {
  BarChart3,
  BookOpen,
  Bot,
  BrainCircuit,
  Cpu,
  Database,
  Gauge,
  GitBranch,
  FolderKanban,
  FlaskConical,
  MessageSquare,
  ShieldCheck,
  ClipboardCheck,
  KeyRound,
  Users,
  Zap
} from 'lucide-react';

export const adminPages = ['users', 'roles', 'models', 'skills', 'agents', 'datasources', 'approvals', 'stats', 'quality', 'agent-evals', 'traces', 'audit', 'resources'];

export const userNavGroups = [
  {
    label: 'REFERENCE APP',
    items: [
      { key: 'chat', label: '我的 Agent', icon: MessageSquare },
      { key: 'experts', label: '专家助手', icon: Bot },
      { key: 'knowledge', label: '知识', icon: BookOpen },
      { key: 'memory', label: '上下文', icon: BrainCircuit }
    ]
  }
];

export const adminNavGroups = [
  {
    label: '运行与质量',
    items: [
      { key: 'traces', label: '运行追踪', icon: GitBranch },
      { key: 'approvals', label: '动作审批', icon: ClipboardCheck },
      { key: 'quality', label: '质量观测', icon: Gauge },
      { key: 'agent-evals', label: 'Agent 评测', icon: FlaskConical },
      { key: 'stats', label: '资源统计', icon: BarChart3 }
    ]
  },
  {
    label: '能力配置',
    items: [
      { key: 'models', label: '模型目录', icon: Cpu },
      { key: 'skills', label: '技能目录', icon: Zap },
      { key: 'resources', label: '知识与 RAG', icon: FolderKanban },
      { key: 'datasources', label: '数据连接', icon: Database }
    ]
  },
  {
    label: '平台治理',
    items: [
      { key: 'users', label: '用户', icon: Users },
      { key: 'agents', label: '用户 Agent', icon: Bot },
      { key: 'roles', label: '角色权限', icon: KeyRound },
      { key: 'audit', label: '审计', icon: ShieldCheck }
    ]
  }
];
