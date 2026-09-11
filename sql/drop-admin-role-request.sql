-- 旧版本曾允许普通用户自助申请管理员。当前版本改为仅由现有管理员分配角色。
-- 确认不再需要保留历史申请记录后，手动执行本脚本。
DROP TABLE IF EXISTS admin_role_request;
