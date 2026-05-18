package com.example.chatbot.ui.chat

import android.annotation.SuppressLint
import android.content.Context
import android.os.SystemClock
import android.text.format.DateFormat
import android.transition.ChangeBounds
import android.transition.TransitionManager
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
import androidx.core.view.isVisible

class ChatAdapter(
    private val markwonForText: Markwon,
    private val markwonForBlocks: Markwon,
    private val onRetryUserClick: (String) -> Unit,
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
                onRetryUserClick,
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
        private val onRetryUserClick: (String) -> Unit,
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

            if (item.showRetry) {
                binding.retryLink.visibility = View.VISIBLE
                binding.retryLink.setOnClickListener {
                    onRetryUserClick(item.id)
                }
            } else {
                binding.retryLink.visibility = View.GONE
                binding.retryLink.setOnClickListener(null)
            }
        }
    }

    private class AssistantViewHolder(
        private val binding: ItemChatMessageAssistantBinding,
        private val markwon: Markwon,
    ) : RecyclerView.ViewHolder(binding.root) {

        private val markdownBlockAdapter: MarkwonAdapter = createChatAssistantMarkdownBlockAdapter()

        private var lastStreamLayoutTransitionElapsed: Long = 0L

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

        @SuppressLint("NotifyDataSetChanged")
        fun bindBody(item: ChatListMessage) {
            if (item.isStreamingMarkdown) {
                binding.streamingPlainText.visibility = View.VISIBLE
                binding.markdownBlocks.visibility = View.GONE
                val prevText = binding.streamingPlainText.text?.toString().orEmpty()
                val contentGrowing = item.content.length > prevText.length
                val now = SystemClock.elapsedRealtime()
                if (contentGrowing &&
                    now - lastStreamLayoutTransitionElapsed >= STREAM_LAYOUT_TRANSITION_MIN_INTERVAL_MS
                ) {
                    lastStreamLayoutTransitionElapsed = now
                    TransitionManager.beginDelayedTransition(
                        binding.card,
                        ChangeBounds().apply {
                            duration = STREAM_LAYOUT_TRANSITION_DURATION_MS
                            interpolator = DecelerateInterpolator()
                        },
                    )
                }
                binding.streamingPlainText.text = item.content
            } else {
                lastStreamLayoutTransitionElapsed = 0L
                if (binding.streamingPlainText.isVisible) {
                    TransitionManager.beginDelayedTransition(
                        binding.card,
                        ChangeBounds().apply {
                            duration = STREAM_TO_MARKDOWN_TRANSITION_MS
                            interpolator = DecelerateInterpolator()
                        },
                    )
                }
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
            val marginEnd = res.getDimensionPixelSize(R.dimen.chat_assistant_bubble_margin_end)

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

        private const val STREAM_LAYOUT_TRANSITION_MIN_INTERVAL_MS = 120L
        private const val STREAM_LAYOUT_TRANSITION_DURATION_MS = 160L
        private const val STREAM_TO_MARKDOWN_TRANSITION_MS = 200L

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
                        oldItem.isStreamingMarkdown != newItem.isStreamingMarkdown ||
                        oldItem.showRetry != newItem.showRetry -> MessageBodyPayload
                    else -> null
                }
        }
    }
}
