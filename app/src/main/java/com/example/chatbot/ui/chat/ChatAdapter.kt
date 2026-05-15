package com.example.chatbot.ui.chat

import android.content.Context
import android.text.format.DateFormat
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.chatbot.R
import com.example.chatbot.databinding.ItemChatMessageAssistantBinding
import com.example.chatbot.databinding.ItemChatMessageUserBinding
import io.noties.markwon.Markwon
import io.noties.markwon.recycler.MarkwonAdapter
import java.util.Date

class ChatAdapter(
    private val markwonForText: Markwon,
    private val markwonForBlocks: Markwon,
) : ListAdapter<ChatListMessage, RecyclerView.ViewHolder>(DIFF) {

    override fun getItemViewType(position: Int): Int = when (getItem(position).speaker) {
        ChatSpeaker.USER -> VIEW_TYPE_USER
        ChatSpeaker.ASSISTANT -> VIEW_TYPE_ASSISTANT
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            VIEW_TYPE_USER -> UserViewHolder(
                ItemChatMessageUserBinding.inflate(inflater, parent, false),
                markwonForText,
            )
            else -> AssistantViewHolder(
                ItemChatMessageAssistantBinding.inflate(inflater, parent, false),
                markwonForBlocks,
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        onBindViewHolder(holder, position, mutableListOf())
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int, payloads: MutableList<Any>) {
        val item = getItem(position)
        when (holder) {
            is UserViewHolder -> holder.bind(item)
            is AssistantViewHolder -> {
                if (payloads.isEmpty() || !payloads.contains(MessageBodyPayload)) {
                    holder.bind(item)
                } else {
                    holder.bindBody(item)
                }
            }
        }
    }

    private class UserViewHolder(
        private val binding: ItemChatMessageUserBinding,
        private val markwon: Markwon,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ChatListMessage) {
            val ctx = binding.root.context
            val res = ctx.resources
            val tail = res.getDimensionPixelSize(R.dimen.chat_bubble_tail_space)
            val innerRowPx = rowInnerWidthPx(binding.root)
            val capOuterPx = res.getDimensionPixelSize(R.dimen.chat_bubble_max_width)
            val bubbleMaxPx = minOf((innerRowPx - tail).coerceAtLeast(0), capOuterPx)

            val lp = binding.card.layoutParams as FrameLayout.LayoutParams
            lp.gravity = Gravity.END
            lp.width = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.marginStart = tail
            lp.marginEnd = 0
            binding.card.layoutParams = lp

            binding.timeText.text = DateFormat.getTimeFormat(ctx).format(Date(item.sentAtMillis))
            binding.card.strokeWidth = res.getDimensionPixelSize(R.dimen.chat_bubble_stroke_width)
            binding.card.cardElevation = 0f

            binding.roleLabel.setText(R.string.chat_role_user)
            binding.roleLabel.setTextColor(ContextCompat.getColor(ctx, R.color.chat_user_role))
            binding.timeText.setTextColor(ContextCompat.getColor(ctx, R.color.chat_user_time))
            binding.card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.chat_user_bubble))
            binding.card.strokeColor = ContextCompat.getColor(ctx, R.color.chat_user_bubble_stroke)

            binding.messageText.setTextColor(ContextCompat.getColor(ctx, R.color.chat_user_message))
            binding.messageText.maxWidth = contentInnerMaxWidthPx(ctx, bubbleMaxPx)
            markwon.setMarkdown(binding.messageText, item.content)
        }
    }

    private class AssistantViewHolder(
        private val binding: ItemChatMessageAssistantBinding,
        private val markwon: Markwon,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val markdownBlockAdapter: MarkwonAdapter = createChatAssistantMarkdownBlockAdapter()

        init {
            binding.markdownBlocks.layoutManager = LinearLayoutManager(
                binding.markdownBlocks.context,
                RecyclerView.VERTICAL,
                false,
            )
            binding.markdownBlocks.adapter = markdownBlockAdapter
            binding.markdownBlocks.isNestedScrollingEnabled = false
            binding.markdownBlocks.itemAnimator = null
        }

        fun bind(item: ChatListMessage) {
            bindChrome(item)
            bindBody(item)
        }

        fun bindBody(item: ChatListMessage) {
            if (item.isStreamingMarkdown) {
                binding.streamingPlainText.visibility = View.VISIBLE
                binding.markdownBlocks.visibility = View.GONE
                binding.streamingPlainText.text = item.content
            } else {
                binding.streamingPlainText.visibility = View.GONE
                binding.markdownBlocks.visibility = View.VISIBLE
                markdownBlockAdapter.setMarkdown(markwon, item.content)
                markdownBlockAdapter.notifyDataSetChanged()
            }
        }

        private fun bindChrome(item: ChatListMessage) {
            val ctx = binding.root.context
            val res = ctx.resources
            val marginStart = res.getDimensionPixelSize(R.dimen.chat_assistant_bubble_side_inset)
            val marginEnd = res.getDimensionPixelSize(R.dimen.chat_assistant_bubble_side_inset)

            val lp = binding.card.layoutParams as FrameLayout.LayoutParams
            lp.gravity = Gravity.START
            lp.width = ViewGroup.LayoutParams.MATCH_PARENT
            lp.height = ViewGroup.LayoutParams.WRAP_CONTENT
            lp.marginStart = marginStart
            lp.marginEnd = marginEnd
            binding.card.layoutParams = lp

            binding.timeText.text = DateFormat.getTimeFormat(ctx).format(Date(item.sentAtMillis))
            binding.card.strokeWidth = res.getDimensionPixelSize(R.dimen.chat_bubble_stroke_width)
            binding.card.cardElevation = 0f

            binding.roleLabel.setText(R.string.chat_role_assistant)
            binding.roleLabel.setTextColor(ContextCompat.getColor(ctx, R.color.chat_assistant_role))
            binding.timeText.setTextColor(ContextCompat.getColor(ctx, R.color.chat_assistant_time))
            binding.card.setCardBackgroundColor(ContextCompat.getColor(ctx, R.color.chat_assistant_bubble))
            binding.card.strokeColor = ContextCompat.getColor(ctx, R.color.chat_assistant_bubble_stroke)
        }
    }

    companion object {
        private const val VIEW_TYPE_USER = 0
        private const val VIEW_TYPE_ASSISTANT = 1

        private object MessageBodyPayload

        private fun rowInnerWidthPx(root: View): Int {
            val res = root.resources
            val w = root.width
            val inner = if (w > 0) {
                w - root.paddingLeft - root.paddingRight
            } else {
                res.displayMetrics.widthPixels -
                    2 * res.getDimensionPixelSize(R.dimen.chat_screen_padding_h)
            }
            return inner.coerceAtLeast(0)
        }

        private fun contentInnerMaxWidthPx(ctx: Context, bubbleMaxOuterPx: Int): Int {
            val pad = 2 * ctx.resources.getDimensionPixelSize(R.dimen.chat_bubble_padding_h)
            return (bubbleMaxOuterPx - pad).coerceAtLeast(
                (160 * ctx.resources.displayMetrics.density).toInt(),
            )
        }

        private val DIFF = object : DiffUtil.ItemCallback<ChatListMessage>() {
            override fun areItemsTheSame(oldItem: ChatListMessage, newItem: ChatListMessage): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: ChatListMessage, newItem: ChatListMessage): Boolean =
                oldItem == newItem

            override fun getChangePayload(oldItem: ChatListMessage, newItem: ChatListMessage): Any? =
                when {
                    oldItem.id != newItem.id -> null
                    oldItem.content != newItem.content ||
                        oldItem.isStreamingMarkdown != newItem.isStreamingMarkdown -> MessageBodyPayload
                    else -> null
                }
        }
    }
}
