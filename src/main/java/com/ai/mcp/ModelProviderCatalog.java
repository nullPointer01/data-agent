package com.ai.mcp;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * OpenAI-compatible 模型厂商目录。
 *
 * <p>目录只描述接入元数据和少量推荐模型，不承诺推荐列表覆盖厂商全部模型。
 * 前端、模型发现和运行时端点解析均以本目录为唯一厂商事实来源。</p>
 *
 * @author data-agent
 */
public enum ModelProviderCatalog {

    OPENAI("openai", "OpenAI / GPT", ProviderGroup.GLOBAL, "https://api.openai.com/v1", "gpt-5-mini",
            true, true, models("gpt-5", "gpt-5-mini", "gpt-4.1", "gpt-4.1-mini"), "gpt"),

    DEEPSEEK("deepseek", "DeepSeek 深度求索", ProviderGroup.DOMESTIC, "https://api.deepseek.com/v1",
            "deepseek-chat", true, true, models("deepseek-chat", "deepseek-reasoner")),

    QWEN("qwen", "通义千问", ProviderGroup.DOMESTIC,
            "https://dashscope.aliyuncs.com/compatible-mode/v1", "qwen-plus", true, true,
            models("qwen-max", "qwen-plus", "qwen-turbo"), "dashscope", "tongyi", "bailian", "alibaba"),

    KIMI("kimi", "Kimi 月之暗面", ProviderGroup.DOMESTIC, "https://api.moonshot.cn/v1", "kimi-k2.5",
            true, true, models("kimi-k2.5", "moonshot-v1-32k", "moonshot-v1-128k"), "moonshot"),

    ZHIPU("zhipu", "智谱 GLM", ProviderGroup.DOMESTIC, "https://open.bigmodel.cn/api/paas/v4", "glm-4.5",
            true, true, models("glm-4.5", "glm-4-plus", "glm-4-flash"), "glm", "chatglm", "bigmodel"),

    DOUBAO("doubao", "豆包（火山方舟）", ProviderGroup.DOMESTIC,
            "https://ark.cn-beijing.volces.com/api/v3", "doubao-1-5-pro-32k-250115", true, false,
            models("doubao-1-5-pro-32k-250115", "doubao-1-5-lite-32k-250115"),
            "ark", "volcengine", "huoshan"),

    HUNYUAN("hunyuan", "腾讯混元", ProviderGroup.DOMESTIC,
            "https://api.hunyuan.cloud.tencent.com/v1", "hunyuan-turbo", true, true,
            models("hunyuan-turbo", "hunyuan-large"), "tencent"),

    ERNIE("ernie", "文心一言（百度千帆）", ProviderGroup.DOMESTIC,
            "https://qianfan.baidubce.com/v2", "ernie-4.0-8k", true, true,
            models("ernie-4.0-8k", "ernie-3.5-8k"), "baidu", "qianfan", "wenxin"),

    SPARK("spark", "讯飞星火", ProviderGroup.DOMESTIC,
            "https://spark-api-open.xf-yun.com/v1", "generalv3.5", true, false,
            models("generalv3.5", "4.0Ultra"), "xunfei", "iflytek", "xinghuo"),

    MINIMAX("minimax", "MiniMax", ProviderGroup.DOMESTIC, "https://api.minimaxi.com/v1", "MiniMax-Text-01",
            true, true, models("MiniMax-Text-01", "MiniMax-M1")),

    BAICHUAN("baichuan", "百川智能", ProviderGroup.DOMESTIC,
            "https://api.baichuan-ai.com/v1", "Baichuan4-Turbo", true, true,
            models("Baichuan4-Turbo", "Baichuan3-Turbo")),

    YI("yi", "零一万物", ProviderGroup.DOMESTIC, "https://api.lingyiwanwu.com/v1", "yi-large",
            true, true, models("yi-large", "yi-medium"), "lingyiwanwu", "01ai"),

    STEPFUN("stepfun", "阶跃星辰", ProviderGroup.DOMESTIC, "https://api.stepfun.com/v1", "step-2-16k",
            true, true, models("step-2-16k", "step-1-32k"), "step"),

    SILICONFLOW("siliconflow", "硅基流动", ProviderGroup.AGGREGATOR,
            "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3", true, true,
            models("deepseek-ai/DeepSeek-V3", "deepseek-ai/DeepSeek-R1", "Qwen/Qwen2.5-72B-Instruct"),
            "silicon"),

    CUSTOM("custom", "自定义 GPT / OpenAI-compatible", ProviderGroup.CUSTOM, null, null,
            false, true, List.of(), "openai-compatible", "compatible", "gpt-compatible");

    public enum ProviderGroup {
        GLOBAL,
        DOMESTIC,
        AGGREGATOR,
        CUSTOM
    }

    public enum Protocol {
        OPENAI_COMPATIBLE
    }

    private final String key;
    private final String displayName;
    private final ProviderGroup group;
    private final String defaultBaseUrl;
    private final String defaultModelName;
    private final boolean apiKeyRequired;
    private final boolean modelDiscoverySupported;
    private final List<String> recommendedModels;
    private final String[] aliases;

    ModelProviderCatalog(String key, String displayName, ProviderGroup group, String defaultBaseUrl,
            String defaultModelName, boolean apiKeyRequired, boolean modelDiscoverySupported,
            List<String> recommendedModels, String... aliases) {
        this.key = key;
        this.displayName = displayName;
        this.group = group;
        this.defaultBaseUrl = defaultBaseUrl;
        this.defaultModelName = defaultModelName;
        this.apiKeyRequired = apiKeyRequired;
        this.modelDiscoverySupported = modelDiscoverySupported;
        this.recommendedModels = List.copyOf(recommendedModels);
        this.aliases = aliases;
    }

    public static Optional<ModelProviderCatalog> resolve(String provider) {
        if (provider == null || provider.isBlank()) {
            return Optional.empty();
        }
        String normalized = provider.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
                .filter(entry -> entry.key.equals(normalized) || Arrays.asList(entry.aliases).contains(normalized))
                .findFirst();
    }

    private static List<String> models(String... modelNames) {
        return List.of(modelNames);
    }

    public String key() {
        return key;
    }

    public String displayName() {
        return displayName;
    }

    public ProviderGroup group() {
        return group;
    }

    public Protocol protocol() {
        return Protocol.OPENAI_COMPATIBLE;
    }

    public String defaultBaseUrl() {
        return defaultBaseUrl;
    }

    public String defaultModelName() {
        return defaultModelName;
    }

    public boolean apiKeyRequired() {
        return apiKeyRequired;
    }

    public boolean modelDiscoverySupported() {
        return modelDiscoverySupported;
    }

    public List<String> recommendedModels() {
        return recommendedModels;
    }
}
