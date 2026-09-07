package com.simplee2eechat.app

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var store: SecureStore
    private var api: ApiClient? = null
    private var poll = false
    private var currentPeer = ""
    private var currentPeerName = ""
    private lateinit var messagesBox: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var chatStatus: TextView
    private val sentPlaintext = mutableMapOf<String, String>()

    companion object {
        private const val DEFAULT_SERVER = "https://simple-e2ee-chat.onrender.com"
        private const val PREFS = "e2ee_ui"
        private const val CONTACTS = "contacts"
        private const val BG = 0xFFF7F9FC.toInt()
        private const val TEXT = 0xFF172033.toInt()
        private const val MUTED = 0xFF687386.toInt()
        private const val PRIMARY = 0xFF2563EB.toInt()
        private const val CARD = 0xFFFFFFFF.toInt()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureStore(this)
        val id = store.userId()
        val password = store.password()
        if (!id.isNullOrBlank() && !password.isNullOrBlank() && !store.privateKeyBlob().isNullOrBlank()) {
            silentLogin(id, password)
        } else {
            showLogin(id.orEmpty())
        }
    }

    private fun dp(v: Int): Int = (v * resources.displayMetrics.density).toInt()

    private fun root(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(BG)
        setPadding(dp(18), dp(16), dp(18), dp(14))
    }

    private fun text(value: String, size: Float = 16f, color: Int = TEXT, bold: Boolean = false): TextView = TextView(this).apply {
        this.text = value
        textSize = size
        setTextColor(color)
        if (bold) typeface = Typeface.DEFAULT_BOLD
    }

    private fun edit(hint: String, password: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        isSingleLine = true
        textSize = 16f
        setTextColor(TEXT)
        setHintTextColor(MUTED)
        setPadding(dp(16), dp(5), dp(16), dp(5))
        background = rounded(CARD, 14, 0xFFD9E0EA.toInt())
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    }

    private fun button(label: String, primary: Boolean = false): Button = Button(this).apply {
        text = label
        textSize = 14f
        isAllCaps = false
        setTextColor(if (primary) Color.WHITE else TEXT)
        background = rounded(if (primary) PRIMARY else CARD, 14, if (primary) PRIMARY else 0xFFD9E0EA.toInt())
        minHeight = dp(48)
        stateListAnimator = null
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int = 0): GradientDrawable = GradientDrawable().apply {
        setColor(fill)
        cornerRadius = dp(radius).toFloat()
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun spacer(height: Int): Space = Space(this).apply { minimumHeight = dp(height) }

    private fun silentLogin(id: String, password: String) {
        showLoading("Signing you in…")
        executor.execute {
            try {
                val base = getServerUrl()
                val r = ApiClient.login(base, id, password)
                store.saveAccount(r.id, r.token, store.privateKeyBlob()!!, r.publicKey, r.displayName, password)
                api = ApiClient(base, r.token)
                main.post { showChatList(r.id, r.displayName) }
            } catch (e: Exception) {
                main.post { showLogin(id, "Please sign in again") }
            }
        }
    }

    private fun showLoading(message: String) {
        val r = root()
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        box.addView(text("Simple E2EE Chat", 28f, TEXT, true))
        box.addView(spacer(12))
        box.addView(text(message, 16f, MUTED))
        r.addView(box, LinearLayout.LayoutParams(-1, 0, 1f))
        setContentView(r)
    }

    private fun showLogin(prefilledId: String = "", message: String = "") {
        poll = false
        val r = root()
        r.gravity = Gravity.CENTER_HORIZONTAL
        r.setPadding(dp(24), dp(40), dp(24), dp(18))
        r.addView(text("Simple E2EE Chat", 30f, TEXT, true), LinearLayout.LayoutParams(-1, -2))
        r.addView(text("Private one-to-one messaging", 16f, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(7), 0, dp(28)) })

        val id = edit("Messenger ID  •  E2E-XXXXXXXX").apply { setText(prefilledId) }
        val password = edit("Password", true).apply { setText(store.password().orEmpty()) }
        r.addView(id, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(12) })
        r.addView(password, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(16) })
        val status = text(message, 14f, if (message.isBlank()) MUTED else 0xFFB42318.toInt()).apply { gravity = Gravity.CENTER }
        r.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val login = button("Log in", true)
        r.addView(login, LinearLayout.LayoutParams(-1, dp(52)))
        r.addView(spacer(10))
        r.addView(text("Server: simple-e2ee-chat.onrender.com", 13f, MUTED).apply { gravity = Gravity.CENTER })

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        bottom.addView(spacer(14))
        bottom.addView(text("New to Simple E2EE Chat?", 14f, MUTED).apply { gravity = Gravity.CENTER })
        val signup = button("Create a new account")
        signup.setTextColor(PRIMARY)
        signup.background = rounded(Color.TRANSPARENT, 12)
        bottom.addView(signup, LinearLayout.LayoutParams(-1, dp(50)))
        r.addView(bottom, LinearLayout.LayoutParams(-1, 0, 1f))
        r.addView(text("Credentials are stored encrypted on this phone for automatic sign-in.", 12f, MUTED).apply { gravity = Gravity.CENTER })
        setContentView(r)

        login.setOnClickListener {
            val uid = id.text.toString().trim().uppercase()
            val pw = password.text.toString()
            if (!uid.matches(Regex("E2E-[A-Z0-9]{8}")) || pw.length < 8) {
                status.text = "Enter a valid Messenger ID and password"
                return@setOnClickListener
            }
            login.isEnabled = false
            status.text = "Signing in…"
            executor.execute {
                try {
                    val base = getServerUrl()
                    val result = ApiClient.login(base, uid, pw)
                    val privateKey = store.privateKeyBlob() ?: throw IllegalStateException("This account's private key is not on this phone. Create a new account here.")
                    store.saveAccount(result.id, result.token, privateKey, result.publicKey, result.displayName, pw)
                    api = ApiClient(base, result.token)
                    main.post { showChatList(result.id, result.displayName) }
                } catch (e: Exception) {
                    main.post { login.isEnabled = true; status.text = e.message ?: "Login failed" }
                }
            }
        }
        signup.setOnClickListener { showSignup() }
    }

    private fun showSignup() {
        poll = false
        val r = root()
        r.gravity = Gravity.CENTER_HORIZONTAL
        r.setPadding(dp(24), dp(34), dp(24), dp(18))
        r.addView(text("Create your account", 28f, TEXT, true), LinearLayout.LayoutParams(-1, -2))
        r.addView(text("Choose the name people will see in chat.", 15f, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(28)) })
        val name = edit("Your name")
        val password = edit("Password  •  8+ characters", true)
        r.addView(name, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(12) })
        r.addView(password, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(16) })
        val status = text("", 14f, 0xFFB42318.toInt()).apply { gravity = Gravity.CENTER }
        r.addView(status, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(10) })
        val create = button("Create account", true)
        r.addView(create, LinearLayout.LayoutParams(-1, dp(52)))
        val bottom = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER }
        bottom.addView(spacer(14))
        bottom.addView(text("Already registered?", 14f, MUTED).apply { gravity = Gravity.CENTER })
        val back = button("Log in")
        back.setTextColor(PRIMARY); back.background = rounded(Color.TRANSPARENT, 12)
        bottom.addView(back, LinearLayout.LayoutParams(-1, dp(50)))
        r.addView(bottom, LinearLayout.LayoutParams(-1, 0, 1f))
        r.addView(text("A unique Messenger ID will be generated for you.", 12f, MUTED).apply { gravity = Gravity.CENTER })
        setContentView(r)

        create.setOnClickListener {
            val n = name.text.toString().trim(); val pw = password.text.toString()
            if (n.isBlank() || pw.length < 8) { status.text = "Enter your name and an 8+ character password"; return@setOnClickListener }
            create.isEnabled = false; status.text = "Creating your secure identity…"
            executor.execute {
                try {
                    val keys = Crypto.generateKeyPair()
                    val result = ApiClient.register(getServerUrl(), n, pw, keys.publicKey)
                    store.saveAccount(result.id, result.token, keys.privateKey, result.publicKey, n, pw)
                    api = ApiClient(getServerUrl(), result.token)
                    main.post { showWelcome(result.id, n) }
                } catch (e: Exception) {
                    main.post { create.isEnabled = true; status.text = e.message ?: "Registration failed" }
                }
            }
        }
        back.setOnClickListener { showLogin(store.userId().orEmpty()) }
    }

    private fun showWelcome(id: String, name: String) {
        poll = false
        val r = root(); r.gravity = Gravity.CENTER_HORIZONTAL
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        box.addView(text("You're ready ✓", 28f, TEXT, true))
        box.addView(text("Welcome, $name", 18f, MUTED).apply { setPadding(0, dp(10), 0, dp(22)) })
        box.addView(text("Your Messenger ID", 14f, MUTED))
        box.addView(text(id, 27f, TEXT, true).apply { setPadding(0, dp(8), 0, dp(18)) })
        val copy = button("Copy Messenger ID")
        val open = button("Open chats", true)
        box.addView(copy, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(10) })
        box.addView(open, LinearLayout.LayoutParams(-1, dp(50)))
        r.addView(box, LinearLayout.LayoutParams(-1, 0, 1f).apply { gravity = Gravity.CENTER })
        r.addView(text("Share your ID with a friend. Never share your password or private key.", 12f, MUTED).apply { gravity = Gravity.CENTER })
        setContentView(r)
        copy.setOnClickListener {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Messenger ID", id)); toast("Messenger ID copied")
        }
        open.setOnClickListener { showChatList(id, name) }
    }

    private data class Contact(val id: String, val name: String)

    private fun contacts(): MutableList<Contact> {
        val raw = getSharedPreferences(PREFS, MODE_PRIVATE).getString(CONTACTS, "") ?: ""
        if (raw.isBlank()) return mutableListOf()
        return raw.split("\n").mapNotNull {
            val p = it.split("|", limit = 2)
            if (p.size == 2 && p[0].matches(Regex("E2E-[A-Z0-9]{8}"))) Contact(p[0], p[1]) else null
        }.toMutableList()
    }

    private fun saveContact(id: String, name: String) {
        val list = contacts(); list.removeAll { it.id == id }; list.add(0, Contact(id, name))
        val raw = list.take(30).joinToString("\n") { "${it.id}|${it.name.replace("\n", " ")}" }
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(CONTACTS, raw).apply()
    }

    private fun showChatList(myId: String, myName: String) {
        poll = false
        val r = root(); r.setPadding(0, 0, 0, 0)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(20), dp(18), dp(14), dp(14)); setBackgroundColor(Color.WHITE) }
        val titleBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        titleBox.addView(text("Chats", 28f, TEXT, true)); titleBox.addView(text(myName, 13f, MUTED))
        top.addView(titleBox, LinearLayout.LayoutParams(0, -2, 1f))
        val me = button("My ID"); me.setTextSize(12f); me.setOnClickListener { showIdentityCard(myId, myName) }
        top.addView(me, LinearLayout.LayoutParams(dp(74), dp(44))); r.addView(top)

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(10), dp(12), dp(10)) }
        val scroll = ScrollView(this).apply { addView(list) }; r.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val contactsNow = contacts()
        if (contactsNow.isEmpty()) {
            val empty = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER; setPadding(dp(30), dp(50), dp(30), dp(50)) }
            empty.addView(text("No chats yet", 22f, TEXT, true).apply { gravity = Gravity.CENTER })
            empty.addView(text("Start a new chat with your friend's Messenger ID.", 15f, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(8), 0, dp(18)) })
            list.addView(empty)
        } else contactsNow.forEach { addChatRow(list, it) }

        val bottom = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(14), dp(8), dp(14), dp(12)); setBackgroundColor(Color.WHITE) }
        val newChat = button("＋  New chat", true); val logout = button("Log out")
        bottom.addView(newChat, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(8) }); bottom.addView(logout, LinearLayout.LayoutParams(dp(92), dp(52)))
        r.addView(bottom); setContentView(r)
        newChat.setOnClickListener { showFindFriend(myId) }
        logout.setOnClickListener { store.clear(); api = null; showLogin() }
    }

    private fun addChatRow(list: LinearLayout, contact: Contact) {
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(12), dp(10), dp(12)); background = rounded(Color.WHITE, 16) }
        val avatar = TextView(this).apply { text = contact.name.trim().take(1).uppercase(Locale.getDefault()); textSize = 20f; typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER; setTextColor(Color.WHITE); background = rounded(PRIMARY, 50) }
        row.addView(avatar, LinearLayout.LayoutParams(dp(52), dp(52)).apply { rightMargin = dp(14) })
        val info = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        info.addView(text(contact.name, 17f, TEXT, true)); info.addView(text(contact.id, 12f, MUTED).apply { setPadding(0, dp(4), 0, 0) })
        row.addView(info, LinearLayout.LayoutParams(0, -2, 1f)); row.addView(text("›", 30f, MUTED).apply { gravity = Gravity.CENTER }, LinearLayout.LayoutParams(dp(30), dp(52)))
        row.setOnClickListener { showChat(contact.id, contact.name) }
        list.addView(row, LinearLayout.LayoutParams(-1, dp(76)).apply { bottomMargin = dp(8) })
    }

    private fun showFindFriend(myId: String) {
        val r = root(); r.setPadding(0, 0, 0, 18)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(16), dp(16), dp(16), dp(12)); setBackgroundColor(Color.WHITE) }
        val back = button("‹"); back.setTextSize(24f); top.addView(back, LinearLayout.LayoutParams(dp(52), dp(48))); top.addView(text("New chat", 23f, TEXT, true).apply { setPadding(dp(12), 0, 0, 0) }); r.addView(top)
        val body = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(20), dp(22), dp(20), 0) }
        body.addView(text("Start a private conversation", 22f, TEXT, true)); body.addView(text("Enter your friend's Messenger ID.", 15f, MUTED).apply { setPadding(0, dp(7), 0, dp(18)) })
        val id = edit("Friend's ID  •  E2E-XXXXXXXX"); body.addView(id, LinearLayout.LayoutParams(-1, dp(56)).apply { bottomMargin = dp(12) })
        val find = button("Find & open chat", true); body.addView(find, LinearLayout.LayoutParams(-1, dp(52)))
        val status = text("", 14f, MUTED).apply { setPadding(0, dp(14), 0, 0) }; body.addView(status); r.addView(body); setContentView(r)
        back.setOnClickListener { showChatList(myId, store.displayName().orEmpty()) }
        find.setOnClickListener {
            val peer = id.text.toString().trim().uppercase()
            if (!peer.matches(Regex("E2E-[A-Z0-9]{8}")) || peer == myId) { status.text = "Enter the other person's Messenger ID"; return@setOnClickListener }
            find.isEnabled = false; status.text = "Finding user…"
            executor.execute {
                try { val friend = api?.getUser(peer) ?: error("Not logged in"); saveContact(friend.id, friend.displayName); main.post { showChat(friend.id, friend.displayName) } }
                catch (e: Exception) { main.post { find.isEnabled = true; status.text = e.message ?: "User not found" } }
            }
        }
    }

    private fun showIdentityCard(myId: String, myName: String) {
        val r = root(); val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL }
        box.addView(text("My profile", 28f, TEXT, true)); box.addView(text(myName, 19f, MUTED).apply { setPadding(0, dp(8), 0, dp(22)) }); box.addView(text("Messenger ID", 14f, MUTED)); box.addView(text(myId, 25f, TEXT, true).apply { setPadding(0, dp(8), 0, dp(20)) })
        val copy = button("Copy ID", true); val close = button("Back to chats")
        box.addView(copy, LinearLayout.LayoutParams(-1, dp(50)).apply { bottomMargin = dp(10) }); box.addView(close, LinearLayout.LayoutParams(-1, dp(50)))
        r.addView(box, LinearLayout.LayoutParams(-1, 0, 1f).apply { gravity = Gravity.CENTER }); setContentView(r)
        copy.setOnClickListener { val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; cm.setPrimaryClip(ClipData.newPlainText("Messenger ID", myId)); toast("Messenger ID copied") }
        close.setOnClickListener { showChatList(myId, myName) }
    }

    private fun showChat(peer: String, name: String) {
        poll = true; currentPeer = peer; currentPeerName = name; saveContact(peer, name)
        val myId = store.userId().orEmpty(); val r = root(); r.setPadding(0, 0, 0, 0)
        val top = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(10), dp(12), dp(10)); setBackgroundColor(Color.WHITE) }
        val back = button("‹"); back.setTextSize(24f); top.addView(back, LinearLayout.LayoutParams(dp(50), dp(48)))
        val avatar = TextView(this).apply { text = name.take(1).uppercase(Locale.getDefault()); textSize = 18f; gravity = Gravity.CENTER; setTextColor(Color.WHITE); typeface = Typeface.DEFAULT_BOLD; background = rounded(PRIMARY, 50) }
        top.addView(avatar, LinearLayout.LayoutParams(dp(44), dp(44)).apply { leftMargin = dp(5); rightMargin = dp(10) })
        val head = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }; head.addView(text(name, 18f, TEXT, true)); head.addView(text(peer, 11f, MUTED).apply { setPadding(0, dp(2), 0, 0) }); top.addView(head, LinearLayout.LayoutParams(0, -2, 1f)); r.addView(top)

        messagesBox = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(12), dp(12), dp(12), dp(8)) }
        val scroll = ScrollView(this).apply { addView(messagesBox); isFillViewport = true }; r.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        chatStatus = text("End-to-end encrypted", 11f, MUTED).apply { gravity = Gravity.CENTER; setPadding(0, dp(4), 0, dp(5)) }; r.addView(chatStatus)
        val compose = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(12)); setBackgroundColor(Color.WHITE) }
        messageInput = edit("Message"); messageInput.background = rounded(0xFFF1F4F8.toInt(), 24); val send = button("Send", true)
        compose.addView(messageInput, LinearLayout.LayoutParams(0, dp(52), 1f).apply { rightMargin = dp(8) }); compose.addView(send, LinearLayout.LayoutParams(dp(82), dp(52))); r.addView(compose); setContentView(r)

        back.setOnClickListener { poll = false; showChatList(myId, store.displayName().orEmpty()) }
        send.setOnClickListener {
            val body = messageInput.text.toString().trim(); if (body.isBlank() || currentPeer.isBlank()) return@setOnClickListener
            send.isEnabled = false; chatStatus.text = "Encrypting and sending…"
            executor.execute {
                try {
                    val client = api ?: error("Not logged in"); val recipient = client.getUser(currentPeer); val envelope = Crypto.encrypt(body, recipient.publicKey); val messageId = client.sendMessage(currentPeer, myId, envelope); sentPlaintext[messageId] = body
                    main.post { messageInput.setText(""); chatStatus.text = "End-to-end encrypted"; send.isEnabled = true; loadMessages(myId, scroll) }
                } catch (e: Exception) { main.post { send.isEnabled = true; chatStatus.text = e.message ?: "Send failed" } }
            }
        }
        loadMessages(myId, scroll)
    }

    private fun loadMessages(myId: String, scroll: ScrollView) {
        if (!poll || currentPeer.isBlank()) return
        executor.execute {
            try {
                val list = api?.conversation(currentPeer).orEmpty()
                val rows = list.map { m ->
                    val mine = m.from == myId
                    val body = if (mine) sentPlaintext[m.id] ?: "[Sent message]" else try { Crypto.decrypt(m.envelope, store.privateKeyBlob() ?: error("private key missing")) } catch (_: Exception) { "[Unable to decrypt]" }
                    Triple(mine, body, formatTime(m.createdAt))
                }
                main.post { renderMessages(rows); chatStatus.text = if (rows.isEmpty()) "End-to-end encrypted • no messages yet" else "End-to-end encrypted"; scroll.post { scroll.fullScroll(View.FOCUS_DOWN) } }
            } catch (e: Exception) { main.post { chatStatus.text = e.message ?: "Unable to load messages" } }
            main.postDelayed({ loadMessages(myId, scroll) }, 3000)
        }
    }

    private fun renderMessages(rows: List<Triple<Boolean, String, String>>) {
        messagesBox.removeAllViews()
        if (rows.isEmpty()) { messagesBox.addView(text("Messages are encrypted on your device before they are sent.", 13f, MUTED).apply { gravity = Gravity.CENTER; setPadding(dp(28), dp(40), dp(28), dp(40) }); return }
        rows.takeLast(100).forEach { (mine, body, time) ->
            val line = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = if (mine) Gravity.END else Gravity.START }
            val bubble = TextView(this).apply { text = body; textSize = 16f; setTextColor(if (mine) Color.WHITE else TEXT); setPadding(dp(14), dp(10), dp(14), dp(4)); background = rounded(if (mine) PRIMARY else Color.WHITE, 18, if (mine) 0 else 0xFFE0E5EC.toInt()) }
            line.addView(bubble, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(36); rightMargin = dp(36); bottomMargin = dp(2) })
            line.addView(text(time, 10f, MUTED).apply { setPadding(dp(8), 0, dp(8), dp(8)) }); messagesBox.addView(line, LinearLayout.LayoutParams(-1, -2))
        }
    }

    private fun formatTime(value: String): String = try {
        val input = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US); val date: Date = input.parse(value.take(19)) ?: return value.take(16).replace('T', ' '); SimpleDateFormat("HH:mm", Locale.getDefault()).format(date)
    } catch (_: Exception) { value.take(16).replace('T', ' ') }

    private fun saveServer(url: String) { getPreferences(Context.MODE_PRIVATE).edit().putString("server", url).apply() }
    private fun getServerUrl(): String = getPreferences(Context.MODE_PRIVATE).getString("server", DEFAULT_SERVER) ?: DEFAULT_SERVER
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() { poll = false; executor.shutdownNow(); super.onDestroy() }
}
