package com.example.chatbot.prism;

import io.noties.prism4j.annotations.PrismBundle;

/**
 * Annotation-only hook for prism4j-bundler. Build generates ChatPrismGrammarLocator
 * in this package (see grammarLocatorClassName).
 */
@PrismBundle(
        include = {
                "clike",
                "java",
                "kotlin",
                "javascript",
                "json",
                "python",
                "sql",
                "yaml",
                "markdown",
                "markup",
                "css",
                "go",
                "csharp",
                "cpp",
                "c",
                "swift",
                "scala",
                "groovy",
                "dart",
                "git",
        },
        grammarLocatorClassName = ".ChatPrismGrammarLocator"
)
public final class PrismBundleTrigger {
    private PrismBundleTrigger() {
    }
}
