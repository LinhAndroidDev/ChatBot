package com.example.chatbot.di

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import androidx.appcompat.app.AppCompatDelegate
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

internal object ChatMarkwonNamed {
    const val TEXT = "chat_markwon_text"
    const val BLOCKS = "chat_markwon_blocks"
}

/**
 * [Prism4jThemeDefault] uses [Prism4jTheme.textColor] ≈ black for tokens without a Prism class.
 * [SyntaxHighlightPlugin] applies that as [MarkwonTheme.codeBlockTextColor], which hides
 * identifiers on dark code cards. This theme keeps Prism token map but uses app foreground.
 */
private class ChatPrism4jTheme(
    prismBackground: Int,
    private val defaultCodeForeground: Int,
) : Prism4jThemeDefault(prismBackground) {
    override fun textColor(): Int = defaultCodeForeground
}

/**
 * Markwon treats color `0` as "unset" and falls back to a dimmed overlay on the text paint
 * ([MarkwonTheme.getCodeBlockBackgroundColor]). ARGB with alpha 0 but value != 0 is still fully
 * transparent when drawn, but skips that fallback.
 */
private val markwonTransparentNonZero: Int = Color.argb(0, 255, 255, 255)

/**
 * [ApplicationContext] đôi khi chưa kịp [Configuration.uiMode] khớp [AppCompatDelegate] sau khi ép sáng/tối,
 * nên [ContextCompat.getColor] vẫn trả màu `values` thay vì `values-night`. Context này ép night bit khi cần.
 */
private fun markwonColorContext(base: Context): Context {
    val forcedNight = when (AppCompatDelegate.getDefaultNightMode()) {
        AppCompatDelegate.MODE_NIGHT_YES -> Configuration.UI_MODE_NIGHT_YES
        AppCompatDelegate.MODE_NIGHT_NO -> Configuration.UI_MODE_NIGHT_NO
        else -> null
    }
    if (forcedNight == null) return base
    val cfg = Configuration(base.resources.configuration)
    cfg.uiMode = (cfg.uiMode and Configuration.UI_MODE_NIGHT_MASK.inv()) or forcedNight
    return base.createConfigurationContext(cfg)
}

@Module
@InstallIn(SingletonComponent::class)
object MarkdownModule {

    // Markwon không @Singleton: màu theme (light/dark) được bake vào MarkwonTheme lúc build.
    // Nếu singleton, đổi dark mode sau khi mở app vẫn giữ màu cũ → chữ code block trùng nền, gần như biến mất.

    @Provides
    @Named(ChatMarkwonNamed.TEXT)
    fun provideChatMarkwonText(@ApplicationContext context: Context): Markwon =
        buildChatMarkwon(context, useTablePluginForTextView = true)

    @Provides
    @Named(ChatMarkwonNamed.BLOCKS)
    fun provideChatMarkwonBlocks(@ApplicationContext context: Context): Markwon =
        buildChatMarkwon(context, useTablePluginForTextView = false)

    private fun buildChatMarkwon(
        context: Context,
        useTablePluginForTextView: Boolean,
    ): Markwon {
        val cc = markwonColorContext(context)
        val prism4j = Prism4j(ChatPrismGrammarLocator())
        val codeFill = ContextCompat.getColor(cc, R.color.chat_markdown_code_block_fill)
        val linkColor = ContextCompat.getColor(cc, R.color.chat_markdown_link)
        val codeBlockBg = ContextCompat.getColor(cc, R.color.chat_markdown_code_block_bg)
        val codeBlockText = ContextCompat.getColor(cc, R.color.chat_markdown_code_text)
        val inlineCodeBg = ContextCompat.getColor(cc, R.color.chat_markdown_inline_code_bg)
        val inlineCodeText = ContextCompat.getColor(cc, R.color.chat_markdown_inline_code_text)
        val prismBackground = if (useTablePluginForTextView) codeFill else markwonTransparentNonZero
        val prismTheme = ChatPrism4jTheme(prismBackground, codeBlockText)
        val tablePlugin = TablePlugin.create(cc)
        val builder = Markwon.builder(cc)
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
                        // Ghi đè lại sau SyntaxHighlight: codeTextColor bị gán = textColor Prism.
                        themeBuilder
                            .codeBlockTextColor(codeBlockText)
                            .codeTextColor(inlineCodeText)
                    }
                },
            )
        return builder.build()
    }
}
