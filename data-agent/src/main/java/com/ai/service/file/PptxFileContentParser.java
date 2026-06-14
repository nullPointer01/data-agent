package com.ai.service.file;

import org.apache.poi.xslf.usermodel.XMLSlideShow;
import org.apache.poi.xslf.usermodel.XSLFShape;
import org.apache.poi.xslf.usermodel.XSLFSlide;
import org.apache.poi.xslf.usermodel.XSLFTextShape;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 将 PPTX 文件解析为文本。
 *
 * @author data-agent
 */
@Component
@Order(40)
public class PptxFileContentParser implements FileContentParser {

    @Override
    public boolean supports(FileParsingContext context) {
        return context.lowerFilename().endsWith(FileTypeSupport.FILE_EXTENSION_PPTX);
    }

    @Override
    public String parse(FileParsingContext context) throws Exception {
        try (InputStream inputStream = context.openInputStream();
                XMLSlideShow slideshow = new XMLSlideShow(inputStream)) {
            StringBuilder content = new StringBuilder();
            for (XSLFSlide slide : slideshow.getSlides()) {
                for (XSLFShape shape : slide.getShapes()) {
                    if (shape instanceof XSLFTextShape textShape) {
                        content.append(textShape.getText()).append('\n');
                    }
                }
                content.append("\n---\n");
            }
            return content.toString();
        }
    }
}
