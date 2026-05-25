package com.ai.agent.react;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ReActResponseParserTest {

    private final ReActResponseParser parser = new ReActResponseParser();

    @Test
    void parseStructuredArrayToolCall() {
        ReActToolCall toolCall = parser.parseToolCall("""
                {"tool":"useSkill","arguments":["销售分析","分析 Q3 销售趋势"]}
                """);

        assertEquals("useSkill", toolCall.name());
        assertEquals("销售分析", toolCall.argument(0));
        assertEquals("分析 Q3 销售趋势", toolCall.argument(1));
    }

    @Test
    void parseStructuredObjectArgumentsByKnownToolOrder() {
        ReActToolCall toolCall = parser.parseToolCall("""
                {"tool":"executeSQL","arguments":{"sql":"select * from orders where city = '上海'","datasourceName":"crm"}}
                """);

        assertEquals("executeSQL", toolCall.name());
        assertEquals("crm", toolCall.argument(0));
        assertEquals("select * from orders where city = '上海'", toolCall.argument(1));
    }

    @Test
    void parseOpenAiFunctionCallWithJsonStringArguments() {
        ReActToolCall toolCall = parser.parseToolCall("""
                {
                  "function_call": {
                    "name": "executeSQL",
                    "arguments": "{\\"datasourceName\\":\\"crm\\",\\"sql\\":\\"select count(*) from orders\\"}"
                  }
                }
                """);

        assertEquals("executeSQL", toolCall.name());
        assertEquals("crm", toolCall.argument(0));
        assertEquals("select count(*) from orders", toolCall.argument(1));
    }

    @Test
    void parseOpenAiToolCallsWithFunctionPayload() {
        ReActToolCall toolCall = parser.parseToolCall("""
                {
                  "tool_calls": [
                    {
                      "type": "function",
                      "function": {
                        "name": "searchKnowledge",
                        "arguments": "{\\"query\\":\\"退款规则\\"}"
                      }
                    }
                  ]
                }
                """);

        assertEquals("searchKnowledge", toolCall.name());
        assertEquals("退款规则", toolCall.argument(0));
    }

    @Test
    void parseStringArgumentsWithCommaAndParentheses() {
        ReActToolCall toolCall = parser.parseToolCall("""
                {"tool":"calculate","arguments":"sum(1, 2) + max(3, 4)"}
                """);

        assertEquals("calculate", toolCall.name());
        assertEquals("sum(1, 2) + max(3, 4)", toolCall.argument(0));
    }

    @Test
    void parseJsonStringArrayArguments() {
        ReActToolCall toolCall = parser.parseToolCall("""
                {"tool":"useSkill","arguments":"[\\"销售分析\\",\\"分析 Q3 销售趋势\\"]"}
                """);

        assertEquals("useSkill", toolCall.name());
        assertEquals("销售分析", toolCall.argument(0));
        assertEquals("分析 Q3 销售趋势", toolCall.argument(1));
    }

    @Test
    void parseStructuredCallWithJsonArgument() {
        ReActToolCall toolCall = parser.parseToolCall("""
                {"tool":"generateChart","arguments":["bar",{"x":["A","B"],"y":[1,2]},"标题"]}
                """);

        assertEquals("generateChart", toolCall.name());
        assertEquals("bar", toolCall.argument(0));
        assertEquals("{\"x\":[\"A\",\"B\"],\"y\":[1,2]}", toolCall.argument(1));
        assertEquals("标题", toolCall.argument(2));
    }

    @Test
    void parseLegacyCallAsPlainAnswer() {
        ReActToolCall toolCall = parser.parseToolCall("""
                [CALL:calculate("1+1")]
                """);

        assertNull(toolCall);
    }

    @Test
    void parseNameOnlyJsonAsPlainAnswer() {
        ReActToolCall toolCall = parser.parseToolCall("""
                {"name":"张三","role":"运营"}
                """);

        assertNull(toolCall);
    }

    @Test
    void cleanAssistantAnswerRemovesStructuredToolCall() {
        String answer = parser.cleanAssistantAnswer("""
                [思考] 需要查库
                {"tool":"searchKnowledge","arguments":["退款规则"]}
                """);

        assertEquals("", answer);
    }

    @Test
    void cleanAssistantAnswerRemovesStandardFunctionCall() {
        String answer = parser.cleanAssistantAnswer("""
                Thought: 需要查知识库
                {"function_call":{"name":"searchKnowledge","arguments":"{\\"query\\":\\"退款规则\\"}"}}
                """);

        assertEquals("", answer);
    }
}
