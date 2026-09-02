# Data Agent

Data Agent 是一个数据分析智能体项目，后端在 `data-agent/` 子目录，前端在 `data-agent/frontend/`。系统支持智能对话、文件与知识库、RAG、长期记忆、多 Agent 编排、动态技能、模型配置、RBAC 管理、执行追踪和质量反馈。

## 目录

```text
.
├── data-agent/            # Spring Boot + React/Vite 主工程
├── docker-compose.yml     # Milvus、Redis、Elasticsearch 等本地依赖
├── CLAUDE.md              # 给 Claude Code / Codex 的项目开发指南
└── README.md              # 当前文件
```

## 快速启动

后端只接受 JDK 17。macOS 可先切换当前终端：

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 17)
export PATH="$JAVA_HOME/bin:$PATH"
java -version
mvn -version
```

仓库根目录的 `.java-version`、Maven Enforcer、后端 CI 和应用镜像都固定为 17。JDK 8、11 或 21 执行 Maven 会在 `validate` 阶段直接失败。

启动依赖：

```bash
docker-compose up -d
```

启动应用：

```bash
cd data-agent
mvn spring-boot:run
```

只调后端时跳过前端构建：

```bash
mvn -Dfrontend.skip=true spring-boot:run
```

默认访问地址：

```text
http://localhost:8080
```

## 当前默认依赖

- JDK 17
- MySQL `data_agent`
- Milvus `localhost:19530`
- 可选 Redis
- Elasticsearch `localhost:9200`
- 可用的大模型 API 配置

local profile 默认连接本机 MySQL。详细配置、管理员初始化、模型配置和开发约定见 [data-agent/README.md](data-agent/README.md)。

## 常用验证

```bash
cd data-agent
mvn -Dfrontend.skip=true validate

cd frontend
npm run build
```

构建 JDK 17 应用镜像：

```bash
docker build -t data-agent:local ./data-agent
```

不要并发跑多个 Maven 命令写同一个 `target/`，资源复制阶段可能互相踩文件。
