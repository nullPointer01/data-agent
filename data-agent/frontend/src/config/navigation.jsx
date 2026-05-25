import {
  BarChart3,
  BookOpen,
  Bot,
  BrainCircuit,
  Cpu,
  Database,
  FileText,
  Gauge,
  GitBranch,
  FolderKanban,
  LayoutDashboard,
  MessageSquare,
  ThumbsUp,
  ShieldCheck,
  BadgeHelp,
  KeyRound,
  Users,
  Zap
} from 'lucide-react';

export const adminPages = ['users', 'roles', 'models', 'skills', 'agents', 'datasources', 'stats', 'quality', 'traces', 'feedbacks', 'audit'];

export const navGroups = [
  {
    label: '使用',
    items: [
      { key: 'overview', label: '工作台', icon: LayoutDashboard },
      { key: 'chat', label: '智能对话', icon: MessageSquare },
      { key: 'resources', label: '资料中心', icon: FolderKanban },
      { key: 'memory', label: '记忆中心', icon: BrainCircuit },
      { key: 'knowledge', label: '知识库', icon: BookOpen },
      { key: 'files', label: '文件', icon: FileText },
      { key: 'admin-request', label: '管理员申请', icon: BadgeHelp }
    ]
  },
  {
    label: '管理',
    admin: true,
    items: [
      { key: 'users', label: '用户', icon: Users },
      { key: 'roles', label: '角色权限', icon: KeyRound },
      { key: 'models', label: '模型', icon: Cpu },
      { key: 'skills', label: '技能', icon: Zap },
      { key: 'agents', label: 'Agent', icon: Bot },
      { key: 'datasources', label: '数据源', icon: Database },
      { key: 'stats', label: '统计', icon: BarChart3 },
      { key: 'quality', label: '质量', icon: Gauge },
      { key: 'traces', label: '追踪', icon: GitBranch },
      { key: 'feedbacks', label: '反馈', icon: ThumbsUp },
      { key: 'audit', label: '审计', icon: ShieldCheck }
    ]
  }
];
