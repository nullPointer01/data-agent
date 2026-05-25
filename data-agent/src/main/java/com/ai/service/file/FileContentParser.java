package com.ai.service.file;

/**
 * Strategy for parsing one file type into text content.
 *
 * @author data-agent
 */
public interface FileContentParser {

    /**
     * Checks whether this parser can handle the given file.
     *
     * @param context normalized file context
     * @return true when the parser can handle the file
     */
    boolean supports(FileParsingContext context);

    /**
     * Parses the file into text.
     *
     * @param context normalized file context
     * @return parsed text content
     * @throws Exception when the file cannot be parsed
     */
    String parse(FileParsingContext context) throws Exception;
}
