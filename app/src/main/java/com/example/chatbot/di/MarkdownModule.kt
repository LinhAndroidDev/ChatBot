package com.example.chatbot.di

import android.content.Context
import android.graphics.Color
import androidx.core.content.ContextCompat
import com.example.chatbot.R
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.noties.markwon.AbstractMarkwonPlugin
import io.noties.markwon.Markwon
import io.noties.markwon.core.MarkwonTheme
import io.noties.markwon.ext.strikethrough.StrikethroughPlugin
import io.noties.markwon.ext.tables.TablePlugin
import com.example.chatbot.prism.ChatPrismGrammarLocator
import io.noties.markwon.recycler.table.TableEntryPlugin
import io.noties.markwon.syntax.Prism4jThemeDefault
import io.noties.markwon.syntax.SyntaxHighlightPlugin
import io.noties.prism4j.Prism4j
import javax.inject.Named
import javax.inject.Singleton

internal object ChatMarkwonNamed {
    const val TEXT = "chat_markwon_text"
    const val BLOCKS = "chat_markwon_blocks"
}

/**
 * Markwon treats color `0` as "unset" and falls back to a dimmed overlay on the text paint
 * ([MarkwonTheme.getCodeBlockBackgroundColor]). ARGB with alpha 0 but value != 0 is still fully
 * transparent when drawn, but skips that fallback.
 */
private val markwonTransparentNonZero: Int = Color.argb(0, 255, 255, 255)

@Module
@InstallIn(SingletonComponent::class)
object MarkdownModule {

    @Provides
    @Singleton
    @Named(ChatMarkwonNamed.TEXT)
    fun provideChatMarkwonText(@ApplicationContext context: Context): Markwon =
        buildChatMarkwon(context, useTablePluginForTextView = true)

    @Provides
    @Singleton
    @Named(ChatMarkwonNamed.BLOCKS)
    fun provideChatMarkwonBlocks(@ApplicationContext context: Context): Markwon =
        buildChatMarkwon(context, useTablePluginForTextView = false)

    private fun buildChatMarkwon(
        context: Context,
        useTablePluginForTextView: Boolean,
    ): Markwon {
        val prism4j = Prism4j(ChatPrismGrammarLocator())
        val codeFill = ContextCompat.getColor(context, R.color.chat_markdown_code_block_fill)
        val prismTheme = if (useTablePluginForTextView) {
            Prism4jThemeDefault.create(codeFill)
        } else {
            Prism4jThemeDefault.create(markwonTransparentNonZero)
        }
        val linkColor = ContextCompat.getColor(context, R.color.chat_markdown_link)
        val codeBlockBg = ContextCompat.getColor(context, R.color.chat_markdown_code_block_bg)
        val codeBlockText = ContextCompat.getColor(context, R.color.chat_markdown_code_text)
        val inlineCodeBg = ContextCompat.getColor(context, R.color.chat_markdown_inline_code_bg)
        val inlineCodeText = ContextCompat.getColor(context, R.color.chat_markdown_inline_code_text)
        val tablePlugin = TablePlugin.create(context)
        val builder = Markwon.builder(context)
            .usePlugin(object : AbstractMarkwonPlugin() {
                override fun configureTheme(themeBuilder: MarkwonTheme.Builder) {
                    themeBuilder
                        .linkColor(linkColor)
                        .codeBlockBackgroundColor(
                            if (useTablePluginForTextView) codeBlockBg else markwonTransparentNonZero,
                        )
                        .codeBlockTextColor(codeBlockText)
                        .codeBackgroundColor(inlineCodeBg)
                        .codeTextColor(inlineCodeText)
                        .headingBreakHeight(0)
                }
            })
            .usePlugin(StrikethroughPlugin.create())
            .usePlugin(
                if (useTablePluginForTextView) {
                    tablePlugin
                } else {
                    TableEntryPlugin.create(tablePlugin)
                },
            )
            .usePlugin(SyntaxHighlightPlugin.create(prism4j, prismTheme))
            .usePlugin(
                object : AbstractMarkwonPlugin() {
                    override fun configureTheme(themeBuilder: MarkwonTheme.Builder) {
                        if (!useTablePluginForTextView) {
                            // SyntaxHighlight overwrites codeBackgroundColor with Prism theme.background();
                            // restore inline code surface while keeping code blocks visually on the card only.
                            themeBuilder.codeBackgroundColor(inlineCodeBg)
                        }
                    }
                },
            )
        return builder.build()
    }
}
