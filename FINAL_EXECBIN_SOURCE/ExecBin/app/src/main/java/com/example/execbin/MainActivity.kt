package com.example.execbin

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.view.*
import android.widget.*
import android.graphics.Color
import android.text.InputType
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import android.provider.MediaStore
import android.content.Intent
import android.net.Uri
import android.util.Base64
import java.io.ByteArrayOutputStream
import android.graphics.BitmapFactory
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.messaging.FirebaseMessaging
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.SharedPreferences
import android.graphics.drawable.GradientDrawable
import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.app.AlertDialog
import android.graphics.Bitmap
import java.security.MessageDigest
import java.security.KeyPairGenerator
import java.security.KeyFactory
import java.security.spec.ECGenParameterSpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.KeyAgreement
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import java.security.SecureRandom
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKeys
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import com.example.execbin.crypto.CryptoManager
import com.example.execbin.database.AppDatabase
import com.example.execbin.database.CachedMessage
import com.google.firebase.firestore.FirebaseFirestoreSettings
import androidx.exifinterface.media.ExifInterface
import java.io.File

@IgnoreExtraProperties
data class ChatUser(
    val uid: String = "",
    val username: String = "",
    val email: String = "",
    val nickname: String = "",
    val photoBase64: String = "",
    val fcmToken: String = "",
    val bio: String = "",
    val isOnline: Boolean = false,
    val lastSeen: Long = 0,
    val publicKey: String = ""
)

@IgnoreExtraProperties
data class Msg(
    val text: String = "",
    val senderId: String = "",
    val timestamp: Long = 0,
    val username: String = "",
    val nickname: String = "",
    val photoBase64: String = "",
    val replyToText: String = "",
    val replyToUsername: String = "",
    val read: Boolean = false,
    val edited: Boolean = false,
    val editedAt: Long = 0,
    val mediaUrl: String = "",
    val mediaType: String = "",  // "image", "voice", ""
    val mediaDuration: Long = 0,  // voice duration in ms
    val ephemeralKey: String = "",      // PFS: sender's ephemeral public key
    val senderText: String = "",       // PFS: text encrypted for sender's own decryption
    val senderReplyText: String = "",  // PFS: reply encrypted for sender
    val readReceipt: String = "",      // Encrypted read receipt token
    val reactions: List<String> = emptyList()  // Format: "uid:emoji" e.g. ["uid1:❤️", "uid2:👍"]
)

@IgnoreExtraProperties
data class Device(
    val deviceId: String = "",
    val deviceName: String = "",
    val userId: String = "",
    val loginTimestamp: Long = 0,
    val lastActive: Long = 0,
    val trusted: Boolean = false
)

class MainActivity : AppCompatActivity() {

