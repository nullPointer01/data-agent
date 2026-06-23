package com.ai.config;

import com.ai.model.SkillConfig;
import com.ai.repository.SkillConfigRepository;
import com.ai.security.rbac.RolePermissionService;
import com.ai.security.SecurityConstants;
import com.ai.security.rbac.SysPermission;
import com.ai.security.rbac.SysPermissionRepository;
import com.ai.security.rbac.SysRole;
import com.ai.security.rbac.SysRoleRepository;
import com.ai.security.SysUserRepository;
import com.ai.skill.DynamicSkill;
import com.ai.skill.SkillManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 初始化默认数据并修复已知旧库结构问题。
 *
 * @author data-agent
 */
@Configuration
public class DataInitializer {

    private static final Logger LOGGER = LoggerFactory.getLogger(DataInitializer.class);
    private static final String TABLE_SYS_USER = "sys_user";
    private static final String TABLE_SYS_USER_ROLES = "sys_user_roles";
    private static final String TABLE_SYS_USER_ROLE = "sys_user_role";
    private static final String COLUMN_DAILY_TOKEN_LIMIT = "daily_token_limit";
    private static final String USERNAME_DELIMITER = ",";
    private static final String PERMISSION_ALL = "*:*";
    private static final String PERMISSION_APP_USE = "app:use";
    private static final String DEFAULT_TENANT_ID = "default";

    @Value("${app.security.bootstrap-admin-users:super}")
    private String bootstrapAdminUsers;

    @Bean
    public CommandLineRunner initData(SkillConfigRepository skillConfigRepository,
            SkillManager skillManager,
            JdbcTemplate jdbcTemplate,
            SysUserRepository sysUserRepository,
            SysRoleRepository roleRepository,
            SysPermissionRepository permissionRepository,
            RolePermissionService rolePermissionService) {
        return args -> {
            repairUserTokenQuotaSchema(jdbcTemplate);
            initDefaultRbac(roleRepository, permissionRepository);
            migrateLegacyUserRoles(jdbcTemplate);
            bootstrapAdminUsers(sysUserRepository, rolePermissionService);
            initDefaultSkill(skillConfigRepository, skillManager);
            loadExistingSkills(skillConfigRepository, skillManager);
        };
    }

