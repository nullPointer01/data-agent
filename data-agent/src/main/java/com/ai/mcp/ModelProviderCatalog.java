package com.ai.mcp;

import java.util.Locale;
import java.util.Optional;

/**
 * 模型供应商目录：收录国产主流厂商的默认 Base URL 与默认模型名（另留一个 OpenAI 兼容扩展位）。
 *
 * <p>设计原则：所有主流厂商均已兼容 OpenAI Chat Completions 协议，
 * 模型调用统一走 LangChain4j 客户端；接入新厂商只需在此登记默认值，
 * 未登记的厂商只要用户填写了 Base URL，同样按 OpenAI 兼容协议调用。</p>
 *
 * @author data-agent
 */
public enum ModelProviderCatalog {

    /** 深度求索 DeepSeek。 */
    DEEPSEEK("deepseek", "DeepSeek 深度求索", "https://api.deepseek.com/v1", "deepseek-chat"),

    /** 阿里通义千问（DashScope 兼容模式）。 */
    QWEN("qwen", "通义千问", "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus",
            "dashscope", "tongyi", "bailian", "alibaba"),

    /** 月之暗面 Kimi（开放平台）。 */
    KIMI("kimi", "Kimi 月之暗面", "https://api.moonshot.cn/v1", "kimi-k2.6", "moonshot"),

    /** 智谱 GLM（开放平台）。 */
    ZHIPU("zhipu", "智谱 GLM", "https://open.bigmodel.cn/api/paas/v4", "glm-4-plus",
            "glm", "chatglm", "bigmodel"),

    /** 字节豆包（火山方舟）。模型名按方舟控制台的 Model ID 填写。 */
    DOUBAO("doubao", "豆包（火山方舟）", "https://ark.cn-beijing.volces.com/api/v3",
            "doubao-1-5-pro-32k-250115", "ark", "volcengine", "huoshan"),

    /** 腾讯混元。 */
    HUNYUAN("hunyuan", "腾讯混元", "https://api.hunyuan.cloud.tencent.com/v1", "hunyuan-turbo", "tencent"),

    /** 百度文心（千帆 v2 OpenAI 兼容接口）。 */
    ERNIE("ernie", "文心一言（百度千帆）", "https://qianfan.baidubce.com/v2", "ernie-4.0-8k",
            "baidu", "qianfan", "wenxin"),

    /** 讯飞星火（API Password 作为 Key）。 */
    SPARK("spark", "讯飞星火", "https://spark-api-open.xf-yun.com/v1", "generalv3.5",
            "xunfei", "iflytek", "xinghuo"),

    /** MiniMax。 */
    MINIMAX("minimax", "MiniMax", "https://api.minimaxi.com/v1", "MiniMax-Text-01"),

    /** 百川智能。 */
    BAICHUAN("baichuan", "百川智能", "https://api.baichuan-ai.com/v1", "Baichuan4-Turbo"),

    /** 零一万物。 */
    YI("yi", "零一万物", "https://api.lingyiwanwu.com/v1", "yi-large", "lingyiwanwu", "01ai"),

    /** 阶跃星辰。 */
    STEPFUN("stepfun", "阶跃星辰", "https://api.stepfun.com/v1", "step-2-16k", "step"),

    /** 硅基流动（国产模型聚合平台）。 */
    SILICONFLOW("siliconflow", "硅基流动", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3",
            "silicon"),

    /** 本地 Ollama（OpenAI 兼容端点）。 */
    OLLAMA("ollama", "Ollama 本地", "http://localhost:11434/v1", "llama3.1", "local"),

    /** 国外厂商扩展位：OpenAI 及任何 OpenAI 兼容端点。 */
    OPENAI("openai", "自定义（OpenAI 兼容扩展）", "https://api.openai.com/v1", "gpt-4o-mini", "custom");

    private final String key;
    private final String displayName;
    private final String defaultBaseUrl;
    private final String defaultModelName;
    private final String[] aliases;

    ModelProviderCatalog(String key, String displayName, String defaultBaseUrl, String defaultModelName,
            String... aliases) {
        this.key = key;
        this.displayName = displayName;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModelName = defaultModelName;
        this.aliases = aliases;
    }

    /**
     * 按厂商标识或别名解析目录项，大小写不敏感。
     *
     * @param provider 厂商标识，如 kimi、zhipu、glm
     * @return 匹配的目录项，未登记厂商返回 empty
     */
    public static Optional<ModelProviderCatalog> resolve(String provider) {
        if (provider == null || provider.isBlank()) {
            return Optional.empty();
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        for (ModelProviderCatalog entry : values()) {
            if (entry.key.equals(normalized)) {
                return Optional.of(entry);
            }
            for (String alias : entry.aliases) {
                if (alias.equals(normalized)) {
                    return Optional.of(entry);
                }
            }
        }
        return Optional.empty();
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String defaultModelName() {
        return defaultModelName;
    }
}