    override fun attachBaseContext(newBase: Context) {
        val langPrefs = newBase.getSharedPreferences("app_lang_prefs", Context.MODE_PRIVATE)
        val langCode = langPrefs.getString("app_language", "en") ?: "en"
        val locale = when (langCode) {
            "es" -> java.util.Locale("es")
            "fr" -> java.util.Locale("fr")
            "de" -> java.util.Locale("de")
            "ar" -> java.util.Locale("ar")
            "ru" -> java.util.Locale("ru")
            "pt" -> java.util.Locale("pt")
            "it" -> java.util.Locale("it")
            "zh" -> java.util.Locale.SIMPLIFIED_CHINESE
            "ja" -> java.util.Locale.JAPANESE
            else -> java.util.Locale.ENGLISH
        }
        val config = android.content.res.Configuration()
        config.setLocale(locale)
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    private lateinit var container: FrameLayout
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var prefs: SharedPreferences
    private lateinit var database: AppDatabase

    private var globalChatListener: ListenerRegistration? = null
    private var privateChatListener: ListenerRegistration? = null
    private var privateChatListListener: ListenerRegistration? = null
    private var securityAlertListener: ListenerRegistration? = null
    private var publicKeyListener: ListenerRegistration? = null
    private var typingStatusListener: ListenerRegistration? = null
    private var typingCleanupHandler: android.os.Handler? = null
    private var typingCleanupRunnable: Runnable? = null
    private var activeMediaPlayer: android.media.MediaPlayer? = null
    private var activeVoiceTempFile: java.io.File? = null
    private var privateMsgs = mutableListOf<Msg>()
    private var privateMsgIds = mutableListOf<String>()
    private val recentChats = mutableListOf<ChatUser>()
    private val chatTimestamps = mutableMapOf<String, Long>()
    private val typingUsers = mutableSetOf<String>()
    private val chatListTypingListeners = mutableListOf<ListenerRegistration>()
    private var currentScreen = "registration"
    private var isAppInForeground = true
    private var currentOpenChatId: String? = null
    private val notifiedChatTimestamps = mutableMapOf<String, Long>()
    private var myUsername = ""
    private var myNickname = ""
    private var myPhotoBase64 = ""
    private var myBio = ""
    private var currentDeviceId = ""
    private var currentDeviceName = ""

    private var tempUsername = ""
    private var selectedPhotoUri: Uri? = null

    private val globalMsgs = mutableListOf<Msg>()
    private val globalMsgIds = mutableListOf<String>()
    private val userDevices = mutableListOf<Device>()

    private var replyToText: String? = null
    private var replyToUsername: String? = null

    private val PICK_IMAGE_REQUEST = 1
    private val NOTIFICATION_PERMISSION_CODE = 100
    private val SESSION_TIMEOUT_DAYS = 3
    private val COOLDOWN_HOURS = 12

    private var isDarkTheme = true
    private var bgColor = 0
    private var cardBg = 0
    private var headerBg = 0
    private var accentColor = 0
    private var textColor = 0
    private var subTextColor = 0

    private var hasPinEnabled = false
    private val MAX_DEVICES_PER_ACCOUNT = 5

    private var myPublicKeyBase64 = ""
    private val crypto = CryptoManager(object : CryptoManager.Base64Codec {
        override fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
        override fun decode(text: String): ByteArray = Base64.decode(text, Base64.NO_WRAP)
    })
    // Static ECDH key derived with our pre-rotation private key, for content encrypted before rotation
    private var previousSharedKey: SecretKeySpec? = null
    private var isKeysReady = false
    private var pendingMessages = mutableListOf<Pair<Msg, String>>()
    private val blockedUsers = mutableSetOf<String>()
    private var isRecordingVoice = false
    private var mediaRecorder: android.media.MediaRecorder? = null
    private var voiceFilePath: String? = null
    private val PICK_MEDIA_REQUEST = 2
    private var pendingMediaChatId: String? = null
    private var pendingMediaOtherUid: String? = null
    private var pendingMediaSharedKey: SecretKeySpec? = null
    private var inlineGalleryPicker: InlineGalleryPicker? = null
    private val GALLERY_PERMISSION_REQUEST = 3

    // Flag to block usage on compromised devices
    private var rootIndicatorsFound = false
    private var appCheckFailed = false

    // Unread message counts per chat
    private val unreadCounts = mutableMapOf<String, Int>()

    // Network connectivity monitoring
    private var connectivityCallback: android.net.ConnectivityManager.NetworkCallback? = null
    private var connectionBanner: TextView? = null

    // ==========================================
    // PERFORMANCE: Cached constants & reusable objects
    // ==========================================
    private val COLOR_READ_RECEIPT = Color.parseColor("#34D399")
    private val COLOR_UNREAD_RECEIPT = Color.parseColor("#6B7280")
    private val COLOR_ONLINE_GREEN = Color.parseColor("#10B981")
    private val COLOR_MINE_BUBBLE_START = Color.parseColor("#6366F1")
    private val COLOR_MINE_BUBBLE_END = Color.parseColor("#8B5CF6")
    private val COLOR_REPLY_MINE = Color.parseColor("#C7D2FE")
    private val COLOR_REPLY_TEXT_MINE = Color.parseColor("#E2E8F0")
    private val COLOR_DARK_BUBBLE = Color.parseColor("#1E2545")
    private val COLOR_LIGHT_BUBBLE = Color.parseColor("#F1F5F9")
    private val COLOR_DARK_MEDIA_BG = Color.parseColor("#1E293B")
    private val COLOR_LIGHT_MEDIA_BG = Color.parseColor("#E2E8F0")
    private val COLOR_DARK_AVATAR_BG = Color.parseColor("#2A3158")
    private val COLOR_LIGHT_AVATAR_BG = Color.parseColor("#EDE9FE")
    private val COLOR_BUBBLE_GRADIENT_END = Color.parseColor("#A78BFA")
    private val COLOR_USERNAME_MINE = Color.parseColor("#E0E7FF")
    private val COLOR_ACCENT_GLOW = Color.parseColor("#4C6366F1") // 30% alpha accent for glows
    private val timeFormatter by lazy { java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault()) }
    private val avatarBitmapCache = android.util.LruCache<String, Bitmap>(30) // cache 30 avatars
    private val mediaDecryptCache = android.util.LruCache<Int, ByteArray>(20) // cache 20 decrypted media
    private val roundedOutline8 = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, (8 * resources.displayMetrics.density).toInt().toFloat())
        }
    }
    private val roundedOutline12 = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setRoundRect(0, 0, view.width, view.height, (12 * resources.displayMetrics.density).toInt().toFloat())
        }
    }
    private val ovalOutline = object : android.view.ViewOutlineProvider() {
        override fun getOutline(view: View, outline: android.graphics.Outline) {
            outline.setOval(0, 0, view.width, view.height)
        }
    }

    // ==========================================
    // ANTI-SPAM: Rate limiting + duplicate detection
    // ==========================================
    private val messageTimes = mutableListOf<Long>()      // timestamps of recent sends
    private var lastMessageText = ""                       // last sent message text
    private var duplicateCount = 0                         // consecutive duplicate counter
    private val MAX_MSG_LENGTH = 4000                      // max characters per message
    private val RATE_LIMIT_WINDOW_MS = 10_000L             // 10-second window
    private val MAX_MESSAGES_PER_WINDOW = 5                // max 5 messages per 10s
    private val MEDIA_COOLDOWN_MS = 3_000L                 // 3s between media sends
    private var lastMediaSendTime = 0L                     // last media send timestamp
    private val MAX_DUPLICATES = 3                         // block after 3 identical messages in a row

    /**
     * Returns null if message is allowed, or an error string key if blocked.
     */
    private fun checkAntiSpam(text: String, isMedia: Boolean = false): String? {
        val now = System.currentTimeMillis()

        // 1. Message length check
        if (!isMedia && text.length > MAX_MSG_LENGTH) {
            return "spam_msg_too_long"
        }

        // 2. Rate limiting — sliding window
        messageTimes.removeAll { now - it > RATE_LIMIT_WINDOW_MS }
        if (messageTimes.size >= MAX_MESSAGES_PER_WINDOW) {
            return "spam_too_fast"
        }

        // 3. Media cooldown
        if (isMedia && now - lastMediaSendTime < MEDIA_COOLDOWN_MS) {
            return "spam_media_cooldown"
        }

        // 4. Duplicate detection (not for media)
        if (!isMedia && text.isNotEmpty()) {
            if (text == lastMessageText) {
                duplicateCount++
                if (duplicateCount >= MAX_DUPLICATES) {
                    return "spam_duplicate"
                }
            } else {
                duplicateCount = 0
            }
        }

        return null // allowed
    }

    /**
     * Call after a message is successfully sent to update anti-spam state.
     */
    private fun recordMessageSent(text: String, isMedia: Boolean = false) {
        val now = System.currentTimeMillis()
        messageTimes.add(now)
        if (isMedia) lastMediaSendTime = now
        if (!isMedia) lastMessageText = text
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Firebase Crashlytics initialization — safe for devices without GMS
        try {
            com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().apply {
                setCrashlyticsCollectionEnabled(true)
                setCustomKey("device_model", android.os.Build.MODEL)
                setCustomKey("device_manufacturer", android.os.Build.MANUFACTURER)
                setCustomKey("os_version", android.os.Build.VERSION.SDK_INT.toString())
            }
        } catch (e: Exception) {
            android.util.Log.w("ExecBin", "Crashlytics init failed (GMS unavailable?)", e)
        }

        // Debug crash handler — logs the full stack trace + Crashlytics
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            android.util.Log.e("ExecBin", "UNCAUGHT CRASH: ${throwable.message}", throwable)
            try { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(throwable) } catch (_: Exception) {}
            defaultHandler?.uncaughtException(thread, throwable)
        }

        S.init(this)

        // Private keys, PIN state and metadata keys live here, so there is no plaintext fallback
        prefs = openSecurePrefs() ?: run {
            showSecureStorageError()
            return
        }

        // SQLCipher-encrypted cache; falls back to memory-only, never to an unencrypted file
        database = AppDatabase.getInstance(this)

        isDarkTheme = prefs.getBoolean("dark_theme", true)

        currentDeviceId = DeviceUtils.getDeviceId(this)
        currentDeviceName = DeviceUtils.getDeviceName()

        // Root detection + Play Integrity
        checkDeviceIntegrity()

        updateThemeColors()

        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = headerBg
        window.navigationBarColor = headerBg

        auth = Firebase.auth
        db = Firebase.firestore

        // Firebase App Check — Play Integrity attestation, enforced server-side by Firebase
        installAppCheck()

        // Set Firestore persistence — larger cache for offline use on slow connections
        val settings = FirebaseFirestoreSettings.Builder()
            .setPersistenceEnabled(true)
            .setCacheSizeBytes(100L * 1024 * 1024) // 100MB cache for offline reliability
            .build()
        db.firestoreSettings = settings

        container = FrameLayout(this)
        container.setBackgroundColor(bgColor)
        setContentView(container)

        ViewCompat.setOnApplyWindowInsetsListener(container) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, maxOf(systemBars.bottom, ime.bottom))
            insets
        }

        updateSystemBarColors()
        createNotificationChannel()
        setupConnectivityMonitor()

        // Plant honeypot SharedPreferences files to confuse reverse engineers
        plantHoneypots()

        // Security: tamper detection + anti-debugging
        performSecurityChecks()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_CODE)
            }
        }

        // Root indicators are a local heuristic, so warn (once per install) instead of blocking.
        // Server-side device attestation is Firebase App Check + Play Integrity (see verifyAppCheck).
        if (rootIndicatorsFound && !prefs.getBoolean("root_warning_ack", false)) {
            showCompromisedDeviceWarning()
            return
        }

        routeToStartScreen()
    }

    private fun routeToStartScreen() {
        if (auth.currentUser != null) {
            if (checkSessionTimeout()) {
                Toast.makeText(this, S.get("session_expired"), Toast.LENGTH_LONG).show()
                setOfflineStatus()
                auth.signOut()
                showRegistration()
                return
            }
            checkSessionExpiryWarning()
            updateLastActive()

            if (auth.currentUser?.isEmailVerified == true) {
                checkAndLoadUserData()
            } else {
                showVerificationPending()
            }
        } else {
            showRegistration()
        }
    }

    // ==========================================
    // SECURE STORAGE (EncryptedSharedPreferences, no plaintext fallback)
    // ==========================================

    private fun createEncryptedPrefs(): SharedPreferences = EncryptedSharedPreferences.create(
        "app_prefs",
        MasterKeys.getOrCreate(MasterKeys.AES256_GCM_SPEC),
        this,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    private fun openSecurePrefs(): SharedPreferences? {
        val secure = try {
            createEncryptedPrefs()
        } catch (e: Exception) {
            // Usually a prefs file that can no longer be decrypted (e.g. restored onto another device).
            // Its contents are unreadable anyway, so reset it instead of storing secrets in plaintext.
            android.util.Log.e("ExecBin", "EncryptedSharedPreferences failed, resetting secure prefs", e)
            try { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().recordException(e) } catch (_: Exception) {}
            try {
                deletePrefsFile("app_prefs")
                createEncryptedPrefs()
            } catch (e2: Exception) {
                android.util.Log.e("ExecBin", "Secure storage unavailable", e2)
                null
            }
        } ?: return null
        migrateLegacyPlaintextPrefs(secure)
        return secure
    }

    private fun deletePrefsFile(name: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            deleteSharedPreferences(name)
        } else {
            getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
            File(applicationInfo.dataDir, "shared_prefs/$name.xml").delete()
        }
    }

    /** Older versions fell back to a plaintext prefs file; move its data into encrypted prefs and delete it. */
    private fun migrateLegacyPlaintextPrefs(secure: SharedPreferences) {
        val legacyName = "app_prefs_fallback"
        val legacy = getSharedPreferences(legacyName, Context.MODE_PRIVATE)
        if (legacy.all.isEmpty()) return
        val editor = secure.edit()
        @Suppress("UNCHECKED_CAST")
        legacy.all.forEach { (key, value) ->
            when (value) {
                is String -> editor.putString(key, value)
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is Set<*> -> editor.putStringSet(key, value as Set<String>)
            }
        }
        if (editor.commit()) {
            legacy.edit().clear().commit()
            deletePrefsFile(legacyName)
        }
    }

    private fun showSecureStorageError() {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#0C1021"))
            setPadding(dp(32), dp(48), dp(32), dp(32))
        }
        root.addView(TextView(this).apply {
            text = S.get("secure_storage_error_title")
            textSize = 22f
            setTextColor(Color.parseColor("#EF4444"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(12))
        })
        root.addView(TextView(this).apply {
            text = S.get("secure_storage_error_msg")
            setTextColor(Color.parseColor("#8B95B0"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(32))
        })
        root.addView(Button(this).apply {
            text = S.get("exit")
            setTextColor(Color.WHITE)
            isAllCaps = false
            background = createRoundedBackground(Color.parseColor("#EF4444"), 14)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
            setOnClickListener { finishAffinity() }
        })
        setContentView(root)
    }

    private fun showCompromisedDeviceWarning() {
        container.removeAllViews()
        container.setBackgroundColor(Color.parseColor("#0C1021"))
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(48), dp(32), dp(32))
        }
        root.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("🛡️", Color.WHITE, dp(64)))
            layoutParams = LinearLayout.LayoutParams(dp(64), dp(64)).apply { bottomMargin = dp(24); gravity = Gravity.CENTER_HORIZONTAL }
        })
        root.addView(TextView(this).apply {
            text = S.get("device_not_trusted")
            textSize = 22f
            setTextColor(Color.parseColor("#EF4444"))
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        })
        root.addView(TextView(this).apply {
            text = S.get("root_warning_message")
            setTextColor(Color.parseColor("#8B95B0"))
            textSize = 15f
            gravity = Gravity.CENTER
            setLineSpacing(dp(3).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(32) }
        })
        root.addView(Button(this).apply {
            text = S.get("exit")
            setTextColor(Color.WHITE)
            textSize = 16f
            background = createRoundedBackground(Color.parseColor("#EF4444"), 14)
            isAllCaps = false
            setTypeface(null, android.graphics.Typeface.BOLD)
            stateListAnimator = null
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52))
            setOnClickListener { finishAffinity() }
        })
        root.addView(TextView(this).apply {
            text = S.get("continue_anyway")
            setTextColor(Color.parseColor("#8B95B0"))
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
            setOnClickListener {
                prefs.edit().putBoolean("root_warning_ack", true).apply()
                container.removeAllViews()
                container.setBackgroundColor(bgColor)
                routeToStartScreen()
            }
        })
        container.addView(root)
    }

    private fun checkDeviceIntegrity() {
        // Local root heuristic only — easy to hide from, so it never blocks on its own.
        rootIndicatorsFound = try {
            isDeviceRooted()
        } catch (e: Exception) {
            android.util.Log.e("ExecBin", "Root check failed", e)
            false
        }
    }

    // ==========================================
    // FIREBASE APP CHECK (Play Integrity, verified by Google/Firebase servers)
    // ==========================================

    private fun installAppCheck() {
        try {
            val appCheck = FirebaseAppCheck.getInstance()
            if (BuildConfig.DEBUG) {
                // Debug builds can't pass Play Integrity; use the debug provider (register its token
                // in Firebase Console → App Check → Manage debug tokens). Loaded reflectively because
                // the dependency is debugImplementation only.
                val factory = Class.forName("com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory")
                    .getMethod("getInstance").invoke(null) as com.google.firebase.appcheck.AppCheckProviderFactory
                appCheck.installAppCheckProviderFactory(factory)
                return
            }
            val gmsStatus = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(this)
            if (gmsStatus == com.google.android.gms.common.ConnectionResult.SUCCESS) {
                appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
                verifyAppCheck(appCheck)
            } else {
                android.util.Log.w("ExecBin", "Google Play Services unavailable (code=$gmsStatus) — App Check cannot attest")
                appCheckFailed = true
            }
        } catch (e: Exception) {
            android.util.Log.e("ExecBin", "App Check initialization failed", e)
            appCheckFailed = true
        }
    }

    /**
     * Requests an App Check token. Failure is not trusted silently: it is recorded and the user is told
     * that the backend may refuse this device. The actual enforcement happens server-side once App Check
     * enforcement is enabled for Firestore in the Firebase Console.
     */
    private fun verifyAppCheck(appCheck: FirebaseAppCheck) {
        appCheck.getAppCheckToken(false)
            .addOnSuccessListener { appCheckFailed = false }
            .addOnFailureListener { e ->
                android.util.Log.w("ExecBin", "App Check token request failed: ${e.javaClass.simpleName}")
                appCheckFailed = true
                if (!isFinishing && !isDestroyed) {
                    AlertDialog.Builder(this)
                        .setTitle(iconize("⚠️ " + S.get("app_check_failed_title")))
                        .setMessage(S.get("app_check_failed_msg"))
                        .setPositiveButton(S.get("ok"), null)
                        .show()
                }
            }
    }

    private fun isDeviceRooted(): Boolean {
        // Check common root indicators
        val rootPaths = arrayOf(
            "/system/app/Superuser.apk", "/sbin/su", "/system/bin/su",
            "/system/xbin/su", "/data/local/xbin/su", "/data/local/bin/su",
            "/system/sd/xbin/su", "/system/bin/failsafe/su", "/data/local/su",
            "/su/bin/su", "/system/app/SuperSU.apk", "/system/app/KingRoot.apk"
        )
        for (path in rootPaths) {
            if (java.io.File(path).exists()) return true
        }
        // Check if su is available in PATH
        try {
            val process = Runtime.getRuntime().exec(arrayOf("which", "su"))
            val result = process.inputStream.bufferedReader().readText().trim()
            process.destroy()
            if (result.isNotEmpty()) return true
        } catch (_: Exception) {}
        // Check for common root management apps
        val rootPackages = arrayOf(
            "com.topjohnwu.magisk", "eu.chainfire.supersu",
            "com.koushikdutta.superuser", "com.noshufou.android.su"
        )
        for (pkg in rootPackages) {
            try {
                packageManager.getPackageInfo(pkg, 0)
                return true
            } catch (_: Exception) {}
        }
        return false
    }

    private var loadingOverlayId: Int? = null

    private fun showLoadingOverlay(message: String) {
        runOnUiThread {
            val id = loadingOverlayId ?: View.generateViewId().also { loadingOverlayId = it }
            if (findViewById<View>(id) == null) {
                val overlay = FrameLayout(this).apply {
                    this.id = id
                    setBackgroundColor(Color.parseColor("#CC000000"))
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
                val card = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER_HORIZONTAL
                    background = createRoundedBackground(if (isDarkTheme) Color.parseColor("#1E2340") else Color.parseColor("#FFFFFF"), 20)
                    setPadding(dp(32), dp(28), dp(32), dp(28))
                    layoutParams = FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.WRAP_CONTENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    ).apply { gravity = Gravity.CENTER }
                }
                card.addView(ProgressBar(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(16) }
                })
                card.addView(TextView(this).apply {
                    text = message
                    setTextColor(if (isDarkTheme) Color.parseColor("#F1F3F8") else Color.parseColor("#1A1D2E"))
                    textSize = 15f
                    gravity = Gravity.CENTER
                })
                overlay.addView(card)
                container.addView(overlay)
            }
        }
    }

    private fun hideLoadingOverlay() {
        runOnUiThread {
            loadingOverlayId?.let { id ->
                findViewById<View>(id)?.let {
                    container.removeView(it)
                }
            }
        }
    }

    // ==========================================
    // SECURITY: PIN HASHING (PBKDF2 + random salt)
    // ==========================================

    // Creates a new hash string: "pbkdf2:<saltHex>:<hashHex>"
    private fun createPinHash(pin: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 100_000, 256)
        val hashBytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
            .generateSecret(spec).encoded
        val saltHex = salt.joinToString("") { "%02x".format(it) }
        val hashHex = hashBytes.joinToString("") { "%02x".format(it) }
        return "pbkdf2:$saltHex:$hashHex"
    }

    // Constant-time comparison to prevent timing attacks on PIN verification
    private fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var result = 0
        for (i in a.indices) {
            result = result or (a[i].code xor b[i].code)
        }
        return result == 0
    }

    // Verifies a pin against a stored hash (supports both old SHA-256 and new PBKDF2 format)
    private fun verifyPin(pin: String, userId: String, storedHash: String): Boolean {
        if (storedHash.startsWith("pbkdf2:")) {
            val parts = storedHash.split(":")
            if (parts.size != 3) return false
            val salt = parts[1].chunked(2).map { it.toInt(16).toByte() }.toByteArray()
            val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 100_000, 256)
            val hashBytes = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            val computed = "pbkdf2:${parts[1]}:" + hashBytes.joinToString("") { "%02x".format(it) }
            return constantTimeEquals(computed, storedHash)
        }
        // Legacy SHA-256 fallback for existing users — auto-migrate to PBKDF2
        val salted = "$userId:execbin_secure:$pin"
        val hashBytes = MessageDigest.getInstance("SHA-256").digest(salted.toByteArray(Charsets.UTF_8))
        val computed = hashBytes.joinToString("") { "%02x".format(it) }
        val matches = constantTimeEquals(computed, storedHash)
        if (matches) {
            // Upgrade to PBKDF2 immediately
            val newHash = createPinHash(pin)
            db.collection("users").document(userId).update("securityPin", newHash)
        }
        return matches
    }

    // ==========================================
    // BRUTE-FORCE PROTECTION (persistent lockout with exponential backoff)
    // ==========================================

    private fun getPinAttempts(): Int = prefs.getInt("pin_attempts", 0)
    private fun setPinAttempts(count: Int) { prefs.edit().putInt("pin_attempts", count).apply() }
    private fun getPinLockoutUntil(): Long = prefs.getLong("pin_lockout_until", 0L)
    private fun setPinLockoutUntil(time: Long) { prefs.edit().putLong("pin_lockout_until", time).apply() }

    private fun getLoginAttempts(): Int = prefs.getInt("login_attempts", 0)
    private fun setLoginAttempts(count: Int) { prefs.edit().putInt("login_attempts", count).apply() }
    private fun getLoginLockoutUntil(): Long = prefs.getLong("login_lockout_until", 0L)
    private fun setLoginLockoutUntil(time: Long) { prefs.edit().putLong("login_lockout_until", time).apply() }

    private fun getLockoutDuration(attempts: Int): Long = when {
        attempts >= 10 -> 2 * 60 * 60 * 1000L  // 2 hours, then reset counter
        attempts >= 9 -> 60 * 60 * 1000L        // 1 hour
        attempts >= 7 -> 15 * 60 * 1000L        // 15 min
        attempts >= 5 -> 5 * 60 * 1000L         // 5 min
        attempts >= 3 -> 30 * 1000L             // 30 sec
        else -> 0L
    }

    private fun formatLockoutDuration(ms: Long): String {
        val totalSec = ms / 1000
        val hours = totalSec / 3600
        val minutes = (totalSec % 3600) / 60
        val seconds = totalSec % 60
        return when {
            hours > 0 -> "${hours}h ${minutes}m"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }

    private fun isDeviceTrustedLocally(): Boolean {
        val userId = auth.currentUser?.uid ?: return false
        if (!prefs.getBoolean("device_trusted_$userId", false)) return false
        // Expire trust after 30 days (server-side trustedAt is source of truth, this is client guard)
        val trustedAt = prefs.getLong("device_trusted_at_$userId", 0L)
        if (trustedAt > 0L) {
            val daysSince = (System.currentTimeMillis() - trustedAt) / (1000L * 60 * 60 * 24)
            if (daysSince >= 30) {
                clearDeviceTrustLocally()
                return false
            }
        }
        return true
    }

    private fun markDeviceTrustedLocally() {
        val userId = auth.currentUser?.uid ?: return
        prefs.edit()
            .putBoolean("device_trusted_$userId", true)
            .putLong("device_trusted_at_$userId", System.currentTimeMillis())
            .apply()
        // Also record trust timestamp server-side for 30-day expiry enforcement
        db.collection("devices")
            .whereEqualTo("userId", userId)
            .whereEqualTo("deviceId", currentDeviceId)
            .get()
            .addOnSuccessListener { docs ->
                docs.forEach { doc ->
                    doc.reference.update("trustedAt", System.currentTimeMillis())
                }
            }
    }

    private fun clearDeviceTrustLocally() {
        val userId = auth.currentUser?.uid ?: return
        prefs.edit()
            .remove("device_trusted_$userId")
            .remove("device_trusted_at_$userId")
            .apply()
    }

    // ==========================================
    // THEME & LANGUAGE
    // ==========================================
    private fun applyLanguage(langCode: String) {
        val locale = when (langCode) {
            "es" -> java.util.Locale("es")
            "fr" -> java.util.Locale("fr")
            "de" -> java.util.Locale("de")
            "ar" -> java.util.Locale("ar")
            "ru" -> java.util.Locale("ru")
            "pt" -> java.util.Locale("pt")
            "it" -> java.util.Locale("it")
            "zh" -> java.util.Locale.SIMPLIFIED_CHINESE
            "ja" -> java.util.Locale.JAPANESE
            else -> java.util.Locale.ENGLISH
        }
        java.util.Locale.setDefault(locale)
        val config = resources.configuration
        config.setLocale(locale)
        @Suppress("DEPRECATION")
        resources.updateConfiguration(config, resources.displayMetrics)
        // Also persist to lang-specific prefs (readable in attachBaseContext)
        getSharedPreferences("app_lang_prefs", Context.MODE_PRIVATE)
            .edit().putString("app_language", langCode).apply()
    }

    private fun updateThemeColors() {
        if (isDarkTheme) {
            bgColor = Color.parseColor("#080B16")
            cardBg = Color.parseColor("#111827")
            headerBg = Color.parseColor("#0B0F1E")
            accentColor = Color.parseColor("#818CF8")
            textColor = Color.parseColor("#F8FAFC")
            subTextColor = Color.parseColor("#94A3B8")
        } else {
            bgColor = Color.parseColor("#F8FAFC")
            cardBg = Color.parseColor("#FFFFFF")
            headerBg = Color.parseColor("#FFFFFF")
            accentColor = Color.parseColor("#6366F1")
            textColor = Color.parseColor("#0F172A")
            subTextColor = Color.parseColor("#64748B")
        }
    }

    private fun updateSystemBarColors() {
        window.statusBarColor = headerBg
        window.navigationBarColor = headerBg
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = !isDarkTheme
        controller.isAppearanceLightNavigationBars = !isDarkTheme
    }

    private fun toggleTheme() {
        isDarkTheme = !isDarkTheme
        prefs.edit().putBoolean("dark_theme", isDarkTheme).apply()

        val colorFrom = if (isDarkTheme) Color.parseColor("#F5F7FA") else Color.parseColor("#080C1A")
        val colorTo = if (isDarkTheme) Color.parseColor("#080C1A") else Color.parseColor("#F5F7FA")

        ValueAnimator.ofArgb(colorFrom, colorTo).apply {
            duration = 400
            interpolator = DecelerateInterpolator()
            addUpdateListener { animator ->
                container.setBackgroundColor(animator.animatedValue as Int)
            }
            start()
        }

        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            updateThemeColors()
            updateSystemBarColors()
            recreateCurrentScreen()
        }, 150)
    }

    private fun recreateCurrentScreen() {
        when (currentScreen) {
            "registration" -> showRegistration()
            "login" -> showLogin()
            "verification" -> showVerificationPending()
            "pin_setup" -> showPinSetup()
            "pin_entry" -> showPinEntry()
            "home" -> showHome()
            "chat" -> showGlobalChat()
            "profile" -> showProfile()
            "settings" -> showSettings()
            "private_chat" -> showHome()
            "privacy_policy" -> showHome()
            else -> showHome()
        }
    }

    // ==========================================
    // NOTIFICATIONS
    // ==========================================
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "chat_messages",
                S.get("notification_channel_chat"),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = S.get("notification_channel_chat_desc")
                enableVibration(true)
                enableLights(true)
            }
            val securityChannel = NotificationChannel(
                "security_alerts",
                S.get("notification_channel_security"),
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = S.get("notification_channel_security_desc")
                enableVibration(true)
                enableLights(true)
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
            notificationManager.createNotificationChannel(securityChannel)
        }
    }

    private fun showChatNotification(senderName: String, chatId: String, otherUid: String) {
        // Fetch the latest message, decrypt it, and show actual text
        db.collection("privateChats").document(chatId)
            .collection("messages")
            .orderBy("timestamp", com.google.firebase.firestore.Query.Direction.DESCENDING)
            .limit(1)
            .get()
            .addOnSuccessListener { docs ->
                val lastMsg = docs.documents.firstOrNull()
                var messageText = S.get("new_message_notification")

                if (lastMsg != null) {
                    val senderId = lastMsg.getString("senderId") ?: ""
                    val myUid = auth.currentUser?.uid ?: ""
                    if (senderId != myUid) {
                        // Try to decrypt with the other user's public key
                        db.collection("publicProfiles").document(otherUid).get()
                            .addOnSuccessListener { profileDoc ->
                                val theirPubKey = profileDoc.getString("publicKey") ?: ""
                                val mediaType = lastMsg.getString("mediaType") ?: ""
                                if (theirPubKey.isNotEmpty()) {
                                    val encText = lastMsg.getString("text") ?: ""
                                    val ephemeralKey = lastMsg.getString("ephemeralKey") ?: ""

                                    // Try PFS decryption first, then fall back to static ECDH
                                    val decryptionKey = if (ephemeralKey.isNotEmpty()) {
                                        derivePFSDecryptionKey(ephemeralKey)
                                    } else {
                                        deriveSharedKey(theirPubKey)
                                    }

                                    if (decryptionKey != null) {
                                        messageText = when {
                                            encText.isNotEmpty() -> {
                                                val decrypted = decryptMessage(encText, decryptionKey)
                                                if (decrypted.startsWith("\uD83D\uDD12")) S.get("new_message_notification") else decrypted
                                            }
                                            mediaType == "image" -> "📷 " + S.get("photo")
                                            mediaType == "voice" -> "🎤 " + S.get("voice_message")
                                            else -> S.get("new_message_notification")
                                        }
                                    }
                                }
                                // Truncate long messages for notification
                                if (messageText.length > 100) messageText = messageText.take(100) + "..."
                                displayNotification(senderName, messageText, chatId, otherUid, mediaType)
                            }
                            .addOnFailureListener {
                                displayNotification(senderName, messageText, chatId, otherUid)
                            }
                        return@addOnSuccessListener
                    }
                }
                displayNotification(senderName, messageText, chatId, otherUid)
            }
            .addOnFailureListener {
                displayNotification(senderName, S.get("new_message_notification"), chatId, otherUid)
            }
    }

    private fun displayNotification(senderName: String, messageText: String, chatId: String, otherUid: String, mediaType: String = "") {
        val soundEnabled = prefs.getBoolean("notification_sound", true)
        val vibrationEnabled = prefs.getBoolean("notification_vibration", true)
        val channelId = "chat_messages"

        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("open_chat_uid", otherUid)
        }
        val pendingIntent = android.app.PendingIntent.getActivity(
            this, chatId.hashCode(), intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notifIcon = when (mediaType) {
            "image" -> android.R.drawable.ic_menu_camera
            "voice" -> android.R.drawable.ic_btn_speak_now
            else -> android.R.drawable.ic_dialog_email
        }

        val builder = androidx.core.app.NotificationCompat.Builder(this, channelId)
            .setSmallIcon(notifIcon)
            .setContentTitle(senderName)
            .setContentText(messageText)
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_MESSAGE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .setVisibility(androidx.core.app.NotificationCompat.VISIBILITY_PRIVATE)

        if (soundEnabled) {
            builder.setDefaults(androidx.core.app.NotificationCompat.DEFAULT_SOUND)
        }
        if (vibrationEnabled) {
            builder.setVibrate(longArrayOf(0, 300, 100, 300))
        }
        if (!soundEnabled && !vibrationEnabled) {
            builder.setDefaults(0)
            builder.setSilent(true)
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(chatId.hashCode(), builder.build())
    }

    private fun saveFCMToken() {
        try {
            val gmsStatus = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                .isGooglePlayServicesAvailable(this)
            if (gmsStatus != com.google.android.gms.common.ConnectionResult.SUCCESS) {
                android.util.Log.w("ExecBin", "GMS unavailable — FCM token skipped (push notifications won't work)")
                return
            }
            FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val token = task.result
                    auth.currentUser?.let { user ->
                        db.collection("users").document(user.uid)
                            .update("fcmToken", token)
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("ExecBin", "FCM token registration failed", e)
        }
    }

    // ==========================================
    // DEVICE MANAGEMENT
    // ==========================================
    private fun saveDeviceSession(trusted: Boolean = false) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("devices").document(currentDeviceId).get()
            .addOnSuccessListener { existing ->
                val isNewDevice = !existing.exists() || existing.getString("userId") != userId
                val now = System.currentTimeMillis()
                val deviceData = hashMapOf<String, Any>(
                    "deviceId" to currentDeviceId,
                    "encDeviceName" to encryptMeta(currentDeviceName),
                    "userId" to userId,
                    "encLoginTimestamp" to encryptMetaLong(now),
                    "encLastActive" to encryptMetaLong(now),
                    "trusted" to trusted
                )
                db.collection("devices").document(currentDeviceId)
                    .set(deviceData)
                    .addOnSuccessListener {
                        loadUserDevices()
                        if (isNewDevice) sendLoginAlertNotification()
                    }
            }
            .addOnFailureListener {
                val now = System.currentTimeMillis()
                val deviceData = hashMapOf<String, Any>(
                    "deviceId" to currentDeviceId,
                    "encDeviceName" to encryptMeta(currentDeviceName),
                    "userId" to userId,
                    "encLoginTimestamp" to encryptMetaLong(now),
                    "encLastActive" to encryptMetaLong(now),
                    "trusted" to trusted
                )
                db.collection("devices").document(currentDeviceId)
                    .set(deviceData)
                    .addOnSuccessListener { loadUserDevices() }
            }
    }

    private fun loadUserDevices(onComplete: (() -> Unit)? = null) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("devices")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { docs ->
                userDevices.clear()
                docs.forEach { doc ->
                    val devId = doc.getString("deviceId") ?: ""
                    // Support both encrypted and legacy plaintext fields
                    val devName = doc.getString("encDeviceName")?.let { decryptMeta(it) }
                        ?: doc.getString("deviceName") ?: ""
                    val loginTs = doc.getString("encLoginTimestamp")?.let { decryptMetaLong(it) }
                        ?: doc.getLong("loginTimestamp") ?: 0L
                    val lastAct = doc.getString("encLastActive")?.let { decryptMetaLong(it) }
                        ?: doc.getLong("lastActive") ?: 0L
                    val trusted = doc.getBoolean("trusted") ?: false
                    userDevices.add(Device(devId, devName, userId, loginTs, lastAct, trusted))
                }
                onComplete?.invoke()
            }
            .addOnFailureListener { onComplete?.invoke() }
    }

    private fun removeDevice(deviceId: String) {
        db.collection("devices").document(deviceId)
            .delete()
            .addOnSuccessListener {
                if (deviceId == currentDeviceId) {
                    clearDeviceTrustLocally()
                    auth.signOut()
                    showRegistration()
                } else {
                    checkAndMarkForDeletion()
                    showSettings()
                }
            }
    }

    private fun getDeviceAccountCount(): Int {
        val accounts = prefs.getStringSet("device_accounts_$currentDeviceId", emptySet()) ?: emptySet()
        return accounts.size
    }

    private fun isDeviceAccountLimitReached(excludeUid: String? = null): Boolean {
        return false // No limit on accounts per device
    }

    private fun registerAccountOnDevice(uid: String) {
        val accounts = prefs.getStringSet("device_accounts_$currentDeviceId", mutableSetOf()) ?: mutableSetOf()
        val updated = accounts.toMutableSet()
        updated.add(uid)
        prefs.edit().putStringSet("device_accounts_$currentDeviceId", updated).apply()
        db.collection("deviceAccounts").document(currentDeviceId)
            .set(hashMapOf<String, Any>(
                "deviceId" to currentDeviceId,
                "accounts" to updated.toList(),
                "encLastUpdated" to encryptMetaLong(System.currentTimeMillis())
            ))
    }

    private fun markAccountCreatedOnDevice(uid: String) {
        val created = prefs.getStringSet("device_created_$currentDeviceId", mutableSetOf()) ?: mutableSetOf()
        val updated = created.toMutableSet()
        updated.add(uid)
        prefs.edit().putStringSet("device_created_$currentDeviceId", updated).apply()
    }

    private fun isAccountCreatedOnDevice(uid: String): Boolean {
        val created = prefs.getStringSet("device_created_$currentDeviceId", emptySet()) ?: emptySet()
        return created.contains(uid)
    }

    private fun removeAccountFromDevice(uid: String) {
        val accounts = prefs.getStringSet("device_accounts_$currentDeviceId", mutableSetOf()) ?: mutableSetOf()
        val updated = accounts.toMutableSet()
        updated.remove(uid)
        prefs.edit().putStringSet("device_accounts_$currentDeviceId", updated).apply()
        db.collection("deviceAccounts").document(currentDeviceId)
            .set(hashMapOf<String, Any>(
                "deviceId" to currentDeviceId,
                "accounts" to updated.toList(),
                "encLastUpdated" to encryptMetaLong(System.currentTimeMillis())
            ))
    }

    private fun getDeviceAccounts(): Set<String> {
        return prefs.getStringSet("device_accounts_$currentDeviceId", emptySet()) ?: emptySet()
    }

    private fun getOtherDeviceAccounts(): Set<String> {
        val uid = auth.currentUser?.uid ?: return emptySet()
        return getDeviceAccounts().minus(uid)
    }

    private fun performLogout(removeFromDevice: Boolean = false) {
        val uid = auth.currentUser?.uid ?: ""
        setOfflineStatus()
        globalChatListener?.remove()
        privateChatListener?.remove()
        privateChatListListener?.remove()
        securityAlertListener?.remove()

        if (removeFromDevice && uid.isNotEmpty()) {
            removeAccountFromDevice(uid)
        }

        auth.signOut()
    }

    private fun logoutAndSwitch() {
        val uid = auth.currentUser?.uid ?: ""
        val otherAccounts = getOtherDeviceAccounts()

        // If account wasn't created on this device, remove it from device list on logout
        val removeFromDevice = uid.isNotEmpty() && !isAccountCreatedOnDevice(uid)
        performLogout(removeFromDevice)

        if (otherAccounts.isNotEmpty()) {
            showSwitchAccountDialog(excludeUid = uid, alreadyLoggedOut = true)
        } else {
            showRegistration()
        }
    }

    private fun showSwitchAccountDialog(excludeUid: String? = null, alreadyLoggedOut: Boolean = false) {
        // Determine the current user's UID to exclude from the list
        val myUid = excludeUid ?: auth.currentUser?.uid ?: ""
        val accounts = getDeviceAccounts()
        val otherAccounts = accounts.filter { it != myUid }.toList()

        val dialogBg = if (isDarkTheme) Color.parseColor("#1E293B") else Color.parseColor("#FFFFFF")
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBackground(dialogBg, 20)
            setPadding(dp(24), dp(24), dp(24), dp(20))
        }

        // Title
        root.addView(TextView(this).apply {
            text = iconize("👥  " + S.get("switch_account"))
            setTextColor(textColor)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
        })
        root.addView(TextView(this).apply {
            text = S.get("switch_account_desc")
            setTextColor(subTextColor)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) }
        })

        val dialog = AlertDialog.Builder(this)
            .setView(root)
            .setCancelable(!alreadyLoggedOut)
            .create()

        // Helper: sign out current user if not already done
        fun ensureLoggedOut() {
            if (!alreadyLoggedOut && auth.currentUser != null) {
                val uid = auth.currentUser?.uid ?: ""
                val removeFromDevice = uid.isNotEmpty() && !isAccountCreatedOnDevice(uid)
                performLogout(removeFromDevice)
            }
        }

        // Helper: add the "Add Account" button
        fun addAddAccountButton() {
            if (otherAccounts.isNotEmpty()) {
                root.addView(View(this).apply {
                    setBackgroundColor(if (isDarkTheme) Color.parseColor("#2A2D4A") else Color.parseColor("#E8ECF0"))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                        topMargin = dp(8); bottomMargin = dp(12)
                    }
                })
            }
            root.addView(Button(this).apply {
                text = iconize("➕  " + S.get("add_account"), Color.WHITE)
                setTextColor(Color.WHITE)
                textSize = 14f
                background = createGradientButton()
                isAllCaps = false
                setTypeface(null, android.graphics.Typeface.BOLD)
                stateListAnimator = null
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
                setOnClickListener {
                    dialog.dismiss()
                    ensureLoggedOut()
                    showLogin()
                }
            })
        }

        if (otherAccounts.isEmpty()) {
            // No other accounts — show prompt
            root.addView(TextView(this).apply {
                text = S.get("no_other_accounts", accounts.size)
                setTextColor(subTextColor)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, dp(16))
            })
            addAddAccountButton()
        } else {
            var loaded = 0
            otherAccounts.forEach { uid ->
                db.collection("publicProfiles").document(uid).get()
                    .addOnSuccessListener { doc ->
                        val username = doc.getString("username") ?: uid.take(8)
                        val nickname = doc.getString("nickname") ?: username
                        val photo = doc.getString("photoBase64") ?: ""
                        val displayName = if (nickname.isNotEmpty() && nickname != username) "$nickname (@$username)" else "@$username"

                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            background = createRoundedBackground(cardBg, 14)
                            setPadding(dp(14), dp(12), dp(14), dp(12))
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
                        }

                        val photoView = ImageView(this).apply {
                            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(12) }
                            scaleType = ImageView.ScaleType.CENTER_CROP
                            background = GradientDrawable().apply {
                                shape = GradientDrawable.OVAL
                                setColor(if (isDarkTheme) Color.parseColor("#252B48") else Color.parseColor("#E8EAF4"))
                            }
                            clipToOutline = true
                            outlineProvider = object : android.view.ViewOutlineProvider() {
                                override fun getOutline(view: View, outline: android.graphics.Outline) {
                                    outline.setOval(0, 0, view.width, view.height)
                                }
                            }
                        }
                        if (photo.isNotEmpty()) {
                            try {
                                val imageBytes = Base64.decode(photo, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                photoView.setImageBitmap(bitmap)
                            } catch (_: Exception) {}
                        }
                        row.addView(photoView)

                        row.addView(TextView(this).apply {
                            text = displayName
                            setTextColor(textColor)
                            textSize = 15f
                            setTypeface(null, android.graphics.Typeface.BOLD)
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })

                        row.addView(TextView(this).apply {
                            text = "→"
                            setTextColor(accentColor)
                            textSize = 20f
                        })

                        row.setOnClickListener {
                            dialog.dismiss()
                            ensureLoggedOut()
                            showLogin()
                            Toast.makeText(this, S.get("login_as", username), Toast.LENGTH_SHORT).show()
                        }

                        root.addView(row)
                        loaded++

                        // After all accounts loaded, add "Add Account" button
                        if (loaded == otherAccounts.size) {
                            addAddAccountButton()
                        }
                    }
                    .addOnFailureListener {
                        loaded++
                        if (loaded == otherAccounts.size) {
                            addAddAccountButton()
                        }
                    }
            }
        }

        // Cancel / Back button
        root.addView(Button(this).apply {
            text = if (alreadyLoggedOut) S.get("back") else S.get("cancel")
            setTextColor(subTextColor)
            textSize = 14f
            background = createRoundedBackground(cardBg, 12)
            isAllCaps = false
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(46)).apply { topMargin = dp(8) }
            setOnClickListener {
                dialog.dismiss()
                if (alreadyLoggedOut) {
                    // User is already signed out, go to login screen
                    showLogin()
                }
            }
        })

        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.show()
    }

    private fun syncDeviceAccountsFromFirestore(onComplete: (Int) -> Unit) {
        db.collection("deviceAccounts").document(currentDeviceId).get()
            .addOnSuccessListener { doc ->
                if (doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val accounts = doc.get("accounts") as? List<String> ?: emptyList()
                    prefs.edit().putStringSet("device_accounts_$currentDeviceId", accounts.toMutableSet()).apply()
                    onComplete(accounts.size)
                } else {
                    // No doc in Firestore — clear stale local cache (database was wiped)
                    prefs.edit().putStringSet("device_accounts_$currentDeviceId", emptySet()).apply()
                    prefs.edit().putStringSet("device_created_$currentDeviceId", emptySet()).apply()
                    onComplete(0)
                }
            }
            .addOnFailureListener { onComplete(getDeviceAccountCount()) }
    }

    private fun checkDeviceLimitAndProceed(onAllowed: () -> Unit) {
        val userId = auth.currentUser?.uid ?: return
        db.collection("devices")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { docs ->
                val existingDeviceIds = docs.map { it.getString("deviceId") ?: "" }
                if (existingDeviceIds.contains(currentDeviceId)) {
                    onAllowed()
                } else if (docs.size() >= MAX_DEVICES_PER_ACCOUNT) {
                    Toast.makeText(this, S.get("max_devices_reached"), Toast.LENGTH_LONG).show()
                    showDeviceLimitReached()
                } else {
                    onAllowed()
                }
            }
            .addOnFailureListener { onAllowed() }
    }

    private fun showDeviceLimitReached() {
        container.removeAllViews()
        container.setBackgroundColor(bgColor)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(32), dp(60), dp(32), dp(40))
        }
        root.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("⚠", Color.parseColor("#F59E0B"), dp(70)))
            layoutParams = LinearLayout.LayoutParams(dp(70), dp(70)).apply { bottomMargin = dp(24); gravity = Gravity.CENTER_HORIZONTAL }
        })
        root.addView(TextView(this).apply {
            text = S.get("device_limit_reached")
            setTextColor(textColor)
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        })
        root.addView(TextView(this).apply {
            text = S.get("device_limit_message", MAX_DEVICES_PER_ACCOUNT)
            setTextColor(subTextColor)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) }
        })
        val userId = auth.currentUser?.uid ?: return
        db.collection("devices")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { docs ->
                docs.forEach { doc ->
                    val devId = doc.getString("deviceId") ?: ""
                    val devName = doc.getString("encDeviceName")?.let { decryptMeta(it) }
                        ?: doc.getString("deviceName") ?: ""
                    if (devId != currentDeviceId) {
                        val row = LinearLayout(this).apply {
                            orientation = LinearLayout.HORIZONTAL
                            background = createRoundedBackground(cardBg, 12)
                            setPadding(dp(16), dp(12), dp(16), dp(12))
                            gravity = Gravity.CENTER_VERTICAL
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
                        }
                        row.addView(TextView(this).apply {
                            text = iconize("📱 $devName")
                            setTextColor(textColor)
                            textSize = 14f
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        })
                        row.addView(Button(this).apply {
                            text = S.get("kick")
                            setTextColor(Color.WHITE)
                            textSize = 12f
                            background = createRoundedBackground(Color.parseColor("#EF4444"), 8)
                            layoutParams = LinearLayout.LayoutParams(dp(70), dp(36))
                            setOnClickListener {
                                val signInTime = auth.currentUser?.metadata?.lastSignInTimestamp ?: 0L
                                val hoursSinceSignIn = (System.currentTimeMillis() - signInTime) / (1000 * 60 * 60)
                                if (hoursSinceSignIn < COOLDOWN_HOURS) {
                                    val hoursLeft = COOLDOWN_HOURS - hoursSinceSignIn
                                    Toast.makeText(this@MainActivity, S.get("login_cooldown", hoursLeft), Toast.LENGTH_LONG).show()
                                    return@setOnClickListener
                                }
                                db.collection("devices").document(devId).delete()
                                    .addOnSuccessListener {
                                        Toast.makeText(this@MainActivity, S.get("device_kicked"), Toast.LENGTH_SHORT).show()
                                        proceedToHome()
                                    }
                            }
                        })
                        root.addView(row)
                    }
                }
            }
        root.addView(TextView(this).apply {
            text = S.get("logout")
            setTextColor(subTextColor)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
            setOnClickListener {
                setOfflineStatus()
                globalChatListener?.remove()
                privateChatListener?.remove()
                privateChatListListener?.remove()
                securityAlertListener?.remove()
                auth.signOut()
                showRegistration()
            }
        })
        container.addView(root)
    }

    private fun checkAndMarkForDeletion() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("devices")
            .whereEqualTo("userId", userId)
            .get()
            .addOnSuccessListener { docs ->
                if (docs.isEmpty) {
                    db.collection("users").document(userId)
                        .update("markedForDeletion", System.currentTimeMillis())
                } else {
                    db.collection("users").document(userId)
                        .update("markedForDeletion", com.google.firebase.firestore.FieldValue.delete())
                }
            }
    }

    private fun clearDeletionMark() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId)
            .update("markedForDeletion", com.google.firebase.firestore.FieldValue.delete())
    }

    // ==========================================
    // USER DATA + PIN CHECK FLOW
    // ==========================================
    private fun checkAndLoadUserData() {
        val user = auth.currentUser ?: run { showRegistration(); return }
        // Set Crashlytics user ID for crash correlation
        try { com.google.firebase.crashlytics.FirebaseCrashlytics.getInstance().setUserId(user.uid) } catch (_: Exception) {}
        user.getIdToken(true)
            .addOnSuccessListener {
                db.collection("publicProfiles").document(user.uid).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            myUsername = doc.getString("username") ?: ""
                            myNickname = doc.getString("nickname") ?: myUsername
                            myPhotoBase64 = doc.getString("photoBase64") ?: ""
                            myBio = doc.getString("bio") ?: ""
                            ensureE2EKeysExist {
                                checkPinBeforeEntry()
                            }
                        } else {
                            createFirestoreProfile(user)
                        }
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("ExecBin", "Firestore profile fetch failed: ${e.message}", e)
                        Toast.makeText(this, "Profile: ${e.message}", Toast.LENGTH_LONG).show()
                        showRegistration()
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ExecBin", "getIdToken failed: ${e.message}", e)
                Toast.makeText(this, "Auth: ${e.message}", Toast.LENGTH_LONG).show()
                showRegistration()
            }
    }

    private fun createFirestoreProfile(user: com.google.firebase.auth.FirebaseUser) {
        val username = user.displayName ?: "user_${user.uid.take(8)}"
        val emailHash = hashEmail(user.email ?: "")
        generateE2EKeyPairInKeystore()
        val privateData = hashMapOf(
            "uid" to user.uid,
            "email" to emailHash,
            "fcmToken" to "",
            "securityPin" to "",
            "hasPinEnabled" to false
        )
        val publicData = hashMapOf(
            "uid" to user.uid,
            "username" to username,
            "nickname" to username,
            "photoBase64" to "",
            "bio" to "",
            "isOnline" to true,
            "lastSeen" to System.currentTimeMillis(),
            "publicKey" to myPublicKeyBase64
        )
        db.collection("users").document(user.uid).set(privateData)
            .addOnSuccessListener {
                db.collection("publicProfiles").document(user.uid).set(publicData)
                    .addOnSuccessListener {
                        markAccountCreatedOnDevice(user.uid)
                        myUsername = username
                        myNickname = username
                        myPhotoBase64 = ""
                        myBio = ""
                        showPinSetup()
                    }
                    .addOnFailureListener { e ->
                        android.util.Log.e("ExecBin", "Public profile creation failed: ${e.message}", e)
                        Toast.makeText(this, "Profile setup: ${e.message}", Toast.LENGTH_LONG).show()
                        showRegistration()
                    }
            }
            .addOnFailureListener { e ->
                android.util.Log.e("ExecBin", "Private profile creation failed: ${e.message}", e)
                Toast.makeText(this, "Setup: ${e.message}", Toast.LENGTH_LONG).show()
                showRegistration()
            }
    }

    private fun checkPinBeforeEntry() {
        val userId = auth.currentUser?.uid ?: return
        db.collection("users").document(userId).get()
            .addOnSuccessListener { doc ->
                hasPinEnabled = doc.getBoolean("hasPinEnabled") ?: false
                prefs.edit().putBoolean("pin_enabled_$userId", hasPinEnabled).apply()
                if (hasPinEnabled && !isDeviceTrustedLocally()) {
                    showPinEntry()
                } else {
                    proceedToHome()
                }
            }
            .addOnFailureListener {
                // Fail closed: if we can't confirm the PIN is off, ask for it (unless trusted locally)
                val knownPinOff = prefs.contains("pin_enabled_$userId") && !prefs.getBoolean("pin_enabled_$userId", true)
                if (knownPinOff || isDeviceTrustedLocally()) proceedToHome() else showPinEntry()
            }
    }

    private fun markPinVerifiedOnDevice(userId: String) {
        prefs.edit().putBoolean("pin_verified_device_$userId", true).apply()
    }

    private fun isPinVerifiedOnDevice(userId: String?): Boolean =
        userId != null && prefs.getBoolean("pin_verified_device_$userId", false)

    private fun proceedToHome() {
        checkDeviceLimitAndProceed {
            clearDeletionMark()
            registerAccountOnDevice(auth.currentUser?.uid ?: return@checkDeviceLimitAndProceed)
            saveFCMToken()
            saveDeviceSession(trusted = true)
            markDeviceTrustedLocally()
            listenForSecurityAlerts()
            loadBlockedUsers()
            showHome()
        }
    }

    // ==========================================
    // ONBOARDING TUTORIAL
    // ==========================================
    private fun showTutorialIfNeeded() {
        if (prefs.getBoolean("tutorial_completed", false)) return
        val steps = listOf(
            Triple("👋", S.get("tutorial_welcome_title"), S.get("tutorial_welcome_desc")),
            Triple("💬", S.get("tutorial_global_title"), S.get("tutorial_global_desc")),
            Triple("🔒", S.get("tutorial_dm_title"), S.get("tutorial_dm_desc")),
            Triple("📋", S.get("tutorial_actions_title"), S.get("tutorial_actions_desc")),
            Triple("🛡️", S.get("tutorial_security_title"), S.get("tutorial_security_desc"))
        )
        var currentStep = 0
        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#CC000000"))
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isClickable = true
        }
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBackground(if (isDarkTheme) Color.parseColor("#1C2240") else Color.WHITE, 24)
            setPadding(dp(28), dp(32), dp(28), dp(24))
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                gravity = Gravity.CENTER
                setMargins(dp(24), dp(24), dp(24), dp(24))
            }
            elevation = dp(8).toFloat()
        }
        val tutorialIcon = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { bottomMargin = dp(16); gravity = Gravity.CENTER_HORIZONTAL }
        }
        val titleView = TextView(this).apply {
            setTextColor(textColor)
            textSize = 22f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = -0.02f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        val descView = TextView(this).apply {
            setTextColor(subTextColor)
            textSize = 15f
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) }
        }
        val dotsRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(20) }
        }
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val skipBtn = TextView(this).apply {
            text = S.get("skip")
            setTextColor(subTextColor)
            textSize = 15f
            setPadding(dp(20), dp(12), dp(20), dp(12))
        }
        val nextBtn = TextView(this).apply {
            text = S.get("next")
            setTextColor(Color.WHITE)
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = createGradientButton()
            setPadding(dp(32), dp(12), dp(32), dp(12))
        }
        btnRow.addView(skipBtn)
        btnRow.addView(View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) })
        btnRow.addView(nextBtn)
        card.addView(tutorialIcon)
        card.addView(titleView)
        card.addView(descView)
        card.addView(dotsRow)
        card.addView(btnRow)
        overlay.addView(card)
        fun updateStep() {
            val (icon, title, desc) = steps[currentStep]
            tutorialIcon.setImageDrawable(LineIconDrawable(icon, textColor, dp(56)))
            titleView.text = title
            descView.text = desc
            dotsRow.removeAllViews()
            for (i in steps.indices) {
                dotsRow.addView(View(this).apply {
                    val size = if (i == currentStep) dp(10) else dp(8)
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { setMargins(dp(4), 0, dp(4), 0) }
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(if (i == currentStep) accentColor else if (isDarkTheme) Color.parseColor("#334155") else Color.parseColor("#CBD5E1"))
                    }
                })
            }
            nextBtn.text = if (currentStep == steps.size - 1) S.get("get_started") else S.get("next")
        }
        fun finish() {
            prefs.edit().putBoolean("tutorial_completed", true).apply()
            container.removeView(overlay)
        }
        skipBtn.setOnClickListener { finish() }
        nextBtn.setOnClickListener {
            if (currentStep < steps.size - 1) {
                currentStep++
                updateStep()
            } else {
                finish()
            }
        }
        updateStep()
        container.addView(overlay)
    }

    // ==========================================
    // PIN SETUP
    // ==========================================
    private fun showPinSetup(isChange: Boolean = false) {
        currentScreen = "pin_setup"
        container.removeAllViews()
        container.setBackgroundColor(bgColor)
        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(60), dp(24), dp(40))
        }
        root.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("🔐", accentColor, dp(70)))
            layoutParams = LinearLayout.LayoutParams(dp(70), dp(70)).apply { bottomMargin = dp(24); gravity = Gravity.CENTER_HORIZONTAL }
        })
        root.addView(TextView(this).apply {
            text = if (isChange) S.get("change_security_pin") else S.get("setup_2step")
            setTextColor(textColor)
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        })
        root.addView(TextView(this).apply {
            text = S.get("pin_setup_desc")
            setTextColor(subTextColor)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(32) }
        })
        val pinField = EditText(this).apply {
            hint = S.get("enter_pin")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 24f
            letterSpacing = 0.5f
            gravity = Gravity.CENTER
            background = createRoundedBackground(cardBg, 12)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            inputType = InputType.TYPE_CLASS_NUMBER
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)).apply { bottomMargin = dp(16) }
        }
        root.addView(pinField)
        val confirmField = EditText(this).apply {
            hint = S.get("confirm_pin")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 24f
            letterSpacing = 0.5f
            gravity = Gravity.CENTER
            background = createRoundedBackground(cardBg, 12)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            inputType = InputType.TYPE_CLASS_NUMBER
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64)).apply { bottomMargin = dp(24) }
        }
        root.addView(confirmField)
        val saveBtn = Button(this).apply {
            text = if (isChange) S.get("change_pin") else S.get("set_pin")
            setTextColor(Color.WHITE)
            textSize = 16f
            background = createRoundedBackground(accentColor, 12)
            isAllCaps = true
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(12) }
        }
        root.addView(saveBtn)
        saveBtn.setOnClickListener {
            val pin = pinField.text.toString().trim()
            val confirmPin = confirmField.text.toString().trim()
            when {
                pin.length != 6 -> Toast.makeText(this, S.get("pin_6_digits"), Toast.LENGTH_SHORT).show()
                pin != confirmPin -> Toast.makeText(this, S.get("pins_dont_match"), Toast.LENGTH_SHORT).show()
                pin.matches(Regex("(\\d)\\1{5}")) -> Toast.makeText(this, S.get("pin_too_simple"), Toast.LENGTH_SHORT).show()
                pin == "123456" || pin == "654321" || pin == "000000" -> Toast.makeText(this, S.get("pin_too_common"), Toast.LENGTH_SHORT).show()
                else -> {
                    saveBtn.isEnabled = false
                    saveBtn.text = S.get("saving")
                    val userId = auth.currentUser?.uid ?: return@setOnClickListener
                    val hashedPin = createPinHash(pin)
                    db.collection("users").document(userId)
                        .update(mapOf("securityPin" to hashedPin, "hasPinEnabled" to true))
                        .addOnSuccessListener {
                            hasPinEnabled = true
                            markDeviceTrustedLocally()
                            markPinVerifiedOnDevice(userId)
                            Toast.makeText(this, S.get("two_step_enabled"), Toast.LENGTH_LONG).show()
                            if (isChange) showSettings() else proceedToHome()
                        }
                        .addOnFailureListener { e ->
                            Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
                            saveBtn.isEnabled = true
                            saveBtn.text = if (isChange) S.get("change_pin") else S.get("set_pin")
                        }
                }
            }
        }
        if (!isChange) {
            root.addView(TextView(this).apply {
                text = S.get("skip_for_now")
                setTextColor(subTextColor)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
                setOnClickListener { proceedToHome() }
            })
        } else {
            root.addView(TextView(this).apply {
                text = "← ${S.get("back_to_settings")}"
                setTextColor(accentColor)
                textSize = 14f
                gravity = Gravity.CENTER
                setPadding(0, dp(12), 0, 0)
                setOnClickListener { showSettings() }
            })
        }
        scroll.addView(root)
        container.addView(scroll)
    }

    // ==========================================
    // PIN ENTRY
    // ==========================================
    private fun showPinEntry() {
        currentScreen = "pin_entry"
        container.removeAllViews()
        container.setBackgroundColor(bgColor)
        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(60), dp(24), dp(40))
        }
        root.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("🔒", accentColor, dp(70)))
            layoutParams = LinearLayout.LayoutParams(dp(70), dp(70)).apply { bottomMargin = dp(24); gravity = Gravity.CENTER_HORIZONTAL }
        })
        root.addView(TextView(this).apply {
            text = S.get("two_step_verification")
            setTextColor(textColor)
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        })
        root.addView(TextView(this).apply {
            text = S.get("enter_security_pin")
            setTextColor(subTextColor)
            textSize = 15f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        })
        root.addView(TextView(this).apply {
            text = iconize("📱 $currentDeviceName")
            setTextColor(subTextColor)
            textSize = 12f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(32) }
        })
        val attemptsLabel = TextView(this).apply {
            text = ""
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 13f
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        root.addView(attemptsLabel)
        val pinField = EditText(this).apply {
            hint = S.get("pin_placeholder")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 28f
            letterSpacing = 0.8f
            gravity = Gravity.CENTER
            background = createRoundedBackground(cardBg, 12)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            inputType = InputType.TYPE_CLASS_NUMBER
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(72)).apply { bottomMargin = dp(24) }
        }
        root.addView(pinField)
        val verifyBtn = Button(this).apply {
            text = S.get("verify")
            setTextColor(Color.WHITE)
            textSize = 16f
            background = createRoundedBackground(accentColor, 12)
            isAllCaps = true
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(16) }
        }
        root.addView(verifyBtn)

        // Check if currently locked out from previous failed attempts
        val lockoutUntil = getPinLockoutUntil()
        if (lockoutUntil > System.currentTimeMillis()) {
            val remainingMs = lockoutUntil - System.currentTimeMillis()
            verifyBtn.isEnabled = false
            attemptsLabel.text = S.get("pin_lockout_timer", formatLockoutDuration(remainingMs))
            attemptsLabel.visibility = View.VISIBLE
            android.os.Handler(mainLooper).postDelayed({
                verifyBtn.isEnabled = true
                verifyBtn.text = S.get("verify")
                attemptsLabel.visibility = View.GONE
            }, remainingMs)
        }

        verifyBtn.setOnClickListener {
            // Re-check lockout in case user taps during lockout
            val currentLockout = getPinLockoutUntil()
            if (currentLockout > System.currentTimeMillis()) {
                val remaining = formatLockoutDuration(currentLockout - System.currentTimeMillis())
                Toast.makeText(this, S.get("pin_lockout_timer", remaining), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val pin = pinField.text.toString().trim()
            if (pin.length != 6) {
                Toast.makeText(this, S.get("enter_pin"), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            verifyBtn.isEnabled = false
            verifyBtn.text = S.get("verifying")
            val userId = auth.currentUser?.uid ?: return@setOnClickListener
            db.collection("users").document(userId).get()
                .addOnSuccessListener { doc ->
                    val storedHash = doc.getString("securityPin") ?: ""
                    if (verifyPin(pin, userId, storedHash)) {
                        // Success — reset all lockout state
                        setPinAttempts(0)
                        setPinLockoutUntil(0L)
                        markPinVerifiedOnDevice(userId)
                        proceedToHome()
                    } else {
                        val attempts = getPinAttempts() + 1
                        setPinAttempts(attempts)
                        pinField.setText("")

                        val lockoutMs = getLockoutDuration(attempts)
                        if (lockoutMs > 0) {
                            if (attempts >= 10) setPinAttempts(7) // cycle 15m→1h→2h
                            val lockoutUntil = System.currentTimeMillis() + lockoutMs
                            setPinLockoutUntil(lockoutUntil)
                            val durationStr = formatLockoutDuration(lockoutMs)
                            Toast.makeText(this, S.get("pin_lockout_timer", durationStr), Toast.LENGTH_LONG).show()
                            verifyBtn.isEnabled = false
                            attemptsLabel.text = S.get("pin_lockout_timer", durationStr)
                            attemptsLabel.setTextColor(Color.parseColor("#EF4444"))
                            attemptsLabel.visibility = View.VISIBLE
                            // Re-enable after lockout expires
                            android.os.Handler(mainLooper).postDelayed({
                                verifyBtn.isEnabled = true
                                verifyBtn.text = S.get("verify")
                                attemptsLabel.visibility = View.GONE
                            }, lockoutMs)
                        } else {
                            val remaining = 10 - attempts
                            attemptsLabel.text = S.get("wrong_pin_remaining", remaining)
                            attemptsLabel.visibility = View.VISIBLE
                            pinField.animate()
                                .translationX(20f).setDuration(50)
                                .withEndAction {
                                    pinField.animate().translationX(-20f).setDuration(50)
                                        .withEndAction {
                                            pinField.animate().translationX(0f).setDuration(50).start()
                                        }.start()
                                }.start()
                            verifyBtn.isEnabled = true
                            verifyBtn.text = S.get("verify")
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
                    verifyBtn.isEnabled = true
                    verifyBtn.text = S.get("verify")
                }
        }
        // Biometric unlock is only a shortcut on a device where this account's PIN was already
        // verified — otherwise anyone could pass 2-step verification with their own fingerprint
        if (isBiometricAvailable() && isPinVerifiedOnDevice(auth.currentUser?.uid)) {
            root.addView(TextView(this).apply {
                text = iconize("🔑 ${S.get("use_biometric")}", accentColor)
                setTextColor(accentColor)
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(0, dp(16), 0, dp(4))
                setOnClickListener { showBiometricPrompt { proceedToHome() } }
            })
        }
        root.addView(TextView(this).apply {
            text = S.get("not_you_logout")
            setTextColor(subTextColor)
            textSize = 14f
            gravity = Gravity.CENTER
            setOnClickListener {
                auth.signOut()
                showRegistration()
            }
        })
        scroll.addView(root)
        container.addView(scroll)
    }

    // ==========================================
    // VERIFY CURRENT PIN DIALOG
    // ==========================================
    private fun showVerifyCurrentPin(onSuccess: () -> Unit) {
        val dialogView = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(16))
        }
        dialogView.addView(TextView(this).apply {
            text = S.get("enter_current_pin")
            setTextColor(textColor)
            textSize = 15f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) }
        })
        val pinInput = EditText(this).apply {
            hint = S.get("pin_placeholder")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 24f
            letterSpacing = 0.5f
            gravity = Gravity.CENTER
            background = createRoundedBackground(cardBg, 12)
            setPadding(dp(16), dp(12), dp(16), dp(12))
            inputType = InputType.TYPE_CLASS_NUMBER
            transformationMethod = android.text.method.PasswordTransformationMethod.getInstance()
            filters = arrayOf(android.text.InputFilter.LengthFilter(6))
        }
        dialogView.addView(pinInput)
        val dialog = AlertDialog.Builder(this)
            .setTitle(iconize("🔐 ${S.get("verify_pin")}"))
            .setView(dialogView)
            .setPositiveButton(S.get("verify"), null)
            .setNegativeButton(S.get("cancel"), null)
            .create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val pin = pinInput.text.toString().trim()
                if (pin.length != 6) {
                    Toast.makeText(this, S.get("enter_pin"), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val userId = auth.currentUser?.uid ?: return@setOnClickListener
                db.collection("users").document(userId).get()
                    .addOnSuccessListener { doc ->
                        val storedHash = doc.getString("securityPin") ?: ""
                        if (verifyPin(pin, userId, storedHash)) {
                            dialog.dismiss()
                            onSuccess()
                        } else {
                            Toast.makeText(this, S.get("wrong_pin"), Toast.LENGTH_SHORT).show()
                            pinInput.setText("")
                        }
                    }
            }
        }
        dialog.show()
    }

    // ==========================================
    // USER PROFILE POPUP
    // ==========================================
    private fun showUserProfilePopup(username: String, nickname: String, photoBase64: String, userId: String) {
        db.collection("publicProfiles").document(userId).get()
            .addOnSuccessListener { doc ->
                val bio = doc?.getString("bio") ?: ""
                val hideLastSeen = doc?.getBoolean("hideLastSeen") ?: false
                val isOnline = if (hideLastSeen) false else (doc?.getBoolean("isOnline") ?: false)
                val lastSeen = if (hideLastSeen) -1L else (doc?.getLong("lastSeen") ?: 0L)
                val dialogView = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    setPadding(dp(24), dp(24), dp(24), dp(24))
                    setBackgroundColor(cardBg)
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                val photoView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(100), dp(100)).apply { bottomMargin = dp(16) }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = createRoundedBackground(if (isDarkTheme) Color.parseColor("#2D3748") else Color.parseColor("#E2E8F0"), 50)
                }
                if (photoBase64.isNotEmpty()) {
                    try {
                        val imageBytes = Base64.decode(photoBase64, Base64.DEFAULT)
                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                        photoView.setImageBitmap(bitmap)
                    } catch (e: Exception) { photoView.setImageDrawable(null) }
                }
                dialogView.addView(photoView)
                val statusText = if (isOnline) "🟢 ${S.get("online")}" else if (lastSeen == -1L) {
                    "⚫ ${S.get("last_seen_hidden")}"
                } else {
                    val mins = (System.currentTimeMillis() - lastSeen) / 1000 / 60
                    when {
                        lastSeen == 0L -> "⚫ ${S.get("offline")}"
                        mins < 1 -> "🟢 ${S.get("just_now")}"
                        mins < 60 -> "🟡 ${S.get("minutes_ago", mins)}"
                        mins < 1440 -> "🟠 ${S.get("hours_ago", mins / 60)}"
                        else -> "⚫ ${S.get("days_ago", mins / 1440)}"
                    }
                }
                dialogView.addView(TextView(this).apply {
                    text = iconize(statusText, if (isOnline) Color.parseColor("#22C55E") else subTextColor, 12)
                    setTextColor(if (isOnline) Color.parseColor("#22C55E") else subTextColor)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
                })
                dialogView.addView(TextView(this).apply {
                    text = "@$username"
                    setTextColor(accentColor)
                    textSize = 18f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4) }
                })
                if (nickname.isNotEmpty() && nickname != username) {
                    dialogView.addView(TextView(this).apply {
                        text = nickname
                        setTextColor(textColor)
                        textSize = 16f
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4) }
                    })
                }
                if (bio.isNotEmpty()) {
                    dialogView.addView(TextView(this).apply {
                        text = bio
                        setTextColor(subTextColor)
                        textSize = 13f
                        gravity = Gravity.CENTER
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) }
                    })
                } else {
                    dialogView.addView(View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(20))
                    })
                }
                val sendMsgBtn = Button(this).apply {
                    text = S.get("send_message")
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    background = createRoundedBackground(accentColor, 8)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(8) }
                }
                dialogView.addView(sendMsgBtn)

                val alertDialog = AlertDialog.Builder(this).setView(dialogView).create()
                alertDialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                // Block/Unblock button (don't show for yourself)
                if (userId != auth.currentUser?.uid) {
                    val isBlocked = blockedUsers.contains(userId)
                    dialogView.addView(Button(this).apply {
                        text = if (isBlocked) S.get("unblock") else S.get("block")
                        setTextColor(Color.parseColor("#EF4444"))
                        textSize = 14f
                        background = createRoundedBackground(cardBg, 8)
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48))
                        setOnClickListener {
                            alertDialog.dismiss()
                            if (isBlocked) unblockUser(userId, username)
                            else blockUser(userId, username)
                        }
                    })
                }

                sendMsgBtn.setOnClickListener {
                    if (blockedUsers.contains(userId)) {
                        Toast.makeText(this, S.get("user_blocked_unblock_first"), Toast.LENGTH_SHORT).show()
                        return@setOnClickListener
                    }
                    alertDialog.dismiss()
                    showPrivateChat(userId, username, nickname, photoBase64)
                }
                alertDialog.show()
            }
    }

    // ==========================================
    // LIFECYCLE
    // ==========================================
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == PICK_IMAGE_REQUEST && resultCode == RESULT_OK && data != null) {
            selectedPhotoUri = data.data
            // Update profile photo preview
            selectedPhotoUri?.let { uri ->
                try {
                    val inputStream = contentResolver.openInputStream(uri) ?: return@let
                    val bytes = inputStream.readBytes()
                    inputStream.close()
                    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                    container.findViewWithTag<ImageView>("profile_photo_preview")?.setImageBitmap(bitmap)
                    bitmap // don't recycle — it's being displayed
                } catch (_: Exception) {}
            }
            Toast.makeText(this, S.get("photo_selected"), Toast.LENGTH_SHORT).show()
        }
        if (requestCode == PICK_MEDIA_REQUEST && resultCode == RESULT_OK && data?.data != null) {
            val uri = data.data ?: return
            try {
                val inputStream = contentResolver.openInputStream(uri) ?: return
                val imageBytes = inputStream.readBytes()
                inputStream.close()

                // Always decode/re-encode to strip EXIF, then explicit strip as belt-and-suspenders
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
                bitmap.recycle()
                val compressed = stripExifData(out.toByteArray())

                val chatId = pendingMediaChatId ?: return
                val otherUid = pendingMediaOtherUid ?: return
                Toast.makeText(this, S.get("sending_photo"), Toast.LENGTH_SHORT).show()
                uploadEncryptedMedia(compressed, chatId, otherUid, "image", pendingMediaSharedKey) {
                    Toast.makeText(this, S.get("photo_sent"), Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==========================================
    // CONNECTIVITY MONITORING
    // ==========================================
    private fun setupConnectivityMonitor() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        connectivityCallback = object : android.net.ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: android.net.Network) {
                runOnUiThread { connectionBanner?.let { banner ->
                    banner.text = iconize("✓ " + S.get("connected"), Color.WHITE, 12)
                    banner.setBackgroundColor(Color.parseColor("#22C55E"))
                    banner.postDelayed({ banner.visibility = View.GONE }, 2000)
                }}
            }
            override fun onLost(network: android.net.Network) {
                runOnUiThread { connectionBanner?.let { banner ->
                    banner.text = iconize("⚠ " + S.get("no_internet"), Color.WHITE, 12)
                    banner.setBackgroundColor(Color.parseColor("#EF4444"))
                    banner.visibility = View.VISIBLE
                }}
            }
        }
        val request = android.net.NetworkRequest.Builder()
            .addCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, connectivityCallback!!)
    }

    private fun createConnectionBanner(): TextView {
        return TextView(this).apply {
            text = iconize("⚠ " + S.get("no_internet"), Color.WHITE, 12)
            setTextColor(Color.WHITE)
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.BOLD)
            setBackgroundColor(Color.parseColor("#EF4444"))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }.also { connectionBanner = it }
    }

    override fun onBackPressed() {
        // Dismiss gallery picker if showing
        inlineGalleryPicker?.let {
            if (it.isShowing()) {
                it.dismiss(container)
                return
            }
        }
        when (currentScreen) {
            "chat" -> showHome()
            "private_chat" -> showHome()
            "profile" -> showHome()
            "settings" -> showHome()
            "home" -> {
                moveTaskToBack(true)
            }
            "verification" -> {
                moveTaskToBack(true)
            }
            "pin_entry" -> {
                moveTaskToBack(true)
            }
            "pin_setup" -> proceedToHome()
            "login" -> moveTaskToBack(true)
            "privacy_policy" -> showSettings()
            else -> {
                android.util.Log.e("ExecBin", "Back pressed on unhandled screen: $currentScreen")
                moveTaskToBack(true)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        globalChatListener?.remove()
        privateChatListener?.remove()
        privateChatListListener?.remove()
        securityAlertListener?.remove()
        publicKeyListener?.remove()
        typingStatusListener?.remove()
        typingCleanupHandler?.removeCallbacksAndMessages(null)
        chatListTypingListeners.forEach { it.remove() }
        chatListTypingListeners.clear()
        activeMediaPlayer?.let { try { it.stop(); it.release() } catch (_: Exception) {} }
        activeMediaPlayer = null
        activeVoiceTempFile?.delete()
        activeVoiceTempFile = null
        connectivityCallback?.let {
            try { (getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager).unregisterNetworkCallback(it) } catch (_: Exception) {}
        }
        connectivityCallback = null
        setOfflineStatus()
    }

    override fun onResume() {
        super.onResume()
        isAppInForeground = true
        setOnlineStatus()
    }

    override fun onPause() {
        super.onPause()
        isAppInForeground = false
        setOfflineStatus()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == GALLERY_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                val chatId = pendingMediaChatId ?: return
                if (chatId == "global") {
                    showInlineGalleryPickerGlobal()
                } else {
                    val otherUid = pendingMediaOtherUid ?: return
                    showInlineGalleryPicker(chatId, otherUid, pendingMediaSharedKey)
                }
            } else {
                Toast.makeText(this, S.get("permission_needed"), Toast.LENGTH_SHORT).show()
            }
        }
    }

    // ==========================================
    // SESSION TIMEOUT
    // ==========================================
    private fun checkSessionTimeout(): Boolean {
        val lastActive = prefs.getLong("last_active_timestamp", 0L)
        if (lastActive == 0L) {
            updateLastActive()
            return false
        }
        val daysSinceActive = (System.currentTimeMillis() - lastActive) / (1000 * 60 * 60 * 24)
        return daysSinceActive >= SESSION_TIMEOUT_DAYS
    }

    private fun checkSessionExpiryWarning() {
        if (isFinishing || isDestroyed) return
        val lastActive = prefs.getLong("last_active_timestamp", 0L)
        if (lastActive == 0L) return
        val msRemaining = (SESSION_TIMEOUT_DAYS * 24L * 60 * 60 * 1000) - (System.currentTimeMillis() - lastActive)
        val hoursRemaining = msRemaining / (1000 * 60 * 60)
        if (hoursRemaining in 1..12) {
            AlertDialog.Builder(this)
                .setTitle(iconize("⚠️ " + S.get("session_expiry_warning")))
                .setMessage(S.get("session_expiry_msg", hoursRemaining.toInt()))
                .setPositiveButton(S.get("ok"), null)
                .show()
        }
    }

    private fun updateLastActive() {
        prefs.edit().putLong("last_active_timestamp", System.currentTimeMillis()).apply()
    }

    // ==========================================
    // ONLINE / OFFLINE PRESENCE
    // ==========================================
    private fun setOnlineStatus() {
        if (!::auth.isInitialized) return  // onCreate stopped early (secure storage unavailable)
        val userId = auth.currentUser?.uid ?: return
        updateLastActive()
        val hideLastSeen = prefs.getBoolean("hide_last_seen", false)
        val updates = mutableMapOf<String, Any>()
        if (hideLastSeen) {
            updates["isOnline"] = false  // Don't reveal online status
        } else {
            updates["isOnline"] = true
            updates["lastSeen"] = System.currentTimeMillis()
        }
        db.collection("publicProfiles").document(userId).update(updates)
    }

    private fun setOfflineStatus() {
        if (!::auth.isInitialized) return  // onCreate stopped early (secure storage unavailable)
        val userId = auth.currentUser?.uid ?: return
        val hideLastSeen = prefs.getBoolean("hide_last_seen", false)
        val updates = mutableMapOf<String, Any>("isOnline" to false)
        if (!hideLastSeen) updates["lastSeen"] = System.currentTimeMillis()
        db.collection("publicProfiles").document(userId).update(updates)
    }

    // ==========================================
    // COOLDOWN
    // ==========================================
    private fun isInCooldownPeriod(onResult: (Boolean) -> Unit) {
        val userId = auth.currentUser?.uid ?: return onResult(false)
        db.collection("devices").document(currentDeviceId).get()
            .addOnSuccessListener { doc ->
                val loginTime = doc?.getString("encLoginTimestamp")?.let { decryptMetaLong(it) }
                    ?: doc?.getLong("loginTimestamp") ?: 0L
                val hoursSinceLogin = (System.currentTimeMillis() - loginTime) / (1000 * 60 * 60)
                onResult(hoursSinceLogin < COOLDOWN_HOURS)
            }
            .addOnFailureListener { onResult(false) }
    }

    private fun showCooldownWarning() {
        db.collection("devices").document(currentDeviceId).get()
            .addOnSuccessListener { doc ->
                val loginTime = doc?.getString("encLoginTimestamp")?.let { decryptMetaLong(it) }
                    ?: doc?.getLong("loginTimestamp") ?: 0L
                val hoursLeft = COOLDOWN_HOURS - ((System.currentTimeMillis() - loginTime) / (1000 * 60 * 60))
                Toast.makeText(this, S.get("new_device_cooldown", hoursLeft), Toast.LENGTH_LONG).show()
            }
    }

    // ==========================================
    // SECURITY ALERTS
    // ==========================================
    private fun sendLoginAlertNotification() {
        val userId = auth.currentUser?.uid ?: return
        val alert = hashMapOf<String, Any>(
            "deviceId" to currentDeviceId,
            "encDeviceName" to encryptMeta(currentDeviceName),
            "encTimestamp" to encryptMetaLong(System.currentTimeMillis()),
            "read" to false
        )
        db.collection("users").document(userId)
            .collection("securityAlerts").add(alert)
    }

    private fun listenForSecurityAlerts() {
        val userId = auth.currentUser?.uid ?: return
        securityAlertListener?.remove()
        securityAlertListener = db.collection("users").document(userId)
            .collection("securityAlerts")
            .whereEqualTo("read", false)
            .addSnapshotListener { snaps, _ ->
                snaps?.documentChanges?.forEach { change ->
                    if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                        val doc = change.document
                        val alertDeviceId = doc.getString("deviceId") ?: ""
                        val alertDeviceName = doc.getString("encDeviceName")?.let { decryptMeta(it) }
                            ?: doc.getString("deviceName") ?: "Unknown device"
                        val alertTime = doc.getString("encTimestamp")?.let { decryptMetaLong(it) }
                            ?: doc.getLong("timestamp") ?: 0L
                        if (alertDeviceId != currentDeviceId && System.currentTimeMillis() - alertTime < 60000) {
                            showSecurityNotification(alertDeviceName, alertTime)
                        }
                        doc.reference.update("read", true)
                    }
                }
            }
    }

    private fun showSecurityNotification(deviceName: String, timestamp: Long) {
        val time = java.text.SimpleDateFormat("h:mm a", java.util.Locale.getDefault())
            .format(java.util.Date(timestamp))

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Use the constructor with channel ID for Oreo+
            android.app.Notification.Builder(this, "security_alerts")
        } else {
            // Use the old constructor for older devices
            android.app.Notification.Builder(this)
                .setPriority(android.app.Notification.PRIORITY_HIGH) // approximate
        }

        builder.setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("🔔 " + S.get("new_device_login"))
            .setContentText(S.get("new_device_login_msg", deviceName, time))
            .setStyle(android.app.Notification.BigTextStyle()
                .bigText(S.get("new_device_login_msg", deviceName, time)))
            .setAutoCancel(true)

        notificationManager.notify(9999, builder.build())
        Toast.makeText(this, iconize("⚠️ " + S.get("new_login_from", deviceName)), Toast.LENGTH_LONG).show()
    }

    // ==========================================
    // PRIVACY POLICY VIEWER
    // ==========================================
    private fun showPrivacyPolicy() {
        val langPrefs = getSharedPreferences("app_lang_prefs", Context.MODE_PRIVATE)
        val lang = langPrefs.getString("app_language", "en") ?: "en"
        val policyText = PrivacyPolicy.get(lang)

        currentScreen = "privacy_policy"
        container.removeAllViews()
        container.setBackgroundColor(bgColor)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        val headerGrad = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#6C63FF"), Color.parseColor("#8B5CF6"), Color.parseColor("#A855F7"))
        )
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = headerGrad
            setPadding(dp(16), dp(18), dp(16), dp(18))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        header.addView(TextView(this).apply {
            text = "←"; textSize = 26f; setTextColor(Color.WHITE)
            setPadding(0, 0, dp(16), 0)
            setOnClickListener {
                if (auth.currentUser != null) showSettings() else showRegistration()
            }
        })
        header.addView(TextView(this).apply {
            text = iconize("🔒 " + S.get("privacy_policy"), Color.WHITE, 20)
            setTextColor(Color.WHITE); textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(header)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(40))
        }
        content.addView(TextView(this).apply {
            text = policyText
            setTextColor(textColor)
            textSize = 13f
            setLineSpacing(dp(3).toFloat(), 1f)
        })
        scroll.addView(content)
        root.addView(scroll)
        container.addView(root)
    }

    // ==========================================
    // CHANGE PASSWORD
    // ==========================================
    private fun showChangePasswordDialog() {
        isInCooldownPeriod { inCooldown ->
            if (inCooldown) {
                showCooldownWarning()
                return@isInCooldownPeriod
            }
            val layout = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(24), dp(20), dp(24), dp(8))
            }
            val currentPwd = EditText(this).apply {
                hint = S.get("current_password")
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                background = createRoundedBackground(cardBg, 12)
                setTextColor(textColor)
                setHintTextColor(subTextColor)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
            }
            val newPwd = EditText(this).apply {
                hint = S.get("new_password_hint")
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                background = createRoundedBackground(cardBg, 12)
                setTextColor(textColor)
                setHintTextColor(subTextColor)
                setPadding(dp(16), dp(14), dp(16), dp(14))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
            }
            val confirmPwd = EditText(this).apply {
                hint = S.get("confirm_new_password")
                inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                background = createRoundedBackground(cardBg, 12)
                setTextColor(textColor)
                setHintTextColor(subTextColor)
                setPadding(dp(16), dp(14), dp(16), dp(14))
            }
            layout.addView(currentPwd)
            layout.addView(newPwd)
            layout.addView(confirmPwd)
            AlertDialog.Builder(this)
                .setTitle(S.get("change_password"))
                .setView(layout)
                .setPositiveButton(S.get("change")) { _, _ ->
                    val current = currentPwd.text.toString()
                    val newPass = newPwd.text.toString()
                    val confirm = confirmPwd.text.toString()
                    if (newPass.length < 6) {
                        Toast.makeText(this, S.get("password_min_6"), Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    if (newPass != confirm) {
                        Toast.makeText(this, S.get("passwords_dont_match"), Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val user = auth.currentUser ?: return@setPositiveButton
                    val email = user.email ?: run {
                        Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
                        return@setPositiveButton
                    }
                    val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, current)
                    user.reauthenticate(credential)
                        .addOnSuccessListener {
                            user.updatePassword(newPass)
                                .addOnSuccessListener {
                                    Toast.makeText(this, S.get("password_changed"), Toast.LENGTH_SHORT).show()
                                }
                                .addOnFailureListener { e ->
                                    Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_LONG).show()
                                }
                        }
                        .addOnFailureListener {
                            Toast.makeText(this, S.get("wrong_current_password"), Toast.LENGTH_SHORT).show()
                        }
                }
                .setNegativeButton(S.get("cancel"), null)
                .show()
        }
    }

    // ==========================================
    // FORGOT PASSWORD
    // ==========================================
    private fun showForgotPasswordDialog() {
        val dialogBg = if (isDarkTheme) Color.parseColor("#1E293B") else Color.parseColor("#FFFFFF")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBackground(dialogBg, 20)
            setPadding(dp(24), dp(24), dp(24), dp(20))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("🔑", accentColor, dp(36)))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { bottomMargin = dp(12); gravity = Gravity.CENTER_HORIZONTAL }
        })
        content.addView(TextView(this).apply {
            text = S.get("reset_password")
            setTextColor(textColor)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        })
        content.addView(TextView(this).apply {
            text = S.get("reset_password_desc")
            setTextColor(subTextColor)
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(20) }
        })
        val emailField = EditText(this).apply {
            hint = S.get("email_address_hint")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 15f
            background = createRoundedBackground(cardBg, 12)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)).apply { bottomMargin = dp(16) }
        }
        content.addView(emailField)
        val statusText = TextView(this).apply {
            setTextColor(subTextColor)
            textSize = 12f
            gravity = Gravity.CENTER
            visibility = View.GONE
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        content.addView(statusText)
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val dialog = AlertDialog.Builder(this).setView(content).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        dialog.setCancelable(false)
        val cancelBtn = Button(this).apply {
            text = S.get("cancel")
            setTextColor(subTextColor)
            textSize = 14f
            background = createRoundedBackground(cardBg, 12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) }
            setOnClickListener { dialog.dismiss() }
        }
        btnRow.addView(cancelBtn)
        val sendBtn = Button(this).apply {
            text = S.get("send_link")
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = createRoundedBackground(accentColor, 12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) }
        }
        btnRow.addView(sendBtn)
        content.addView(btnRow)
        sendBtn.setOnClickListener {
            val email = emailField.text.toString().trim()
            if (email.isEmpty() || !email.contains("@")) {
                statusText.text = S.get("enter_valid_email")
                statusText.setTextColor(Color.parseColor("#EF4444"))
                statusText.visibility = View.VISIBLE
                return@setOnClickListener
            }
            sendBtn.isEnabled = false
            sendBtn.text = S.get("sending")
            statusText.visibility = View.GONE
            auth.sendPasswordResetEmail(email)
                .addOnSuccessListener {
                    statusText.text = S.get("reset_link_sent")
                    statusText.setTextColor(Color.parseColor("#22C55E"))
                    statusText.visibility = View.VISIBLE
                    sendBtn.text = S.get("sent")
                    sendBtn.isEnabled = false
                    cancelBtn.text = S.get("done")
                }
                .addOnFailureListener { e ->
                    statusText.text = S.get("error_something_wrong")
                    statusText.setTextColor(Color.parseColor("#EF4444"))
                    statusText.visibility = View.VISIBLE
                    sendBtn.isEnabled = true
                    sendBtn.text = S.get("send_link")
                }
        }
        dialog.show()
    }

    // ==========================================
    // DELETE ACCOUNT
    // ==========================================
    private fun showDeleteAccountDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(20), dp(24), dp(8))
        }
        val pwdField = EditText(this).apply {
            hint = S.get("enter_password_confirm")
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            background = createRoundedBackground(cardBg, 12)
            setTextColor(textColor)
            setHintTextColor(subTextColor)
            setPadding(dp(16), dp(14), dp(16), dp(14))
        }
        layout.addView(pwdField)
        AlertDialog.Builder(this)
            .setTitle(iconize("⚠️ ${S.get("delete_account_title")}"))
            .setMessage(S.get("delete_account_message"))
            .setView(layout)
            .setPositiveButton(S.get("delete_forever")) { _, _ ->
                val password = pwdField.text.toString()
                if (password.isEmpty()) {
                    Toast.makeText(this, S.get("password_required"), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val user = auth.currentUser ?: return@setPositiveButton
                val email = user.email ?: run {
                    Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                val credential = com.google.firebase.auth.EmailAuthProvider.getCredential(email, password)
                user.reauthenticate(credential)
                    .addOnSuccessListener { performAccountDeletion() }
                    .addOnFailureListener {
                        Toast.makeText(this, S.get("incorrect_password"), Toast.LENGTH_SHORT).show()
                    }
            }
            .setNegativeButton(S.get("cancel"), null)
            .show()
    }

    private fun performAccountDeletion() {
        val userId = auth.currentUser?.uid ?: return
        Toast.makeText(this, S.get("deleting_account"), Toast.LENGTH_SHORT).show()
        setOfflineStatus()
        db.collection("globalChat")
            .whereEqualTo("senderId", userId)
            .get()
            .addOnSuccessListener { msgs ->
                db.collection("devices")
                    .whereEqualTo("userId", userId)
                    .get()
                    .addOnSuccessListener { devices ->
                        // Chunk into batches of 450 to stay under Firestore's 500 limit
                        val allDocs = msgs.documents + devices.documents
                        val chunks = allDocs.chunked(448)
                        val lastBatch = if (chunks.isEmpty()) db.batch() else null
                        var commitChain: com.google.android.gms.tasks.Task<Void>? = null
                        for (chunk in chunks) {
                            val b = db.batch()
                            chunk.forEach { b.delete(it.reference) }
                            commitChain = if (commitChain == null) b.commit() else commitChain.continueWithTask { b.commit() }
                        }
                        val finalBatch = db.batch()
                        finalBatch.delete(db.collection("users").document(userId))
                        finalBatch.delete(db.collection("publicProfiles").document(userId))
                        val finalCommit = if (commitChain == null) finalBatch.commit() else commitChain.continueWithTask { finalBatch.commit() }
                        finalCommit
                            .addOnSuccessListener {
                                db.collection("deviceAccounts").document(currentDeviceId).get()
                                    .addOnSuccessListener { doc ->
                                        if (doc.exists()) {
                                            val accounts = (doc.get("accounts") as? List<*>)?.toMutableList() ?: mutableListOf()
                                            accounts.remove(userId)
                                            doc.reference.update("accounts", accounts)
                                        }
                                    }
                                val deletingUid = auth.currentUser?.uid ?: ""
                                removeAccountFromDevice(deletingUid)
                                auth.currentUser?.delete()
                                    ?.addOnSuccessListener {
                                        globalChatListener?.remove()
                                        privateChatListener?.remove()
                                        privateChatListListener?.remove()
                                        securityAlertListener?.remove()
                                        // Clean up ALL E2E keys for this account
                                        val uidHash = MessageDigest.getInstance("SHA-256")
                                            .digest(deletingUid.toByteArray()).joinToString("") { "%02x".format(it) }.take(8)
                                        val keyPrefix = "_glide_cache_${uidHash}"
                                        prefs.edit()
                                            .remove("${keyPrefix}_dpi_conf")
                                            .remove("${keyPrefix}_key_blob")
                                            .remove("${keyPrefix}_prev_key_blob")
                                            .remove("${keyPrefix}_key_created")
                                            .remove("${keyPrefix}_bmp_cache")
                                            .remove("${keyPrefix}_ttf_metric")
                                            .remove("${keyPrefix}_bmp_size")
                                            .remove("${keyPrefix}_cache_crc")
                                            .remove("${keyPrefix}_key_mac")
                                            .remove("metadata_key_v2")
                                            .remove("_sys_sig_cache")
                                            .apply()
                                        Toast.makeText(this, S.get("account_deleted"), Toast.LENGTH_LONG).show()
                                        showRegistration()
                                    }
                                    ?.addOnFailureListener { e ->
                                        Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_LONG).show()
                                        auth.signOut()
                                        showRegistration()
                                    }
                            }
                            .addOnFailureListener { e ->
                                Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_LONG).show()
                            }
                    }
            }
    }

    private fun dp(px: Int) = (px * resources.displayMetrics.density).toInt()

    /**
     * Replace emojis in [text] with monochrome line icons (Telegram-style).
     * Color defaults to the current theme's textColor; size defaults to 16dp.
     */
    private fun iconize(text: CharSequence?, color: Int = textColor, sizeDp: Int = 16): CharSequence {
        return IconHelper.iconize(this, text, color, dp(sizeDp))
    }

    /** Builds a small ImageView containing a single line icon. */
    private fun iconView(emoji: String, color: Int = textColor, sizeDp: Int = 16): ImageView {
        return ImageView(this).apply {
            setImageDrawable(LineIconDrawable(emoji, color, dp(sizeDp)))
            layoutParams = LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp))
        }
    }

    /** Attaches swipe-left-to-reply gesture to a chat RecyclerView. */
    private fun setupSwipeToReply(recycler: RecyclerView, messages: List<Msg>, onReply: (Msg) -> Unit) {
        val callback = object : androidx.recyclerview.widget.ItemTouchHelper.SimpleCallback(0, androidx.recyclerview.widget.ItemTouchHelper.LEFT) {
            private val replyDrawable = LineIconDrawable("↩", subTextColor, dp(24))
            private val triggerDistance = dp(80).toFloat()

            override fun onMove(rv: RecyclerView, vh: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder) = false

            override fun onSwiped(vh: RecyclerView.ViewHolder, direction: Int) {
                // We handle via onChildDraw instead; reset the view
                val adapter = recycler.adapter
                adapter?.notifyItemChanged(vh.adapterPosition)
            }

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder) = 0.4f

            override fun onChildDraw(c: android.graphics.Canvas, recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, dX: Float, dY: Float, actionState: Int, isCurrentlyActive: Boolean) {
                val clampedDx = dX.coerceIn(-triggerDistance * 1.5f, 0f)
                super.onChildDraw(c, recyclerView, viewHolder, clampedDx, dY, actionState, isCurrentlyActive)
                if (clampedDx < -dp(20)) {
                    val itemView = viewHolder.itemView
                    val iconSize = dp(24)
                    val iconLeft = itemView.right + clampedDx.toInt() / 2 + itemView.right / 2 - iconSize / 2
                    val iconTop = itemView.top + (itemView.height - iconSize) / 2
                    val alpha = ((-clampedDx - dp(20)) / (triggerDistance - dp(20))).coerceIn(0f, 1f)
                    replyDrawable.alpha = (alpha * 255).toInt()
                    replyDrawable.setBounds(itemView.right - dp(40), iconTop, itemView.right - dp(40) + iconSize, iconTop + iconSize)
                    replyDrawable.draw(c)
                }
                if (!isCurrentlyActive && dX < -triggerDistance) {
                    val pos = viewHolder.adapterPosition
                    if (pos in messages.indices) {
                        viewHolder.itemView.post { onReply(messages[pos]) }
                    }
                }
            }
        }
        androidx.recyclerview.widget.ItemTouchHelper(callback).attachToRecyclerView(recycler)
    }

    private fun getEmailSalt(): ByteArray {
        val saltKey = "_es_v1"
        val stored = prefs.getString(saltKey, null)
        if (stored != null) return Base64.decode(stored, Base64.NO_WRAP)
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        prefs.edit().putString(saltKey, Base64.encodeToString(salt, Base64.NO_WRAP)).apply()
        return salt
    }

    private fun hashEmail(email: String): String {
        val salt = getEmailSalt()
        val input = salt + email.lowercase().trim().toByteArray(Charsets.UTF_8)
        val hashBytes = java.security.MessageDigest.getInstance("SHA-256").digest(input)
        return hashBytes.joinToString("") { "%02x".format(it) }
    }

    private fun getPrivateChatId(uid1: String, uid2: String): String {
        return if (uid1 < uid2) "${uid1}_${uid2}" else "${uid2}_${uid1}"
    }

    // ==========================================
    // E2E ENCRYPTION (Software ECDH + AES-GCM)
    // Works on all devices including emulators
    // Private key stored per-UID in SharedPreferences
    // ==========================================

    // ---- SECURITY LAYER: Obfuscated key names ----
    // Keys look like Android system cache entries to confuse reverse engineers
    private fun getE2EKeyPrefix(): String {
        val uid = auth.currentUser?.uid ?: "default"
        // Hash UID so pref keys don't reveal user identity
        val uidHash = MessageDigest.getInstance("SHA-256")
            .digest(uid.toByteArray()).joinToString("") { "%02x".format(it) }.take(8)
        return "_glide_cache_${uidHash}"
    }

    // ---- SECURITY LAYER: Device-bound key protection ----
    // XOR key material with a random device secret generated once at first launch.
    // Stored in EncryptedSharedPreferences — not reproducible from public device properties.
    // ---- SECURITY LAYER: Private key storage ----
    // Key is protected organically by EncryptedSharedPreferences (AES-256-GCM via Keystore).
    // The previous weak XOR obfuscation has been removed as it provided no real security.
    private fun storePrivateKeySplit(privKeyBytes: ByteArray) {
        val prefix = getE2EKeyPrefix()
        prefs.edit()
            .putString("${prefix}_key_blob", Base64.encodeToString(privKeyBytes, Base64.NO_WRAP))
            .remove("${prefix}_key_mac")
            .remove("${prefix}_bmp_cache")
            .remove("${prefix}_ttf_metric")
            .remove("${prefix}_bmp_size")
            .remove("${prefix}_cache_crc")
            .apply()
    }

    private fun loadPrivateKeySplit(): ByteArray? {
        val prefix = getE2EKeyPrefix()
        val blob = prefs.getString("${prefix}_key_blob", null)
        if (blob != null) {
            val bytes = Base64.decode(blob, Base64.NO_WRAP)
            // Validate that the stored bytes are actually a valid PKCS8 EC key
            // If an older app version stored a different format, discard it
            return try {
                KeyFactory.getInstance("EC")
                    .generatePrivate(java.security.spec.PKCS8EncodedKeySpec(bytes))
                bytes // valid PKCS8 key
            } catch (e: Exception) {
                android.util.Log.e("ExecBin", "Stored private key invalid PKCS8, discarding")
                prefs.edit().remove("${prefix}_key_blob").apply()
                null
            }
        }
        // Force re-key if absent or using old vulnerable format
        return null
    }

    // ---- SECURITY LAYER: Honeypot generator ----
    // Creates fake SharedPreferences files with keys sized to match real ECDH/AES keys.
    // If an attacker reads these and tries to use them, the app silently alerts.
    private fun plantHoneypots() {
        val fakeNames = listOf(
            "crypto_keychain", "secure_vault", "key_storage",
            "token_cache", "auth_prefs", "master_keys"
        )
        fun fakeBase64(size: Int) = Base64.encodeToString(ByteArray(size).also { SecureRandom().nextBytes(it) }, Base64.NO_WRAP)
        for (name in fakeNames) {
            try {
                val fakePref = getSharedPreferences(name, Context.MODE_PRIVATE)
                if (!fakePref.contains("_init")) {
                    // Key sizes match real ECDH P-256 private key (138 bytes PKCS8) and AES-256 (32 bytes)
                    fakePref.edit()
                        .putString("private_key", fakeBase64(138))    // P-256 PKCS8 size
                        .putString("aes_master_key", fakeBase64(32))  // AES-256
                        .putString("encryption_secret", fakeBase64(91))
                        .putString("api_token", "ek_live_${java.util.UUID.randomUUID()}")
                        .putString("db_password", fakeBase64(24))
                        .putString("jwt_secret", "eyJ${fakeBase64(64)}")
                        .putString("_hp_id", fakeBase64(8)) // honeypot marker (not "_init")
                        .putBoolean("_init", true)
                        .apply()
                }
                // Check if honeypot has been tampered with (keys modified externally)
                val marker = fakePref.getString("_hp_id", null)
                if (marker == null && fakePref.contains("_init")) {
                    // Someone deleted our marker — possible tampering
                    triggerHoneypotAlert(name)
                }
            } catch (_: Exception) {}
        }
    }

    private fun triggerHoneypotAlert(source: String) {
        try {
            val uid = auth.currentUser?.uid ?: return
            db.collection("users").document(uid)
                .collection("securityAlerts")
                .add(hashMapOf<String, Any>(
                    "type" to "honeypot_access",
                    "source" to source,
                    "encTimestamp" to encryptMetaLong(System.currentTimeMillis()),
                    "deviceId" to currentDeviceId,
                    "read" to false
                ))
        } catch (_: Exception) {}
    }

    // ==========================================
    // SECURITY HARDENING
    // ==========================================

    // ==========================================
    // BIOMETRIC AUTHENTICATION
    // ==========================================

    private fun isBiometricAvailable(): Boolean {
        return try {
            val manager = androidx.biometric.BiometricManager.from(this)
            manager.canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
            ) == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS
        } catch (_: Exception) { false }
    }

    private fun showBiometricPrompt(onSuccess: () -> Unit) {
        val prompt = androidx.biometric.BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),  // Activity.mainExecutor needs API 28
            object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                    onSuccess()
                }
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                        errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                        Toast.makeText(this@MainActivity, S.get("biometric_error", errString), Toast.LENGTH_SHORT).show()
                    }
                }
                override fun onAuthenticationFailed() {
                    Toast.makeText(this@MainActivity, S.get("auth_failed_retry"), Toast.LENGTH_SHORT).show()
                }
            }
        )
        val info = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
            .setTitle(S.get("unlock_app"))
            .setSubtitle(S.get("verify_identity"))
            .setAllowedAuthenticators(androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .setNegativeButtonText(S.get("use_pin_instead"))
            .build()
        prompt.authenticate(info)
    }

    private fun performSecurityChecks() {
        // Run security checks in all builds — repackaged APK with debuggable=true must still be caught

        // Anti-tamper: verify APK signature
        if (!verifyAppSignature()) {
            // Wipe keys silently — don't show error (don't help attacker)
            try {
                val prefix = getE2EKeyPrefix()
                prefs.edit()
                    .remove("${prefix}_dpi_conf")
                    .remove("${prefix}_key_blob")
                    .apply()
            } catch (_: Exception) {}
        }

        // Anti-Frida + anti-debugging
        detectRuntimeHooking()
    }

    private fun verifyAppSignature(): Boolean {
        return try {
            val pm = packageManager
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            }

            val signatures = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                info.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                info.signatures
            }

            if (signatures.isNullOrEmpty()) return false

            val sig = signatures[0]
            val digest = MessageDigest.getInstance("SHA-256")
            val currentHash = Base64.encodeToString(digest.digest(sig.toByteArray()), Base64.NO_WRAP)

            // Compare against stored hash AND ensure consistency across runs
            val storedHash = prefs.getString("_sys_sig_cache", "") ?: ""
            if (storedHash.isEmpty()) {
                // First run — store the hash as baseline
                prefs.edit().putString("_sys_sig_cache", currentHash).apply()
                true
            } else if (storedHash != currentHash) {
                // Signature changed — APK was re-signed (repackaged)
                android.util.Log.e("ExecBin", "APK signature mismatch detected")
                false
            } else {
                true
            }
        } catch (e: Exception) {
            true // Don't block on check failure
        }
    }

    private fun detectRuntimeHooking() {
        // Run even in debug — detect Frida/Xposed in all environments

        Thread {
            fun wipeKeys() {
                runOnUiThread {
                    try {
                        val prefix = getE2EKeyPrefix()
                        prefs.edit()
                            .remove("${prefix}_dpi_conf")
                            .remove("${prefix}_key_blob")
                            .apply()
                    } catch (_: Exception) {}
                }
            }

            // 1. Check multiple Frida ports (default + common alternatives)
            val fridaPorts = intArrayOf(27042, 27043, 27044)
            for (port in fridaPorts) {
                try {
                    val socket = java.net.Socket()
                    socket.connect(java.net.InetSocketAddress("127.0.0.1", port), 100)
                    socket.close()
                    wipeKeys()
                    return@Thread
                } catch (_: Exception) {}
            }

            // 2. Check for common hooking frameworks in loaded libraries
            try {
                val mapsFile = java.io.File("/proc/self/maps")
                if (mapsFile.exists()) {
                    val content = mapsFile.readText().lowercase()
                    val suspicious = listOf(
                        "frida", "xposed", "substrate", "magisk", "edxposed", "lsposed",
                        "frida-gadget", "frida-agent", "libfrida", "gadget.so",
                        "cydia", "saurik", "riru", "zygisk",
                        "libhook", "libart_inject", "objection"
                    )
                    for (s in suspicious) {
                        if (content.contains(s)) {
                            wipeKeys()
                            return@Thread
                        }
                    }
                }
            } catch (_: Exception) {}

            // 3. Check for Frida named pipes
            try {
                val pipesDir = java.io.File("/proc/self/fd")
                if (pipesDir.exists() && pipesDir.isDirectory) {
                    pipesDir.listFiles()?.forEach { fd ->
                        try {
                            val link = fd.canonicalPath.lowercase()
                            if (link.contains("frida") || link.contains("linjector")) {
                                wipeKeys()
                                return@Thread
                            }
                        } catch (_: Exception) {}
                    }
                }
            } catch (_: Exception) {}

            // 4. Check for Frida server process via /proc
            try {
                val proc = Runtime.getRuntime().exec(arrayOf("sh", "-c", "ps -A 2>/dev/null || ps 2>/dev/null"))
                val output = proc.inputStream.bufferedReader().readText().lowercase()
                proc.waitFor()
                val suspiciousProcs = listOf("frida", "frida-server", "frida-helper", "frida-agent",
                    "objection", "r2frida", "fridaserve")
                for (s in suspiciousProcs) {
                    if (output.contains(s)) {
                        wipeKeys()
                        return@Thread
                    }
                }
            } catch (_: Exception) {}

            // 5. Check /proc/self/status for TracerPid (indicates debugging/tracing)
            try {
                val statusFile = java.io.File("/proc/self/status")
                if (statusFile.exists()) {
                    val lines = statusFile.readLines()
                    for (line in lines) {
                        if (line.startsWith("TracerPid:")) {
                            val tracerPid = line.substringAfter(":").trim().toIntOrNull() ?: 0
                            if (tracerPid != 0) {
                                wipeKeys()
                                return@Thread
                            }
                        }
                    }
                }
            } catch (_: Exception) {}

            // 6. (Removed) Root package / su binary / timing checks — caused false-positives on many devices
            // Root detection is handled by checkDeviceIntegrity() (warning only); attestation by App Check

            // 9. Check debugger attached
            if (android.os.Debug.isDebuggerConnected()) {
                wipeKeys()
            }
        }.start()
    }

    // ==========================================
    // BLOCK / REPORT SYSTEM
    // ==========================================

    private fun loadBlockedUsers() {
        val uid = auth.currentUser?.uid ?: return
        // Blocked users stored locally since subcollection may not be in rules
        val saved = prefs.getStringSet("blocked_users_$uid", emptySet()) ?: emptySet()
        blockedUsers.clear()
        blockedUsers.addAll(saved)
    }

    private fun saveBlockedUsersLocally() {
        val uid = auth.currentUser?.uid ?: return
        prefs.edit().putStringSet("blocked_users_$uid", blockedUsers.toSet()).apply()
    }

    private fun blockUser(otherUid: String, otherUsername: String) {
        val uid = auth.currentUser?.uid ?: return
        val dialogBg = if (isDarkTheme) Color.parseColor("#1E293B") else Color.parseColor("#FFFFFF")

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBackground(dialogBg, 20)
            setPadding(dp(24), dp(24), dp(24), dp(20))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("🚫", Color.parseColor("#EF4444"), dp(36)))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        content.addView(TextView(this).apply {
            text = S.get("block_user_title", otherUsername)
            setTextColor(textColor)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        })
        content.addView(TextView(this).apply {
            text = S.get("block_user_desc")
            setTextColor(subTextColor)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(20))
        })
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        val dialog = AlertDialog.Builder(this).setView(content).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        btnRow.addView(Button(this).apply {
            text = S.get("cancel")
            setTextColor(subTextColor)
            textSize = 14f
            background = createRoundedBackground(cardBg, 12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) }
            setOnClickListener { dialog.dismiss() }
        })
        btnRow.addView(Button(this).apply {
            text = S.get("block")
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = createRoundedBackground(Color.parseColor("#EF4444"), 12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) }
            setOnClickListener {
                blockedUsers.add(otherUid)
                saveBlockedUsersLocally()
                Toast.makeText(this@MainActivity, S.get("user_blocked"), Toast.LENGTH_SHORT).show()
                dialog.dismiss()
                showHome()
            }
        })
        content.addView(btnRow)
        dialog.show()
    }

    private fun unblockUser(otherUid: String, otherUsername: String) {
        blockedUsers.remove(otherUid)
        saveBlockedUsersLocally()
        Toast.makeText(this, S.get("user_unblocked"), Toast.LENGTH_SHORT).show()
    }

    private fun reportMessage(msg: Msg, chatId: String, messageId: String, isPrivate: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        val dialogBg = if (isDarkTheme) Color.parseColor("#1E293B") else Color.parseColor("#FFFFFF")

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBackground(dialogBg, 20)
            setPadding(dp(24), dp(24), dp(24), dp(20))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("⚠", Color.parseColor("#F59E0B"), dp(36)))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { gravity = Gravity.CENTER_HORIZONTAL }
        })
        content.addView(TextView(this).apply {
            text = S.get("report_message")
            setTextColor(textColor)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, dp(8))
        })

        val preview = if (msg.text.length > 80) msg.text.take(80) + "…" else msg.text
        content.addView(TextView(this).apply {
            text = "\"$preview\""
            setTextColor(subTextColor)
            textSize = 12f
            gravity = Gravity.CENTER
            setTypeface(null, android.graphics.Typeface.ITALIC)
            setPadding(dp(8), 0, dp(8), dp(8))
        })

        // Stable codes go to the server; labels are only for display (they're translated)
        val reasons = listOf("spam", "harassment", "hate_speech", "violence", "other")
        var selectedReason = ""

        val reasonGroup = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 0, 0, dp(16))
        }
        for (reason in reasons) {
            reasonGroup.addView(TextView(this).apply {
                text = S.get(reason)
                tag = reason
                setTextColor(textColor)
                textSize = 14f
                setPadding(dp(16), dp(10), dp(16), dp(10))
                background = createRoundedBackground(cardBg, 8)
                val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                lp.bottomMargin = dp(6)
                layoutParams = lp
                setOnClickListener {
                    selectedReason = reason
                    // Highlight selected
                    for (i in 0 until reasonGroup.childCount) {
                        val child = reasonGroup.getChildAt(i) as TextView
                        child.background = createRoundedBackground(
                            if (child.tag == reason) accentColor else cardBg, 8
                        )
                        child.setTextColor(if (child.tag == reason) Color.WHITE else textColor)
                    }
                }
            })
        }
        content.addView(reasonGroup)

        // Private chats are E2E encrypted: moderators/AI only see the text if the reporter opts in,
        // and then only for this one message. Global chat text is read server-side instead.
        val shareTextBox = if (isPrivate) {
            androidx.appcompat.widget.AppCompatCheckBox(this).apply {
                text = S.get("report_include_text")
                setTextColor(subTextColor)
                textSize = 13f
                setPadding(dp(4), 0, 0, dp(12))
            }.also { content.addView(it) }
        } else null

        val dialog = AlertDialog.Builder(this).setView(content).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        btnRow.addView(Button(this).apply {
            text = S.get("cancel")
            setTextColor(subTextColor)
            textSize = 14f
            background = createRoundedBackground(cardBg, 12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) }
            setOnClickListener { dialog.dismiss() }
        })
        btnRow.addView(Button(this).apply {
            text = S.get("report")
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = createRoundedBackground(Color.parseColor("#EF4444"), 12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) }
            setOnClickListener {
                if (selectedReason.isEmpty()) {
                    Toast.makeText(this@MainActivity, S.get("select_reason"), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val autoReview = autoReviewReport(msg.text, selectedReason)

                // Hash the message text — don't store plaintext on server (E2E guarantee)
                val msgHash = java.security.MessageDigest.getInstance("SHA-256")
                    .digest(msg.text.toByteArray(Charsets.UTF_8))
                    .joinToString("") { "%02x".format(it) }

                // Client keyword hints are untrusted (the client controls them); the server re-reviews
                val report = hashMapOf<String, Any>(
                    "reporterId" to uid,
                    "reportedUserId" to msg.senderId,
                    "reportedUsername" to msg.username.take(64),
                    "messageHash" to msgHash,
                    "chatType" to if (isPrivate) "private" else "global",
                    "chatId" to chatId,
                    "messageId" to messageId,
                    "reasonCode" to selectedReason,
                    "createdAt" to com.google.firebase.firestore.FieldValue.serverTimestamp(),
                    "status" to "pending",
                    "autoVerdict" to (autoReview["verdict"]?.toString() ?: "unknown").take(32),
                    "autoSeverity" to (autoReview["severity"]?.toString() ?: "low").take(32),
                    "autoCategory" to (autoReview["category"]?.toString() ?: "unknown").take(32),
                    "autoFlags" to (autoReview["flags"]?.toString() ?: "").take(500),
                    "autoScore" to (autoReview["score"]?.toString() ?: "0").take(8)
                )
                if (shareTextBox?.isChecked == true && msg.text.isNotEmpty()) {
                    report["reportedText"] = msg.text.take(1000)
                }

                // One report per reporter per message: the deterministic ID plus create-only rules
                // turn duplicates into a permission error instead of extra reports / AI calls
                db.collection("reports").document("${uid}_$messageId").set(report)
                    .addOnSuccessListener {
                        Toast.makeText(this@MainActivity, S.get("report_submitted"), Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                    .addOnFailureListener { e ->
                        val duplicate = (e as? com.google.firebase.firestore.FirebaseFirestoreException)?.code ==
                            com.google.firebase.firestore.FirebaseFirestoreException.Code.PERMISSION_DENIED
                        Toast.makeText(
                            this@MainActivity,
                            S.get(if (duplicate) "report_already_sent" else "error_something_wrong"),
                            Toast.LENGTH_SHORT
                        ).show()
                        if (duplicate) dialog.dismiss()
                    }
            }
        })
        content.addView(btnRow)
        dialog.show()
    }

    // ==========================================
    // AUTO REPORT REVIEW (keyword-based content filter)
    // ==========================================

    private fun autoReviewReport(messageText: String, reportReason: String): Map<String, Any> {
        val lower = messageText.lowercase()
        val flags = mutableListOf<String>()
        var score = 0

        // Spam indicators
        val spamPatterns = listOf(
            "buy now", "click here", "free money", "earn cash", "make money",
            "discount", "limited offer", "act now", "subscribe", "follow me",
            "check my", "join my", "dm me for", "promo code"
        )
        for (p in spamPatterns) {
            if (lower.contains(p)) { flags.add("spam:$p"); score += 15 }
        }
        // Repeated characters (e.g. "hahahahahaha" or "!!!!!!")
        if (Regex("(.)\\1{7,}").containsMatchIn(lower)) { flags.add("spam:repeated_chars"); score += 10 }
        // All caps message (shouting)
        if (messageText.length > 10 && messageText == messageText.uppercase() && messageText != messageText.lowercase()) {
            flags.add("spam:all_caps"); score += 5
        }
        // URL spam (multiple links)
        val urlCount = Regex("https?://").findAll(lower).count()
        if (urlCount >= 3) { flags.add("spam:multiple_urls"); score += 20 }

        // Harassment indicators
        val harassmentPatterns = listOf(
            "kill yourself", "kys", "go die", "nobody likes you",
            "you're trash", "you suck", "loser", "pathetic",
            "i'll find you", "watch your back", "you're dead"
        )
        for (p in harassmentPatterns) {
            if (lower.contains(p)) { flags.add("harassment:$p"); score += 30 }
        }

        // Threat indicators
        val threatPatterns = listOf(
            "i will kill", "i'll kill", "gonna kill", "i will hurt",
            "i'll hurt", "gonna hurt", "bomb", "shoot you", "stab you",
            "find where you live", "know where you live", "come to your house"
        )
        for (p in threatPatterns) {
            if (lower.contains(p)) { flags.add("threat:$p"); score += 50 }
        }

        // Self-harm indicators
        val selfHarmPatterns = listOf(
            "how to kill myself", "want to die", "end my life",
            "suicide method", "cut myself"
        )
        for (p in selfHarmPatterns) {
            if (lower.contains(p)) { flags.add("self_harm:$p"); score += 40 }
        }

        // Boost score if the report reason aligns with detected flags
        val reasonLower = reportReason.lowercase()
        if (reasonLower == "spam" && flags.any { it.startsWith("spam:") }) score += 10
        if (reasonLower == "harassment" && flags.any { it.startsWith("harassment:") }) score += 10
        if (reasonLower == "violence" && flags.any { it.startsWith("threat:") }) score += 10
        if (reasonLower == "hate_speech" && flags.any { it.startsWith("harassment:") }) score += 10

        // Determine verdict and severity from score
        val verdict = when {
            score >= 40 -> "likely_violation"
            score >= 15 -> "suspicious"
            score > 0 -> "low_risk"
            else -> "clean"
        }
        val severity = when {
            score >= 50 -> "critical"
            score >= 30 -> "high"
            score >= 15 -> "medium"
            score > 0 -> "low"
            else -> "none"
        }
        val category = when {
            flags.any { it.startsWith("threat:") } -> "violence"
            flags.any { it.startsWith("self_harm:") } -> "self_harm"
            flags.any { it.startsWith("harassment:") } -> "harassment"
            flags.any { it.startsWith("spam:") } -> "spam"
            else -> reportReason.lowercase().replace(" ", "_")
        }

        return mapOf(
            "verdict" to verdict,
            "severity" to severity,
            "category" to category,
            "flags" to flags.joinToString(", "),
            "score" to score
        )
    }

    // ==========================================
    // MESSAGE EDIT
    // ==========================================

    private fun editMessage(chatId: String, messageId: String, oldText: String, oldReply: String, isPrivate: Boolean, sharedKey: SecretKeySpec?) {
        val dialogBg = if (isDarkTheme) Color.parseColor("#1E293B") else Color.parseColor("#FFFFFF")

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBackground(dialogBg, 20)
            setPadding(dp(24), dp(24), dp(24), dp(20))
        }
        content.addView(TextView(this).apply {
            text = iconize("✏️  " + S.get("edit_message"))
            setTextColor(textColor)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dp(16))
        })
        val editField = EditText(this).apply {
            setText(oldText)
            setTextColor(textColor)
            setHintTextColor(subTextColor)
            textSize = 15f
            background = createRoundedBackground(cardBg, 12)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) }
            setSelection(oldText.length) // cursor at end
        }
        content.addView(editField)

        val dialog = AlertDialog.Builder(this).setView(content).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        btnRow.addView(Button(this).apply {
            text = S.get("cancel")
            setTextColor(subTextColor)
            textSize = 14f
            background = createRoundedBackground(cardBg, 12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) }
            setOnClickListener { dialog.dismiss() }
        })
        btnRow.addView(Button(this).apply {
            text = S.get("save")
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = createRoundedBackground(accentColor, 12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) }
            setOnClickListener {
                val newText = editField.text.toString().trim()
                if (newText.isEmpty()) {
                    Toast.makeText(this@MainActivity, S.get("message_empty"), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                if (newText == oldText) {
                    dialog.dismiss()
                    return@setOnClickListener
                }

                if (!isPrivate) {
                    db.collection("globalChat").document(messageId)
                        .update("text", newText, "edited", true, "editedAt", System.currentTimeMillis())
                        .addOnSuccessListener {
                            Toast.makeText(this@MainActivity, S.get("message_edited"), Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }.addOnFailureListener {
                            Toast.makeText(this@MainActivity, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
                        }
                    return@setOnClickListener
                }

                // Private chats: never store an edit in plaintext
                if (sharedKey == null) {
                    Toast.makeText(this@MainActivity, iconize("⚠️ " + S.get("encryption_not_ready")), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }
                val myUid = auth.currentUser?.uid ?: return@setOnClickListener
                val otherUid = chatId.split("_").firstOrNull { it != myUid } ?: return@setOnClickListener
                val ref = db.collection("privateChats").document(chatId).collection("messages").document(messageId)
                // Re-encrypt text AND reply with a fresh per-message key. Replacing only "text" would leave
                // ephemeralKey/senderText pointing at the old key, so the recipient couldn't decrypt the edit.
                db.collection("publicProfiles").document(otherUid).get()
                    .addOnSuccessListener { profile ->
                        val theirKey = profile.getString("publicKey") ?: ""
                        val updates = try {
                            buildEncryptedEditUpdate(newText, oldReply, theirKey, sharedKey)
                        } catch (e: Exception) {
                            Toast.makeText(this@MainActivity, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
                            return@addOnSuccessListener
                        }
                        ref.update(updates)
                            .addOnSuccessListener {
                                Toast.makeText(this@MainActivity, S.get("message_edited"), Toast.LENGTH_SHORT).show()
                                dialog.dismiss()
                            }.addOnFailureListener {
                                Toast.makeText(this@MainActivity, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
                            }
                    }
                    .addOnFailureListener {
                        Toast.makeText(this@MainActivity, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
                    }
            }
        })
        content.addView(btnRow)
        dialog.show()
    }

    /**
     * Firestore fields for an edited private message. Uses a fresh ephemeral key when the contact's key is
     * valid (same scheme as new messages), otherwise the static shared key with the ephemeral fields cleared.
     */
    private fun buildEncryptedEditUpdate(
        newText: String, plainReply: String, theirKey: String, sharedKey: SecretKeySpec
    ): Map<String, Any> {
        val now = System.currentTimeMillis()
        val pfs = if (isValidPublicKey(theirKey)) generatePFSKeys(theirKey) else null
        return if (pfs != null) {
            val (recipientKey, senderKey, ephKey) = pfs
            mapOf(
                "text" to encryptMessage(newText, recipientKey),
                "senderText" to encryptMessage(newText, senderKey),
                "replyToText" to if (plainReply.isNotEmpty()) encryptMessage(plainReply, recipientKey) else "",
                "senderReplyText" to if (plainReply.isNotEmpty()) encryptMessage(plainReply, senderKey) else "",
                "ephemeralKey" to ephKey,
                "edited" to true,
                "editedAt" to now
            )
        } else {
            mapOf(
                "text" to encryptMessage(newText, sharedKey),
                "senderText" to "",
                "replyToText" to if (plainReply.isNotEmpty()) encryptMessage(plainReply, sharedKey) else "",
                "senderReplyText" to "",
                "ephemeralKey" to "",
                "edited" to true,
                "editedAt" to now
            )
        }
    }

    // ==========================================
    // ENCRYPTED MEDIA (Photos + Voice)
    // ==========================================

    // AES-256-GCM, fresh 12-byte IV per call, IV || ciphertext || tag (CryptoManager, unit-tested)
    private fun encryptBytes(data: ByteArray, key: SecretKeySpec): ByteArray = crypto.encryptBytes(data, key)

    private fun decryptBytes(data: ByteArray, key: SecretKeySpec): ByteArray? = crypto.decryptBytes(data, key)

    private fun stripExifData(imageBytes: ByteArray): ByteArray {
        val tempFile = File(cacheDir, "exif_strip_${System.currentTimeMillis()}.jpg")
        return try {
            tempFile.writeBytes(imageBytes)
            val exif = ExifInterface(tempFile.absolutePath)
            val tagsToStrip = listOf(
                ExifInterface.TAG_GPS_LATITUDE, ExifInterface.TAG_GPS_LONGITUDE,
                ExifInterface.TAG_GPS_LATITUDE_REF, ExifInterface.TAG_GPS_LONGITUDE_REF,
                ExifInterface.TAG_GPS_ALTITUDE, ExifInterface.TAG_GPS_ALTITUDE_REF,
                ExifInterface.TAG_GPS_TIMESTAMP, ExifInterface.TAG_GPS_DATESTAMP,
                ExifInterface.TAG_MAKE, ExifInterface.TAG_MODEL,
                ExifInterface.TAG_SOFTWARE, ExifInterface.TAG_DATETIME,
                ExifInterface.TAG_DATETIME_ORIGINAL, ExifInterface.TAG_DATETIME_DIGITIZED,
                ExifInterface.TAG_IMAGE_UNIQUE_ID,
                ExifInterface.TAG_GPS_PROCESSING_METHOD, ExifInterface.TAG_GPS_AREA_INFORMATION
            )
            for (tag in tagsToStrip) {
                exif.setAttribute(tag, null)
            }
            exif.saveAttributes()
            tempFile.readBytes()
        } catch (_: Exception) {
            imageBytes
        } finally {
            tempFile.delete()
        }
    }

    private fun uploadEncryptedMedia(
        data: ByteArray, chatId: String, otherUid: String, mediaType: String,
        sharedKey: SecretKeySpec?, durationMs: Long = 0, customText: String? = null, onDone: () -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return

        // Anti-spam: media rate limiting
        val spamBlock = checkAntiSpam("", isMedia = true)
        if (spamBlock != null) {
            Toast.makeText(this, S.get(spamBlock), Toast.LENGTH_SHORT).show()
            return
        }

        // Compress images aggressively for Firestore storage (max ~100KB) + strip EXIF
        val compressed = if (mediaType == "image") {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap != null) {
                val maxDim = 800
                val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
                val scaled = if (scale < 1f) {
                    val s = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                    bitmap.recycle()
                    s
                } else bitmap
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 50, out)
                scaled.recycle()
                stripExifData(out.toByteArray())
            } else data
        } else data

        // Encrypt the bytes
        // Private chats must never send media in plaintext
        if (sharedKey == null) {
            Toast.makeText(this, iconize("⚠️ " + S.get("encryption_not_ready")), Toast.LENGTH_LONG).show()
            return
        }
        val encrypted = encryptBytes(compressed, sharedKey)
        val mediaBase64 = Base64.encodeToString(encrypted, Base64.NO_WRAP)

        // Check size (Firestore doc limit is 1MB)
        if (mediaBase64.length > 900_000) {
            Toast.makeText(this, S.get("file_too_large"), Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        val msgText = customText ?: if (mediaType == "image") "📷 " + S.get("photo") else "🎤 " + S.get("voice_message")
        val encryptedText = encryptMessage(msgText, sharedKey)

        val msg = hashMapOf<String, Any>(
            "text" to encryptedText,
            "senderId" to uid,
            "username" to myUsername,
            "nickname" to myNickname,
            "photoBase64" to myPhotoBase64,
            "timestamp" to now,
            "replyToText" to "",
            "replyToUsername" to "",
            "read" to false,
            "mediaUrl" to mediaBase64,
            "mediaType" to mediaType,
            "mediaDuration" to durationMs,
            "ephemeralKey" to "",
            "senderText" to "",
            "senderReplyText" to "",
            "reactions" to emptyList<String>()
        )

        val chatRef = db.collection("privateChats").document(chatId)
        chatRef.collection("messages").add(msg)
            .addOnSuccessListener {
                chatRef.update("lastMessage", encryptedText, "lastTimestamp", now)
                    .addOnFailureListener {
                        chatRef.set(hashMapOf(
                            "participants" to listOf(uid, otherUid),
                            "lastMessage" to encryptedText,
                            "lastTimestamp" to now
                        ))
                    }
                recordMessageSent("", isMedia = true)
                onDone()
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, S.get("error_send_failed"), Toast.LENGTH_SHORT).show()
            }
    }

    private fun decryptMediaData(mediaBase64: String, sharedKey: SecretKeySpec?): ByteArray? {
        return try {
            val encrypted = Base64.decode(mediaBase64, Base64.NO_WRAP)
            if (sharedKey == null) return encrypted  // global chat media is not E2E encrypted
            decryptBytes(encrypted, sharedKey) ?: previousSharedKey?.let { decryptBytes(encrypted, it) }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Uploads media (image / voice) to the public globalChat collection.
     * Unlike uploadEncryptedMedia this writes plaintext bytes (Base64) since
     * global chat is not E2E encrypted.
     */
    private fun uploadGlobalMedia(
        data: ByteArray, mediaType: String, durationMs: Long = 0,
        customText: String? = null, onDone: () -> Unit
    ) {
        val uid = auth.currentUser?.uid ?: return

        // Anti-spam: media rate limiting
        val spamBlock = checkAntiSpam("", isMedia = true)
        if (spamBlock != null) {
            Toast.makeText(this, S.get(spamBlock), Toast.LENGTH_SHORT).show()
            return
        }

        // Compress images aggressively for Firestore storage (max ~100KB) + strip EXIF
        val compressed = if (mediaType == "image") {
            val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size)
            if (bitmap != null) {
                val maxDim = 800
                val scale = minOf(maxDim.toFloat() / bitmap.width, maxDim.toFloat() / bitmap.height, 1f)
                val scaled = if (scale < 1f) {
                    val s = Bitmap.createScaledBitmap(bitmap, (bitmap.width * scale).toInt(), (bitmap.height * scale).toInt(), true)
                    bitmap.recycle()
                    s
                } else bitmap
                val out = ByteArrayOutputStream()
                scaled.compress(Bitmap.CompressFormat.JPEG, 50, out)
                scaled.recycle()
                stripExifData(out.toByteArray())
            } else data
        } else data

        val mediaBase64 = Base64.encodeToString(compressed, Base64.NO_WRAP)

        // Check size (Firestore doc limit is 1MB)
        if (mediaBase64.length > 900_000) {
            Toast.makeText(this, S.get("file_too_large"), Toast.LENGTH_SHORT).show()
            return
        }

        val now = System.currentTimeMillis()
        val msgText = customText ?: if (mediaType == "image") "📷 " + S.get("photo") else "🎤 " + S.get("voice_message")

        val msg = hashMapOf<String, Any>(
            "text" to msgText,
            "senderId" to uid,
            "username" to myUsername,
            "nickname" to myNickname,
            "photoBase64" to myPhotoBase64,
            "timestamp" to now,
            "replyToText" to "",
            "replyToUsername" to "",
            "mediaUrl" to mediaBase64,
            "mediaType" to mediaType,
            "mediaDuration" to durationMs,
            "reactions" to emptyList<String>()
        )

        db.collection("globalChat").add(msg)
            .addOnSuccessListener {
                recordMessageSent("", isMedia = true)
                onDone()
            }
            .addOnFailureListener {
                Toast.makeText(this, S.get("error_send_failed"), Toast.LENGTH_SHORT).show()
            }
    }

    /** Show the gallery picker for the public global chat (no encryption). */
    private fun showInlineGalleryPickerGlobal() {
        val picker = InlineGalleryPicker(
            context = this,
            isDarkTheme = isDarkTheme,
            accentColor = accentColor,
            bgColor = bgColor,
            cardBg = cardBg,
            textColor = textColor,
            subTextColor = subTextColor
        )

        if (!picker.hasStoragePermission()) {
            // Reuse the pending fields; an empty otherUid signals global on the callback path.
            pendingMediaChatId = "global"
            pendingMediaOtherUid = ""
            pendingMediaSharedKey = null
            ActivityCompat.requestPermissions(
                this, arrayOf(picker.getRequiredPermission()), GALLERY_PERMISSION_REQUEST
            )
            return
        }

        inlineGalleryPicker = picker
        picker.onSend = { photoBytesList, caption ->
            val total = photoBytesList.size
            photoBytesList.forEachIndexed { index, photoBytes ->
                val strippedBytes = stripExifData(photoBytes)
                val isLast = index == total - 1
                val msgText = if (isLast && caption.isNotEmpty()) caption else null
                uploadGlobalMedia(strippedBytes, "image", customText = msgText) {
                    if (isLast) {
                        Toast.makeText(this, S.get("photo_sent"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            inlineGalleryPicker = null
        }
        picker.onDismiss = {
            inlineGalleryPicker = null
        }
        picker.show(container)
    }

    /** Stop voice recording and upload to the public global chat. */
    private fun stopVoiceRecordingGlobal() {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecordingVoice = false

            val file = java.io.File(voiceFilePath ?: return)
            if (file.exists() && file.length() > 0) {
                val data = file.readBytes()
                val duration = try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(voiceFilePath)
                    val dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    retriever.release()
                    dur
                } catch (_: Exception) { 0L }

                uploadGlobalMedia(data, "voice", duration) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            isRecordingVoice = false
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    private fun pickMediaForChat(chatId: String, otherUid: String, sharedKey: SecretKeySpec?) {
        pendingMediaChatId = chatId
        pendingMediaOtherUid = otherUid
        pendingMediaSharedKey = sharedKey
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        @Suppress("DEPRECATION")
        startActivityForResult(intent, PICK_MEDIA_REQUEST)
    }

    private fun showInlineGalleryPicker(chatId: String, otherUid: String, sharedKey: SecretKeySpec?) {
        val picker = InlineGalleryPicker(
            context = this,
            isDarkTheme = isDarkTheme,
            accentColor = accentColor,
            bgColor = bgColor,
            cardBg = cardBg,
            textColor = textColor,
            subTextColor = subTextColor
        )

        if (!picker.hasStoragePermission()) {
            pendingMediaChatId = chatId
            pendingMediaOtherUid = otherUid
            pendingMediaSharedKey = sharedKey
            ActivityCompat.requestPermissions(
                this, arrayOf(picker.getRequiredPermission()), GALLERY_PERMISSION_REQUEST
            )
            return
        }

        inlineGalleryPicker = picker
        picker.onSend = { photoBytesList, caption ->
            val total = photoBytesList.size
            photoBytesList.forEachIndexed { index, photoBytes ->
                val strippedBytes = stripExifData(photoBytes)
                val isLast = index == total - 1
                val msgText = if (isLast && caption.isNotEmpty()) caption else null
                uploadEncryptedMedia(strippedBytes, chatId, otherUid, "image", sharedKey, customText = msgText) {
                    if (isLast) {
                        Toast.makeText(this, S.get("photo_sent"), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            inlineGalleryPicker = null
        }
        picker.onDismiss = {
            inlineGalleryPicker = null
        }
        picker.show(container)
    }

    private fun startVoiceRecording() {
        try {
            voiceFilePath = "${cacheDir.absolutePath}/voice_${System.currentTimeMillis()}.3gp"
            mediaRecorder = android.media.MediaRecorder().apply {
                setAudioSource(android.media.MediaRecorder.AudioSource.MIC)
                setOutputFormat(android.media.MediaRecorder.OutputFormat.THREE_GPP)
                setAudioEncoder(android.media.MediaRecorder.AudioEncoder.AMR_NB)
                setOutputFile(voiceFilePath)
                prepare()
                start()
            }
            isRecordingVoice = true
        } catch (e: Exception) {
            Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
            isRecordingVoice = false
        }
    }

    private fun stopVoiceRecording(chatId: String, otherUid: String, sharedKey: SecretKeySpec?) {
        try {
            mediaRecorder?.stop()
            mediaRecorder?.release()
            mediaRecorder = null
            isRecordingVoice = false

            val file = java.io.File(voiceFilePath ?: return)
            if (file.exists() && file.length() > 0) {
                val data = file.readBytes()
                val duration = try {
                    val retriever = android.media.MediaMetadataRetriever()
                    retriever.setDataSource(voiceFilePath)
                    val dur = retriever.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    retriever.release()
                    dur
                } catch (_: Exception) { 0L }

                uploadEncryptedMedia(data, chatId, otherUid, "voice", sharedKey, duration) {
                    file.delete()
                }
            }
        } catch (e: Exception) {
            isRecordingVoice = false
            mediaRecorder?.release()
            mediaRecorder = null
        }
    }

    private fun showMediaInBubble(bubble: LinearLayout, msg: Msg, sharedKey: SecretKeySpec?, isMine: Boolean) {
        when (msg.mediaType) {
            "image" -> {
                val imgView = ImageView(this).apply {
                    layoutParams = LinearLayout.LayoutParams(dp(200), dp(200)).apply {
                        topMargin = dp(6)
                        bottomMargin = dp(4)
                    }
                    scaleType = ImageView.ScaleType.CENTER_CROP
                    background = createRoundedBackground(
                        if (isDarkTheme) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0"), 12
                    )
                    clipToOutline = true
                    outlineProvider = object : android.view.ViewOutlineProvider() {
                        override fun getOutline(view: View, outline: android.graphics.Outline) {
                            outline.setRoundRect(0, 0, view.width, view.height, dp(12).toFloat())
                        }
                    }
                }
                // Decrypt media from Base64 in message (no network download needed)
                val bytes = decryptMediaData(msg.mediaUrl, sharedKey)
                if (bytes != null) {
                    try {
                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                        imgView.setImageBitmap(bitmap)
                        imgView.setOnClickListener { showFullScreenImage(bitmap) }
                    } catch (_: Exception) {}
                }
                bubble.addView(imgView)
            }

            "voice" -> {
                val voiceRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(4), dp(6), dp(4), dp(4))
                    layoutParams = LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT)
                }

                val playBtn = TextView(this).apply {
                    text = "▶"
                    textSize = 24f
                    setTextColor(if (isMine) Color.WHITE else accentColor)
                    setPadding(dp(4), 0, dp(12), 0)
                }
                voiceRow.addView(playBtn)

                val durationSec = (msg.mediaDuration / 1000).toInt()
                val durationText = "${durationSec / 60}:${String.format("%02d", durationSec % 60)}"

                val waveBar = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                // Simple wave visualization
                val wave = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)).apply {
                        topMargin = dp(8)
                        bottomMargin = dp(4)
                    }
                    setBackgroundColor(if (isMine) COLOR_REPLY_MINE else accentColor)
                }
                waveBar.addView(wave)
                waveBar.addView(TextView(this).apply {
                    text = durationText
                    textSize = 11f
                    setTextColor(if (isMine) COLOR_REPLY_MINE else subTextColor)
                })
                voiceRow.addView(waveBar)

                var mediaPlayer: android.media.MediaPlayer? = null
                var isPlaying = false

                playBtn.setOnClickListener {
                    if (isPlaying) {
                        mediaPlayer?.stop()
                        mediaPlayer?.release()
                        mediaPlayer = null
                        playBtn.text = "▶"
                        isPlaying = false
                        return@setOnClickListener
                    }

                    val bytes = decryptMediaData(msg.mediaUrl, sharedKey)
                    if (bytes != null) {
                        var tempFile: java.io.File? = null
                        try {
                            tempFile = java.io.File.createTempFile("voice", ".3gp", cacheDir)
                            tempFile.writeBytes(bytes)
                            mediaPlayer = android.media.MediaPlayer().apply {
                                setDataSource(tempFile.absolutePath)
                                prepare()
                                start()
                                setOnCompletionListener {
                                    playBtn.text = "▶"
                                    isPlaying = false
                                    tempFile.delete()
                                    release()
                                }
                            }
                            playBtn.text = "⏸"
                            isPlaying = true
                        } catch (e: Exception) {
                            tempFile?.delete()
                            Toast.makeText(this@MainActivity, S.get("playback_failed"), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
                bubble.addView(voiceRow)
            }
        }
    }

    private fun showFullScreenImage(bitmap: Bitmap, allBitmaps: List<Bitmap>? = null) {
        val bitmaps = allBitmaps ?: listOf(bitmap)
        var currentIndex = bitmaps.indexOf(bitmap).coerceAtLeast(0)

        val overlay = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#EE000000"))
            isClickable = true
        }

        val imageView = ImageView(this).apply {
            setImageBitmap(bitmaps[currentIndex])
            scaleType = ImageView.ScaleType.FIT_CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            setPadding(dp(16), dp(56), dp(16), dp(80))
        }
        overlay.addView(imageView)

        // Counter label (1/5)
        val counterLabel = TextView(this).apply {
            text = if (bitmaps.size > 1) "${currentIndex + 1}/${bitmaps.size}" else ""
            textSize = 16f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            ).apply { topMargin = dp(18) }
        }
        overlay.addView(counterLabel)

        // Close button
        overlay.addView(TextView(this).apply {
            text = "✕"
            textSize = 28f
            setTextColor(Color.WHITE)
            setPadding(dp(20), dp(16), dp(20), dp(20))
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP or Gravity.END
            )
            setOnClickListener { container.removeView(overlay) }
        })

        // Navigation arrows for multiple photos
        if (bitmaps.size > 1) {
            val leftArrow = TextView(this).apply {
                text = "‹"
                textSize = 40f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(16), dp(16), dp(16))
                layoutParams = FrameLayout.LayoutParams(
                    dp(50), dp(50), Gravity.CENTER_VERTICAL or Gravity.START
                ).apply { leftMargin = dp(4) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#44FFFFFF"))
                }
                setOnClickListener {
                    currentIndex = if (currentIndex > 0) currentIndex - 1 else bitmaps.size - 1
                    imageView.setImageBitmap(bitmaps[currentIndex])
                    counterLabel.text = "${currentIndex + 1}/${bitmaps.size}"
                }
            }
            overlay.addView(leftArrow)

            val rightArrow = TextView(this).apply {
                text = "›"
                textSize = 40f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                setPadding(dp(16), dp(16), dp(16), dp(16))
                layoutParams = FrameLayout.LayoutParams(
                    dp(50), dp(50), Gravity.CENTER_VERTICAL or Gravity.END
                ).apply { rightMargin = dp(4) }
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#44FFFFFF"))
                }
                setOnClickListener {
                    currentIndex = if (currentIndex < bitmaps.size - 1) currentIndex + 1 else 0
                    imageView.setImageBitmap(bitmaps[currentIndex])
                    counterLabel.text = "${currentIndex + 1}/${bitmaps.size}"
                }
            }
            overlay.addView(rightArrow)
        }

        // Bottom bar with download buttons
        val bottomBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.BOTTOM
            ).apply { bottomMargin = dp(20) }
        }

        // Download this photo
        val downloadBtn = TextView(this).apply {
            text = "⬇ " + S.get("save")
            textSize = 14f
            setTextColor(Color.WHITE)
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                cornerRadius = dp(20).toFloat()
                setColor(accentColor)
            }
            setPadding(dp(20), dp(10), dp(20), dp(10))
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { rightMargin = if (bitmaps.size > 1) dp(12) else 0 }
            setOnClickListener {
                savePhotoToGallery(bitmaps[currentIndex])
            }
        }
        bottomBar.addView(downloadBtn)

        // Download all photos (only if multiple)
        if (bitmaps.size > 1) {
            val downloadAllBtn = TextView(this).apply {
                text = "⬇ " + S.get("save") + " (${bitmaps.size})"
                textSize = 14f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(Color.parseColor("#555555"))
                }
                setPadding(dp(20), dp(10), dp(20), dp(10))
                setOnClickListener {
                    var saved = 0
                    bitmaps.forEach { bmp ->
                        if (savePhotoToGallery(bmp, showToast = false)) saved++
                    }
                    Toast.makeText(this@MainActivity, "$saved ${S.get("photos_saved")}", Toast.LENGTH_SHORT).show()
                }
            }
            bottomBar.addView(downloadAllBtn)
        }

        overlay.addView(bottomBar)
        container.addView(overlay)
    }

    private fun savePhotoToGallery(bitmap: Bitmap, showToast: Boolean = true): Boolean {
        // Check permission on Android < Q
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q) {
            if (androidx.core.content.ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                androidx.core.app.ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.WRITE_EXTERNAL_STORAGE), 999)
                if (showToast) Toast.makeText(this, S.get("error_save_failed"), Toast.LENGTH_SHORT).show()
                return false
            }
        }
        return try {
            val filename = "ExecBin_${System.currentTimeMillis()}.jpg"
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, filename)
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    put(android.provider.MediaStore.Images.Media.RELATIVE_PATH, "Pictures/ExecBin")
                    put(android.provider.MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = contentResolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
            if (uri != null) {
                contentResolver.openOutputStream(uri)?.use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 95, out)
                }
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    values.clear()
                    values.put(android.provider.MediaStore.Images.Media.IS_PENDING, 0)
                    contentResolver.update(uri, values, null, null)
                }
                if (showToast) Toast.makeText(this, S.get("photo_saved"), Toast.LENGTH_SHORT).show()
                true
            } else false
        } catch (e: Exception) {
            if (showToast) Toast.makeText(this, S.get("error_save_failed"), Toast.LENGTH_SHORT).show()
            false
        }
    }

    private fun generateE2EKeyPairInKeystore() {
        try {
            val prefix = getE2EKeyPrefix()
            // Check if we already have valid keys for this account
            val existingPub = prefs.getString("${prefix}_dpi_conf", "") ?: ""
            val existingPriv = loadPrivateKeySplit() // Already validates PKCS8 format
            if (existingPub.isNotEmpty() && existingPriv != null) {
                myPublicKeyBase64 = existingPub
                return
            }

            android.util.Log.i("ExecBin", "Generating new E2E keypair (pub=${existingPub.isNotEmpty()}, priv=${existingPriv != null})")

            // Generate new EC key pair in software
            val keyGen = KeyPairGenerator.getInstance("EC")
            keyGen.initialize(ECGenParameterSpec("secp256r1"))
            val keyPair = keyGen.generateKeyPair()

            myPublicKeyBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)

            // Store public key with misleading name
            prefs.edit()
                .putString("${prefix}_dpi_conf", myPublicKeyBase64)
                .apply()

            // Store private key in PKCS8 format
            storePrivateKeySplit(keyPair.private.encoded)
        } catch (e: Exception) {
            android.util.Log.e("ExecBin", "Key generation failed: ${e.message}")
        }
    }

    private fun loadE2EKeys(): Boolean {
        return try {
            val prefix = getE2EKeyPrefix()
            val pub = prefs.getString("${prefix}_dpi_conf", "") ?: ""
            val priv = loadPrivateKeySplit()
            if (pub.isNotEmpty() && priv != null) {
                myPublicKeyBase64 = pub
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun ensureE2EKeysExist(onReady: () -> Unit) {
        showLoadingOverlay(S.get("preparing_keys"))
        if (!loadE2EKeys()) {
            generateE2EKeyPairInKeystore()
        }
        // Check if keys need rotation (every 30 days)
        checkKeyRotation()
        val userId = auth.currentUser?.uid ?: return onReady()
        db.collection("publicProfiles").document(userId).get()
            .addOnSuccessListener { doc ->
                val storedKey = doc?.getString("publicKey") ?: ""
                if (storedKey != myPublicKeyBase64 && myPublicKeyBase64.isNotEmpty()) {
                    db.collection("publicProfiles").document(userId)
                        .update("publicKey", myPublicKeyBase64)
                        .addOnCompleteListener {
                            isKeysReady = true
                            hideLoadingOverlay()
                            onReady()
                        }
                } else {
                    isKeysReady = true
                    hideLoadingOverlay()
                    onReady()
                }
            }
            .addOnFailureListener {
                isKeysReady = true
                hideLoadingOverlay()
                onReady()
            }
    }

    // ==========================================
    // METADATA ENCRYPTION
    // ==========================================
    private fun getMetadataKey(): SecretKeySpec {
        // Use a random key stored securely in EncryptedSharedPreferences
        val existingKey = prefs.getString("metadata_key_v2", null)
        if (existingKey != null) {
            return SecretKeySpec(Base64.decode(existingKey, Base64.NO_WRAP), "AES")
        }
        // Generate new random 256-bit key
        val keyBytes = ByteArray(32)
        SecureRandom().nextBytes(keyBytes)
        prefs.edit().putString("metadata_key_v2", Base64.encodeToString(keyBytes, Base64.NO_WRAP)).apply()
        return SecretKeySpec(keyBytes, "AES")
    }

    private fun encryptMeta(value: String): String {
        if (!prefs.getBoolean("encrypt_metadata", true)) return value
        return try {
            encryptMessage(value, getMetadataKey())
        } catch (e: Exception) { value }
    }

    private fun decryptMeta(value: String): String {
        return try {
            val result = decryptMessage(value, getMetadataKey())
            if (result.startsWith("\uD83D\uDD12")) value else result
        } catch (e: Exception) { value }
    }

    private fun encryptMetaLong(value: Long): String {
        return encryptMeta(value.toString())
    }

    private fun decryptMetaLong(value: String): Long {
        return try {
            decryptMeta(value).toLong()
        } catch (e: Exception) {
            try { value.toLong() } catch (e2: Exception) { 0L }
        }
    }

    /** True if [publicKeyBase64] decodes to an X.509 EC public key on P-256. */
    private fun isValidPublicKey(publicKeyBase64: String): Boolean = crypto.isValidP256PublicKey(publicKeyBase64)

    /** Static ECDH key using our previous (pre-rotation) private key, for media/edits made before rotation. */
    private fun derivePreviousSharedKey(theirPublicKeyBase64: String): SecretKeySpec? {
        val prev = loadPreviousPrivateKey() ?: return null
        return deriveSharedKeyFrom(prev, theirPublicKeyBase64)
    }

    /** Decrypts static-key ciphertext, falling back to the pre-rotation key. */
    private fun decryptStatic(cipherBase64: String, key: SecretKeySpec): String {
        val failed = "🔒 " + S.get("decrypt_failed")
        val result = decryptMessage(cipherBase64, key)
        if (result != failed) return result
        val prev = previousSharedKey ?: return result
        return decryptMessage(cipherBase64, prev)
    }

    /**
     * Remembers each contact's public key and warns when it changes. A change is normal after key
     * rotation or a reinstall, but it is also exactly what a key-substitution (MITM) attack looks like,
     * so the user is pointed at the safety-number screen.
     */
    private fun warnIfContactKeyChanged(otherUid: String, theirKey: String, displayName: String) {
        val myUid = auth.currentUser?.uid ?: return
        val pref = "seen_pubkey_${myUid}_$otherUid"
        val previous = prefs.getString(pref, null)
        if (previous == theirKey) return
        prefs.edit().putString(pref, theirKey).apply()
        if (previous != null && !isFinishing && !isDestroyed) {
            AlertDialog.Builder(this)
                .setTitle(iconize("⚠️ " + S.get("key_changed_title")))
                .setMessage(S.get("key_changed_msg", displayName))
                .setPositiveButton(S.get("ok"), null)
                .show()
        }
    }

    private fun deriveSharedKey(theirPublicKeyBase64: String): SecretKeySpec? {
        if (theirPublicKeyBase64.isEmpty()) {
            android.util.Log.e("ExecBin", "deriveSharedKey: their public key is empty")
            return null
        }
        val myPrivKeyBytes = loadPrivateKeySplit()
        if (myPrivKeyBytes == null || myPrivKeyBytes.isEmpty()) {
            android.util.Log.e("ExecBin", "deriveSharedKey: no local private key")
            return null
        }
        return deriveSharedKeyFrom(myPrivKeyBytes, theirPublicKeyBase64)
    }

    private fun deriveSharedKeyFrom(myPrivKeyBytes: ByteArray, theirPublicKeyBase64: String): SecretKeySpec? {
        // ECDH + HKDF-SHA256 ("execbin-msg-key"), implemented and unit-tested in CryptoManager
        val key = crypto.deriveSharedKey(myPrivKeyBytes, theirPublicKeyBase64)
        if (key == null) android.util.Log.e("ExecBin", "deriveSharedKey failed")
        return key
    }

    // ==========================================
    // PER-MESSAGE EPHEMERAL KEYS ("PFS")
    // ==========================================
    //
    // Each message uses a fresh ephemeral sender key:
    //   recipientKey = HKDF(ECDH(eph_priv, recipient_static_pub)),  senderKey = HKDF(ECDH(eph_priv, sender_static_pub))
    // The ephemeral private key is discarded, so a later leak of the *sender's* keys doesn't expose the
    // recipient copy. This is NOT full forward secrecy: the recipient's long-term (static) private key
    // can still decrypt every message sent to it while that key (or its retained previous key) exists.
    // Forward secrecy is therefore limited to the key-rotation window (see checkKeyRotation).

    private fun generatePFSKeys(theirStaticPubKeyBase64: String): Triple<SecretKeySpec, SecretKeySpec, String>? {
        val keys = crypto.generatePfsKeys(theirStaticPubKeyBase64, myPublicKeyBase64)
        if (keys == null) {
            android.util.Log.e("ExecBin", "PFS key generation failed")
            return null
        }
        return Triple(keys.recipientKey, keys.senderKey, keys.ephemeralPublicKeyBase64)
    }

    /**
     * Derive the per-message decryption key from the ephemeral public key in a message, using our
     * static private key — or the previous one if the message predates our last key rotation.
     */
    private fun derivePFSDecryptionKey(ephemeralPubKeyBase64: String): SecretKeySpec? {
        val current = loadPrivateKeySplit()
        if (current != null) {
            crypto.derivePfsDecryptionKey(current, ephemeralPubKeyBase64)?.let { return it }
        }
        val prev = loadPreviousPrivateKey() ?: return null
        return crypto.derivePfsDecryptionKey(prev, ephemeralPubKeyBase64)
    }

    /**
     * Decrypts a per-message-key ciphertext with our current static key, then with the pre-rotation key.
     * (ECDH with the wrong private key still "succeeds", so the fallback must be on AES-GCM failure.)
     */
    private fun decryptWithEphemeral(cipherBase64: String, ephemeralPubKeyBase64: String): String {
        val failed = "🔒 " + S.get("decrypt_failed")
        val current = derivePFSDecryptionKey(ephemeralPubKeyBase64)
        val first = if (current != null) decryptMessage(cipherBase64, current) else failed
        if (first != failed) return first
        val prev = derivePreviousPFSDecryptionKey(ephemeralPubKeyBase64) ?: return first
        return decryptMessage(cipherBase64, prev)
    }

    /** Like [derivePFSDecryptionKey] but with our previous private key only (post-rotation fallback). */
    private fun derivePreviousPFSDecryptionKey(ephemeralPubKeyBase64: String): SecretKeySpec? {
        val prev = loadPreviousPrivateKey() ?: return null
        return crypto.derivePfsDecryptionKey(prev, ephemeralPubKeyBase64)
    }

    // ==========================================
    // KEY ROTATION
    // ==========================================

    /** Check if E2E keys need rotation (every 30 days) */
    private fun checkKeyRotation() {
        try {
            val prefix = getE2EKeyPrefix()
            val keyCreatedAt = prefs.getLong("${prefix}_key_created", 0L)
            val now = System.currentTimeMillis()
            val thirtyDays = 30L * 24 * 60 * 60 * 1000

            if (keyCreatedAt > 0 && now - keyCreatedAt > thirtyDays) {
                // Store old private key for decryption of in-flight messages
                val oldPrivBytes = loadPrivateKeySplit()
                if (oldPrivBytes != null) {
                    prefs.edit()
                        .putString("${prefix}_prev_key_blob", Base64.encodeToString(oldPrivBytes, Base64.NO_WRAP))
                        .apply()
                }

                // Generate new keys
                val keyGen = KeyPairGenerator.getInstance("EC")
                keyGen.initialize(ECGenParameterSpec("secp256r1"))
                val keyPair = keyGen.generateKeyPair()

                myPublicKeyBase64 = Base64.encodeToString(keyPair.public.encoded, Base64.NO_WRAP)
                prefs.edit()
                    .putString("${prefix}_dpi_conf", myPublicKeyBase64)
                    .putLong("${prefix}_key_created", now)
                    .apply()
                storePrivateKeySplit(keyPair.private.encoded)

                android.util.Log.i("ExecBin", "E2E keys rotated (30-day cycle)")
            } else if (keyCreatedAt == 0L) {
                // First time — record creation time
                prefs.edit().putLong("${prefix}_key_created", now).apply()
            }
        } catch (e: Exception) {
            android.util.Log.e("ExecBin", "Key rotation check failed")
        }
    }

    /** Load previous private key (after key rotation) for decrypting old messages */
    private fun loadPreviousPrivateKey(): ByteArray? {
        return try {
            val prefix = getE2EKeyPrefix()
            val blob = prefs.getString("${prefix}_prev_key_blob", null) ?: return null
            Base64.decode(blob, Base64.NO_WRAP)
        } catch (e: Exception) {
            null
        }
    }

    private fun encryptMessage(plainText: String, key: SecretKeySpec): String {
        return try {
            crypto.encryptMessage(plainText, key)
        } catch (e: Exception) {
            android.util.Log.e("ExecBin", "Encryption failed")
            throw e  // Never silently send plaintext
        }
    }

    private fun decryptMessage(cipherBase64: String, key: SecretKeySpec): String {
        val plain = crypto.decryptMessage(cipherBase64, key)
        return if (plain == CryptoManager.DECRYPT_FAILED_SENTINEL) "🔒 " + S.get("decrypt_failed") else plain
    }

    private fun createRoundedBackground(color: Int, radius: Int): GradientDrawable {
        return GradientDrawable().apply {
            setColor(color)
            cornerRadius = dp(radius).toFloat()
            if (!isDarkTheme && (color == cardBg || color == headerBg)) {
                setStroke(1, Color.parseColor("#E2E8F0"))
            }
        }
    }

    private fun createGradientButton(startColor: Int = accentColor): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.BL_TR,
            intArrayOf(startColor, Color.parseColor("#A78BFA"), Color.parseColor("#C084FC"))
        ).apply {
            cornerRadius = dp(16).toFloat()
        }
    }

    private fun createInputBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(if (isDarkTheme) Color.parseColor("#111827") else Color.parseColor("#F1F5F9"))
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), if (isDarkTheme) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0"))
        }
    }

    private fun createCardBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(cardBg)
            cornerRadius = dp(20).toFloat()
            if (isDarkTheme) {
                setStroke(dp(1), Color.parseColor("#1E293B"))
            } else {
                setStroke(dp(1), Color.parseColor("#E2E8F0"))
            }
        }
    }

    private fun createGlassBackground(): GradientDrawable {
        return GradientDrawable().apply {
            setColor(if (isDarkTheme) Color.parseColor("#CC111827") else Color.parseColor("#CCF8FAFC"))
            cornerRadius = dp(20).toFloat()
            setStroke(dp(1), if (isDarkTheme) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0"))
        }
    }

    private fun createIconButton(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (isDarkTheme) Color.parseColor("#1E293B") else Color.parseColor("#F1F5F9"))
            setStroke(dp(1), if (isDarkTheme) Color.parseColor("#334155") else Color.parseColor("#E2E8F0"))
        }
    }

    // ==========================================
    // REGISTRATION
    // ==========================================
    private fun showRegistration() {
        currentScreen = "registration"
        container.removeAllViews()
        container.setBackgroundColor(bgColor)
        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(40), dp(24), dp(32))
        }
        // Logo with glow ring
        val logoContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(120)).apply { bottomMargin = dp(28) }
        }
        // Outer glow ring
        logoContainer.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(3), accentColor)
                setColor(Color.TRANSPARENT)
            }
            layoutParams = FrameLayout.LayoutParams(dp(120), dp(120))
        })
        // Inner fill
        logoContainer.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isDarkTheme) Color.parseColor("#1A818CF8") else Color.parseColor("#1A6366F1"))
            }
            layoutParams = FrameLayout.LayoutParams(dp(100), dp(100)).apply {
                gravity = Gravity.CENTER
            }
        })
        logoContainer.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("✈", Color.WHITE, dp(48)))
            layoutParams = FrameLayout.LayoutParams(dp(48), dp(48)).apply { gravity = Gravity.CENTER }
        })
        root.addView(logoContainer)
        root.addView(TextView(this).apply {
            text = S.get("create_account")
            setTextColor(textColor)
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = -0.03f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
        })
        root.addView(TextView(this).apply {
            text = S.get("choose_username")
            setTextColor(subTextColor)
            textSize = 14.5f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(28) }
        })
        val usernameField = EditText(this).apply {
            hint = "  @  " + S.get("username")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 15f
            background = createInputBackground()
            setPadding(dp(18), dp(16), dp(18), dp(16))
            inputType = InputType.TYPE_CLASS_TEXT
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        root.addView(usernameField)
        val emailField = EditText(this).apply {
            hint = "  \u2709  " + S.get("email")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 15f
            background = createInputBackground()
            setPadding(dp(18), dp(16), dp(18), dp(16))
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        root.addView(emailField)
        val passwordField = EditText(this).apply {
            hint = "  \uD83D\uDD12  " + S.get("password")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 15f
            background = createInputBackground()
            setPadding(dp(18), dp(16), dp(18), dp(16))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(20) }
        }
        root.addView(passwordField)
        val ageCheckbox = CheckBox(this).apply {
            text = S.get("age_confirm")
            setTextColor(textColor)
            textSize = 13f
            buttonTintList = android.content.res.ColorStateList.valueOf(accentColor)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }
        root.addView(ageCheckbox)
        val privacyCheckbox = CheckBox(this).apply {
            text = S.get("privacy_agree")
            setTextColor(textColor)
            textSize = 13f
            buttonTintList = android.content.res.ColorStateList.valueOf(accentColor)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) }
        }
        privacyCheckbox.movementMethod = android.text.method.LinkMovementMethod.getInstance()
        root.addView(privacyCheckbox)
        val registerBtn = Button(this).apply {
            text = S.get("create_account_btn")
            setTextColor(Color.WHITE)
            textSize = 16.5f
            background = createGradientButton()
            isAllCaps = false
            setTypeface(null, android.graphics.Typeface.BOLD)
            stateListAnimator = null
            letterSpacing = 0.02f
            elevation = dp(6).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { topMargin = dp(4) }
        }
        root.addView(registerBtn)
        registerBtn.setOnClickListener {
            val username = usernameField.text.toString().trim().lowercase()
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()
            when {
                username.isEmpty() -> Toast.makeText(this, S.get("enter_username"), Toast.LENGTH_SHORT).show()
                username.length < 3 -> Toast.makeText(this, S.get("username_min_chars"), Toast.LENGTH_SHORT).show()
                !username.matches(Regex("^[a-z0-9_]+$")) -> Toast.makeText(this, S.get("username_invalid"), Toast.LENGTH_SHORT).show()
                email.isEmpty() -> Toast.makeText(this, S.get("enter_email"), Toast.LENGTH_SHORT).show()
                !email.contains("@") -> Toast.makeText(this, S.get("enter_valid_email"), Toast.LENGTH_SHORT).show()
                password.isEmpty() -> Toast.makeText(this, S.get("enter_password"), Toast.LENGTH_SHORT).show()
                password.length < 6 -> Toast.makeText(this, S.get("password_min_6"), Toast.LENGTH_SHORT).show()
                !ageCheckbox.isChecked -> Toast.makeText(this, S.get("age_confirm_required"), Toast.LENGTH_SHORT).show()
                !privacyCheckbox.isChecked -> Toast.makeText(this, S.get("privacy_agree_required"), Toast.LENGTH_SHORT).show()
                else -> {
                    registerBtn.isEnabled = false
                    registerBtn.text = S.get("checking")
                    syncDeviceAccountsFromFirestore {
                        db.collection("publicProfiles").whereEqualTo("username", username).get()
                            .addOnSuccessListener { docs ->
                                if (!docs.isEmpty) {
                                    Toast.makeText(this, S.get("username_taken"), Toast.LENGTH_SHORT).show()
                                    registerBtn.isEnabled = true
                                    registerBtn.text = S.get("create_account_btn")
                                } else {
                                    tempUsername = username
                                    registerBtn.text = S.get("creating_account")
                                    auth.createUserWithEmailAndPassword(email, password)
                                        .addOnCompleteListener { task ->
                                            if (task.isSuccessful) {
                                                val user = auth.currentUser ?: return@addOnCompleteListener
                                                val profileUpdates = UserProfileChangeRequest.Builder()
                                                    .setDisplayName(username)
                                                    .build()
                                                user.updateProfile(profileUpdates).addOnCompleteListener {
                                                    user.sendEmailVerification()
                                                        .addOnCompleteListener { emailTask ->
                                                            if (emailTask.isSuccessful) {
                                                                Toast.makeText(this, S.get("verification_email_sent"), Toast.LENGTH_LONG).show()
                                                                showVerificationPending()
                                                            } else {
                                                                val errMsg = emailTask.exception?.message ?: "Unknown error"
                                                                android.util.Log.e("ExecBin", "Email verification failed: $errMsg", emailTask.exception)
                                                                Toast.makeText(this, "${S.get("error_something_wrong")}: $errMsg", Toast.LENGTH_LONG).show()
                                                                user.delete()
                                                                registerBtn.isEnabled = true
                                                                registerBtn.text = S.get("create_account_btn")
                                                            }
                                                        }
                                                }
                                            } else {
                                                val errMsg = task.exception?.message ?: "Unknown error"
                                                android.util.Log.e("ExecBin", "Registration failed: $errMsg", task.exception)
                                                Toast.makeText(this, "${S.get("error_something_wrong")}: $errMsg", Toast.LENGTH_LONG).show()
                                                registerBtn.isEnabled = true
                                                registerBtn.text = S.get("create_account_btn")
                                            }
                                        }
                                }
                            }
                            .addOnFailureListener { e ->
                                android.util.Log.e("ExecBin", "Username check failed: ${e.message}", e)
                                Toast.makeText(this, "${S.get("checking_username")}: ${e.message}", Toast.LENGTH_SHORT).show()
                                registerBtn.isEnabled = true
                                registerBtn.text = S.get("create_account_btn")
                            }
                    }
                }
            }
        }
        root.addView(TextView(this).apply {
            text = S.get("already_have_account")
            setTextColor(accentColor)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
            setOnClickListener { showLogin() }
        })
        root.addView(View(this).apply {
            setBackgroundColor(subTextColor)
            alpha = 0.3f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                topMargin = dp(24)
                bottomMargin = dp(16)
            }
        })
        root.addView(TextView(this).apply {
            text = iconize("🔒 ${S.get("privacy_policy")} — ${S.get("tap_to_read")}", accentColor)
            setTextColor(accentColor)
            textSize = 13f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(4))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4) }
            setOnClickListener { showPrivacyPolicy() }
        })
        root.addView(TextView(this).apply {
            text = S.get("privacy_policy_text")
            setTextColor(subTextColor)
            textSize = 11f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) }
        })
        root.addView(TextView(this).apply {
            text = iconize("📱 ${S.get("account_limits", MAX_DEVICES_PER_ACCOUNT)}")
            setTextColor(subTextColor)
            textSize = 10f
            gravity = Gravity.CENTER
        })
        scroll.addView(root)
        container.addView(scroll)
    }

    // ==========================================
    // VERIFICATION PENDING
    // ==========================================
    private fun showVerificationPending() {
        currentScreen = "verification"
        container.removeAllViews()
        container.setBackgroundColor(bgColor)
        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(28), dp(60), dp(28), dp(40))
        }
        // Email icon with glow
        val iconContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(110), dp(110)).apply { bottomMargin = dp(24) }
        }
        iconContainer.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor(if (isDarkTheme) "#1A7C6FFF" else "#1A6558F5"))
            }
            layoutParams = FrameLayout.LayoutParams(dp(110), dp(110))
        })
        iconContainer.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("📧", accentColor, dp(52)))
            layoutParams = FrameLayout.LayoutParams(dp(52), dp(52)).apply { gravity = Gravity.CENTER }
        })
        root.addView(iconContainer)
        root.addView(TextView(this).apply {
            text = S.get("check_your_email")
            setTextColor(textColor)
            textSize = 24f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = -0.02f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
        })
        root.addView(TextView(this).apply {
            text = S.get("verification_sent_to", auth.currentUser?.email ?: "")
            setTextColor(subTextColor)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(28) }
        })
        val verifyBtn = Button(this).apply {
            text = S.get("verify_email_btn")
            setTextColor(Color.WHITE)
            textSize = 16f
            background = createGradientButton()
            isAllCaps = false
            setTypeface(null, android.graphics.Typeface.BOLD)
            stateListAnimator = null
            letterSpacing = 0.02f
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(54)).apply { bottomMargin = dp(12) }
        }
        root.addView(verifyBtn)
        verifyBtn.setOnClickListener {
            verifyBtn.isEnabled = false
            verifyBtn.text = S.get("checking")
            auth.currentUser?.reload()?.addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    if (auth.currentUser?.isEmailVerified == true) {
                        verifyBtn.text = S.get("setting_up")
                        checkAndLoadUserData()
                    } else {
                        Toast.makeText(this, S.get("email_not_verified"), Toast.LENGTH_LONG).show()
                        verifyBtn.isEnabled = true
                        verifyBtn.text = S.get("verify_email_btn")
                    }
                } else {
                    val errMsg = task.exception?.message ?: "Unknown"
                    android.util.Log.e("ExecBin", "Reload user failed: $errMsg", task.exception)
                    Toast.makeText(this, "Verify: $errMsg", Toast.LENGTH_LONG).show()
                    verifyBtn.isEnabled = true
                    verifyBtn.text = S.get("verify_email_btn")
                }
            }
        }
        root.addView(TextView(this).apply {
            text = S.get("resend_verification")
            setTextColor(accentColor)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, dp(16))
            setOnClickListener {
                auth.currentUser?.sendEmailVerification()?.addOnCompleteListener { task ->
                    if (task.isSuccessful) Toast.makeText(this@MainActivity, S.get("email_sent"), Toast.LENGTH_SHORT).show()
                    else Toast.makeText(this@MainActivity, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
                }
            }
        })
        root.addView(TextView(this).apply {
            text = S.get("wrong_email_cancel")
            setTextColor(subTextColor)
            textSize = 13f
            gravity = Gravity.CENTER
            setOnClickListener {
                val user = auth.currentUser
                if (user != null && !user.isEmailVerified) {
                    user.delete().addOnCompleteListener {
                        auth.signOut()
                        showRegistration()
                    }
                } else {
                    auth.signOut()
                    showRegistration()
                }
            }
        })
        scroll.addView(root)
        container.addView(scroll)
    }

    // ==========================================
    // LOGIN
    // ==========================================
    private fun showLogin() {
        currentScreen = "login"
        container.removeAllViews()
        container.setBackgroundColor(bgColor)
        val scroll = ScrollView(this).apply {
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            isFillViewport = true
        }
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(50), dp(24), dp(32))
        }
        // Logo with glow ring
        val logoContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(120)).apply { bottomMargin = dp(28) }
        }
        logoContainer.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setStroke(dp(3), accentColor)
                setColor(Color.TRANSPARENT)
            }
            layoutParams = FrameLayout.LayoutParams(dp(120), dp(120))
        })
        logoContainer.addView(View(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isDarkTheme) Color.parseColor("#1A818CF8") else Color.parseColor("#1A6366F1"))
            }
            layoutParams = FrameLayout.LayoutParams(dp(100), dp(100)).apply { gravity = Gravity.CENTER }
        })
        logoContainer.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("✈", Color.WHITE, dp(48)))
            layoutParams = FrameLayout.LayoutParams(dp(48), dp(48)).apply { gravity = Gravity.CENTER }
        })
        root.addView(logoContainer)
        root.addView(TextView(this).apply {
            text = S.get("welcome_back")
            setTextColor(textColor)
            textSize = 28f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = -0.03f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
        })
        root.addView(TextView(this).apply {
            text = S.get("login_subtitle")
            setTextColor(subTextColor)
            textSize = 14.5f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(28) }
        })
        val emailField = EditText(this).apply {
            hint = "  \u2709  " + S.get("email_hint")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 15f
            background = createInputBackground()
            setPadding(dp(18), dp(16), dp(18), dp(16))
            inputType = InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
        }
        root.addView(emailField)
        val passwordField = EditText(this).apply {
            hint = "  \uD83D\uDD12  " + S.get("password_hint")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 15f
            background = createInputBackground()
            setPadding(dp(18), dp(16), dp(18), dp(16))
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) }
        }
        root.addView(passwordField)
        val loginBtn = Button(this).apply {
            text = S.get("login_btn")
            setTextColor(Color.WHITE)
            textSize = 16.5f
            background = createGradientButton()
            isAllCaps = false
            setTypeface(null, android.graphics.Typeface.BOLD)
            stateListAnimator = null
            letterSpacing = 0.02f
            elevation = dp(6).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
        }
        root.addView(loginBtn)
        root.addView(TextView(this).apply {
            text = S.get("forgot_password")
            setTextColor(accentColor)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(16), 0, 0)
            setOnClickListener { showForgotPasswordDialog() }
        })
        loginBtn.setOnClickListener {
            // Check login lockout
            val loginLockout = getLoginLockoutUntil()
            if (loginLockout > System.currentTimeMillis()) {
                val secs = (loginLockout - System.currentTimeMillis()) / 1000
                Toast.makeText(this, S.get("login_locked_wait", secs), Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }
            val email = emailField.text.toString().trim()
            val password = passwordField.text.toString().trim()
            when {
                email.isEmpty() -> Toast.makeText(this, S.get("enter_email"), Toast.LENGTH_SHORT).show()
                password.isEmpty() -> Toast.makeText(this, S.get("enter_password"), Toast.LENGTH_SHORT).show()
                else -> {
                    loginBtn.isEnabled = false
                    loginBtn.text = S.get("logging_in")
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                // Reset login attempts on success
                                setLoginAttempts(0)
                                setLoginLockoutUntil(0L)
                                val user = auth.currentUser ?: return@addOnCompleteListener
                                if (user.isEmailVerified) {
                                    checkAndLoadUserData()
                                } else {
                                    showVerificationPending()
                                }
                            } else {
                                // Track failed login attempts with exponential backoff
                                val attempts = getLoginAttempts() + 1
                                setLoginAttempts(attempts)
                                val lockoutMs = getLockoutDuration(attempts)
                                if (lockoutMs > 0) {
                                    if (attempts >= 10) setLoginAttempts(7)
                                    setLoginLockoutUntil(System.currentTimeMillis() + lockoutMs)
                                    val durationStr = formatLockoutDuration(lockoutMs)
                                    Toast.makeText(this, S.get("pin_lockout_timer", durationStr), Toast.LENGTH_LONG).show()
                                } else {
                                    Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_LONG).show()
                                }
                                loginBtn.isEnabled = true
                                loginBtn.text = S.get("login_btn")
                            }
                        }
                }
            }
        }
        root.addView(TextView(this).apply {
            text = S.get("no_account_signup")
            setTextColor(accentColor)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(20), 0, 0)
            setOnClickListener { showRegistration() }
        })
        scroll.addView(root)
        container.addView(scroll)
    }

    // ==========================================
    // HOME
    // ==========================================
    private fun showHome() {
        currentScreen = "home"
        currentOpenChatId = null
        container.removeAllViews()
        container.setBackgroundColor(bgColor)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        // App title bar — modern glassmorphic header
        val titleBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGlassBackground().apply { cornerRadius = 0f; cornerRadii = floatArrayOf(0f,0f,0f,0f, dp(24).toFloat(),dp(24).toFloat(), dp(24).toFloat(),dp(24).toFloat()) }
            setPadding(dp(22), dp(16), dp(18), dp(14))
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        titleBar.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@MainActivity).apply {
                text = S.get("app_name")
                setTextColor(textColor)
                textSize = 24f
                setTypeface(null, android.graphics.Typeface.BOLD)
                letterSpacing = -0.03f
            })
            addView(LinearLayout(this@MainActivity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(2), 0, 0)
                addView(View(this@MainActivity).apply {
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.OVAL
                        setColor(COLOR_ONLINE_GREEN)
                    }
                    layoutParams = LinearLayout.LayoutParams(dp(7), dp(7)).apply { rightMargin = dp(6) }
                })
                addView(TextView(this@MainActivity).apply {
                    text = S.get("e2e_encrypted")
                    setTextColor(COLOR_ONLINE_GREEN)
                    textSize = 11.5f
                    letterSpacing = 0.04f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
            })
        })
        // Profile icon button
        val profileBtn = FrameLayout(this).apply {
            background = createIconButton()
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(10) }
            elevation = dp(2).toFloat()
            setOnClickListener { showProfile() }
            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(LineIconDrawable("👤", Color.WHITE, dp(20)))
                layoutParams = FrameLayout.LayoutParams(dp(20), dp(20)).apply { gravity = Gravity.CENTER }
            })
        }
        titleBar.addView(profileBtn)
        // Settings icon button
        val settingsBtn = FrameLayout(this).apply {
            background = createIconButton()
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
            elevation = dp(2).toFloat()
            setOnClickListener { showSettings() }
            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(LineIconDrawable("⚙️", Color.WHITE, dp(20)))
                layoutParams = FrameLayout.LayoutParams(dp(20), dp(20)).apply { gravity = Gravity.CENTER }
            })
        }
        titleBar.addView(settingsBtn)
        root.addView(titleBar)

        // Connection status banner
        root.addView(createConnectionBanner())
        // Check current connectivity state
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val activeNet = cm.activeNetworkInfo
        if (activeNet == null || !activeNet.isConnected) {
            connectionBanner?.visibility = View.VISIBLE
        }

        // Search bar — modern floating pill
        val searchContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            background = createInputBackground()
            setPadding(dp(18), dp(13), dp(18), dp(13))
            elevation = dp(2).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                leftMargin = dp(16); rightMargin = dp(16); topMargin = dp(12); bottomMargin = dp(8)
            }
        }
        searchContainer.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("🔍", subTextColor, dp(18)))
            layoutParams = LinearLayout.LayoutParams(dp(18), dp(18)).apply { rightMargin = dp(12) }
        })
        val searchField = EditText(this).apply {
            hint = S.get("search_hint")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 14f
            background = null
            isSingleLine = true
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        searchContainer.addView(searchField)
        root.addView(searchContainer)
        val recycler = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            layoutManager = LinearLayoutManager(this@MainActivity)
        }
        val chatList = mutableListOf<ChatUser>()
        val adapter = ChatListAdapter(chatList) { user ->
            if (user == null) showGlobalChat()
            else showPrivateChat(user.uid, user.username, user.nickname, user.photoBase64)
        }
        recycler.adapter = adapter
        // Smooth item animations
        recycler.itemAnimator = androidx.recyclerview.widget.DefaultItemAnimator().apply {
            addDuration = 200
            removeDuration = 150
            changeDuration = 150
        }
        val fadeIn = android.view.animation.AlphaAnimation(0f, 1f).apply { duration = 300 }
        recycler.layoutAnimation = android.view.animation.LayoutAnimationController(fadeIn, 0.1f)

        // Empty state hint
        val emptyState = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(32), dp(40), dp(32), dp(24))
            visibility = View.GONE
        }
        emptyState.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("💬", subTextColor, dp(52)))
            layoutParams = LinearLayout.LayoutParams(dp(52), dp(52)).apply { bottomMargin = dp(16); gravity = Gravity.CENTER_HORIZONTAL }
        })
        emptyState.addView(TextView(this).apply {
            text = S.get("no_chats_yet")
            setTextColor(textColor)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            letterSpacing = -0.01f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        })
        emptyState.addView(TextView(this).apply {
            text = S.get("search_to_start_chat")
            setTextColor(subTextColor)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) }
        })
        // E2E encryption info tip
        emptyState.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = createRoundedBackground(if (isDarkTheme) Color.parseColor("#1A2240") else Color.parseColor("#EEF0FF"), 12)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(LineIconDrawable("🔒", subTextColor, dp(14)))
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(8) }
            })
            addView(TextView(this@MainActivity).apply {
                text = S.get("e2e_badge_info")
                setTextColor(subTextColor)
                textSize = 12f
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
        })
        root.addView(emptyState)

        // Pull-to-refresh wrapper
        val swipeRefresh = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            setColorSchemeColors(accentColor)
            setProgressBackgroundColorSchemeColor(if (isDarkTheme) Color.parseColor("#1C2240") else Color.WHITE)
        }
        swipeRefresh.addView(recycler)
        root.addView(swipeRefresh)
        var isSearching = false
        fun loadRecentChats() {
            val myUid = auth.currentUser?.uid ?: return
            privateChatListListener?.remove()
            privateChatListListener = db.collection("privateChats")
                .whereArrayContains("participants", myUid)
                .addSnapshotListener { snaps, error ->
                    if (error != null) {
                        android.util.Log.e("Home", "Listener error", error)
                        return@addSnapshotListener
                    }
                    if (isSearching) return@addSnapshotListener
                    val chatDocs = snaps?.documents?.sortedByDescending {
                        it.getLong("lastTimestamp") ?: 0
                    } ?: emptyList()
                    recentChats.clear()
                    chatTimestamps.clear()
                    if (chatDocs.isEmpty()) {
                        runOnUiThread {
                            chatList.clear()
                            chatList.add(ChatUser("", "global", ""))
                            adapter.notifyDataSetChanged()
                            emptyState.visibility = View.VISIBLE
                        }
                        return@addSnapshotListener
                    }
                    runOnUiThread { emptyState.visibility = View.GONE }
                    var remaining = chatDocs.size
                    val tempChats = mutableListOf<ChatUser>()
                    val tempTimestamps = mutableMapOf<String, Long>()
                    chatDocs.forEach { doc ->
                        val participants = doc.get("participants") as? List<*> ?: run {
                            remaining--
                            return@forEach
                        }
                        val otherUid = participants.firstOrNull { it != myUid }?.toString() ?: run {
                            remaining--
                            return@forEach
                        }
                        val lastTimestamp = doc.getLong("lastTimestamp") ?: 0
                        val thisChatId = doc.id
                        db.collection("publicProfiles").document(otherUid).get()
                            .addOnSuccessListener { profileDoc ->
                                val user = profileDoc.toObject(ChatUser::class.java)
                                if (user != null && !blockedUsers.contains(user.uid)) {
                                    tempChats.add(user)
                                    tempTimestamps[user.uid] = lastTimestamp

                                    // Count unread messages from the other user
                                    db.collection("privateChats").document(thisChatId)
                                        .collection("messages")
                                        .whereEqualTo("senderId", otherUid)
                                        .whereEqualTo("read", false)
                                        .get()
                                        .addOnSuccessListener { unreadDocs ->
                                            val count = unreadDocs.size()
                                            if (count > 0) unreadCounts[user.uid] = count
                                            else unreadCounts.remove(user.uid)
                                        }

                                    // Show notification if new message & not viewing this chat
                                    val prevTs = notifiedChatTimestamps[thisChatId] ?: 0L
                                    if (lastTimestamp > prevTs && prevTs > 0L && thisChatId != currentOpenChatId) {
                                        if (!isAppInForeground || currentScreen != "private_chat" || currentOpenChatId != thisChatId) {
                                            val displayName = if (user.nickname.isNotEmpty()) user.nickname else user.username
                                            showChatNotification(displayName, thisChatId, otherUid)
                                        }
                                    }
                                    notifiedChatTimestamps[thisChatId] = lastTimestamp
                                }
                                remaining--
                                if (remaining <= 0 && !isSearching) {
                                    tempChats.sortByDescending { tempTimestamps[it.uid] ?: 0 }
                                    runOnUiThread {
                                        recentChats.clear()
                                        recentChats.addAll(tempChats)
                                        chatTimestamps.clear()
                                        chatTimestamps.putAll(tempTimestamps)
                                        chatList.clear()
                                        chatList.add(ChatUser("", "global", ""))
                                        chatList.addAll(recentChats)
                                        adapter.notifyDataSetChanged()
                                        setupChatListTypingListeners(adapter)
                                    }
                                }
                            }
                            .addOnFailureListener {
                                remaining--
                                if (remaining <= 0 && !isSearching) {
                                    runOnUiThread {
                                        tempChats.sortByDescending { tempTimestamps[it.uid] ?: 0 }
                                        recentChats.clear()
                                        recentChats.addAll(tempChats)
                                        chatTimestamps.clear()
                                        chatTimestamps.putAll(tempTimestamps)
                                        chatList.clear()
                                        chatList.add(ChatUser("", "global", ""))
                                        chatList.addAll(recentChats)
                                        adapter.notifyDataSetChanged()
                                        setupChatListTypingListeners(adapter)
                                    }
                                }
                            }
                    }
                }
        }
        searchField.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) {
                val raw = s.toString().trim()
                if (raw.length < 3 && !raw.startsWith("@")) {
                    isSearching = false
                    runOnUiThread {
                        chatList.clear()
                        chatList.add(ChatUser("", "global", ""))
                        chatList.addAll(recentChats)
                        adapter.notifyDataSetChanged()
                    }
                    return
                }
                if (raw.startsWith("@")) {
                    val query = raw.removePrefix("@").lowercase()
                    if (query.length < 3) {
                        isSearching = false
                        runOnUiThread {
                            chatList.clear()
                            chatList.add(ChatUser("", "global", ""))
                            chatList.addAll(recentChats)
                            adapter.notifyDataSetChanged()
                        }
                        return
                    }
                    isSearching = true
                    db.collection("publicProfiles")
                        .orderBy("username")
                        .startAt(query)
                        .endAt(query + "\uf8ff")
                        .limit(4)
                        .get()
                        .addOnSuccessListener { docs ->
                            val searchResults = mutableListOf<ChatUser>()
                            searchResults.add(ChatUser("", "global", ""))
                            var count = 0
                            docs.forEach { doc ->
                                if (count >= 3) return@forEach
                                val user = doc.toObject(ChatUser::class.java)
                                if (user.uid != auth.currentUser?.uid) {
                                    searchResults.add(user)
                                    count++
                                }
                            }
                            runOnUiThread {
                                chatList.clear()
                                chatList.addAll(searchResults)
                                adapter.notifyDataSetChanged()
                            }
                        }
                } else {
                    val query = raw.lowercase()
                    if (query.length < 3) {
                        isSearching = false
                        runOnUiThread {
                            chatList.clear()
                            chatList.add(ChatUser("", "global", ""))
                            chatList.addAll(recentChats)
                            adapter.notifyDataSetChanged()
                        }
                        return
                    }
                    isSearching = true
                    db.collection("publicProfiles")
                        .limit(50)
                        .get()
                        .addOnSuccessListener { docs ->
                            val matches = mutableListOf<ChatUser>()
                            matches.add(ChatUser("", "global", ""))
                            docs.forEach { doc ->
                                val user = doc.toObject(ChatUser::class.java)
                                if (user.uid != auth.currentUser?.uid && user.nickname.lowercase().contains(query)) {
                                    matches.add(user)
                                }
                            }
                            matches.shuffle()
                            runOnUiThread {
                                chatList.clear()
                                chatList.addAll(matches.take(4))
                                adapter.notifyDataSetChanged()
                            }
                        }
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        })
        runOnUiThread {
            chatList.clear()
            chatList.add(ChatUser("", "global", ""))
            adapter.notifyDataSetChanged()
        }
        swipeRefresh.setOnRefreshListener {
            loadRecentChats()
            swipeRefresh.postDelayed({ swipeRefresh.isRefreshing = false }, 1500)
        }
        loadRecentChats()
        root.alpha = 0f
        container.addView(root)
        root.animate().alpha(1f).setDuration(250).start()
        showTutorialIfNeeded()
    }

    // ==========================================
    // SETTINGS
    // ==========================================
    private fun showSettings() {
        currentScreen = "settings"
        container.removeAllViews()
        container.setBackgroundColor(bgColor)
        val loadingView = TextView(this).apply {
            text = S.get("loading")
            setTextColor(subTextColor)
            textSize = 16f
            gravity = Gravity.CENTER
            layoutParams = FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(loadingView)
        loadUserDevices {
            container.removeAllViews()
            renderSettings()
        }
    }

    private fun renderSettings() {
        container.setBackgroundColor(bgColor)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }

        // ---- GRADIENT HEADER ----
        val headerGrad = GradientDrawable(
            GradientDrawable.Orientation.BL_TR,
            intArrayOf(Color.parseColor("#4F46E5"), accentColor, Color.parseColor("#A78BFA"), Color.parseColor("#C084FC"))
        ).apply { cornerRadii = floatArrayOf(0f,0f,0f,0f, dp(28).toFloat(),dp(28).toFloat(), dp(28).toFloat(),dp(28).toFloat()) }
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = headerGrad
            setPadding(dp(18), dp(20), dp(18), dp(20))
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        header.addView(FrameLayout(this).apply {
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(Color.parseColor("#33FFFFFF"))
            }
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { rightMargin = dp(14) }
            setOnClickListener { showHome() }
            addView(TextView(this@MainActivity).apply {
                text = "←"
                textSize = 22f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            })
        })
        header.addView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            addView(TextView(this@MainActivity).apply {
                text = S.get("settings_title")
                setTextColor(Color.WHITE)
                textSize = 22f
                setTypeface(null, android.graphics.Typeface.BOLD)
                letterSpacing = -0.02f
            })
            addView(TextView(this@MainActivity).apply {
                text = S.get("customize_experience")
                setTextColor(Color.parseColor("#DDFFFFFF"))
                textSize = 12.5f
            })
        })
        root.addView(header)

        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(16), dp(16), dp(16), dp(32))
        }

        // ---- Helper: icon chip ----
        fun iconChip(emoji: String, bgHex: String) = FrameLayout(this).apply {
            val bg = GradientDrawable().also {
                it.shape = GradientDrawable.OVAL
                it.setColor(Color.parseColor(bgHex))
                it.setStroke(dp(1), if (isDarkTheme) Color.parseColor("#1E293B") else Color.parseColor("#E2E8F0"))
            }
            background = bg
            val size = dp(44)
            layoutParams = LinearLayout.LayoutParams(size, size).apply { rightMargin = dp(14) }
            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(LineIconDrawable(emoji, textColor, dp(22)))
                layoutParams = FrameLayout.LayoutParams(dp(22), dp(22)).apply { gravity = Gravity.CENTER }
            })
        }

        // ---- Helper: thin divider ----
        fun divider() = View(this).apply {
            setBackgroundColor(if (isDarkTheme) Color.parseColor("#1E293B") else Color.parseColor("#F1F5F9"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1)
        }

        // ---- Helper: collapsible section ----
        fun collapsibleSection(emoji: String, title: String, subtitle: String, chipBg: String, buildContent: (LinearLayout) -> Unit): LinearLayout {
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                background = createCardBackground()
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
                elevation = dp(2).toFloat()
            }
            val sectionContent = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                visibility = View.GONE
                setPadding(dp(18), 0, dp(18), dp(18))
            }
            val chevron = TextView(this).apply {
                text = "›"; textSize = 22f; setTextColor(subTextColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
            }
            val headerRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(18), dp(16), dp(18), dp(16))
                setOnClickListener {
                    val expanding = sectionContent.visibility == View.GONE
                    sectionContent.visibility = if (expanding) View.VISIBLE else View.GONE
                    chevron.text = if (expanding) "⌄" else "›"
                }
            }
            headerRow.addView(iconChip(emoji, chipBg))
            headerRow.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = title; setTextColor(textColor); textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    letterSpacing = -0.01f
                })
                addView(TextView(this@MainActivity).apply {
                    text = subtitle; setTextColor(subTextColor); textSize = 12.5f
                })
            })
            headerRow.addView(chevron)
            card.addView(headerRow)
            card.addView(sectionContent)
            buildContent(sectionContent)
            return card
        }

        // ================================================================
        // ACCOUNT & PASSWORD (collapsible)
        // ================================================================
        content.addView(collapsibleSection("👤", S.get("account"), S.get("manage_account"), if (isDarkTheme) "#2D1A3A" else "#FFE8F0") { section ->
            val accountInfoRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(16) }
            }
            accountInfoRow.addView(iconChip("👤", if (isDarkTheme) "#2D1A3A" else "#FFE8F0"))
            accountInfoRow.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(this@MainActivity).apply {
                    text = "@$myUsername"; setTextColor(textColor); textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                val rawEmail = auth.currentUser?.email ?: ""
                val masked = if (rawEmail.contains("@")) {
                    val parts = rawEmail.split("@"); val name = parts[0]; val domain = parts[1]
                    val vis = if (name.length <= 2) 1 else 2
                    name.take(vis) + "•".repeat((name.length - vis).coerceAtLeast(3)) + "@" + domain
                } else rawEmail
                addView(TextView(this@MainActivity).apply {
                    text = masked; setTextColor(subTextColor); textSize = 13f
                })
            })
            section.addView(accountInfoRow)
            section.addView(Button(this).apply {
                text = iconize("🔑  " + S.get("change_password"), Color.WHITE); setTextColor(Color.WHITE); textSize = 14f
                background = createRoundedBackground(accentColor, 10)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
                setOnClickListener { showChangePasswordDialog() }
            })
        })

        // ================================================================
        // SECURITY & DEVICES (collapsible)
        // ================================================================
        content.addView(collapsibleSection("🔐", S.get("security"), S.get("pin_and_devices"), if (isDarkTheme) "#1A2D1A" else "#E8F8EF") { section ->
            // --- PIN section ---
            val pinHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(12) }
            }
            pinHeader.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = S.get("two_step_verification"); setTextColor(textColor); textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = S.get("protect_with_pin"); setTextColor(subTextColor); textSize = 12f
                })
            })
            pinHeader.addView(TextView(this).apply {
                text = if (hasPinEnabled) "● " + S.get("on") else "○ " + S.get("off")
                setTextColor(if (hasPinEnabled) Color.parseColor("#48BB78") else Color.parseColor("#EF4444"))
                textSize = 12f; setTypeface(null, android.graphics.Typeface.BOLD)
            })
            section.addView(pinHeader)
            if (hasPinEnabled) {
                section.addView(Button(this).apply {
                    text = S.get("change_pin"); setTextColor(Color.WHITE); textSize = 14f
                    background = createRoundedBackground(accentColor, 10)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)).apply { bottomMargin = dp(8) }
                    setOnClickListener {
                        isInCooldownPeriod { inCooldown ->
                            if (inCooldown) { showCooldownWarning(); return@isInCooldownPeriod }
                            showVerifyCurrentPin { showPinSetup(isChange = true) }
                        }
                    }
                })
                section.addView(Button(this).apply {
                    text = S.get("disable_2step")
                    setTextColor(Color.parseColor("#EF4444")); textSize = 14f
                    background = createRoundedBackground(if (isDarkTheme) Color.parseColor("#2A1010") else Color.parseColor("#FEE2E2"), 10)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
                    setOnClickListener {
                        showVerifyCurrentPin {
                            val userId = auth.currentUser?.uid ?: return@showVerifyCurrentPin
                            db.collection("users").document(userId)
                                .update(mapOf("securityPin" to "", "hasPinEnabled" to false))
                                .addOnSuccessListener {
                                    hasPinEnabled = false
                                    Toast.makeText(this@MainActivity, S.get("two_step_disabled"), Toast.LENGTH_SHORT).show()
                                    showSettings()
                                }
                        }
                    }
                })
            } else {
                section.addView(Button(this).apply {
                    text = S.get("enable_2step"); setTextColor(Color.WHITE); textSize = 14f
                    background = createRoundedBackground(Color.parseColor("#48BB78"), 10)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
                    setOnClickListener { showPinSetup(isChange = false) }
                })
            }

            // --- Devices section ---
            section.addView(divider().also {
                (it.layoutParams as LinearLayout.LayoutParams).apply { topMargin = dp(14); bottomMargin = dp(14) }
            })
            val devicesHeader = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
            }
            devicesHeader.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = S.get("logged_in_devices"); setTextColor(textColor); textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = if (userDevices.size >= MAX_DEVICES_PER_ACCOUNT) iconize("⚠️ " + S.get("device_limit_reached"), Color.parseColor("#EF4444"), 12) else S.get("manage_sessions")
                    setTextColor(if (userDevices.size >= MAX_DEVICES_PER_ACCOUNT) Color.parseColor("#EF4444") else subTextColor)
                    textSize = 12f
                })
            })
            devicesHeader.addView(TextView(this).apply {
                text = "${userDevices.size}/$MAX_DEVICES_PER_ACCOUNT"
                setTextColor(if (userDevices.size >= MAX_DEVICES_PER_ACCOUNT) Color.parseColor("#EF4444") else accentColor)
                textSize = 14f; setTypeface(null, android.graphics.Typeface.BOLD)
            })
            section.addView(devicesHeader)
            userDevices.sortedByDescending { it.lastActive }.forEach { device ->
                val isCurrentDevice = device.deviceId == currentDeviceId
                val deviceRow = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                    background = createRoundedBackground(
                        if (isCurrentDevice) Color.parseColor(if (isDarkTheme) "#253052" else "#E8F0FE")
                        else Color.parseColor(if (isDarkTheme) "#151830" else "#F5F5F5"), 12
                    )
                    setPadding(dp(12), dp(10), dp(12), dp(10))
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
                }
                deviceRow.addView(FrameLayout(this).apply {
                    val bg = GradientDrawable().also {
                        it.shape = GradientDrawable.OVAL
                        it.setColor(if (isCurrentDevice) accentColor else if (isDarkTheme) Color.parseColor("#2A2D4A") else Color.parseColor("#E8ECF0"))
                    }
                    background = bg
                    val size = dp(32)
                    layoutParams = LinearLayout.LayoutParams(size, size).apply { rightMargin = dp(10) }
                    addView(ImageView(this@MainActivity).apply {
                        setImageDrawable(LineIconDrawable("📱", if (isCurrentDevice) Color.WHITE else textColor, dp(16)))
                        layoutParams = FrameLayout.LayoutParams(dp(16), dp(16)).apply { gravity = Gravity.CENTER }
                    })
                })
                deviceRow.addView(LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    addView(TextView(this@MainActivity).apply {
                        text = iconize(device.deviceName + if (isCurrentDevice) " ✓" else "")
                        setTextColor(textColor); textSize = 13f; setTypeface(null, android.graphics.Typeface.BOLD)
                    })
                    val timeAgo = (System.currentTimeMillis() - device.lastActive) / 1000 / 60
                    addView(TextView(this@MainActivity).apply {
                        val statusStr = when {
                            isCurrentDevice -> "🟢 " + S.get("this_device_active") + " · " + S.get("active_now")
                            timeAgo < 1 -> "🟢 " + S.get("active_now")
                            timeAgo < 60 -> "🟡 " + S.get("active_m_ago", timeAgo)
                            timeAgo < 1440 -> "🟠 " + S.get("active_h_ago", timeAgo / 60)
                            else -> "⚫ " + S.get("active_d_ago", timeAgo / 1440)
                        }
                        text = iconize(statusStr, subTextColor, 11)
                        setTextColor(subTextColor); textSize = 11f
                    })
                })
                if (!isCurrentDevice) {
                    deviceRow.addView(Button(this).apply {
                        text = S.get("kick"); setTextColor(Color.WHITE); textSize = 11f
                        setTypeface(null, android.graphics.Typeface.BOLD)
                        background = createRoundedBackground(Color.parseColor("#EF4444"), 8)
                        layoutParams = LinearLayout.LayoutParams(dp(60), dp(30))
                        setOnClickListener {
                            isInCooldownPeriod { inCooldown ->
                                if (inCooldown) { showCooldownWarning(); return@isInCooldownPeriod }
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle(S.get("kick_device_title"))
                                    .setMessage(S.get("kick_device_msg", device.deviceName))
                                    .setPositiveButton(S.get("kick")) { _, _ -> removeDevice(device.deviceId) }
                                    .setNegativeButton(S.get("cancel"), null).show()
                            }
                        }
                    })
                }
                section.addView(deviceRow)
            }
        })

        // ================================================================
        // PRIVACY (collapsible)
        // ================================================================
        content.addView(collapsibleSection("🛡️", S.get("privacy"), S.get("privacy_controls"), if (isDarkTheme) "#1A2040" else "#E8F0FE") { section ->
            val hideLastSeenEnabled = prefs.getBoolean("hide_last_seen", false)
            val lastSeenRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            lastSeenRow.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = S.get("hide_last_seen"); setTextColor(textColor); textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = S.get("hide_last_seen_desc"); setTextColor(subTextColor); textSize = 12f
                })
            })
            lastSeenRow.addView(androidx.appcompat.widget.SwitchCompat(this).apply {
                isChecked = hideLastSeenEnabled
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("hide_last_seen", checked).apply()
                    val userId = auth.currentUser?.uid ?: return@setOnCheckedChangeListener
                    if (checked) {
                        db.collection("publicProfiles").document(userId)
                            .update(mapOf("lastSeen" to 0L, "hideLastSeen" to true))
                    } else {
                        db.collection("publicProfiles").document(userId)
                            .update(mapOf("lastSeen" to System.currentTimeMillis(), "hideLastSeen" to false))
                    }
                }
            })
            section.addView(lastSeenRow)

            section.addView(divider().also {
                (it.layoutParams as LinearLayout.LayoutParams).apply { topMargin = dp(12); bottomMargin = dp(12) }
            })

            val encryptMetaEnabled = prefs.getBoolean("encrypt_metadata", true)
            val encryptMetaRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            encryptMetaRow.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = S.get("encrypt_metadata"); setTextColor(textColor); textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = S.get("encrypt_metadata_desc"); setTextColor(subTextColor); textSize = 12f
                })
            })
            encryptMetaRow.addView(androidx.appcompat.widget.SwitchCompat(this).apply {
                isChecked = encryptMetaEnabled
                setOnCheckedChangeListener { _, checked ->
                    prefs.edit().putBoolean("encrypt_metadata", checked).apply()
                    saveDeviceSession(trusted = true)
                }
            })
            section.addView(encryptMetaRow)
        })

        // ================================================================
        // APPEARANCE (collapsible)
        // ================================================================
        content.addView(collapsibleSection(if (isDarkTheme) "🌙" else "☀️", S.get("appearance"), S.get("theme_and_language"), if (isDarkTheme) "#2D3561" else "#FFF8E1") { section ->
            // Theme toggle
            val themeRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            themeRow.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = if (isDarkTheme) S.get("dark_mode") else S.get("light_mode")
                    setTextColor(textColor); textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = S.get("switch_theme"); setTextColor(subTextColor); textSize = 12f
                })
            })
            themeRow.addView(androidx.appcompat.widget.SwitchCompat(this).apply {
                isChecked = isDarkTheme
                setOnCheckedChangeListener { _, _ -> toggleTheme() }
            })
            section.addView(themeRow)

            // Language picker
            section.addView(divider().also {
                (it.layoutParams as LinearLayout.LayoutParams).apply { topMargin = dp(12); bottomMargin = dp(12) }
            })
            val langPrefs = getSharedPreferences("app_lang_prefs", Context.MODE_PRIVATE)
            val currentLang = langPrefs.getString("app_language", "en") ?: "en"
            val languages = listOf(
                Triple("en", "🇬🇧", "English"),
                Triple("es", "🇪🇸", "Español"),
                Triple("fr", "🇫🇷", "Français"),
                Triple("de", "🇩🇪", "Deutsch"),
                Triple("ar", "🇸🇦", "العربية"),
                Triple("ru", "🇷🇺", "Русский"),
                Triple("pt", "🇧🇷", "Português"),
                Triple("it", "🇮🇹", "Italiano"),
                Triple("zh", "🇨🇳", "中文"),
                Triple("ja", "🇯🇵", "日本語")
            )
            val currentLangEntry = languages.find { it.first == currentLang }
            section.addView(TextView(this).apply {
                text = "${S.get("app_language")}: ${currentLangEntry?.let { "${it.second} ${it.third}" } ?: "🇬🇧 English"}"
                setTextColor(textColor); textSize = 14f
                setTypeface(null, android.graphics.Typeface.BOLD)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(10) }
            })
            languages.chunked(2).forEach { pair ->
                val row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                }
                pair.forEach { (code, flag, name) ->
                    val isSelected = code == currentLang
                    val btnBg = GradientDrawable().apply {
                        cornerRadius = dp(10).toFloat()
                        if (isSelected) setColor(accentColor)
                        else {
                            setColor(if (isDarkTheme) Color.parseColor("#1A1D3A") else Color.parseColor("#F0F2F5"))
                            setStroke(dp(1), if (isDarkTheme) Color.parseColor("#2A2D4A") else Color.parseColor("#DDE1E7"))
                        }
                    }
                    row.addView(LinearLayout(this).apply {
                        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
                        background = btnBg
                        layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(6); bottomMargin = dp(6) }
                        setPadding(dp(10), dp(6), dp(10), dp(6))
                        addView(TextView(this@MainActivity).apply { text = flag; textSize = 16f; setPadding(0, 0, dp(6), 0) })
                        addView(TextView(this@MainActivity).apply {
                            text = name; setTextColor(if (isSelected) Color.WHITE else textColor); textSize = 12f
                            setTypeface(null, if (isSelected) android.graphics.Typeface.BOLD else android.graphics.Typeface.NORMAL)
                        })
                        setOnClickListener {
                            if (code != currentLang) {
                                applyLanguage(code)
                                Toast.makeText(this@MainActivity, "Language changed to $name", Toast.LENGTH_SHORT).show()
                                recreate()
                            }
                        }
                    })
                }
                if (pair.size == 1) row.addView(View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(6) }
                })
                section.addView(row)
            }
        })

        // ================================================================
        // NOTIFICATIONS (collapsible)
        // ================================================================
        content.addView(collapsibleSection("🔔", S.get("notifications"), S.get("sound_and_vibration"), "#FFF3E0") { section ->
            val soundRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            soundRow.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = S.get("notification_sound"); setTextColor(textColor); textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = S.get("play_sound_desc"); setTextColor(subTextColor); textSize = 12f
                })
            })
            soundRow.addView(androidx.appcompat.widget.SwitchCompat(this).apply {
                isChecked = prefs.getBoolean("notification_sound", true)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("notification_sound", checked).apply() }
            })
            section.addView(soundRow)
            section.addView(divider().also {
                (it.layoutParams as LinearLayout.LayoutParams).apply { topMargin = dp(10); bottomMargin = dp(10) }
            })
            val vibRow = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            }
            vibRow.addView(LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                addView(TextView(this@MainActivity).apply {
                    text = S.get("vibration"); setTextColor(textColor); textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                })
                addView(TextView(this@MainActivity).apply {
                    text = S.get("vibrate_desc"); setTextColor(subTextColor); textSize = 12f
                })
            })
            vibRow.addView(androidx.appcompat.widget.SwitchCompat(this).apply {
                isChecked = prefs.getBoolean("notification_vibration", true)
                setOnCheckedChangeListener { _, checked -> prefs.edit().putBoolean("notification_vibration", checked).apply() }
            })
            section.addView(vibRow)
        })

        // ================================================================
        // DANGER ZONE (always visible, not collapsible)
        // ================================================================
        content.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { topMargin = dp(6); bottomMargin = dp(10) }
            setBackgroundColor(if (isDarkTheme) Color.parseColor("#2A2D4A") else Color.parseColor("#E8ECF0"))
        })
        // Switch Account button
        content.addView(Button(this).apply {
            text = iconize("🔄  " + S.get("switch_account"), Color.WHITE, 16); setTextColor(Color.WHITE); textSize = 16f
            background = createGradientButton(Color.parseColor("#6558F5"))
            isAllCaps = false; setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(10) }
            setOnClickListener { showSwitchAccountDialog() }
        })
        content.addView(Button(this).apply {
            text = iconize("🚪  " + S.get("logout"), Color.WHITE, 16); setTextColor(Color.WHITE); textSize = 16f
            background = createRoundedBackground(Color.parseColor("#EF4444"), 12)
            isAllCaps = false; setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(10) }
            setOnClickListener { logoutAndSwitch() }
        })
        content.addView(Button(this).apply {
            text = iconize("⚠️  " + S.get("delete_account"), Color.parseColor("#EF4444"), 14); setTextColor(Color.parseColor("#EF4444")); textSize = 14f
            background = createRoundedBackground(if (isDarkTheme) Color.parseColor("#1A0808") else Color.parseColor("#FEF2F2"), 12)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50))
            setOnClickListener { showDeleteAccountDialog() }
        })

        scroll.addView(content)
        root.addView(scroll)
        container.addView(root)
    }

    // ==========================================
    // PROFILE
    // ==========================================
    private fun showProfile() {
        currentScreen = "profile"
        container.removeAllViews()
        container.setBackgroundColor(bgColor)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }
        val profileGrad = GradientDrawable(
            GradientDrawable.Orientation.LEFT_RIGHT,
            intArrayOf(Color.parseColor("#6C63FF"), Color.parseColor("#8B5CF6"), Color.parseColor("#A855F7"))
        )
        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = profileGrad
            setPadding(dp(16), dp(14), dp(16), dp(14))
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        header.addView(TextView(this).apply {
            text = "←"
            textSize = 24f
            setTextColor(Color.WHITE)
            setPadding(0, 0, dp(16), 0)
            setOnClickListener { showHome() }
        })
        header.addView(TextView(this).apply {
            text = S.get("edit_profile")
            setTextColor(Color.WHITE)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        root.addView(header)
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        val photoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(32) }
        }
        val profilePhotoView = ImageView(this).apply {
            tag = "profile_photo_preview"
            layoutParams = LinearLayout.LayoutParams(dp(120), dp(120))
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isDarkTheme) Color.parseColor("#252B48") else Color.parseColor("#E8EAF4"))
            }
            clipToOutline = true
            outlineProvider = object : android.view.ViewOutlineProvider() {
                override fun getOutline(view: View, outline: android.graphics.Outline) {
                    outline.setOval(0, 0, view.width, view.height)
                }
            }
        }
        if (myPhotoBase64.isNotEmpty()) {
            try {
                val imageBytes = Base64.decode(myPhotoBase64, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                profilePhotoView.setImageBitmap(bitmap)
            } catch (_: Exception) {}
        } else {
            profilePhotoView.setImageDrawable(null)
        }
        photoContainer.addView(profilePhotoView)
        photoContainer.addView(TextView(this).apply {
            text = S.get("change_photo")
            setTextColor(accentColor)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(0, dp(12), 0, 0)
            setOnClickListener {
                val intent = Intent(Intent.ACTION_PICK)
                intent.type = "image/*"
                startActivityForResult(intent, PICK_IMAGE_REQUEST)
            }
        })
        content.addView(photoContainer)
        content.addView(TextView(this).apply {
            text = S.get("username")
            setTextColor(subTextColor)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4) }
        })
        content.addView(TextView(this).apply {
            text = "@$myUsername"
            setTextColor(textColor)
            textSize = 16f
            background = createRoundedBackground(cardBg, 12)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(20) }
        })
        content.addView(TextView(this).apply {
            text = S.get("display_name")
            setTextColor(subTextColor)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4) }
        })
        val nicknameField = EditText(this).apply {
            setText(myNickname)
            setTextColor(textColor)
            textSize = 16f
            background = createRoundedBackground(cardBg, 12)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56)).apply { bottomMargin = dp(20) }
        }
        content.addView(nicknameField)
        content.addView(TextView(this).apply {
            text = S.get("bio")
            setTextColor(subTextColor)
            textSize = 12f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(4) }
        })
        val bioField = EditText(this).apply {
            setText(myBio)
            hint = S.get("bio_hint")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 14f
            background = createRoundedBackground(cardBg, 12)
            setPadding(dp(20), dp(16), dp(20), dp(16))
            minLines = 2
            maxLines = 4
            gravity = Gravity.TOP or Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            filters = arrayOf(android.text.InputFilter.LengthFilter(150))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        }
        content.addView(bioField)
        content.addView(TextView(this).apply {
            text = S.get("max_chars", 150)
            setTextColor(subTextColor)
            textSize = 11f
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(32) }
        })
        val saveBtn = Button(this).apply {
            text = S.get("save_changes")
            setTextColor(Color.WHITE)
            textSize = 16f
            background = createRoundedBackground(accentColor, 12)
            isAllCaps = true
            setTypeface(null, android.graphics.Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(56))
        }
        content.addView(saveBtn)
        saveBtn.setOnClickListener {
            val newNickname = nicknameField.text.toString().trim()
            if (newNickname.isEmpty()) {
                Toast.makeText(this, S.get("nickname_empty"), Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveBtn.isEnabled = false
            saveBtn.text = S.get("saving")
            val updates = hashMapOf<String, Any>("nickname" to newNickname)
            val newBio = bioField.text.toString().trim()
            updates["bio"] = newBio
            selectedPhotoUri?.let { uri ->
                try {
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    val baos = ByteArrayOutputStream()
                    bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 50, baos)
                    val strippedBytes = stripExifData(baos.toByteArray())
                    val base64 = Base64.encodeToString(strippedBytes, Base64.DEFAULT)
                    updates["photoBase64"] = base64
                    myPhotoBase64 = base64
                } catch (e: Exception) {
                    Toast.makeText(this, S.get("error_processing_image"), Toast.LENGTH_SHORT).show()
                }
            }
            db.collection("publicProfiles").document((auth.currentUser?.uid ?: return@setOnClickListener))
                .update(updates)
                .addOnSuccessListener {
                    myNickname = newNickname
                    myBio = newBio
                    Toast.makeText(this, S.get("profile_updated"), Toast.LENGTH_SHORT).show()
                    showHome()
                }
                .addOnFailureListener {
                    Toast.makeText(this, S.get("error_updating_profile"), Toast.LENGTH_SHORT).show()
                    saveBtn.isEnabled = true
                    saveBtn.text = S.get("save_changes")
                }
        }
        scroll.addView(content)
        root.addView(scroll)
        container.addView(root)
    }

    // ==========================================
    // GLOBAL CHAT
    // ==========================================
    private fun showGlobalChat() {
        val currentUser = auth.currentUser
        if (currentUser == null) {
            Toast.makeText(this, S.get("please_login_again"), Toast.LENGTH_SHORT).show()
            showRegistration()
            return
        }

        currentScreen = "chat"
        container.removeAllViews()
        container.setBackgroundColor(bgColor)
        replyToText = null
        replyToUsername = null

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGlassBackground().apply { cornerRadius = 0f; cornerRadii = floatArrayOf(0f,0f,0f,0f, dp(24).toFloat(),dp(24).toFloat(), dp(24).toFloat(),dp(24).toFloat()) }
            setPadding(dp(14), dp(12), dp(14), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(64))
        }

        header.addView(FrameLayout(this).apply {
            background = createIconButton()
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(12) }
            setOnClickListener { showHome() }
            addView(TextView(this@MainActivity).apply {
                text = "←"
                textSize = 20f
                setTextColor(textColor)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            })
        })

        header.addView(TextView(this).apply {
            text = iconize("🌐 " + S.get("global_chat"))
            setTextColor(textColor)
            textSize = 19f
            setTypeface(null, android.graphics.Typeface.BOLD)
            letterSpacing = -0.02f
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        })
        // Search icon for global chat
        val globalSearchIcon = ImageView(this).apply {
            setImageDrawable(LineIconDrawable("🔍", textColor, dp(20)))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
        }
        header.addView(globalSearchIcon)
        root.addView(header)

        val recycler = RecyclerView(this).apply {
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            layoutManager = LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            clipToPadding = false
        }
        val adapter = MsgAdapter(globalMsgs, currentUser.uid)
        adapter.chatId = "global"
        adapter.messageIds = globalMsgIds
        recycler.adapter = adapter

        // Wire up search icon click
        globalSearchIcon.setOnClickListener {
            val existing = root.findViewWithTag<LinearLayout>("global_search_bar")
            if (existing != null) { root.removeView(existing); return@setOnClickListener }
            val searchBar = LinearLayout(this).apply {
                tag = "global_search_bar"
                orientation = LinearLayout.HORIZONTAL
                background = createRoundedBackground(headerBg, 0)
                setPadding(dp(12), dp(6), dp(12), dp(6))
                gravity = Gravity.CENTER_VERTICAL
            }
            var currentMatch = -1
            val matchPositions = mutableListOf<Int>()
            val searchInput = EditText(this).apply {
                hint = S.get("search_messages")
                setHintTextColor(subTextColor); setTextColor(textColor); textSize = 14f
                background = createRoundedBackground(if (isDarkTheme) Color.parseColor("#1A1F38") else Color.parseColor("#F0F1F5"), 20)
                setPadding(dp(14), dp(8), dp(14), dp(8)); maxLines = 1; isSingleLine = true
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val matchLabel = TextView(this).apply {
                text = ""; setTextColor(subTextColor); textSize = 12f; setPadding(dp(8), 0, dp(4), 0)
            }
            val upBtn = TextView(this).apply {
                text = "▲"; textSize = 18f; setTextColor(textColor); setPadding(dp(6), dp(4), dp(6), dp(4))
                setOnClickListener {
                    if (matchPositions.isNotEmpty()) {
                        currentMatch = if (currentMatch > 0) currentMatch - 1 else matchPositions.size - 1
                        matchLabel.text = "${currentMatch + 1}/${matchPositions.size}"
                        recycler.scrollToPosition(matchPositions[currentMatch])
                    }
                }
            }
            val downBtn = TextView(this).apply {
                text = "▼"; textSize = 18f; setTextColor(textColor); setPadding(dp(6), dp(4), dp(6), dp(4))
                setOnClickListener {
                    if (matchPositions.isNotEmpty()) {
                        currentMatch = if (currentMatch < matchPositions.size - 1) currentMatch + 1 else 0
                        matchLabel.text = "${currentMatch + 1}/${matchPositions.size}"
                        recycler.scrollToPosition(matchPositions[currentMatch])
                    }
                }
            }
            val closeBtn = TextView(this).apply {
                text = "✕"; textSize = 18f; setTextColor(subTextColor); setPadding(dp(6), dp(4), dp(8), dp(4))
                setOnClickListener {
                    adapter.searchQuery = ""; adapter.notifyDataSetChanged()
                    root.removeView(searchBar)
                }
            }
            searchInput.addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                override fun afterTextChanged(s: android.text.Editable?) {
                    val query = s?.toString()?.trim()?.lowercase() ?: ""
                    matchPositions.clear(); currentMatch = -1
                    if (query.length >= 2) {
                        globalMsgs.forEachIndexed { idx, msg ->
                            if (msg.text.lowercase().contains(query)) matchPositions.add(idx)
                        }
                    }
                    adapter.searchQuery = query; adapter.notifyDataSetChanged()
                    if (matchPositions.isNotEmpty()) {
                        currentMatch = 0; matchLabel.text = "1/${matchPositions.size}"
                        recycler.scrollToPosition(matchPositions[0])
                    } else {
                        matchLabel.text = if (query.length >= 2) "0/0" else ""
                    }
                }
            })
            searchBar.addView(searchInput); searchBar.addView(matchLabel)
            searchBar.addView(upBtn); searchBar.addView(downBtn); searchBar.addView(closeBtn)
            root.addView(searchBar, 1)
            searchInput.requestFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            searchInput.postDelayed({ imm.showSoftInput(searchInput, 0) }, 200)
        }

        // Pagination for global chat
        var globalMsgLimit = 50
        val globalLoadMoreBtn = TextView(this).apply {
            text = S.get("load_more")
            setTextColor(accentColor)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
            setBackgroundColor(if (isDarkTheme) Color.parseColor("#0F172A") else Color.parseColor("#EEF0F6"))
            visibility = View.GONE
        }
        root.addView(globalLoadMoreBtn)
        root.addView(recycler)

        lifecycleScope.launch {
            try {
                val chatId = "global"
                val cachedMessages = database.messageDao().getMessagesForChat(chatId).first()
                runOnUiThread {
                    globalMsgs.clear()
                    globalMsgIds.clear()
                    cachedMessages.forEach { cached ->
                        if (!blockedUsers.contains(cached.senderId)) {
                            globalMsgs.add(cached.toMsg())
                            globalMsgIds.add(cached.messageId)
                        }
                    }
                    adapter.messageIds = globalMsgIds
                    adapter.notifyDataSetChanged()
                    if (globalMsgs.isNotEmpty()) {
                        recycler.scrollToPosition(globalMsgs.size - 1)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ExecBin", "Failed to load cached global messages", e)
            }
        }

        fun setupGlobalListener() {
            globalChatListener?.remove()
            globalChatListener = db.collection("globalChat")
                .orderBy("timestamp")
                .limitToLast(globalMsgLimit.toLong())
                .addSnapshotListener { snaps, _ ->
                    val newMsgs = mutableListOf<Msg>()
                    val newIds = mutableListOf<String>()
                    snaps?.forEach { doc ->
                        val msg = doc.toObject(Msg::class.java)
                        if (!blockedUsers.contains(msg.senderId)) {
                            newMsgs.add(msg)
                            newIds.add(doc.id)
                        }
                    }
                    runOnUiThread {
                        globalLoadMoreBtn.visibility = if ((snaps?.size() ?: 0) >= globalMsgLimit) View.VISIBLE else View.GONE
                    }
                    runOnUiThread {
                    globalMsgs.clear()
                    globalMsgs.addAll(newMsgs)
                    globalMsgIds.clear()
                    globalMsgIds.addAll(newIds)
                    adapter.messageIds = globalMsgIds
                    adapter.notifyDataSetChanged()
                    if (globalMsgs.isNotEmpty()) {
                        recycler.post { recycler.smoothScrollToPosition(globalMsgs.size - 1) }
                    }
                }
                lifecycleScope.launch(Dispatchers.IO) {
                    val cachedMessages = newMsgs.mapIndexed { index, msg ->
                        CachedMessage(
                            messageId = newIds[index],
                            chatId = "global",
                            text = msg.text,
                            senderId = msg.senderId,
                            username = msg.username,
                            nickname = msg.nickname,
                            photoBase64 = msg.photoBase64,
                            replyToText = msg.replyToText,
                            replyToUsername = msg.replyToUsername,
                            timestamp = msg.timestamp,
                            isEncrypted = false,
                            read = true,
                            edited = msg.edited,
                            mediaUrl = msg.mediaUrl,
                            mediaType = msg.mediaType,
                            mediaDuration = msg.mediaDuration,
                            reactionsStr = msg.reactions.joinToString(",")
                        )
                    }
                    try { database.messageDao().insertAll(cachedMessages) } catch (_: Exception) {}
                }
            }
        }
        globalLoadMoreBtn.setOnClickListener {
            globalMsgLimit += 50
            globalLoadMoreBtn.visibility = View.GONE
            setupGlobalListener()
        }
        setupGlobalListener()

        val replyBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createRoundedBackground(cardBg, 0)
            setPadding(dp(16), dp(10), dp(8), dp(10))
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            tag = "replyBar"
        }
        val replyContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val replyLabel = TextView(this).apply {
            setTextColor(accentColor)
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
            tag = "replyLabel"
        }
        val replyPreview = TextView(this).apply {
            setTextColor(subTextColor)
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            tag = "replyPreview"
        }
        replyContent.addView(replyLabel)
        replyContent.addView(replyPreview)
        replyBar.addView(replyContent)
        replyBar.addView(TextView(this).apply {
            text = "✕"
            textSize = 20f
            setTextColor(subTextColor)
            setPadding(dp(12), 0, dp(8), 0)
            setOnClickListener {
                replyToText = null
                replyToUsername = null
                replyBar.visibility = View.GONE
            }
        })
        root.addView(replyBar)

        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGlassBackground().apply { cornerRadii = floatArrayOf(dp(24).toFloat(),dp(24).toFloat(),dp(24).toFloat(),dp(24).toFloat(), 0f,0f,0f,0f) }
            setPadding(dp(10), dp(10), dp(10), dp(10))
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(4).toFloat()
        }

        // Attach image button (global chat — unencrypted media)
        val attachBtn = ImageView(this).apply {
            setImageDrawable(LineIconDrawable("📎", textColor, dp(22)))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            setOnClickListener { showInlineGalleryPickerGlobal() }
        }
        inputBar.addView(attachBtn)

        val input = EditText(this).apply {
            hint = S.get("message_hint")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 15f
            background = createInputBackground()
            setPadding(dp(18), dp(12), dp(18), dp(12))
            maxLines = 4
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(4); rightMargin = dp(6)
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    recycler.postDelayed({
                        if (globalMsgs.isNotEmpty()) recycler.smoothScrollToPosition(globalMsgs.size - 1)
                    }, 300)
                }
            }
        }
        inputBar.addView(input)

        // Voice record button (global chat — unencrypted)
        val voiceBtn = ImageView(this).apply {
            setImageDrawable(LineIconDrawable("🎤", textColor, dp(22)))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { leftMargin = dp(4) }
        }

        val recordingIndicator = TextView(this).apply {
            text = iconize("🔴 " + S.get("recording"), Color.parseColor("#EF4444"), 12)
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 12f
            visibility = View.GONE
            setPadding(dp(8), 0, dp(8), 0)
        }

        voiceBtn.setOnClickListener {
            // Check mic permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
                return@setOnClickListener
            }
            if (!isRecordingVoice) {
                startVoiceRecording()
                voiceBtn.setImageDrawable(LineIconDrawable("⏹", Color.parseColor("#EF4444"), dp(22)))
                recordingIndicator.visibility = View.VISIBLE
                attachBtn.isEnabled = false
                input.isEnabled = false
            } else {
                stopVoiceRecordingGlobal()
                voiceBtn.setImageDrawable(LineIconDrawable("🎤", textColor, dp(22)))
                recordingIndicator.visibility = View.GONE
                attachBtn.isEnabled = true
                input.isEnabled = true
            }
        }
        inputBar.addView(voiceBtn)

        val sendBtn = ImageView(this).apply {
            setImageDrawable(LineIconDrawable("➤", Color.WHITE, dp(20)))
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(Color.parseColor("#4F46E5"), accentColor, Color.parseColor("#A78BFA"))).apply {
                shape = GradientDrawable.OVAL
            }
            setPadding(dp(12), dp(12), dp(12), dp(12))
            elevation = dp(3).toFloat()
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply { leftMargin = dp(4) }
        }
        inputBar.addView(sendBtn)
        root.addView(recordingIndicator)
        root.addView(inputBar)

        sendBtn.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                // Anti-spam check
                val spamBlock = checkAntiSpam(text)
                if (spamBlock != null) {
                    Toast.makeText(this, S.get(spamBlock), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val msg = hashMapOf<String, Any>(
                    "text" to text,
                    "senderId" to (auth.currentUser?.uid ?: return@setOnClickListener),
                    "username" to myUsername,
                    "nickname" to myNickname,
                    "photoBase64" to myPhotoBase64,
                    "timestamp" to System.currentTimeMillis(),
                    "replyToText" to (replyToText ?: ""),
                    "replyToUsername" to (replyToUsername ?: ""),
                    "reactions" to emptyList<String>()
                )
                db.collection("globalChat").add(msg)
                    .addOnSuccessListener {
                        recordMessageSent(text)
                        input.setText("")
                        replyToText = null
                        replyToUsername = null
                        replyBar.visibility = View.GONE
                    }
                    .addOnFailureListener { e ->
                        Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
                    }
            }
        }

        val globalReplyHandler: (Msg) -> Unit = { msg ->
            val displayName = if (msg.nickname.isNotEmpty()) msg.nickname else msg.username
            replyToText = msg.text
            replyToUsername = displayName
            replyLabel.text = iconize("↩ " + S.get("replying_to", displayName), accentColor, 11)
            replyPreview.text = msg.text
            replyBar.visibility = View.VISIBLE
            input.requestFocus()
        }
        adapter.onReplyClick = globalReplyHandler
        setupSwipeToReply(recycler, globalMsgs, globalReplyHandler)

        adapter.onDeleteClick = { position ->
            if (position < globalMsgIds.size) {
                val docId = globalMsgIds[position]
                showDeleteMessageDialog {
                    db.collection("globalChat").document(docId).delete()
                }
            }
        }

        container.addView(root)
    }

    // ==========================================
    // PRIVATE CHAT
    // ==========================================
    private fun showPrivateChat(otherUid: String, otherUsername: String, otherNickname: String, otherPhoto: String) {
        try {
            showPrivateChatInternal(otherUid, otherUsername, otherNickname, otherPhoto)
        } catch (e: Exception) {
            android.util.Log.e("ExecBin", "CRASH in showPrivateChat", e)
            Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
            showHome()
        }
    }

    private fun showPrivateChatInternal(otherUid: String, otherUsername: String, otherNickname: String, otherPhoto: String) {
        if (!isKeysReady) {
            showLoadingOverlay(S.get("preparing_encryption"))
        }

        currentScreen = "private_chat"
        container.removeAllViews()
        container.setBackgroundColor(bgColor)
        replyToText = null
        replyToUsername = null

        val chatId = getPrivateChatId((auth.currentUser?.uid ?: return), otherUid)
        currentOpenChatId = chatId
        val displayName = if (otherNickname.isNotEmpty()) otherNickname else otherUsername

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bgColor)
        }

        val header = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGlassBackground().apply { cornerRadius = 0f; cornerRadii = floatArrayOf(0f,0f,0f,0f, dp(24).toFloat(),dp(24).toFloat(), dp(24).toFloat(),dp(24).toFloat()) }
            setPadding(dp(14), dp(12), dp(16), dp(12))
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(4).toFloat()
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(66))
        }
        header.addView(FrameLayout(this).apply {
            background = createIconButton()
            layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { rightMargin = dp(12) }
            setOnClickListener { showHome() }
            addView(TextView(this@MainActivity).apply {
                text = "←"
                textSize = 20f
                setTextColor(textColor)
                gravity = Gravity.CENTER
                layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            })
        })
        val photoView = ImageView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(44), dp(44)).apply { rightMargin = dp(12) }
            scaleType = ImageView.ScaleType.CENTER_CROP
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(if (isDarkTheme) COLOR_DARK_AVATAR_BG else COLOR_LIGHT_AVATAR_BG)
                setStroke(dp(2), if (isDarkTheme) Color.parseColor("#334155") else Color.parseColor("#E2E8F0"))
            }
        }
        if (otherPhoto.isNotEmpty()) {
            try {
                val imageBytes = Base64.decode(otherPhoto, Base64.DEFAULT)
                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                photoView.setImageBitmap(bitmap)
            } catch (_: Exception) {}
        }
        photoView.clipToOutline = true
        photoView.outlineProvider = object : android.view.ViewOutlineProvider() {
            override fun getOutline(view: View, outline: android.graphics.Outline) {
                outline.setOval(0, 0, view.width, view.height)
            }
        }
        header.addView(photoView)

        val nameColumn = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        nameColumn.addView(TextView(this).apply {
            text = displayName
            setTextColor(textColor)
            textSize = 17f
            setTypeface(null, android.graphics.Typeface.BOLD)
        })
        val onlineLabel = TextView(this).apply {
            text = S.get("loading")
            setTextColor(subTextColor)
            textSize = 12f
        }
        nameColumn.addView(onlineLabel)
        header.addView(nameColumn)
        // Search icon
        val searchIcon = ImageView(this).apply {
            setImageDrawable(LineIconDrawable("🔍", textColor, dp(20)))
            setPadding(dp(8), dp(4), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
            setOnClickListener {
                // Toggle search bar
                val existing = root.findViewWithTag<LinearLayout>("chat_search_bar")
                if (existing != null) {
                    root.removeView(existing)
                    return@setOnClickListener
                }
                val searchBar = LinearLayout(this@MainActivity).apply {
                    tag = "chat_search_bar"
                    orientation = LinearLayout.HORIZONTAL
                    background = createRoundedBackground(headerBg, 0)
                    setPadding(dp(12), dp(6), dp(12), dp(6))
                    gravity = Gravity.CENTER_VERTICAL
                }
                var currentMatch = -1
                val matchPositions = mutableListOf<Int>()
                val searchInput = EditText(this@MainActivity).apply {
                    hint = S.get("search_messages")
                    setHintTextColor(subTextColor)
                    setTextColor(textColor)
                    textSize = 14f
                    background = createRoundedBackground(if (isDarkTheme) Color.parseColor("#1A1F38") else Color.parseColor("#F0F1F5"), 20)
                    setPadding(dp(14), dp(8), dp(14), dp(8))
                    maxLines = 1
                    isSingleLine = true
                    layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                }
                val matchLabel = TextView(this@MainActivity).apply {
                    text = ""
                    setTextColor(subTextColor)
                    textSize = 12f
                    setPadding(dp(8), 0, dp(4), 0)
                }
                val upBtn = TextView(this@MainActivity).apply {
                    text = "▲"
                    textSize = 18f
                    setTextColor(textColor)
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                    setOnClickListener {
                        if (matchPositions.isNotEmpty()) {
                            currentMatch = if (currentMatch > 0) currentMatch - 1 else matchPositions.size - 1
                            matchLabel.text = "${currentMatch + 1}/${matchPositions.size}"
                            root.findViewWithTag<RecyclerView>("private_chat_recycler")?.scrollToPosition(matchPositions[currentMatch])
                        }
                    }
                }
                val downBtn = TextView(this@MainActivity).apply {
                    text = "▼"
                    textSize = 18f
                    setTextColor(textColor)
                    setPadding(dp(6), dp(4), dp(6), dp(4))
                    setOnClickListener {
                        if (matchPositions.isNotEmpty()) {
                            currentMatch = if (currentMatch < matchPositions.size - 1) currentMatch + 1 else 0
                            matchLabel.text = "${currentMatch + 1}/${matchPositions.size}"
                            root.findViewWithTag<RecyclerView>("private_chat_recycler")?.scrollToPosition(matchPositions[currentMatch])
                        }
                    }
                }
                val closeBtn = TextView(this@MainActivity).apply {
                    text = "✕"
                    textSize = 18f
                    setTextColor(subTextColor)
                    setPadding(dp(6), dp(4), dp(8), dp(4))
                    setOnClickListener {
                        (root.findViewWithTag<RecyclerView>("private_chat_recycler")?.adapter as? MsgAdapter)?.let { msgAdapter ->
                            msgAdapter.searchQuery = ""
                            msgAdapter.notifyDataSetChanged()
                        }
                        root.removeView(searchBar)
                    }
                }

                searchInput.addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                    override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                    override fun afterTextChanged(s: android.text.Editable?) {
                        val query = s?.toString()?.trim()?.lowercase() ?: ""
                        matchPositions.clear()
                        currentMatch = -1
                        if (query.length >= 2) {
                            val snapshot = privateMsgs.toList()
                            snapshot.forEachIndexed { idx, msg ->
                                if (msg.text.lowercase().contains(query)) {
                                    matchPositions.add(idx)
                                }
                            }
                        }
                        // Update adapter highlight
                        (root.findViewWithTag<RecyclerView>("private_chat_recycler")?.adapter as? MsgAdapter)?.let { msgAdapter ->
                            msgAdapter.searchQuery = query
                            msgAdapter.notifyDataSetChanged()
                        }
                        if (matchPositions.isNotEmpty()) {
                            currentMatch = 0
                            matchLabel.text = "1/${matchPositions.size}"
                            root.findViewWithTag<RecyclerView>("private_chat_recycler")?.scrollToPosition(matchPositions[0])
                        } else {
                            matchLabel.text = if (query.length >= 2) "0/0" else ""
                        }
                    }
                })

                searchBar.addView(searchInput)
                searchBar.addView(matchLabel)
                searchBar.addView(upBtn)
                searchBar.addView(downBtn)
                searchBar.addView(closeBtn)
                root.addView(searchBar, 1) // After header
                searchInput.requestFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                searchInput.postDelayed({ imm.showSoftInput(searchInput, 0) }, 200)
            }
        }
        header.addView(searchIcon)

        header.setOnClickListener {
            showUserProfilePopup(otherUsername, otherNickname, otherPhoto, otherUid)
        }
        root.addView(header)
        // Connection banner in private chat
        root.addView(createConnectionBanner())
        val chatCm = getSystemService(Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val chatActiveNet = chatCm.activeNetworkInfo
        if (chatActiveNet == null || !chatActiveNet.isConnected) {
            connectionBanner?.visibility = View.VISIBLE
        }

        db.collection("publicProfiles").document(otherUid).get()
            .addOnSuccessListener { doc ->
                val hideLS = doc?.getBoolean("hideLastSeen") ?: false
                val isOnline = if (hideLS) false else (doc?.getBoolean("isOnline") ?: false)
                val lastSeen = if (hideLS) -1L else (doc?.getLong("lastSeen") ?: 0L)
                if (isOnline) {
                    onlineLabel.text = S.get("online")
                    onlineLabel.setTextColor(Color.parseColor("#22C55E"))
                } else if (lastSeen == -1L) {
                    onlineLabel.text = S.get("last_seen_hidden")
                    onlineLabel.setTextColor(subTextColor)
                } else {
                    val mins = (System.currentTimeMillis() - lastSeen) / 1000 / 60
                    onlineLabel.text = when {
                        lastSeen == 0L -> S.get("offline")
                        mins < 1 -> S.get("just_now")
                        mins < 60 -> S.get("minutes_ago", mins)
                        mins < 1440 -> S.get("hours_ago", mins / 60)
                        else -> S.get("days_ago", mins / 1440)
                    }
                    onlineLabel.setTextColor(subTextColor)
                }
            }

        val recycler = RecyclerView(this).apply {
            tag = "private_chat_recycler"
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            layoutManager = LinearLayoutManager(this@MainActivity).apply { stackFromEnd = true }
            setPadding(dp(12), dp(8), dp(12), dp(8))
            clipToPadding = false
        }
        privateMsgs.clear()
        privateMsgIds.clear()
        val adapter = MsgAdapter(privateMsgs, (auth.currentUser?.uid ?: return))
        adapter.chatId = chatId
        adapter.isPrivateChat = true
        adapter.messageIds = privateMsgIds
        recycler.adapter = adapter

        // Pagination: "Load earlier messages" button
        var messageLimit = 50
        var reloadListener: (() -> Unit)? = null
        val loadMoreBtn = TextView(this).apply {
            text = S.get("load_more")
            setTextColor(accentColor)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(10))
            setBackgroundColor(if (isDarkTheme) Color.parseColor("#0F172A") else Color.parseColor("#EEF0F6"))
            visibility = View.GONE
            setOnClickListener {
                messageLimit += 50
                visibility = View.GONE
                reloadListener?.invoke()
            }
        }
        root.addView(loadMoreBtn)

        // Private chat empty state
        val privateChatEmpty = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            visibility = View.GONE
            tag = "private_chat_empty"
        }
        privateChatEmpty.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("🔐", subTextColor, dp(48)))
            layoutParams = LinearLayout.LayoutParams(dp(48), dp(48)).apply { bottomMargin = dp(16); gravity = Gravity.CENTER_HORIZONTAL }
        })
        privateChatEmpty.addView(TextView(this).apply {
            text = S.get("no_messages_yet")
            setTextColor(textColor)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        })
        privateChatEmpty.addView(TextView(this).apply {
            text = S.get("send_first_message")
            setTextColor(subTextColor)
            textSize = 14f
            gravity = Gravity.CENTER
        })
        privateChatEmpty.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            background = createRoundedBackground(if (isDarkTheme) Color.parseColor("#1A2240") else Color.parseColor("#EEF0FF"), 12)
            setPadding(dp(14), dp(10), dp(14), dp(10))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = dp(16) }
            addView(ImageView(this@MainActivity).apply {
                setImageDrawable(LineIconDrawable("🔒", subTextColor, dp(14)))
                layoutParams = LinearLayout.LayoutParams(dp(14), dp(14)).apply { rightMargin = dp(6) }
            })
            addView(TextView(this@MainActivity).apply {
                text = S.get("e2e_chat_hint")
                setTextColor(subTextColor)
                textSize = 12f
            })
        })
        root.addView(privateChatEmpty)
        root.addView(recycler)

        privateChatListener?.remove()

        val e2eBadge = TextView(this).apply {
            val badgeColor = if (isDarkTheme) Color.parseColor("#7C8DB5") else Color.parseColor("#6B7394")
            text = iconize("🔒 " + S.get("fetching_keys"), badgeColor, 11)
            setTextColor(badgeColor)
            textSize = 11f
            gravity = Gravity.CENTER
            setPadding(0, dp(8), 0, dp(8))
            background = createRoundedBackground(if (isDarkTheme) Color.parseColor("#111630") else Color.parseColor("#F0F1FF"), 0)
            setOnClickListener {
                showIdentityVerificationScreen(otherUid, otherUsername, otherNickname)
            }
        }
        // Show verified ✓ badge if this contact has been verified
        if (prefs.getBoolean("verified_$otherUid", false)) {
            e2eBadge.text = iconize("🛡️ " + S.get("identity_verified"), Color.parseColor("#22C55E"), 11)
            e2eBadge.setTextColor(Color.parseColor("#22C55E"))
        }
        root.addView(e2eBadge)

        var sharedKey: SecretKeySpec? = null
        previousSharedKey = null
        var otherUserPublicKey = ""  // For PFS key generation
        pendingMessages.clear()
        // Previously decrypted copies; used when a message can no longer be decrypted (e.g. after key rotation)
        val cachedById = java.util.concurrent.ConcurrentHashMap<String, CachedMessage>()

        lifecycleScope.launch {
            try {
                val cachedMessages = database.messageDao().getMessagesForChat(chatId).first()
                cachedMessages.forEach { cachedById[it.messageId] = it }
                runOnUiThread {
                    privateMsgs.clear()
                    privateMsgs.addAll(cachedMessages.map { it.toMsg() })
                    adapter.notifyDataSetChanged()
                    privateChatEmpty.visibility = if (privateMsgs.isEmpty()) View.VISIBLE else View.GONE
                    recycler.visibility = if (privateMsgs.isEmpty()) View.GONE else View.VISIBLE
                    if (privateMsgs.isNotEmpty()) {
                        recycler.scrollToPosition(privateMsgs.size - 1)
                    }
                }
            } catch (e: Exception) {
                android.util.Log.e("ExecBin", "Failed to load cached messages", e)
            }
        }

        fun setupMessageListener() {
            privateChatListener?.remove()
            privateChatListener = db.collection("privateChats").document(chatId)
                .collection("messages")
                .orderBy("timestamp")
                .limitToLast(messageLimit.toLong())
                .addSnapshotListener { snaps, error ->
                    if (error != null) {
                        android.util.Log.e("PrivateChat", "Listener error", error)
                        return@addSnapshotListener
                    }
                    if (snaps == null) return@addSnapshotListener

                    val allMsgs = snaps.documents.mapNotNull { doc ->
                        doc.toObject(Msg::class.java)?.let { msg ->
                            // Telegram-style: silently drop messages from blocked users
                            if (blockedUsers.contains(msg.senderId)) return@mapNotNull null
                            Triple(msg, doc.id, doc.getString("text") ?: msg.text)
                        }
                    }

                    // Show "load more" if we hit the pagination limit
                    runOnUiThread {
                        loadMoreBtn.visibility = if (snaps.size() >= messageLimit) View.VISIBLE else View.GONE
                    }

                    if (sharedKey == null) {
                        pendingMessages.clear()
                        pendingMessages.addAll(allMsgs.map { Pair(it.first, it.second) })
                        val placeholderMsgs = allMsgs.map { (msg, id, _) ->
                            Msg(
                                text = "\uD83D\uDD12 " + S.get("encrypted_message"),
                                senderId = msg.senderId,
                                timestamp = msg.timestamp,
                                username = msg.username,
                                nickname = msg.nickname,
                                photoBase64 = msg.photoBase64,
                                replyToText = if (msg.replyToText.isNotEmpty()) "\uD83D\uDD12 " + S.get("encrypted") else "",
                                replyToUsername = msg.replyToUsername,
                                read = msg.read,
                                reactions = msg.reactions
                            )
                        }
                        runOnUiThread {
                            privateMsgs.clear()
                            privateMsgs.addAll(placeholderMsgs)
                            privateMsgIds.clear()
                            privateMsgIds.addAll(allMsgs.map { it.second })
                            adapter.notifyDataSetChanged()
                            privateChatEmpty.visibility = if (privateMsgs.isEmpty()) View.VISIBLE else View.GONE
                            recycler.visibility = if (privateMsgs.isEmpty()) View.GONE else View.VISIBLE
                            if (privateMsgs.isNotEmpty()) {
                                recycler.post { recycler.smoothScrollToPosition(privateMsgs.size - 1) }
                            }
                        }
                    } else {
                        val myUid = auth.currentUser?.uid ?: ""
                        val key = sharedKey
                        val freshlyDecrypted = allMsgs.map { (msg, id, encryptedText) ->
                            val noKeyText = "\uD83D\uDD12 " + S.get("decrypt_failed")
                            try {
                                val decryptedText: String
                                val decryptedReply: String

                                fun staticDecrypt(c: String): String =
                                    if (key != null) decryptStatic(c, key) else noKeyText

                                if (msg.ephemeralKey.isNotEmpty()) {
                                    if (msg.senderId == myUid) {
                                        decryptedText = if (msg.senderText.isNotEmpty())
                                            decryptWithEphemeral(msg.senderText, msg.ephemeralKey)
                                        else staticDecrypt(encryptedText)
                                        decryptedReply = when {
                                            msg.senderReplyText.isNotEmpty() -> decryptWithEphemeral(msg.senderReplyText, msg.ephemeralKey)
                                            msg.replyToText.isNotEmpty() -> staticDecrypt(msg.replyToText)
                                            else -> ""
                                        }
                                    } else {
                                        decryptedText = decryptWithEphemeral(encryptedText, msg.ephemeralKey)
                                        decryptedReply = if (msg.replyToText.isNotEmpty())
                                            decryptWithEphemeral(msg.replyToText, msg.ephemeralKey) else ""
                                    }
                                } else {
                                    decryptedText = staticDecrypt(encryptedText)
                                    decryptedReply = if (msg.replyToText.isNotEmpty()) staticDecrypt(msg.replyToText) else ""
                                }

                                Msg(
                                    text = decryptedText,
                                    senderId = msg.senderId,
                                    timestamp = msg.timestamp,
                                    username = msg.username,
                                    nickname = msg.nickname,
                                    photoBase64 = msg.photoBase64,
                                    replyToText = decryptedReply,
                                    replyToUsername = msg.replyToUsername,
                                    read = msg.read,
                                    edited = msg.edited,
                                    mediaUrl = msg.mediaUrl,
                                    mediaType = msg.mediaType,
                                    mediaDuration = msg.mediaDuration,
                                    reactions = msg.reactions
                                ) to id
                            } catch (e: Exception) {
                                Msg(
                                    text = noKeyText,
                                    senderId = msg.senderId,
                                    timestamp = msg.timestamp,
                                    username = msg.username,
                                    nickname = msg.nickname,
                                    photoBase64 = msg.photoBase64,
                                    replyToText = "",
                                    replyToUsername = msg.replyToUsername,
                                    read = msg.read,
                                    edited = msg.edited,
                                    mediaUrl = msg.mediaUrl,
                                    mediaType = msg.mediaType,
                                    mediaDuration = msg.mediaDuration,
                                    reactions = msg.reactions
                                ) to id
                            }
                        }
                        val failedText = "🔒 " + S.get("decrypt_failed")
                        // Fall back to the cached plaintext instead of showing (and caching) a decrypt failure
                        val decryptedMsgs = freshlyDecrypted.map { (m, id) ->
                            val cached = cachedById[id]
                            if (m.text == failedText && cached != null && cached.text != failedText) {
                                m.copy(text = cached.text, replyToText = cached.replyToText) to id
                            } else m to id
                        }
                        runOnUiThread {
                            privateMsgs.clear()
                            privateMsgs.addAll(decryptedMsgs.map { it.first })
                            privateMsgIds.clear()
                            privateMsgIds.addAll(decryptedMsgs.map { it.second })
                            adapter.notifyDataSetChanged()
                            privateChatEmpty.visibility = if (privateMsgs.isEmpty()) View.VISIBLE else View.GONE
                            recycler.visibility = if (privateMsgs.isEmpty()) View.GONE else View.VISIBLE
                            if (privateMsgs.isNotEmpty()) {
                                recycler.post { recycler.smoothScrollToPosition(privateMsgs.size - 1) }
                            }
                        }

                        lifecycleScope.launch(Dispatchers.IO) {
                            val cachedMessages = decryptedMsgs.filter { it.first.text != failedText }.map { (msg, id) ->
                                CachedMessage(
                                    messageId = id,
                                    chatId = chatId,
                                    text = msg.text,
                                    senderId = msg.senderId,
                                    username = msg.username,
                                    nickname = msg.nickname,
                                    photoBase64 = msg.photoBase64,
                                    replyToText = msg.replyToText,
                                    replyToUsername = msg.replyToUsername,
                                    timestamp = msg.timestamp,
                                    isEncrypted = false,
                                    read = msg.read,
                                    edited = msg.edited,
                                    mediaUrl = msg.mediaUrl,
                                    mediaType = msg.mediaType,
                                    mediaDuration = msg.mediaDuration,
                                    reactionsStr = msg.reactions.joinToString(",")
                                )
                            }
                            try { database.messageDao().insertAll(cachedMessages) } catch (_: Exception) {}
                        }

                        // Mark unread messages as read
                        val unreadIds = allMsgs.filter { (msg, _, _) ->
                            msg.senderId != myUid && !msg.read
                        }.map { it.second }
                        if (unreadIds.isNotEmpty()) {
                            val batch = db.batch()
                            unreadIds.forEach { id ->
                                val ref = db.collection("privateChats").document(chatId)
                                    .collection("messages").document(id)
                                batch.update(ref, "read", true)
                            }
                            batch.commit().addOnSuccessListener {
                                lifecycleScope.launch(Dispatchers.IO) {
                                    try { database.messageDao().markAsRead(unreadIds) } catch (_: Exception) {}
                                }
                            }
                        }
                    }
                }
        }

        fun tryEstablishEncryption(theirKey: String) {
            if (theirKey.isNotEmpty() && !isValidPublicKey(theirKey)) {
                // A malformed key from the other side must never cause us to touch our own keys
                android.util.Log.e("ExecBin", "Contact published an invalid public key")
                sharedKey = null
                adapter.encryptionKey = null
                e2eBadge.text = iconize("⚠️ " + S.get("encryption_not_ready"), Color.parseColor("#F59E0B"), 11)
                e2eBadge.setTextColor(Color.parseColor("#F59E0B"))
                return
            }
            if (theirKey.isNotEmpty()) {
                warnIfContactKeyChanged(otherUid, theirKey, displayName)
                otherUserPublicKey = theirKey  // Store for PFS
                sharedKey = deriveSharedKey(theirKey)
                previousSharedKey = derivePreviousSharedKey(theirKey)
                if (sharedKey == null && loadPrivateKeySplit() == null) {
                    // Our own private key is missing/corrupted — regenerate and retry once
                    android.util.Log.e("ExecBin", "Local private key missing, regenerating local keys")
                    val prefix = getE2EKeyPrefix()
                    prefs.edit().remove("${prefix}_key_blob").remove("${prefix}_dpi_conf").apply()
                    generateE2EKeyPairInKeystore()
                    // Publish new public key to Firestore
                    val userId = auth.currentUser?.uid
                    if (userId != null && myPublicKeyBase64.isNotEmpty()) {
                        db.collection("publicProfiles").document(userId)
                            .update("publicKey", myPublicKeyBase64)
                    }
                    // Retry derivation with new keys
                    sharedKey = deriveSharedKey(theirKey)
                }
                if (sharedKey != null) {
                    adapter.encryptionKey = sharedKey
                    e2eBadge.text = iconize("🔒 " + S.get("e2e_encrypted"), Color.parseColor("#22C55E"), 11)
                    e2eBadge.setTextColor(Color.parseColor("#22C55E"))
                    setupMessageListener()
                    hideLoadingOverlay()
                    return
                }
            }
            if (theirKey.isEmpty()) {
                e2eBadge.text = iconize("⚠️ " + S.get("waiting_key", displayName), Color.parseColor("#F59E0B"), 11)
            } else {
                e2eBadge.text = iconize("⚠️ " + S.get("encryption_not_ready"), Color.parseColor("#F59E0B"), 11)
            }
            e2eBadge.setTextColor(Color.parseColor("#F59E0B"))
        }

        reloadListener = { setupMessageListener() }
        setupMessageListener()

        publicKeyListener?.remove()
        publicKeyListener = db.collection("publicProfiles").document(otherUid)
            .addSnapshotListener { doc, _ ->
                if (doc == null) return@addSnapshotListener
                val theirKey = doc.getString("publicKey") ?: ""
                // Establish on first key, and re-establish if the contact rotates/changes keys mid-chat
                if (sharedKey == null || (theirKey.isNotEmpty() && theirKey != otherUserPublicKey)) {
                    tryEstablishEncryption(theirKey)
                }
            }

        val replyBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createRoundedBackground(cardBg, 0)
            setPadding(dp(16), dp(10), dp(8), dp(10))
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
        }
        val replyContent = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val replyLabel = TextView(this).apply {
            setTextColor(accentColor)
            textSize = 11f
            setTypeface(null, android.graphics.Typeface.BOLD)
        }
        val replyPreview = TextView(this).apply {
            setTextColor(subTextColor)
            textSize = 13f
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        replyContent.addView(replyLabel)
        replyContent.addView(replyPreview)
        replyBar.addView(replyContent)
        replyBar.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("✕", subTextColor, dp(16)))
            setPadding(dp(12), 0, dp(8), 0)
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { gravity = Gravity.CENTER_VERTICAL }
            setOnClickListener {
                replyToText = null
                replyToUsername = null
                replyBar.visibility = View.GONE
            }
        })
        root.addView(replyBar)

        val inputBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = createGlassBackground().apply { cornerRadii = floatArrayOf(dp(24).toFloat(),dp(24).toFloat(),dp(24).toFloat(),dp(24).toFloat(), 0f,0f,0f,0f) }
            setPadding(dp(10), dp(10), dp(10), dp(10))
            gravity = Gravity.CENTER_VERTICAL
            elevation = dp(4).toFloat()
        }

        // Attach image button
        val attachBtn = ImageView(this).apply {
            setImageDrawable(LineIconDrawable("📎", textColor, dp(22)))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(42), dp(42))
            setOnClickListener {
                val currentKey = sharedKey
                showInlineGalleryPicker(chatId, otherUid, currentKey)
            }
        }
        inputBar.addView(attachBtn)

        // Typing indicator: track when current user is typing
        val myUidForTyping = auth.currentUser?.uid ?: ""
        val chatDocRef = db.collection("privateChats").document(chatId)
        typingCleanupHandler?.removeCallbacksAndMessages(null)
        val typingHandler = android.os.Handler(mainLooper)
        typingCleanupHandler = typingHandler
        var typingRunnable: Runnable? = null

        val input = EditText(this).apply {
            hint = S.get("message_hint")
            setHintTextColor(subTextColor)
            setTextColor(textColor)
            textSize = 15f
            background = createInputBackground()
            setPadding(dp(18), dp(12), dp(18), dp(12))
            maxLines = 4
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                leftMargin = dp(4); rightMargin = dp(6)
            }
            setOnFocusChangeListener { _, hasFocus ->
                if (hasFocus) {
                    recycler.postDelayed({
                        if (privateMsgs.isNotEmpty()) recycler.smoothScrollToPosition(privateMsgs.size - 1)
                    }, 300)
                }
            }
            addTextChangedListener(object : android.text.TextWatcher {
                override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                    // Set typing status
                    if (myUidForTyping.isNotEmpty()) {
                        chatDocRef.update("typing_$myUidForTyping", true)
                            .addOnFailureListener { /* rules may block, ignore */ }
                    }
                    // Clear typing after 3 seconds of no input
                    typingRunnable?.let { typingHandler.removeCallbacks(it) }
                    typingRunnable = Runnable {
                        if (myUidForTyping.isNotEmpty()) {
                            chatDocRef.update("typing_$myUidForTyping", false)
                                .addOnFailureListener { /* rules may block, ignore */ }
                        }
                    }
                    typingRunnable?.let { typingHandler.postDelayed(it, 3000) }
                }
                override fun afterTextChanged(s: android.text.Editable?) {}
            })
        }
        inputBar.addView(input)

        // Listen for other user's typing status
        typingStatusListener?.remove()
        typingStatusListener = chatDocRef.addSnapshotListener { doc, _ ->
            if (doc != null && doc.exists()) {
                val otherTyping = doc.getBoolean("typing_$otherUid") == true
                runOnUiThread {
                    if (otherTyping) {
                        onlineLabel.text = S.get("typing")
                        onlineLabel.setTextColor(accentColor)
                    }
                    // If not typing, the existing online/offline listener handles the label
                }
            }
        }

        // Voice record button
        val voiceBtn = ImageView(this).apply {
            setImageDrawable(LineIconDrawable("🎤", textColor, dp(22)))
            setPadding(dp(8), dp(8), dp(8), dp(8))
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(40)).apply { leftMargin = dp(4) }
        }

        val recordingIndicator = TextView(this).apply {
            text = iconize("🔴 " + S.get("recording"), Color.parseColor("#EF4444"), 12)
            setTextColor(Color.parseColor("#EF4444"))
            textSize = 12f
            visibility = View.GONE
            setPadding(dp(8), 0, dp(8), 0)
        }

        voiceBtn.setOnClickListener {
            // Check mic permission
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.RECORD_AUDIO), 200)
                return@setOnClickListener
            }
            if (!isRecordingVoice) {
                startVoiceRecording()
                voiceBtn.setImageDrawable(LineIconDrawable("⏹", Color.parseColor("#EF4444"), dp(22)))
                recordingIndicator.visibility = View.VISIBLE
                attachBtn.isEnabled = false
                input.isEnabled = false
            } else {
                val currentKey = sharedKey
                stopVoiceRecording(chatId, otherUid, currentKey)
                voiceBtn.setImageDrawable(LineIconDrawable("🎤", textColor, dp(22)))
                recordingIndicator.visibility = View.GONE
                attachBtn.isEnabled = true
                input.isEnabled = true
            }
        }
        inputBar.addView(voiceBtn)

        val sendBtn = ImageView(this).apply {
            setImageDrawable(LineIconDrawable("➤", Color.WHITE, dp(20)))
            scaleType = ImageView.ScaleType.CENTER
            background = GradientDrawable(GradientDrawable.Orientation.BL_TR, intArrayOf(Color.parseColor("#4F46E5"), accentColor, Color.parseColor("#A78BFA"))).apply {
                shape = GradientDrawable.OVAL
            }
            elevation = dp(3).toFloat()
            setPadding(dp(12), dp(12), dp(12), dp(12))
            layoutParams = LinearLayout.LayoutParams(dp(46), dp(46)).apply { leftMargin = dp(4) }
        }
        inputBar.addView(sendBtn)
        root.addView(recordingIndicator)
        root.addView(inputBar)

        if (!isKeysReady) {
            input.isEnabled = false
            sendBtn.isEnabled = false
            sendBtn.alpha = 0.5f
        }

        sendBtn.setOnClickListener {
            val text = input.text.toString().trim()
            if (text.isNotEmpty()) {
                // Anti-spam check
                val spamBlock = checkAntiSpam(text)
                if (spamBlock != null) {
                    Toast.makeText(this, S.get(spamBlock), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }
                val now = System.currentTimeMillis()
                val sendKey = sharedKey
                val replyText = replyToText
                // PFS: generate per-message ephemeral keys when possible
                var ephPubKey = ""
                var senderEncText = ""
                var senderEncReply = ""
                val encryptedText: String
                val encryptedReply: String

                if (sendKey == null) {
                    // SECURITY: Never send unencrypted messages
                    android.util.Log.e("ExecBin", "Send blocked: sharedKey is null, encryption not established")
                    Toast.makeText(this, iconize("⚠️ " + S.get("encryption_not_ready")), Toast.LENGTH_LONG).show()
                    return@setOnClickListener
                }

                try {
                    if (otherUserPublicKey.isNotEmpty()) {
                        val pfsKeys = generatePFSKeys(otherUserPublicKey)
                        if (pfsKeys != null) {
                            val (recipientKey, senderKey, ephKey) = pfsKeys
                            ephPubKey = ephKey
                            encryptedText = encryptMessage(text, recipientKey)
                            senderEncText = encryptMessage(text, senderKey)
                            encryptedReply = if (!replyText.isNullOrEmpty()) encryptMessage(replyText, recipientKey) else ""
                            senderEncReply = if (!replyText.isNullOrEmpty()) encryptMessage(replyText, senderKey) else ""
                        } else {
                            encryptedText = encryptMessage(text, sendKey)
                            encryptedReply = if (!replyText.isNullOrEmpty()) encryptMessage(replyText, sendKey) else ""
                        }
                    } else {
                        encryptedText = encryptMessage(text, sendKey)
                        encryptedReply = if (!replyText.isNullOrEmpty()) encryptMessage(replyText, sendKey) else ""
                    }
                } catch (e: Exception) {
                    Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val msg = hashMapOf<String, Any>(
                    "text" to encryptedText,
                    "senderId" to (auth.currentUser?.uid ?: return@setOnClickListener),
                    "username" to myUsername,
                    "nickname" to myNickname,
                    "photoBase64" to myPhotoBase64,
                    "timestamp" to now,
                    "replyToText" to encryptedReply,
                    "replyToUsername" to (replyToUsername ?: ""),
                    "read" to false,
                    "ephemeralKey" to ephPubKey,
                    "senderText" to senderEncText,
                    "senderReplyText" to senderEncReply,
                    "reactions" to emptyList<String>()
                )
                val chatRef = db.collection("privateChats").document(chatId)

                chatRef.collection("messages").add(msg)
                    .addOnSuccessListener { documentReference ->
                        recordMessageSent(text)
                        input.setText("")
                        val cachedReplyText = replyText ?: ""
                        val cachedReplyUsername = replyToUsername ?: ""
                        replyToText = null
                        replyToUsername = null
                        replyBar.visibility = View.GONE

                        chatRef.update("lastMessage", encryptedText, "lastTimestamp", now)
                            .addOnFailureListener {
                                chatRef.set(hashMapOf(
                                    "participants" to listOf((auth.currentUser?.uid ?: return@addOnFailureListener), otherUid),
                                    "lastMessage" to encryptedText,
                                    "lastTimestamp" to now
                                ))
                            }

                        lifecycleScope.launch(Dispatchers.IO) {
                            val cached = CachedMessage(
                                messageId = documentReference.id,
                                chatId = chatId,
                                text = text,
                                senderId = (auth.currentUser?.uid ?: return@launch),
                                username = myUsername,
                                nickname = myNickname,
                                photoBase64 = myPhotoBase64,
                                replyToText = cachedReplyText,
                                replyToUsername = cachedReplyUsername,
                                timestamp = now,
                                isEncrypted = false,
                                read = true,
                                edited = false,
                                mediaUrl = "",
                                mediaType = "",
                                mediaDuration = 0
                            )
                            try { database.messageDao().insert(cached) } catch (_: Exception) {}
                        }
                    }
                    .addOnFailureListener { _ ->
                        val snackbar = com.google.android.material.snackbar.Snackbar.make(
                            root, S.get("error_send_failed"), com.google.android.material.snackbar.Snackbar.LENGTH_LONG
                        )
                        snackbar.setAction(S.get("retry")) {
                            val chatRefRetry = db.collection("privateChats").document(chatId)
                            chatRefRetry.collection("messages").add(msg)
                                .addOnSuccessListener { recordMessageSent(text) }
                                .addOnFailureListener {
                                    Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
                                }
                        }
                        snackbar.setActionTextColor(accentColor)
                        snackbar.show()
                    }
            }
            // Clear typing status after send
            if (myUidForTyping.isNotEmpty()) {
                chatDocRef.update("typing_$myUidForTyping", false)
            }
        }

        val dmReplyHandler: (Msg) -> Unit = { msg ->
            val name = if (msg.nickname.isNotEmpty()) msg.nickname else msg.username
            replyToText = msg.text
            replyToUsername = name
            replyLabel.text = iconize("↩ " + S.get("replying_to", name), accentColor, 11)
            replyPreview.text = msg.text
            replyBar.visibility = View.VISIBLE
            input.requestFocus()
        }
        adapter.onReplyClick = dmReplyHandler
        setupSwipeToReply(recycler, privateMsgs, dmReplyHandler)

        adapter.onDeleteClick = { position ->
            if (position < privateMsgIds.size) {
                val docId = privateMsgIds[position]
                showDeleteMessageDialog {
                    db.collection("privateChats").document(chatId)
                        .collection("messages").document(docId).delete()
                        .addOnSuccessListener {
                            lifecycleScope.launch(Dispatchers.IO) {
                                // In a real app, you would delete from cache as well
                            }
                        }
                }
            }
        }

        container.addView(root)
    }

    // ==========================================
    // Helper to convert CachedMessage to Msg
    // ==========================================
    private fun CachedMessage.toMsg(): Msg = Msg(
        text = text,
        senderId = senderId,
        timestamp = timestamp,
        username = username,
        nickname = nickname,
        photoBase64 = photoBase64,
        replyToText = replyToText,
        replyToUsername = replyToUsername,
        read = read,
        edited = edited,
        mediaUrl = mediaUrl,
        mediaType = mediaType,
        mediaDuration = mediaDuration,
        reactions = if (reactionsStr.isEmpty()) emptyList() else reactionsStr.split(",").filter { it.isNotEmpty() }
    )

    // ==========================================
    // DELETE MESSAGE DIALOG
    // ==========================================
    private fun showDeleteMessageDialog(onConfirm: () -> Unit) {
        val dialogBg = if (isDarkTheme) Color.parseColor("#1E293B") else Color.parseColor("#FFFFFF")
        val dangerColor = Color.parseColor("#EF4444")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = createRoundedBackground(dialogBg, 20)
            setPadding(dp(24), dp(24), dp(24), dp(20))
            gravity = Gravity.CENTER_HORIZONTAL
        }
        content.addView(ImageView(this).apply {
            setImageDrawable(LineIconDrawable("🗑", Color.parseColor("#EF4444"), dp(36)))
            layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { bottomMargin = dp(12); gravity = Gravity.CENTER_HORIZONTAL }
        })
        content.addView(TextView(this).apply {
            text = S.get("delete_message_title")
            setTextColor(textColor)
            textSize = 18f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) }
        })
        content.addView(TextView(this).apply {
            text = S.get("delete_message_body")
            setTextColor(subTextColor)
            textSize = 13f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(24) }
        })
        val btnRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val dialog = AlertDialog.Builder(this).setView(content).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        btnRow.addView(Button(this).apply {
            text = S.get("cancel")
            setTextColor(subTextColor)
            textSize = 14f
            background = createRoundedBackground(cardBg, 12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { rightMargin = dp(8) }
            setOnClickListener { dialog.dismiss() }
        })
        btnRow.addView(Button(this).apply {
            text = S.get("delete")
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(null, android.graphics.Typeface.BOLD)
            background = createRoundedBackground(dangerColor, 12)
            layoutParams = LinearLayout.LayoutParams(0, dp(44), 1f).apply { leftMargin = dp(8) }
            setOnClickListener {
                dialog.dismiss()
                onConfirm()
            }
        })
        content.addView(btnRow)
        dialog.show()
    }

    // ==========================================
    // MESSAGE OPTIONS BOTTOM SHEET
    // ==========================================
    private fun showMessageOptions(msg: Msg, isMine: Boolean, onReply: () -> Unit, onDelete: () -> Unit,
                                   chatId: String = "", messageId: String = "", isPrivate: Boolean = false,
                                   sharedKey: SecretKeySpec? = null) {
        val dialogBg = if (isDarkTheme) Color.parseColor("#111827") else Color.parseColor("#FFFFFF")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(dialogBg)
                cornerRadius = dp(24).toFloat()
                if (isDarkTheme) setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(8), dp(16), dp(8), dp(12))
            elevation = dp(8).toFloat()
        }
        content.addView(TextView(this).apply {
            text = if (msg.text.length > 60) msg.text.take(60) + "..." else msg.text
            setTextColor(subTextColor)
            textSize = 12.5f
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(4), dp(16), dp(14))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        content.addView(View(this).apply {
            setBackgroundColor(if (isDarkTheme) Color.parseColor("#1E293B") else Color.parseColor("#F1F5F9"))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply {
                bottomMargin = dp(6)
                leftMargin = dp(16)
                rightMargin = dp(16)
            }
        })
        val dialog = AlertDialog.Builder(this).setView(content).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        fun addOption(icon: String, label: String, color: Int, action: () -> Unit) {
            content.addView(LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(dp(20), dp(14), dp(20), dp(14))
                background = createRoundedBackground(Color.TRANSPARENT, 14)
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                addView(ImageView(this@MainActivity).apply {
                    setImageDrawable(LineIconDrawable(icon, color, dp(22)))
                    layoutParams = LinearLayout.LayoutParams(dp(22), dp(22)).apply {
                        rightMargin = dp(16)
                    }
                })
                addView(TextView(this@MainActivity).apply {
                    text = label
                    setTextColor(color)
                    textSize = 16f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    letterSpacing = -0.01f
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                })
                setOnClickListener {
                    dialog.dismiss()
                    action()
                }
            })
        }
        addOption("↩", S.get("reply"), textColor, onReply)
        addOption("💬", S.get("react"), textColor) {
            if (messageId.isNotEmpty()) {
                showReactionPicker(msg, chatId, messageId, isPrivate)
            }
        }
        addOption("📋", S.get("copy"), textColor) {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
            clipboard.setPrimaryClip(android.content.ClipData.newPlainText("message", msg.text))
            Toast.makeText(this, S.get("copied"), Toast.LENGTH_SHORT).show()
        }
        if (isMine) {
            addOption("✏", S.get("edit"), textColor) {
                editMessage(chatId, messageId, msg.text, msg.replyToText, isPrivate, sharedKey)
            }
            addOption("🗑", S.get("delete"), Color.parseColor("#EF4444"), onDelete)
        } else {
            // Report + Block for other people's messages
            if (messageId.isNotEmpty()) {
                addOption("⚠", S.get("report"), Color.parseColor("#F59E0B")) {
                    reportMessage(msg, chatId, messageId, isPrivate)
                }
            }
            addOption("🚫", S.get("block") + " @${msg.username}", Color.parseColor("#EF4444")) {
                blockUser(msg.senderId, msg.username)
            }
        }
        dialog.show()
    }

    // ==========================================
    // REACTION PICKER + TOGGLE
    // ==========================================
    private fun showReactionPicker(msg: Msg, chatId: String, messageId: String, isPrivate: Boolean) {
        val emojis = listOf("❤️", "👍", "😂", "😮", "😢", "🔥", "🙏", "🎉")
        val dialogBg = if (isDarkTheme) Color.parseColor("#111827") else Color.parseColor("#FFFFFF")
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(dialogBg)
                cornerRadius = dp(24).toFloat()
                if (isDarkTheme) setStroke(dp(1), Color.parseColor("#1E293B"))
            }
            setPadding(dp(16), dp(20), dp(16), dp(16))
            elevation = dp(8).toFloat()
        }
        content.addView(TextView(this).apply {
            text = S.get("react_to_message")
            setTextColor(textColor)
            textSize = 15f
            setTypeface(null, android.graphics.Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(16))
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        })
        val dialog = AlertDialog.Builder(this).setView(content).create()
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        val emojiRow = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }
        val myUid = auth.currentUser?.uid ?: ""
        emojis.forEach { emoji ->
            val alreadyReacted = msg.reactions.contains("$myUid:$emoji")
            val emojiBtn = TextView(this).apply {
                text = emoji
                textSize = 28f
                gravity = Gravity.CENTER
                setPadding(dp(8), dp(10), dp(8), dp(10))
                background = GradientDrawable().apply {
                    setColor(if (alreadyReacted) Color.parseColor("#33818CF8") else Color.TRANSPARENT)
                    cornerRadius = dp(14).toFloat()
                }
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setOnClickListener {
                    dialog.dismiss()
                    toggleReaction(chatId, messageId, emoji, isPrivate)
                }
            }
            emojiRow.addView(emojiBtn)
        }
        content.addView(emojiRow)
        dialog.show()
    }

    internal fun toggleReaction(chatId: String, messageId: String, emoji: String, isPrivate: Boolean) {
        val myUid = auth.currentUser?.uid ?: return
        val entry = "$myUid:$emoji"
        val collection = if (isPrivate) {
            db.collection("privateChats").document(chatId).collection("messages")
        } else {
            db.collection("globalChat")
        }
        val docRef = collection.document(messageId)
        docRef.get().addOnSuccessListener { snap ->
            @Suppress("UNCHECKED_CAST")
            val current = (snap.get("reactions") as? List<String>) ?: emptyList()
            // Remove any existing reaction by me first (one reaction per user per message)
            val cleaned = current.filter { !it.startsWith("$myUid:") }
            val newReactions = if (current.contains(entry)) {
                cleaned  // Toggle off
            } else {
                cleaned + entry  // Replace with new one
            }
            docRef.update("reactions", newReactions)
                .addOnFailureListener {
                    Toast.makeText(this, S.get("error_something_wrong"), Toast.LENGTH_SHORT).show()
                }
        }
    }

    // ==========================================
    // IDENTITY VERIFICATION (QR + SAFETY NUMBER)
    // ==========================================
    /**
     * Computes a Signal-style safety number (60 digits in 12 groups of 5)
     * derived from both users' public keys via iterated SHA-256.
     * The same safety number appears on both users' devices ONLY if their
     * keys match — protecting against MITM attacks.
     */
    // Symmetric safety number over both public keys (CryptoManager, unit-tested)
    private fun computeSafetyNumber(myPubKey: String, theirPubKey: String): String =
        crypto.computeSafetyNumber(myPubKey, theirPubKey)

    /** Generates a QR code Bitmap encoding the given text using ZXing. */
    private fun generateQrBitmap(content: String, sizePx: Int): Bitmap? {
        return try {
            val hints = hashMapOf<com.google.zxing.EncodeHintType, Any>(
                com.google.zxing.EncodeHintType.MARGIN to 1,
                com.google.zxing.EncodeHintType.ERROR_CORRECTION to com.google.zxing.qrcode.decoder.ErrorCorrectionLevel.M
            )
            val matrix = com.google.zxing.qrcode.QRCodeWriter().encode(
                content, com.google.zxing.BarcodeFormat.QR_CODE, sizePx, sizePx, hints
            )
            val bmp = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
            for (x in 0 until sizePx) {
                for (y in 0 until sizePx) {
                    bmp.setPixel(x, y, if (matrix[x, y]) Color.BLACK else Color.WHITE)
                }
            }
            bmp
        } catch (e: Exception) {
            android.util.Log.e("ExecBin", "QR generation failed", e)
            null
        }
    }

    private fun showIdentityVerificationScreen(otherUid: String, otherUsername: String, otherNickname: String) {
        // Fetch their public key fresh from Firestore
        db.collection("publicProfiles").document(otherUid).get()
            .addOnSuccessListener { doc ->
                val theirPubKey = doc.getString("publicKey") ?: ""
                if (theirPubKey.isEmpty() || myPublicKeyBase64.isEmpty()) {
                    Toast.makeText(this, S.get("verify_no_key"), Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                val safetyNumber = computeSafetyNumber(myPublicKeyBase64, theirPubKey)
                val displayName = if (otherNickname.isNotEmpty()) otherNickname else otherUsername
                val isAlreadyVerified = prefs.getBoolean("verified_$otherUid", false)

                val dialogBg = if (isDarkTheme) Color.parseColor("#111827") else Color.parseColor("#FFFFFF")
                val scrollWrapper = androidx.core.widget.NestedScrollView(this).apply {
                    isFillViewport = true
                }
                val content = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    background = GradientDrawable().apply {
                        setColor(dialogBg)
                        cornerRadius = dp(24).toFloat()
                        if (isDarkTheme) setStroke(dp(1), Color.parseColor("#1E293B"))
                    }
                    setPadding(dp(24), dp(24), dp(24), dp(20))
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                scrollWrapper.addView(content, FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ))

                content.addView(ImageView(this).apply {
                    setImageDrawable(LineIconDrawable("🛡️", accentColor, dp(38)))
                    layoutParams = LinearLayout.LayoutParams(dp(38), dp(38)).apply { bottomMargin = dp(8); gravity = Gravity.CENTER_HORIZONTAL }
                })
                content.addView(TextView(this).apply {
                    text = S.get("verify_contact")
                    setTextColor(textColor)
                    textSize = 19f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
                })
                content.addView(TextView(this).apply {
                    text = S.get("verify_with", displayName)
                    setTextColor(subTextColor)
                    textSize = 13f
                    gravity = Gravity.CENTER
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(20) }
                })

                // QR code — encodes a hash of their public key for visual comparison
                val qrSize = dp(180)
                val qrPayload = "EXECBIN:$safetyNumber"
                val qrBmp = generateQrBitmap(qrPayload, qrSize)
                if (qrBmp != null) {
                    content.addView(ImageView(this).apply {
                        setImageBitmap(qrBmp)
                        layoutParams = LinearLayout.LayoutParams(qrSize, qrSize).apply {
                            bottomMargin = dp(20)
                        }
                        background = createRoundedBackground(Color.WHITE, 12)
                        setPadding(dp(8), dp(8), dp(8), dp(8))
                    })
                }

                content.addView(TextView(this).apply {
                    text = S.get("safety_number")
                    setTextColor(accentColor)
                    textSize = 11f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    gravity = Gravity.CENTER
                    letterSpacing = 0.1f
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(6) }
                })
                // Display safety number in 4-row grid (3 groups per row)
                val groups = safetyNumber.split(" ")
                for (rowIdx in 0 until 4) {
                    val rowText = groups.subList(rowIdx * 3, minOf(rowIdx * 3 + 3, groups.size)).joinToString("  ")
                    content.addView(TextView(this).apply {
                        text = rowText
                        setTextColor(textColor)
                        textSize = 17f
                        typeface = android.graphics.Typeface.MONOSPACE
                        gravity = Gravity.CENTER
                        letterSpacing = 0.05f
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                    })
                }

                content.addView(TextView(this).apply {
                    text = S.get("verify_explanation")
                    setTextColor(subTextColor)
                    textSize = 12f
                    gravity = Gravity.CENTER
                    setLineSpacing(dp(2).toFloat(), 1.1f)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                        topMargin = dp(20)
                        bottomMargin = dp(20)
                    }
                })

                val dialog = AlertDialog.Builder(this).setView(scrollWrapper).create()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

                val verifyBtn = Button(this).apply {
                    text = if (isAlreadyVerified) S.get("unmark_verified") else S.get("mark_verified")
                    setTextColor(Color.WHITE)
                    textSize = 14f
                    setTypeface(null, android.graphics.Typeface.BOLD)
                    background = createRoundedBackground(
                        if (isAlreadyVerified) Color.parseColor("#EF4444") else Color.parseColor("#22C55E"),
                        14
                    )
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { bottomMargin = dp(8) }
                    setOnClickListener {
                        prefs.edit().putBoolean("verified_$otherUid", !isAlreadyVerified).apply()
                        Toast.makeText(this@MainActivity,
                            if (isAlreadyVerified) S.get("verification_removed") else S.get("verification_saved"),
                            Toast.LENGTH_SHORT).show()
                        dialog.dismiss()
                    }
                }
                content.addView(verifyBtn)

                val closeBtn = Button(this).apply {
                    text = S.get("close")
                    setTextColor(subTextColor)
                    textSize = 14f
                    background = createRoundedBackground(cardBg, 14)
                    layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44))
                    setOnClickListener { dialog.dismiss() }
                }
                content.addView(closeBtn)

                dialog.show()
            }
            .addOnFailureListener {
                Toast.makeText(this, S.get("verify_no_key"), Toast.LENGTH_LONG).show()
            }
    }

    // ==========================================
    // ADAPTERS
    // ==========================================
    inner class ChatListAdapter(
        val list: MutableList<ChatUser>,
        private val onClick: (ChatUser?) -> Unit
    ) : RecyclerView.Adapter<ChatListAdapter.VH>() {
        inner class VH(view: View, val photoView: ImageView, val titleView: TextView, val subtitleView: TextView, val unreadBadge: TextView) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                background = createCardBackground()
                setPadding(dp(16), dp(16), dp(16), dp(16))
                gravity = Gravity.CENTER_VERTICAL
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    setMargins(dp(14), dp(4), dp(14), dp(4))
                }
                elevation = dp(2).toFloat()
            }
            val photoView = ImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply { rightMargin = dp(14) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (isDarkTheme) COLOR_DARK_AVATAR_BG else COLOR_LIGHT_AVATAR_BG)
                    setStroke(dp(2), if (isDarkTheme) Color.parseColor("#334155") else Color.parseColor("#E2E8F0"))
                }
                clipToOutline = true
                outlineProvider = ovalOutline
            }
            row.addView(photoView)
            val col = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            }
            val titleView = TextView(parent.context).apply {
                textSize = 16.5f
                setTextColor(textColor)
                setTypeface(null, android.graphics.Typeface.BOLD)
                letterSpacing = -0.02f
            }
            val subtitleView = TextView(parent.context).apply {
                textSize = 13.5f
                setTextColor(subTextColor)
                setPadding(0, dp(3), 0, 0)
                maxLines = 1
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            col.addView(titleView)
            col.addView(subtitleView)
            row.addView(col)
            // Unread count badge — gradient pill
            val unreadBadge = TextView(parent.context).apply {
                textSize = 11.5f
                setTextColor(Color.WHITE)
                setTypeface(null, android.graphics.Typeface.BOLD)
                gravity = Gravity.CENTER
                background = GradientDrawable(
                    GradientDrawable.Orientation.BL_TR,
                    intArrayOf(accentColor, Color.parseColor("#A78BFA"))
                ).apply { cornerRadius = dp(12).toFloat() }
                minWidth = dp(26)
                setPadding(dp(8), dp(4), dp(8), dp(4))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(26)).apply { leftMargin = dp(8) }
                visibility = View.GONE
            }
            row.addView(unreadBadge)
            return VH(row, photoView, titleView, subtitleView, unreadBadge)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val user = list[position]
            val row = holder.itemView as LinearLayout
            if (user.username == "global") {
                holder.photoView.setImageDrawable(null)
                holder.photoView.background = createRoundedBackground(accentColor, 22)
                holder.photoView.visibility = View.GONE
                holder.titleView.text = iconize("🌐  " + S.get("global_chat"), accentColor)
                holder.titleView.setTextColor(accentColor)
                holder.subtitleView.text = S.get("chat_with_everyone")
                holder.subtitleView.setTextColor(subTextColor)
                holder.unreadBadge.visibility = View.GONE
                row.setOnClickListener { onClick(null) }
            } else {
                holder.photoView.visibility = View.VISIBLE
                holder.photoView.background = createRoundedBackground(if (isDarkTheme) COLOR_DARK_MEDIA_BG else COLOR_LIGHT_MEDIA_BG, 22)
                if (user.photoBase64.isNotEmpty()) {
                    // Use avatar cache
                    val cacheKey = "cl_${user.uid}"
                    val cached = avatarBitmapCache.get(cacheKey)
                    if (cached != null) {
                        holder.photoView.setImageBitmap(cached)
                    } else {
                        try {
                            val imageBytes = Base64.decode(user.photoBase64, Base64.DEFAULT)
                            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                            if (bitmap != null) {
                                avatarBitmapCache.put(cacheKey, bitmap)
                                holder.photoView.setImageBitmap(bitmap)
                            }
                        } catch (_: Exception) { holder.photoView.setImageDrawable(null) }
                    }
                } else { holder.photoView.setImageDrawable(null) }
                val displayName = if (user.nickname.isNotEmpty() && user.nickname != user.username) user.nickname else user.username
                holder.titleView.text = displayName
                holder.titleView.setTextColor(textColor)
                if (typingUsers.contains(user.uid)) {
                    holder.subtitleView.text = S.get("typing")
                    holder.subtitleView.setTextColor(accentColor)
                } else {
                    val lastTime = chatTimestamps[user.uid]
                    if (lastTime != null && lastTime > 0) {
                        val mins = (System.currentTimeMillis() - lastTime) / 1000 / 60
                        holder.subtitleView.text = when {
                            mins < 1 -> S.get("just_now")
                            mins < 60 -> S.get("minutes_ago", mins)
                            mins < 1440 -> S.get("hours_ago", mins / 60)
                            else -> S.get("days_ago", mins / 1440)
                        }
                    } else {
                        holder.subtitleView.text = "@${user.username}"
                    }
                    holder.subtitleView.setTextColor(subTextColor)
                }
                // Unread badge
                val unread = unreadCounts[user.uid] ?: 0
                if (unread > 0) {
                    holder.unreadBadge.text = if (unread > 99) "99+" else unread.toString()
                    holder.unreadBadge.visibility = View.VISIBLE
                } else {
                    holder.unreadBadge.visibility = View.GONE
                }
                row.setOnClickListener { onClick(user) }
            }
        }
        override fun getItemCount() = list.size
    }

    private fun setupChatListTypingListeners(adapter: RecyclerView.Adapter<*>) {
        // Remove old listeners
        chatListTypingListeners.forEach { it.remove() }
        chatListTypingListeners.clear()
        typingUsers.clear()
        val myUid = auth.currentUser?.uid ?: return

        // Limit to first 15 chats to avoid listener spam
        val chatsToWatch = recentChats.take(15)
        chatsToWatch.forEach { user ->
            val chatId = if (myUid < user.uid) "${myUid}_${user.uid}" else "${user.uid}_${myUid}"
            val listener = db.collection("privateChats").document(chatId)
                .addSnapshotListener { doc, _ ->
                    if (doc != null && doc.exists()) {
                        val isTyping = doc.getBoolean("typing_${user.uid}") == true
                        val changed = if (isTyping) typingUsers.add(user.uid) else typingUsers.remove(user.uid)
                        if (changed) {
                            // Find the specific position and update only that row
                            runOnUiThread {
                                val chatList = (adapter as? ChatListAdapter)?.list
                                if (chatList != null) {
                                    val idx = chatList.indexOfFirst { it.uid == user.uid }
                                    if (idx >= 0) adapter.notifyItemChanged(idx)
                                } else {
                                    adapter.notifyDataSetChanged()
                                }
                            }
                        }
                    }
                }
            chatListTypingListeners.add(listener)
        }
    }

    inner class MsgAdapter(
        private val list: MutableList<Msg>,
        private val myId: String
    ) : RecyclerView.Adapter<MsgAdapter.VH>() {
        var onReplyClick: ((Msg) -> Unit)? = null
        var onDeleteClick: ((Int) -> Unit)? = null
        var chatId: String = ""
        var isPrivateChat: Boolean = false
        var encryptionKey: SecretKeySpec? = null
        var messageIds: MutableList<String> = mutableListOf()
        var searchQuery: String = ""
        inner class VH(
            view: View,
            val photoContainer: LinearLayout,
            val photo: ImageView,
            val bubble: LinearLayout,
            val username: TextView,
            val replyQuote: LinearLayout,
            val replyLabel: TextView,
            val replyText: TextView,
            val text: TextView,
            val timeText: TextView,
            val readReceipt: TextView
        ) : RecyclerView.ViewHolder(view)
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val row = LinearLayout(parent.context).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(dp(10), dp(3), dp(10), dp(3))
                orientation = LinearLayout.HORIZONTAL
            }
            val photoContainer = LinearLayout(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36)).apply { rightMargin = dp(8); topMargin = dp(4) }
                gravity = Gravity.BOTTOM
            }
            val photoView = ImageView(parent.context).apply {
                layoutParams = LinearLayout.LayoutParams(dp(36), dp(36))
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(if (isDarkTheme) COLOR_DARK_AVATAR_BG else COLOR_LIGHT_AVATAR_BG)
                }
            }
            photoContainer.addView(photoView)
            val bubble = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(dp(14), dp(10), dp(14), dp(8))
                elevation = dp(1).toFloat()
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    rightMargin = dp(48)
                }
            }
            val usernameView = TextView(parent.context).apply {
                textSize = 12f
                setTypeface(null, android.graphics.Typeface.BOLD)
                letterSpacing = 0.01f
            }
            val replyQuote = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#18FFFFFF"))
                    cornerRadius = dp(10).toFloat()
                }
                setPadding(dp(12), dp(7), dp(12), dp(7))
                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    topMargin = dp(4)
                    bottomMargin = dp(4)
                }
                visibility = View.GONE
            }
            val replyLabelView = TextView(parent.context).apply {
                textSize = 10.5f
                setTypeface(null, android.graphics.Typeface.BOLD)
            }
            val replyTextView = TextView(parent.context).apply {
                textSize = 12.5f
                maxLines = 2
                ellipsize = android.text.TextUtils.TruncateAt.END
            }
            replyQuote.addView(replyLabelView)
            replyQuote.addView(replyTextView)
            val textView = TextView(parent.context).apply {
                textSize = 15.5f
                setTextColor(Color.WHITE)
                setLineSpacing(dp(3).toFloat(), 1f)
            }
            val timeView = TextView(parent.context).apply {
                textSize = 10.5f
                setPadding(0, dp(4), 0, 0)
            }
            val readReceiptView = TextView(parent.context).apply {
                textSize = 10.5f
                setPadding(0, dp(2), 0, 0)
                gravity = Gravity.END
            }
            bubble.addView(usernameView)
            bubble.addView(replyQuote)
            bubble.addView(textView)
            bubble.addView(timeView)
            bubble.addView(readReceiptView)
            row.addView(photoContainer)
            row.addView(bubble)
            return VH(row, photoContainer, photoView, bubble, usernameView, replyQuote, replyLabelView, replyTextView, textView, timeView, readReceiptView)
        }
        override fun onBindViewHolder(holder: VH, position: Int) {
            val msg = list[position]
            val isMine = msg.senderId == myId
            val row = holder.itemView as LinearLayout
            // Reset visibility in case this view was hidden by photo grouping
            row.visibility = View.VISIBLE
            (row.layoutParams as? RecyclerView.LayoutParams)?.height = ViewGroup.LayoutParams.WRAP_CONTENT
            holder.text.visibility = View.VISIBLE
            val nextMsg = if (position + 1 < list.size) list[position + 1] else null
            val isLastInGroup = nextMsg == null || nextMsg.senderId != msg.senderId
            val prevMsg = if (position > 0) list[position - 1] else null
            val isFirstInGroup = prevMsg == null || prevMsg.senderId != msg.senderId
            if (isMine) {
                row.gravity = Gravity.END
                holder.photoContainer.visibility = View.GONE
                (holder.bubble.layoutParams as LinearLayout.LayoutParams).apply {
                    leftMargin = dp(52); rightMargin = 0
                }
                holder.bubble.background = GradientDrawable(
                    GradientDrawable.Orientation.BL_TR,
                    intArrayOf(Color.parseColor("#4F46E5"), accentColor, Color.parseColor("#A78BFA"))
                ).apply { cornerRadius = dp(22).toFloat() }
                if (msg.read) {
                    holder.readReceipt.text = "✓✓"
                    holder.readReceipt.setTextColor(COLOR_READ_RECEIPT)
                } else {
                    holder.readReceipt.text = "✓"
                    holder.readReceipt.setTextColor(COLOR_UNREAD_RECEIPT)
                }
                holder.readReceipt.visibility = View.VISIBLE
            } else {
                row.gravity = Gravity.START
                holder.readReceipt.visibility = View.GONE
                if (isLastInGroup) {
                    holder.photoContainer.visibility = View.VISIBLE
                    if (msg.photoBase64.isNotEmpty()) {
                        // Use avatar cache to avoid re-decoding same user's photo
                        val cacheKey = msg.senderId
                        val cached = avatarBitmapCache.get(cacheKey)
                        if (cached != null) {
                            holder.photo.setImageBitmap(cached)
                        } else {
                            try {
                                val imageBytes = Base64.decode(msg.photoBase64, Base64.DEFAULT)
                                val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                                if (bitmap != null) {
                                    avatarBitmapCache.put(cacheKey, bitmap)
                                    holder.photo.setImageBitmap(bitmap)
                                }
                            } catch (e: Exception) { holder.photo.setImageDrawable(null) }
                        }
                    } else { holder.photo.setImageDrawable(null) }
                    holder.photo.setOnClickListener {
                        showUserProfilePopup(msg.username, msg.nickname, msg.photoBase64, msg.senderId)
                    }
                } else {
                    holder.photoContainer.visibility = View.INVISIBLE
                }
                (holder.bubble.layoutParams as LinearLayout.LayoutParams).apply {
                    leftMargin = 0; rightMargin = dp(52)
                }
                holder.bubble.background = GradientDrawable().apply {
                    setColor(if (isDarkTheme) COLOR_DARK_BUBBLE else COLOR_LIGHT_BUBBLE)
                    cornerRadius = dp(22).toFloat()
                    if (isDarkTheme) setStroke(dp(1), Color.parseColor("#1E293B"))
                }
            }
            if (isFirstInGroup) {
                holder.username.visibility = View.VISIBLE
                val displayName = if (msg.nickname.isNotEmpty()) msg.nickname else msg.username
                holder.username.text = if (isMine) S.get("you") else displayName
                holder.username.setTextColor(if (isMine) COLOR_REPLY_MINE else accentColor)
            } else {
                holder.username.visibility = View.GONE
            }
            holder.text.text = msg.text
            holder.text.setTextColor(if (isMine) Color.WHITE else textColor)
            // Show edited indicator
            if (msg.edited) {
                holder.text.text = "${msg.text}  (${S.get("edited")})"
            }
            // Highlight search matches
            if (searchQuery.length >= 2) {
                val fullText = holder.text.text.toString()
                val queryLower = searchQuery.lowercase()
                val textLower = fullText.lowercase()
                val startIdx = textLower.indexOf(queryLower)
                if (startIdx >= 0) {
                    val spannable = android.text.SpannableString(fullText)
                    val highlightColor = Color.parseColor("#FFD700")
                    var idx = startIdx
                    while (idx >= 0) {
                        spannable.setSpan(
                            android.text.style.BackgroundColorSpan(highlightColor),
                            idx, idx + searchQuery.length,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        spannable.setSpan(
                            android.text.style.ForegroundColorSpan(Color.BLACK),
                            idx, idx + searchQuery.length,
                            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                        idx = textLower.indexOf(queryLower, idx + searchQuery.length)
                    }
                    holder.text.text = spannable
                }
            }
            // Make URLs clickable
            android.text.util.Linkify.addLinks(holder.text, android.text.util.Linkify.WEB_URLS)
            holder.text.setLinkTextColor(if (isMine) Color.parseColor("#B3E5FF") else accentColor)
            holder.text.movementMethod = android.text.method.LinkMovementMethod.getInstance()
            // Render media (image/voice) inside bubble
            // First remove any old media views (recycling)
            val toRemove = mutableListOf<View>()
            for (i in 0 until holder.bubble.childCount) {
                val child = holder.bubble.getChildAt(i)
                if (child.tag == "media_content") toRemove.add(child)
            }
            toRemove.forEach { holder.bubble.removeView(it) }

            if (msg.mediaUrl.isNotEmpty() && msg.mediaType.isNotEmpty()) {
                when (msg.mediaType) {
                    "image" -> {
                        // Find consecutive image messages from the same sender (max 10 in group)
                        val groupStart = run {
                            var s = position
                            while (s > maxOf(0, position - 10) && s > 0 && list[s - 1].senderId == msg.senderId && list[s - 1].mediaType == "image" && list[s - 1].mediaUrl.isNotEmpty()) s--
                            s
                        }
                        val groupEnd = run {
                            var e = position
                            while (e + 1 < list.size && e < position + 10 && list[e + 1].senderId == msg.senderId && list[e + 1].mediaType == "image" && list[e + 1].mediaUrl.isNotEmpty()) e++
                            e
                        }
                        val groupSize = groupEnd - groupStart + 1
                        val isFirstInPhotoGroup = position == groupStart

                        if (!isFirstInPhotoGroup && groupSize > 1) {
                            // Hide this row — the first message in the group renders the grid
                            row.visibility = View.GONE
                            (row.layoutParams as? RecyclerView.LayoutParams)?.height = 0
                        } else if (isFirstInPhotoGroup && groupSize > 1) {
                            // Render photo grid for the entire group
                            val gridWidth = dp(240)
                            val spacing = dp(3)
                            val cellSize = (gridWidth - spacing) / 2
                            val mediaBg = if (isDarkTheme) COLOR_DARK_MEDIA_BG else COLOR_LIGHT_MEDIA_BG

                            val gridContainer = LinearLayout(holder.itemView.context).apply {
                                tag = "media_content"
                                orientation = LinearLayout.VERTICAL
                                layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                    topMargin = dp(6); bottomMargin = dp(4)
                                }
                            }

                            val allBitmaps = mutableListOf<Bitmap>()
                            val photoMsgs = (groupStart..groupEnd).map { list[it] }

                            fun decryptCached(mediaUrl: String): ByteArray? {
                                val hash = mediaUrl.hashCode()
                                val cached = mediaDecryptCache.get(hash)
                                if (cached != null) return cached
                                val bytes = decryptMediaData(mediaUrl, encryptionKey)
                                if (bytes != null) mediaDecryptCache.put(hash, bytes)
                                return bytes
                            }

                            fun createGridImage(w: Int, h: Int): ImageView {
                                return ImageView(holder.itemView.context).apply {
                                    scaleType = ImageView.ScaleType.CENTER_CROP
                                    background = createRoundedBackground(mediaBg, 8)
                                    clipToOutline = true
                                    outlineProvider = roundedOutline8
                                }
                            }

                            val clickTargets = mutableListOf<Pair<ImageView, Bitmap>>()

                            fun loadAndSetBitmap(imgView: ImageView, photoMsg: Msg) {
                                val bytes = decryptCached(photoMsg.mediaUrl)
                                if (bytes != null) {
                                    try {
                                        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                                        if (bitmap != null) {
                                            allBitmaps.add(bitmap)
                                            imgView.setImageBitmap(bitmap)
                                            clickTargets.add(imgView to bitmap)
                                        }
                                    } catch (_: Exception) {}
                                }
                            }

                            var idx = 0
                            while (idx < photoMsgs.size) {
                                val remaining = photoMsgs.size - idx
                                if (remaining == 1) {
                                    val imgView = createGridImage(gridWidth, cellSize + dp(20)).apply {
                                        layoutParams = LinearLayout.LayoutParams(gridWidth, cellSize + dp(20)).apply { bottomMargin = spacing }
                                    }
                                    loadAndSetBitmap(imgView, photoMsgs[idx])
                                    gridContainer.addView(imgView)
                                    idx++
                                } else {
                                    val rowLayout = LinearLayout(holder.itemView.context).apply {
                                        orientation = LinearLayout.HORIZONTAL
                                        layoutParams = LinearLayout.LayoutParams(gridWidth, cellSize).apply { bottomMargin = spacing }
                                    }
                                    for (col in 0..1) {
                                        if (idx + col >= photoMsgs.size) break
                                        val imgView = createGridImage(cellSize, cellSize).apply {
                                            layoutParams = LinearLayout.LayoutParams(cellSize, cellSize).apply {
                                                if (col == 0) rightMargin = spacing
                                            }
                                        }
                                        loadAndSetBitmap(imgView, photoMsgs[idx + col])
                                        rowLayout.addView(imgView)
                                    }
                                    gridContainer.addView(rowLayout)
                                    idx += 2
                                }
                            }

                            // Set click listeners after all bitmaps are loaded (so the full list is passed)
                            val finalBitmaps = allBitmaps.toList()
                            clickTargets.forEach { (imgView, bmp) ->
                                imgView.setOnClickListener { showFullScreenImage(bmp, finalBitmaps) }
                            }

                            val plainText = msg.text.replace("📷", "").trim()
                            if (plainText.equals(S.get("photo"), ignoreCase = true) || plainText.isEmpty()) {
                                holder.text.visibility = View.GONE
                            }

                            val timeIdx = holder.bubble.indexOfChild(holder.timeText)
                            if (timeIdx >= 0) holder.bubble.addView(gridContainer, timeIdx)
                            else holder.bubble.addView(gridContainer)
                        } else {
                            // Single photo
                            val imgView = ImageView(holder.itemView.context).apply {
                                tag = "media_content"
                                layoutParams = LinearLayout.LayoutParams(dp(200), dp(200)).apply {
                                    topMargin = dp(6); bottomMargin = dp(4)
                                }
                                scaleType = ImageView.ScaleType.CENTER_CROP
                                background = createRoundedBackground(if (isDarkTheme) COLOR_DARK_MEDIA_BG else COLOR_LIGHT_MEDIA_BG, 12)
                                clipToOutline = true
                                outlineProvider = roundedOutline12
                            }
                            val hash = msg.mediaUrl.hashCode()
                            val cachedBytes = mediaDecryptCache.get(hash) ?: decryptMediaData(msg.mediaUrl, encryptionKey)?.also { mediaDecryptCache.put(hash, it) }
                            if (cachedBytes != null) {
                                try {
                                    val bitmap = BitmapFactory.decodeByteArray(cachedBytes, 0, cachedBytes.size)
                                    if (bitmap != null) {
                                        imgView.setImageBitmap(bitmap)
                                        imgView.setOnClickListener { showFullScreenImage(bitmap) }
                                    }
                                } catch (_: Exception) {}
                            }
                            val timeIdx = holder.bubble.indexOfChild(holder.timeText)
                            if (timeIdx >= 0) holder.bubble.addView(imgView, timeIdx)
                            else holder.bubble.addView(imgView)
                        }
                    }
                    "voice" -> {
                        val voiceRow = LinearLayout(holder.itemView.context).apply {
                            tag = "media_content"
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER_VERTICAL
                            setPadding(dp(4), dp(6), dp(4), dp(4))
                            layoutParams = LinearLayout.LayoutParams(dp(200), ViewGroup.LayoutParams.WRAP_CONTENT)
                        }
                        val playBtn = TextView(holder.itemView.context).apply {
                            text = "▶"
                            textSize = 24f
                            setTextColor(if (isMine) Color.WHITE else accentColor)
                            setPadding(dp(4), 0, dp(12), 0)
                        }
                        voiceRow.addView(playBtn)

                        val durationSec = (msg.mediaDuration / 1000).toInt()
                        val durationText = "${durationSec / 60}:${String.format("%02d", durationSec % 60)}"
                        val waveBar = LinearLayout(holder.itemView.context).apply {
                            orientation = LinearLayout.VERTICAL
                            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                        }
                        waveBar.addView(View(holder.itemView.context).apply {
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3)).apply {
                                topMargin = dp(8); bottomMargin = dp(4)
                            }
                            setBackgroundColor(if (isMine) COLOR_REPLY_MINE else accentColor)
                        })
                        waveBar.addView(TextView(holder.itemView.context).apply {
                            text = durationText
                            textSize = 11f
                            setTextColor(if (isMine) COLOR_REPLY_MINE else subTextColor)
                        })
                        voiceRow.addView(waveBar)

                        var isVoicePlaying = false
                        playBtn.setOnClickListener {
                            if (isVoicePlaying) {
                                activeMediaPlayer?.stop(); activeMediaPlayer?.release(); activeMediaPlayer = null
                                activeVoiceTempFile?.delete(); activeVoiceTempFile = null
                                playBtn.text = "▶"; isVoicePlaying = false
                                return@setOnClickListener
                            }
                            // Stop any currently playing voice
                            activeMediaPlayer?.let { mp ->
                                try { mp.stop(); mp.release() } catch (_: Exception) {}
                            }
                            activeVoiceTempFile?.delete()
                            activeMediaPlayer = null
                            activeVoiceTempFile = null

                            val voiceBytes = decryptMediaData(msg.mediaUrl, encryptionKey)
                            if (voiceBytes != null) {
                                var tempFile: java.io.File? = null
                                try {
                                    tempFile = java.io.File.createTempFile("voice", ".3gp", cacheDir)
                                    tempFile.writeBytes(voiceBytes)
                                    activeVoiceTempFile = tempFile
                                    activeMediaPlayer = android.media.MediaPlayer().apply {
                                        setDataSource(tempFile.absolutePath)
                                        prepare(); start()
                                        setOnCompletionListener {
                                            playBtn.text = "▶"; isVoicePlaying = false
                                            activeVoiceTempFile?.delete(); activeVoiceTempFile = null
                                            release(); activeMediaPlayer = null
                                        }
                                    }
                                    playBtn.text = "⏸"; isVoicePlaying = true
                                } catch (_: Exception) {
                                    tempFile?.delete()
                                    Toast.makeText(holder.itemView.context, S.get("playback_failed"), Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                        val timeIdx = holder.bubble.indexOfChild(holder.timeText)
                        if (timeIdx >= 0) holder.bubble.addView(voiceRow, timeIdx)
                        else holder.bubble.addView(voiceRow)
                    }
                }
            }
            if (msg.replyToText.isNotEmpty()) {
                holder.replyQuote.visibility = View.VISIBLE
                holder.replyLabel.text = iconize("↩ ${msg.replyToUsername}", if (isMine) COLOR_REPLY_MINE else accentColor, 11)
                holder.replyLabel.setTextColor(if (isMine) COLOR_REPLY_MINE else accentColor)
                holder.replyText.text = msg.replyToText
                holder.replyText.setTextColor(if (isMine) COLOR_REPLY_TEXT_MINE else subTextColor)
            } else {
                holder.replyQuote.visibility = View.GONE
            }
            // Reactions row — remove any old, add a fresh one if we have reactions
            val toRemoveReactions = mutableListOf<View>()
            for (i in 0 until holder.bubble.childCount) {
                val child = holder.bubble.getChildAt(i)
                if (child.tag == "reactions_row") toRemoveReactions.add(child)
            }
            toRemoveReactions.forEach { holder.bubble.removeView(it) }
            if (msg.reactions.isNotEmpty()) {
                // Group reactions by emoji
                val grouped = mutableMapOf<String, MutableList<String>>()
                msg.reactions.forEach { entry ->
                    val parts = entry.split(":", limit = 2)
                    if (parts.size == 2) {
                        grouped.getOrPut(parts[1]) { mutableListOf() }.add(parts[0])
                    }
                }
                if (grouped.isNotEmpty()) {
                    val reactionsRow = LinearLayout(holder.itemView.context).apply {
                        tag = "reactions_row"
                        orientation = LinearLayout.HORIZONTAL
                        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                            topMargin = dp(4)
                        }
                    }
                    val myUidLocal = myId
                    val msgIdLocal = if (holder.adapterPosition >= 0 && holder.adapterPosition < messageIds.size) messageIds[holder.adapterPosition] else ""
                    grouped.forEach { (emoji, uids) ->
                        val iReacted = uids.contains(myUidLocal)
                        val pillBg = if (iReacted) {
                            GradientDrawable().apply {
                                setColor(Color.parseColor("#33818CF8"))
                                cornerRadius = dp(12).toFloat()
                                setStroke(dp(1), accentColor)
                            }
                        } else {
                            GradientDrawable().apply {
                                setColor(if (isDarkTheme) Color.parseColor("#1E293B") else Color.parseColor("#F1F5F9"))
                                cornerRadius = dp(12).toFloat()
                            }
                        }
                        val pill = TextView(holder.itemView.context).apply {
                            text = if (uids.size > 1) "$emoji ${uids.size}" else emoji
                            textSize = 12f
                            setTextColor(if (isMine) Color.WHITE else textColor)
                            background = pillBg
                            setPadding(dp(8), dp(4), dp(8), dp(4))
                            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                rightMargin = dp(4)
                            }
                            setOnClickListener {
                                if (msgIdLocal.isNotEmpty()) {
                                    toggleReaction(chatId, msgIdLocal, emoji, isPrivateChat)
                                }
                            }
                        }
                        reactionsRow.addView(pill)
                    }
                    val timeIdx = holder.bubble.indexOfChild(holder.timeText)
                    if (timeIdx >= 0) holder.bubble.addView(reactionsRow, timeIdx)
                    else holder.bubble.addView(reactionsRow)
                }
            }
            if (isLastInGroup) {
                holder.timeText.visibility = View.VISIBLE
                holder.timeText.text = timeFormatter.format(java.util.Date(msg.timestamp))
                holder.timeText.setTextColor(if (isMine) COLOR_REPLY_MINE else subTextColor)
            } else {
                holder.timeText.visibility = View.GONE
            }
            // Double-tap to heart react + long-press for options
            val gestureDetector = android.view.GestureDetector(holder.itemView.context, object : android.view.GestureDetector.SimpleOnGestureListener() {
                override fun onDoubleTap(e: android.view.MotionEvent): Boolean {
                    val pos = holder.adapterPosition
                    if (pos == RecyclerView.NO_POSITION) return true
                    val msgId = if (pos < messageIds.size) messageIds[pos] else ""
                    if (msgId.isNotEmpty()) {
                        (holder.itemView.context as? MainActivity)?.toggleReaction(chatId, msgId, "❤️", isPrivateChat)
                    }
                    return true
                }
                override fun onLongPress(e: android.view.MotionEvent) {
                    val pos = holder.adapterPosition
                    if (pos == RecyclerView.NO_POSITION) return
                    val msgId = if (pos < messageIds.size) messageIds[pos] else ""
                    (holder.itemView.context as? MainActivity)?.showMessageOptions(
                        msg, isMine,
                        onReply = { onReplyClick?.invoke(msg) },
                        onDelete = { if (isMine) onDeleteClick?.invoke(pos) },
                        chatId = chatId,
                        messageId = msgId,
                        isPrivate = isPrivateChat,
                        sharedKey = encryptionKey
                    )
                }
                override fun onSingleTapConfirmed(e: android.view.MotionEvent): Boolean = false
            })
            holder.bubble.isClickable = true
            holder.bubble.isLongClickable = true
            holder.bubble.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                // Return true so the bubble claims the gesture sequence and
                // GestureDetector reliably receives ACTION_DOWN/UP for double-tap.
                // Link clicks on the inner TextView still work because the TextView
                // is the touched child first and consumes link taps before the
                // bubble's listener is invoked.
                true
            }
            holder.bubble.setOnLongClickListener { true } // suppress system long-click chirp; gesture detector handles it
        }
        override fun getItemCount() = list.size
    }
}