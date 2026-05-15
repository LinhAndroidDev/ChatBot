package com.example.chatbot.ui.main

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.text.Editable
import android.text.TextWatcher
import android.view.MenuItem
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.chatbot.R
import com.example.chatbot.di.ChatMarkwonNamed
import com.example.chatbot.databinding.ActivityMainBinding
import com.example.chatbot.ui.chat.ChatAdapter
import com.example.chatbot.ui.chat.ChatSessionSummary
import com.example.chatbot.ui.chat.ChatViewModel
import com.google.android.material.snackbar.Snackbar
import dagger.hilt.android.AndroidEntryPoint
import io.noties.markwon.Markwon
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Named

@AndroidEntryPoint
class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    @Inject
    @Named(ChatMarkwonNamed.TEXT)
    lateinit var chatMarkwonText: Markwon

    @Inject
    @Named(ChatMarkwonNamed.BLOCKS)
    lateinit var chatMarkwonBlocks: Markwon

    private lateinit var binding: ActivityMainBinding
    private lateinit var drawerToggle: ActionBarDrawerToggle
    private val viewModel: ChatViewModel by viewModels()
    private lateinit var chatAdapter: ChatAdapter

    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var lastError: String? = null

    private val sessionTimeFormat: DateFormat =
        DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)

    private val requestMicPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            startVoiceInput()
        } else {
            Toast.makeText(this, R.string.mic_permission_required, Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            v.setPadding(
                bars.left,
                bars.top,
                bars.right,
                maxOf(bars.bottom, ime.bottom),
            )
            insets
        }

        setSupportActionBar(binding.toolbar)
        drawerToggle = object : ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.nav_drawer_open,
            R.string.nav_drawer_close,
        ) {
            override fun onDrawerOpened(drawerView: View) {
                super.onDrawerOpened(drawerView)
                if (drawerView.id == R.id.nav_chat_sessions) {
                    viewModel.refreshRecentSessions()
                }
            }
        }
        binding.drawerLayout.addDrawerListener(drawerToggle)
        drawerToggle.syncState()

        binding.toolbar.inflateMenu(R.menu.main_toolbar)
        binding.toolbar.setOnMenuItemClickListener(::onToolbarMenuItemSelected)

        binding.navChatSessions.setNavigationItemSelectedListener(::onDrawerSessionSelected)

        chatAdapter = ChatAdapter(chatMarkwonText, chatMarkwonBlocks)

        tts = TextToSpeech(this, this)

        binding.chatList.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.chatList.adapter = chatAdapter

        binding.btnSend.setOnClickListener { sendMessageFromInput() }

        binding.input.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                sendMessageFromInput()
                true
            } else {
                false
            }
        }

        binding.btnMic.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                startVoiceInput()
            } else {
                requestMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
        }

        binding.input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                updateSendButtonState()
            }
        })

        binding.fabNewChat.setOnClickListener {
            viewModel.startNewChat()
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.uiState.collect { state ->
                        chatAdapter.submitList(state.messages) {
                            if (state.messages.isNotEmpty()) {
                                binding.chatList.scrollToPosition(state.messages.lastIndex)
                            }
                        }
                        binding.progress.isVisible = state.isLoading
                        binding.btnMic.isEnabled = !state.isLoading
                        binding.input.isEnabled = !state.isLoading
                        updateSendButtonState()

                        val err = state.error
                        if (err != null && err != lastError) {
                            lastError = err
                            Snackbar.make(binding.main, err, Snackbar.LENGTH_LONG)
                                .setAction(android.R.string.ok) { viewModel.clearError() }
                                .addCallback(
                                    object : Snackbar.Callback() {
                                        override fun onDismissed(
                                            transientBottomBar: Snackbar?,
                                            event: Int,
                                        ) {
                                            viewModel.clearError()
                                        }
                                    },
                                )
                                .show()
                        } else if (err == null) {
                            lastError = null
                        }
                    }
                }
                launch {
                    viewModel.recentSessions.collect { sessions ->
                        rebuildDrawerSessionMenu(sessions)
                    }
                }
                launch {
                    viewModel.ttsPrompts.collect { text ->
                        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UTTERANCE_ID)
                    }
                }
            }
        }

        updateSendButtonState()
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        drawerToggle.syncState()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        drawerToggle.onConfigurationChanged(newConfig)
    }

    private fun onToolbarMenuItemSelected(item: MenuItem): Boolean {
        if (item.itemId == R.id.action_open_sessions) {
            viewModel.refreshRecentSessions()
            binding.drawerLayout.openDrawer(GravityCompat.START)
            return true
        }
        return false
    }

    private fun onDrawerSessionSelected(item: MenuItem): Boolean {
        val sid = item.intent?.getStringExtra(EXTRA_SESSION_ID) ?: return false
        viewModel.openSession(sid)
        binding.drawerLayout.closeDrawer(GravityCompat.START)
        return true
    }

    private fun rebuildDrawerSessionMenu(sessions: List<ChatSessionSummary>) {
        val menu = binding.navChatSessions.menu
        menu.removeGroup(R.id.group_chat_sessions)
        sessions.forEachIndexed { index, session ->
            val title = sessionMenuTitle(session)
            val menuItem = menu.add(R.id.group_chat_sessions, View.generateViewId(), index, title)
            menuItem.intent = Intent().putExtra(EXTRA_SESSION_ID, session.id)
        }
    }

    private fun sessionMenuTitle(session: ChatSessionSummary): String {
        val time = sessionTimeFormat.format(Date(session.updatedAtMillis))
        return "${session.title}\n$time"
    }

    private fun sendMessageFromInput() {
        val text = binding.input.text?.toString().orEmpty()
        if (text.isBlank()) {
            Toast.makeText(this, R.string.error_empty_message, Toast.LENGTH_SHORT).show()
            return
        }
        viewModel.sendUserMessage(text)
        binding.input.text?.clear()
        updateSendButtonState()
    }

    private fun updateSendButtonState() {
        val hasText = binding.input.text?.isNotBlank() == true
        binding.btnSend.isEnabled = hasText && !viewModel.uiState.value.isLoading
    }

    private fun startVoiceInput() {
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Toast.makeText(this, R.string.speech_not_supported, Toast.LENGTH_SHORT).show()
            return
        }
        if (speechRecognizer == null) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(RecognitionListenerImpl())
            }
        }
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "vi-VN")
        }
        speechRecognizer?.startListening(intent)
    }

    override fun onInit(status: Int) {
        if (status != TextToSpeech.SUCCESS) return
        val loc = Locale.forLanguageTag("vi-VN")
        val result = tts?.setLanguage(loc) ?: TextToSpeech.ERROR
        if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
            tts?.setLanguage(Locale.ROOT)
        }
    }

    override fun onDestroy() {
        speechRecognizer?.destroy()
        speechRecognizer = null
        tts?.stop()
        tts?.shutdown()
        tts = null
        super.onDestroy()
    }

    private inner class RecognitionListenerImpl : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onRmsChanged(rmsdB: Float) = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEndOfSpeech() = Unit
        override fun onPartialResults(partialResults: Bundle?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onError(error: Int) {
            val msg = speechErrorMessage(error) ?: return
            Toast.makeText(this@MainActivity, msg, Toast.LENGTH_SHORT).show()
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val spoken = matches?.firstOrNull().orEmpty()
            if (spoken.isNotBlank()) {
                binding.input.setText(spoken)
                binding.input.setSelection(spoken.length)
                updateSendButtonState()
            }
        }

        private fun speechErrorMessage(error: Int): String? = when (error) {
            SpeechRecognizer.ERROR_AUDIO -> "Lỗi ghi âm"
            SpeechRecognizer.ERROR_CLIENT -> null
            SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> getString(R.string.mic_permission_required)
            SpeechRecognizer.ERROR_NETWORK -> "Lỗi mạng nhận dạng giọng nói"
            SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Hết giờ mạng"
            SpeechRecognizer.ERROR_NO_MATCH -> "Không nhận dạng được"
            SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Dịch vụ đang bận"
            SpeechRecognizer.ERROR_SERVER -> "Lỗi máy chủ"
            SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Không nghe thấy giọng nói"
            else -> "Lỗi nhận dạng ($error)"
        }
    }

    companion object {
        private const val EXTRA_SESSION_ID = "session_id"
        private const val UTTERANCE_ID = "chat_bot_reply"
    }
}
