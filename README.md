# ChatBot (Android + Ollama)

Ứng dụng chat Android kết nối **Ollama** qua **`POST /api/chat`** với **`stream: true`** (NDJSON), hiển thị phản hồi dạng **stream** rồi render **markdown** khi xong. Lịch sử cục bộ **Realm** (phiên + tin nhắn, có cờ **Thử lại** sau lỗi). Giao diện **Material 3**, **sáng/tối** (công tắc trong drawer), markdown (**Markwon** + **Prism**), nhập giọng (**SpeechRecognizer**) và đọc phản hồi (**TextToSpeech**, tiếng Việt).

## Ảnh mô tả dự án
<table align="center">
  <tr>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/8b357f34-9d49-4c99-9d5c-151479fef6f7" width="260"/>
    </td>
    <td width="30"></td>
    <td align="center">
      <img src="https://github.com/user-attachments/assets/7b1d8f22-917d-4d18-ba04-5399b719e658" width="260"/>
    </td>
  </tr>
</table>

<p align="center">
  <img src="https://github.com/user-attachments/assets/da04c636-906d-47ae-8a77-6db0e6d32984" width="320"/>
</p>

---

## Yêu cầu

| Thành phần | Ghi chú |
|-------------|---------|
| **Android Studio** | Bản hỗ trợ Gradle trong repo (xem `gradle/wrapper`) |
| **JDK 17** | `jvmToolchain(17)` trong `app/build.gradle.kts` |
| **Ollama** | Chạy trên máy tính cùng mạng với thiết bị/emulator |
| **Thiết bị** | `minSdk 24` |

---

## Cài đặt Ollama trên máy tính

