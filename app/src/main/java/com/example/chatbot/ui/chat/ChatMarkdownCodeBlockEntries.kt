package com.example.chatbot.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.SpannableStringBuilder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.example.chatbot.R
import io.noties.markwon.Markwon
import io.noties.markwon.recycler.MarkwonAdapter
import org.commonmark.node.FencedCodeBlock
import org.commonmark.node.IndentedCodeBlock
import org.commonmark.node.Node

internal class ChatMarkdownCodeBlockHolder(itemView: View) : MarkwonAdapter.Holder(itemView) {
    val codeView: TextView = itemView.findViewById(R.id.chat_markdown_code_text)
    val copyButton: ImageView = itemView.findViewById(R.id.chat_markdown_code_copy)
}

internal class ChatFencedCodeBlockEntry : MarkwonAdapter.Entry<FencedCodeBlock, ChatMarkdownCodeBlockHolder>() {

    override fun createHolder(inflater: LayoutInflater, parent: ViewGroup): ChatMarkdownCodeBlockHolder {
        val view = inflater.inflate(R.layout.item_chat_markdown_fenced_code, parent, false)
        return ChatMarkdownCodeBlockHolder(view)
    }

    override fun bindHolder(markwon: Markwon, holder: ChatMarkdownCodeBlockHolder, node: FencedCodeBlock) {
        val plain = node.literal.trim()
        bindCodeBlockMarkdown(markwon, holder.codeView, node)
        holder.copyButton.setOnClickListener { copyCode(it.context, plain) }
    }
}

internal class ChatIndentedCodeBlockEntry : MarkwonAdapter.Entry<IndentedCodeBlock, ChatMarkdownCodeBlockHolder>() {

    override fun createHolder(inflater: LayoutInflater, parent: ViewGroup): ChatMarkdownCodeBlockHolder {
        val view = inflater.inflate(R.layout.item_chat_markdown_fenced_code, parent, false)
        return ChatMarkdownCodeBlockHolder(view)
    }

    override fun bindHolder(markwon: Markwon, holder: ChatMarkdownCodeBlockHolder, node: IndentedCodeBlock) {
        val plain = node.literal.trim()
        bindCodeBlockMarkdown(markwon, holder.codeView, node)
        holder.copyButton.setOnClickListener { copyCode(it.context, plain) }
    }
}

/**
 * [io.noties.markwon.core.CorePlugin] prefixes fenced/indented code with NBSP + newline and
 * appends a trailing NBSP. That yields an empty first line (large gap below the copy row) even
 * when XML margins are zero.
 */
private fun bindCodeBlockMarkdown(markwon: Markwon, textView: TextView, node: Node) {
    val rendered = markwon.render(node)
    val ssb = SpannableStringBuilder(rendered)
    stripMarkwonCoreCodeBlockFiller(ssb)
    markwon.setParsedMarkdown(textView, ssb)
}

private fun stripMarkwonCoreCodeBlockFiller(content: SpannableStringBuilder) {
    if (content.length >= 2 && content[0] == NBSP && content[1] == '\n') {
        content.delete(0, 2)
    }
    if (content.isNotEmpty() && content[content.length - 1] == NBSP) {
        content.delete(content.length - 1, content.length)
    }
}

private const val NBSP = '\u00a0'

private fun copyCode(context: Context, plain: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("code", plain))
    Toast.makeText(context, R.string.chat_code_copied, Toast.LENGTH_SHORT).show()
}