    private void repairUserTokenQuotaSchema(JdbcTemplate jdbcTemplate) {
        try {
            if (!columnExists(jdbcTemplate, TABLE_SYS_USER, COLUMN_DAILY_TOKEN_LIMIT)) {
                jdbcTemplate.execute("ALTER TABLE sys_user ADD COLUMN daily_token_limit BIGINT DEFAULT 0 NOT NULL");
                LOGGER.warn("Added missing sys_user.daily_token_limit column");
            }
            int repairedRows = jdbcTemplate.update("UPDATE sys_user SET daily_token_limit = 0 WHERE daily_token_limit IS NULL");
            if (repairedRows > 0) {
                LOGGER.warn("Repaired {} users with NULL daily_token_limit", repairedRows);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not repair sys_user.daily_token_limit automatically: {}", e.getMessage());
        }
    }

    private boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) throws SQLException {
        if (jdbcTemplate.getDataSource() == null) {
            return false;
        }
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            return columnExists(connection, tableName, columnName)
                    || columnExists(connection, tableName.toUpperCase(), columnName.toUpperCase());
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = connection.getMetaData().getColumns(null, null, tableName, columnName)) {
            return columns.next();
        }
    }

    private void initDefaultRbac(SysRoleRepository roleRepository, SysPermissionRepository permissionRepository) {
        SysPermission appUse = savePermissionIfAbsent(permissionRepository, PERMISSION_APP_USE,
                "应用使用", "登录后使用普通功能", "APP", "USE");
        SysPermission all = savePermissionIfAbsent(permissionRepository, PERMISSION_ALL,
                "全部权限", "系统管理员拥有全部管理权限", "SYSTEM", "*");

        saveRoleIfAbsent(roleRepository, SecurityConstants.ROLE_USER, "普通用户",
                "可使用对话、资料、知识库等基础功能", Set.of(appUse));
        saveRoleIfAbsent(roleRepository, SecurityConstants.ROLE_ADMIN, "系统管理员",
                "可管理用户、角色、模型、技能、Agent、数据源和审计", Set.of(appUse, all));
    }

    private SysPermission savePermissionIfAbsent(SysPermissionRepository repository, String code, String name,
            String description, String resourceType, String action) {
        return repository.findById(code).orElseGet(() -> {
            SysPermission permission = new SysPermission();
            permission.setPermissionCode(code);
            permission.setName(name);
            permission.setDescription(description);
            permission.setResourceType(resourceType);
            permission.setAction(action);
            permission.setEnabled(true);
            return repository.save(permission);
        });
    }

    private void saveRoleIfAbsent(SysRoleRepository repository, String code, String name, String description,
            Set<SysPermission> permissions) {
        repository.findById(code).orElseGet(() -> {
            SysRole role = new SysRole();
            role.setRoleCode(code);
            role.setName(name);
            role.setDescription(description);
            role.setSystemRole(true);
            role.setEnabled(true);
            role.setPermissions(permissions);
            return repository.save(role);
        });
    }

    private void migrateLegacyUserRoles(JdbcTemplate jdbcTemplate) {
        try {
            if (!tableExists(jdbcTemplate, TABLE_SYS_USER_ROLES) || !tableExists(jdbcTemplate, TABLE_SYS_USER_ROLE)) {
                return;
            }
            int migratedRows = jdbcTemplate.update("""
                    INSERT INTO sys_user_role (user_id, role_code)
                    SELECT old_roles.user_id, old_roles.role
                    FROM sys_user_roles old_roles
                    WHERE old_roles.role IS NOT NULL
                      AND EXISTS (SELECT 1 FROM sys_role role_table WHERE role_table.role_code = old_roles.role)
                      AND NOT EXISTS (
                          SELECT 1 FROM sys_user_role new_roles
                          WHERE new_roles.user_id = old_roles.user_id
                            AND new_roles.role_code = old_roles.role
                      )
                    """);
            if (migratedRows > 0) {
                LOGGER.warn("Migrated {} legacy sys_user_roles rows into sys_user_role", migratedRows);
            }
        } catch (Exception e) {
            LOGGER.warn("Could not migrate legacy sys_user_roles automatically: {}", e.getMessage());
        }
    }

    private boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) throws SQLException {
        if (jdbcTemplate.getDataSource() == null) {
            return false;
        }
        try (Connection connection = jdbcTemplate.getDataSource().getConnection()) {
            return tableExists(connection, tableName) || tableExists(connection, tableName.toUpperCase());
        }
    }

    private boolean tableExists(Connection connection, String tableName) throws SQLException {
        try (ResultSet tables = connection.getMetaData().getTables(null, null, tableName, null)) {
            return tables.next();
        }
    }

    private void bootstrapAdminUsers(SysUserRepository userRepository, RolePermissionService rolePermissionService) {
        if (bootstrapAdminUsers == null || bootstrapAdminUsers.isBlank()) {
            return;
        }

        for (String username : bootstrapAdminUsers.split(USERNAME_DELIMITER)) {
            String trimmedUsername = username.trim();
            if (trimmedUsername.isEmpty()) {
                continue;
            }
            userRepository.findByUsername(trimmedUsername).ifPresent(user -> {
                if (!rolePermissionService.hasRole(user.getRoles(), SecurityConstants.ROLE_ADMIN)) {
                    user.setRoles(Set.of(rolePermissionService.requireRole(SecurityConstants.ROLE_USER),
                            rolePermissionService.requireRole(SecurityConstants.ROLE_ADMIN)));
                    userRepository.save(user);
                    LOGGER.warn("Bootstrapped ADMIN role for user {}", trimmedUsername);
                }
            });
        }
    }

    private void initDefaultSkill(SkillConfigRepository repository, SkillManager skillManager) {
        if (repository.countByIsDefaultTrue() == 0) {
            SkillConfig defaultSkill = new SkillConfig();
            defaultSkill.setName("通用数据分析");
            defaultSkill.setDescription("默认数据分析技能，支持通用数据查询、统计分析和趋势预测");
            defaultSkill.setVersion("1.0");
            defaultSkill.setApiUrl("");
            defaultSkill.setApiMethod("POST");
            defaultSkill.setPromptTemplate(
                    "你是数据分析助手。基于{{data}}回答{{query}}。规则:1.有数据时分析数据 2.无数据时提示用户上传 3.绝不编造数据 4.简洁专业");
            defaultSkill.setKeywords("分析,数据,统计,查询,报表,趋势,预测,对比,汇总");
            defaultSkill.setEnabled(true);
            defaultSkill.setDefault(true);
            defaultSkill.setTenantId("default");

            repository.save(defaultSkill);
            LOGGER.info("默认技能已初始化: {}", defaultSkill.getName());
        }
    }

    private void loadExistingSkills(SkillConfigRepository repository, SkillManager skillManager) {
        List<SkillConfig> skills = repository.findByEnabledTrue();
        for (SkillConfig config : skills) {
            if (config.isDefault()) {
                registerDefaultSkillToManager(config, skillManager);
            } else {
                registerSkillToManager(config, skillManager);
            }
        }
        LOGGER.info("已加载 {} 个启用技能到 SkillManager", skills.size());
    }

    private void registerDefaultSkillToManager(SkillConfig config, SkillManager skillManager) {
        DynamicSkill skill = buildDynamicSkill(config);
        skillManager.registerDefaultSkillWithoutVectorRefresh(skill);
    }

    private void registerSkillToManager(SkillConfig config, SkillManager skillManager) {
        DynamicSkill skill = buildDynamicSkill(config);
        skillManager.registerSkillWithoutVectorRefresh(skill);
    }

    private DynamicSkill buildDynamicSkill(SkillConfig config) {
        return new DynamicSkill(
                config.getName(),
                config.getDescription(),
                Map.of(
                        "apiUrl", config.getApiUrl() != null ? config.getApiUrl() : "",
                        "apiMethod", config.getApiMethod() != null ? config.getApiMethod() : "POST",
                        "apiHeaders", config.getApiHeaders() != null ? config.getApiHeaders() : "",
                        "promptTemplate", config.getPromptTemplate() != null ? config.getPromptTemplate() : "",
                        "responseTemplate", config.getResponseTemplate() != null ? config.getResponseTemplate() : "",
                        "keywords", config.getKeywords() != null ? config.getKeywords() : ""));
    }
}
