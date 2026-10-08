package com.ai.vector;

import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.splitter.DocumentByCharacterSplitter;
import dev.langchain4j.data.document.splitter.DocumentByLineSplitter;
import dev.langchain4j.data.document.splitter.DocumentByParagraphSplitter;
import dev.langchain4j.data.document.splitter.DocumentByRegexSplitter;
import dev.langchain4j.data.document.splitter.DocumentBySentenceSplitter;
import dev.langchain4j.data.document.splitter.DocumentByWordSplitter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** 官方分块组件集中装配；TextChunker 只负责章节、来源位置与父上下文。 */
@Configuration
public class DocumentSplitterConfiguration {
    // 零宽句界保留标点与闭合引号；连续句末标点不拆成单独的块。
    private static final String CHINESE_SENTENCE_BOUNDARY = "(?<=[。！？；][”’」』）]{0,4})(?![。！？；”’」』）])";

    @Bean
    public DocumentSplitter documentSplitter(@Value("${app.vector.chunk-size:500}") int size,
            @Value("${app.vector.chunk-overlap:100}") int overlap) {
        if (size <= 0 || overlap < 0 || overlap >= size) {
            throw new IllegalArgumentException("分块参数必须满足 size > 0 且 0 <= overlap < size");
        }
        DocumentSplitter characters = new DocumentByCharacterSplitter(size, overlap) {
            @Override
            public String[] split(String text) {
                return text.codePoints().mapToObj(cp -> new String(Character.toChars(cp))).toArray(String[]::new);
            }
        };
        DocumentSplitter words = new DocumentByWordSplitter(size, overlap, characters);
        DocumentSplitter englishSentences = new DocumentBySentenceSplitter(size, overlap, words);
        DocumentSplitter chineseSentences = new DocumentByRegexSplitter(
                CHINESE_SENTENCE_BOUNDARY, "", size, overlap, englishSentences);
        // 长句继续递归降级；重叠仍由框架实现，不能宣称已替换其内部英文重叠断句。
        return new DocumentByParagraphSplitter(size, overlap,
                new DocumentByLineSplitter(size, overlap, chineseSentences));
    }
}
