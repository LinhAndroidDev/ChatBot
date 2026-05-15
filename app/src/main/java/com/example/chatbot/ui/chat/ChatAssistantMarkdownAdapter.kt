package com.example.chatbot.ui.chat

import com.example.chatbot.R
import io.noties.markwon.recycler.MarkwonAdapter
import io.noties.markwon.recycler.table.TableEntry
import org.commonmark.ext.gfm.tables.TableBlock
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock

/**
 * Markwon [MarkwonAdapter] for assistant bubbles: one row per top-level markdown block,
 * fenced / indented code in [HorizontalScrollView] for horizontal panning.
 */
fun createChatAssistantMarkdownBlockAdapter(): MarkwonAdapter {
    return MarkwonAdapter.builderTextViewIsRoot(R.layout.item_chat_markdown_block_default)
        .include(FencedCodeBlock::class.java, ChatFencedCodeBlockEntry())
        .include(IndentedCodeBlock::class.java, ChatIndentedCodeBlockEntry())
        .include(
            TableBlock::class.java,
            TableEntry.create { builder ->
                builder.tableLayout(
                    R.layout.item_chat_markdown_table_block,
                    R.id.chat_markdown_table,
                ).textLayoutIsRoot(R.layout.item_chat_markdown_table_cell)
            },
        )
        .build()
}