1. **Cài Ollama** theo hướng dẫn chính thức: [https://ollama.com](https://ollama.com) (Windows / macOS / Linux).

2. **Kéo model** trùng với model app đang cấu hình (mục [Đổi model Ollama](#đổi-model-ollama)):

   ```bash
   ollama pull qwen2.5:7b
   ```

3. **Chạy server** (thường tự chạy sau khi cài; nếu cần kiểm tra):

   ```bash
   ollama serve
   ```

   Mặc định API lắng nghe **`http://127.0.0.1:11434`**.

4. **Thử nhanh API chat (stream)** (tùy chọn):

   ```bash
   curl -N http://127.0.0.1:11434/api/chat -d '{
     "model": "qwen2.5:7b",
     "messages": [{"role":"user","content":"Xin chào"}],
     "stream": true
   }'
   ```

Ứng dụng gọi **`POST {baseUrl}/api/chat`** với body `OllamaChatRequest` (`model`, `messages`, `stream: true`), đọc từng dòng NDJSON (`OllamaChatResponse`), gom `message.content` và phát sự kiện stream trong `ChatViewModel`.

---

## Cấu hình URL Ollama cho Android

Trong `app/build.gradle.kts`, `defaultConfig`:

```kotlin
buildConfigField("String", "OLLAMA_BASE_URL", "\"http://...\"")
```

### Emulator (AVD)

Máy ảo trỏ về máy host qua **`10.0.2.2`**. Ví dụ:

```kotlin
buildConfigField("String", "OLLAMA_BASE_URL", "\"http://10.0.2.2:11434\"")
```

### Điện thoại thật (USB / Wi-Fi)

`10.0.2.2` **không** trỏ tới máy dev. Cần:

1. Máy chạy Ollama và điện thoại **cùng mạng** (ví dụ cùng Wi-Fi).
2. Lấy **IP LAN** của máy (ví dụ `192.168.1.50`).
3. Sửa `OLLAMA_BASE_URL` thành `http://<IP-LAN>:11434` rồi **Sync / Rebuild**.
4. Mở tường lửa OS cho cổng **11434** nếu bị chặn.

### HTTP (cleartext)

`app/src/main/res/xml/network_security_config.xml` cho phép cleartext để phát triển với Ollama HTTP. **Không** dùng cấu hình này cho bản production công khai; nên HTTPS + cấu hình bảo mật chặt hơn.

---

## Đổi model Ollama

Model mặc định nằm trong Dagger module:

- File: `app/src/main/java/com/example/chatbot/di/AppModule.kt`
- Hàm: `provideOllamaModel()` — hiện trả về `"qwen2.5:7b"`.

Đổi chuỗi cho trùng tên model đã `ollama pull` (ví dụ `llama3`, `mistral`, …).

---

## Timeout mạng (OkHttp)

Trong `AppModule.kt`, client dùng **15 giây** cho `connectTimeout`, `readTimeout`, `writeTimeout`.

**Lưu ý:** `readTimeout` áp dụng cho khoảng thời gian **không có dữ liệc** giữa hai lần đọc. Stream model chậm có thể cần tăng `readTimeout` (ví dụ 120s) nếu gặp lỗi timeout giữa chừng.

---

## Chạy dự án trong Android Studio

1. Clone / mở thư mục repo.
2. Đảm bảo **Ollama** đang chạy và model đã pull.
3. **File → Sync Project with Gradle Files**.
4. Chọn **Run** trên emulator hoặc thiết bị (đã chỉnh URL nếu dùng máy thật).

Lệnh biên dịch (tùy chọn):

```bash
./gradlew :app:assembleDebug
```

---

## Quyền ứng dụng

| Quyền | Mục đích |
|--------|----------|
| `INTERNET` | Gọi Ollama |
| `RECORD_AUDIO` | Nút mic — nhận diện giọng nói |

---

## Giao diện sáng / tối

- **Công tắc “Chế độ tối”** trong header **drawer** (danh sách phiên chat).
- Lưu preference: `ThemeModePreferences` (`SharedPreferences`). Lần đầu: theo hệ thống; sau khi gạt: ép sáng hoặc tối.
- Áp dụng khi mở app: `ChatBotApplication` gọi `applyPersistedMode`.
- Màu theo `values/` và `values-night/` (theme, bubble, markdown, code block).

---

## Cấu trúc thư mục (tóm tắt)

```
ChatBot2/
├── app/                              # Module ứng dụng Android
│   ├── build.gradle.kts              # BuildConfig OLLAMA_BASE_URL, dependencies
│   └── src/main/
│       ├── AndroidManifest.xml       # Application, Activity, permissions
│       ├── java/com/example/chatbot/
│       │   ├── ChatBotApplication.kt # @HiltAndroidApp, áp dụng theme đã lưu
│       │   ├── data/
│       │   │   ├── local/            # Realm: ChatSessionStore
│       │   │   ├── model/            # Ollama DTO (request/response/message)
│       │   │   └── repository/       # ChatRepository, OllamaChatRepository, ChatStreamEvent
│       │   ├── di/                   # Hilt: AppModule, MarkdownModule, RealmModule, …
│       │   ├── prism/                # Cấu hình grammar Prism (highlight)
│       │   └── ui/
│       │       ├── main/MainActivity.kt   # Toolbar, drawer, TTS/STT, cuộn chat
│       │       ├── theme/ThemeModePreferences.kt
│       │       └── chat/             # ViewModel, Adapter, Markwon entries, UI models
│       └── res/                      # layout, values, values-night, menu, …
├── markwon-prism-bundles/            # Module Java: bundle grammar Prism4j cho Markwon
├── gradle/libs.versions.toml         # Phiên bản thư viện
├── settings.gradle.kts               # include :app, :markwon-prism-bundles
└── build.gradle.kts                  # Plugins cấp root (nếu có)
```

### Luồng dữ liệu (khái quát)

1. **UI** (`MainActivity` + `ChatAdapter`) hiển thị tin nhắn; user gửi nội dung qua `ChatViewModel`.
2. **ChatViewModel** gọi **`ChatRepository.chatStream`** (`OllamaChatRepository`) → **OkHttp** stream **`POST /api/chat`** (NDJSON), cập nhật UI theo chunk / `Done` / `Failed`; lỗi có **Thử lại** trên bubble user tương ứng.
3. **ChatSessionStore** (Realm, schema version **2**) lưu/đọc tin theo phiên; trường **`showRetry`** trên tin user để sau khi kill app vẫn hiện Thử lại; drawer mở các phiên gần đây.

### Markdown & highlight

- **`MarkdownModule`**: hai instance **Markwon** được inject theo tên (`TEXT` cho bubble user; **BLOCKS** cho bubble trợ lý với `MarkwonAdapter` + fenced/indented code + bảng). **Không** `@Singleton` để mỗi lần tạo lại `MainActivity` (đổi theme) có màu Markwon đúng `night`.
- **`ChatPrism4jTheme`**: ghi đè `Prism4jTheme.textColor()` bằng `chat_markdown_code_text` để token không highlight không bị màu đen mặc định của Prism trên nền tối.
- **`markwonColorContext`**: khi ép `MODE_NIGHT_YES` / `NO`, resolve màu qua `createConfigurationContext` để `values-night` khớp với `AppCompatDelegate` dù `ApplicationContext` chưa đổi `uiMode`.
- **`ChatMarkdownCodeBlockEntries`**: fenced/indented code, nút copy, xử lý filler Markwon core.
- **`dimens`**: `chat_markdown_theme_block_margin` — `MarkwonTheme.blockMargin` (list / blockquote) nhỏ hơn mặc định thư viện.

---

## Module `markwon-prism-bundles`

Module **`java-library`** build grammar **Prism4j** (annotation processor `prism4j-bundler`) để Markwon highlight cú pháp. `app` phụ thuộc `project(":markwon-prism-bundles")`.

---

## Gỡ lỗi thường gặp

| Hiện tượng | Gợi ý |
|-------------|--------|
| Lỗi kết nối / timeout | Kiểm tra Ollama chạy, URL đúng (emulator vs IP LAN), firewall. Stream chậm: cân nhắc tăng `readTimeout` trong `AppModule`. |
| HTTP 404 / model | Đúng tên model trong `AppModule` và đã `ollama pull`. |
| Mic không hoạt động | Cấp quyền RECORD_AUDIO; thiết bị hỗ trợ `SpeechRecognizer`. |
| Chữ code / biến khó nhìn sau đổi theme | Đảm bảo đã rebuild; Markwon không singleton + `markwonColorContext` + `ChatPrism4jTheme` xử lý màu theo dark mode. |

Log HTTP cơ bản (DEBUG) được gắn qua **OkHttp LoggingInterceptor** trong `AppModule`.

---

## License

Dự án mẫu / cá nhân — bổ sung file license nếu bạn phân phối công khai.
