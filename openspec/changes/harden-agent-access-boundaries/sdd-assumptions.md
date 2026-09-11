# SDD Assumptions

## A001 当前 ADMIN 暂按平台治理角色保留

- 来源：当前 `SecurityConfig`、用户管理服务和管理控制台实现。
- 置信度：中。
- 状态：Unverified。
- 验证方式：下一阶段由用户确认是否拆分平台管理员与租户管理员。

## A002 普通注册只用于本地演示或受控环境

- 来源：当前登录页开放注册，同时项目目标包含企业化能力。
- 置信度：高。
- 状态：Confirmed for this change。
- 验证方式：local 显式开启，prod 默认关闭，服务端配置固定注册租户。

## A003 普通用户需要选择管理员已启用的模型

- 来源：个人 Agent 设置页面已有模型选择控件，但当前调用管理员接口。
- 置信度：高。
- 状态：Confirmed。
- 验证方式：新增最小只读目录并切换前端。

## A004 禁用和撤权应在下一次请求生效

- 来源：企业账号治理语义和当前 Tool Authorization 已有的数据库实时检查。
- 置信度：高。
- 状态：Confirmed。
- 验证方式：Filter 不使用 claims 中的角色构建授权主体，refresh 检查 enabled。

## A005 当前禁止自动化测试和构建仍有效

- 来源：用户历史明确要求及 `CLAUDE.md`。
- 置信度：高。
- 状态：Confirmed。
- 验证方式：只执行静态检查，不调用 Maven、npm build、Docker 或测试命令。
