#!/bin/bash
# 个人项目专用 Maven 启动脚本
# - 自动切换 JDK 17
# - 使用阿里云镜像（不依赖公司内网）
# - 独立本地仓库，不污染公司项目缓存

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

export JAVA_HOME="/Library/Java/JavaVirtualMachines/jdk-17.0.9.jdk/Contents/Home"
export PATH="$JAVA_HOME/bin:$PATH"

mvn -s "$SCRIPT_DIR/.mvn/settings.xml" "$@"
