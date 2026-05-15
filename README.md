# ChatBot (Android + Ollama)

Ứng dụng chat Android kết nối **Ollama** qua API `/api/chat` (JSON, không stream), lưu lịch sử cục bộ bằng **Realm**, giao diện **Material 3**, markdown (Markwon + Prism), giọng nói nhập (**SpeechRecognizer**) và đọc phản hồi (**Text-to-Speech**, tiếng Việt).

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

2. **Kéo model** mà app đang dùng mặc định (có thể đổi trong code, mục dưới):

   ```bash
   ollama pull llama3
   ```

3. **Chạy server** (thường tự chạy sau khi cài; nếu cần kiểm tra):

   ```bash
   ollama serve
   ```

   Mặc định API lắng nghe **`http://127.0.0.1:11434`**.

4. **Thử nhanh API chat** (tùy chọn):

   ```bash
   curl http://127.0.0.1:11434/api/chat -d '{
     "model": "llama3",
     "messages": [{"role":"user","content":"Xin chào"}],
     "stream": false
   }'
   ```

Ứng dụng gọi **`POST {baseUrl}/api/chat`** với body `OllamaChatRequest` (`model`, `messages`, `stream: false`) và đọc `message.content` từ JSON phản hồi (`OllamaChatResponse`).

---

## Cấu hình URL Ollama cho Android

### Emulator (AVD)

Máy ảo trỏ về máy host qua **`10.0.2.2`**. Mặc định trong `app/build.gradle.kts`:

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
- Hàm: `provideOllamaModel()` — hiện trả về `"llama3"`.

Đổi chuỗi cho trùng tên model đã `ollama pull` (ví dụ `llama3.2`, `mistral`, …).

---

## Chạy dự án trong Android Studio

1. Clone / mở thư mục repo.
2. Đảm bảo **Ollama** đang chạy và model đã pull.
3. **File → Sync Project with Gradle Files**.
4. Chọn **Run** trên emulator hoặc thiết bại (đã chỉnh URL nếu dùng máy thật).

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

## Cấu trúc thư mục (tóm tắt)

```
ChatBot2/
├── app/                              # Module ứng dụng Android
│   ├── build.gradle.kts              # BuildConfig OLLAMA_BASE_URL, dependencies
│   └── src/main/
│       ├── AndroidManifest.xml       # Application, Activity, permissions
│       ├── java/com/example/chatbot/
│       │   ├── ChatBotApplication.kt # @HiltAndroidApp
│       │   ├── data/
│       │   │   ├── local/            # Realm: ChatSessionStore
│       │   │   ├── model/            # Ollama DTO (request/response/message)
│       │   │   └── repository/       # ChatRepository + OllamaChatRepository
│       │   ├── di/                   # Hilt: AppModule, MarkdownModule, RealmModule, …
│       │   ├── prism/                # Cấu hình grammar Prism (highlight)
│       │   └── ui/
│       │       ├── main/MainActivity.kt
│       │       └── chat/             # ViewModel, Adapter, Markwon entries, UI models
│       └── res/                      # layout, values, menu, network_security_config, …
├── markwon-prism-bundles/            # Module Java: bundle grammar Prism4j cho Markwon
├── gradle/libs.versions.toml         # Phiên bản thư viện
├── settings.gradle.kts               # include :app, :markwon-prism-bundles
└── build.gradle.kts                  # Plugins cấp root (nếu có)
```

### Luồng dữ liệu (khái quát)

1. **UI** (`MainActivity` + `ChatAdapter`) hiển thị tin nhắn; user gửi nội dung qua `ChatViewModel`.
2. **ChatViewModel** gọi **`ChatRepository`** (triển khai **`OllamaChatRepository`**) → **OkHttp** `POST /api/chat`.
3. **ChatSessionStore** (Realm) lưu/đọc tin theo phiên; drawer mở các phiên gần đây.

### Markdown

- **`MarkdownModule`**: hai instance **Markwon** (`TEXT` cho bubble user; **BLOCKS** cho bubble trợ lý với `MarkwonAdapter` + code block / bảng).
- **`ChatMarkdownCodeBlockEntries`**: fenced/indented code, nút copy, xử lý filler Markwon.

---

## Module `markwon-prism-bundles`

Module **`java-library`** build grammar **Prism4j** (annotation processor `prism4j-bundler`) để Markwon highlight cú pháp. `app` phụ thuộc `project(":markwon-prism-bundles")`.

---

## Gỡ lỗi thường gặp

| Hiện tượng | Gợi ý |
|-------------|--------|
| Lỗi kết nối / timeout | Kiểm tra Ollama chạy, URL đúng (emulator vs IP LAN), firewall. |
| HTTP 404 / model | Đúng tên model trong `AppModule` và đã `ollama pull`. |
| Mic không hoạt động | Cấp quyền RECORD_AUDIO; thiết bị hỗ trợ `SpeechRecognizer`. |

Log HTTP cơ bản (DEBUG) được gắn qua **OkHttp LoggingInterceptor** trong `AppModule`.

---

## License

Dự án mẫu / cá nhân — bổ sung file license nếu bạn phân phối công khai.
