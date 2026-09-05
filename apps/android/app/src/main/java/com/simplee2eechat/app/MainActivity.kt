package com.simplee2eechat.app

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private lateinit var store: SecureStore
    private var api: ApiClient? = null
    private var currentPeer = ""
    private lateinit var messagesBox: LinearLayout
    private lateinit var messageInput: EditText
    private lateinit var status: TextView
    private var poll = false
    private val sentPlaintext = mutableMapOf<String, String>()

    companion object {
        private const val DEFAULT_SERVER = "https://simple-e2ee-chat-api-sajju8378.onrender.com"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = SecureStore(this)
        val savedUrl = getPreferences(Context.MODE_PRIVATE).getString("server", DEFAULT_SERVER) ?: DEFAULT_SERVER
        val token = store.token()
        val id = store.userId()
        if (!token.isNullOrBlank() && !id.isNullOrBlank()) {
            api = ApiClient(savedUrl, token)
            showChat(id)
        } else {
            showAuth(savedUrl)
        }
    }

    private fun baseLayout(titleText: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(32, 42, 32, 28)
        addView(TextView(this@MainActivity).apply {
            text = titleText
            textSize = 28f
            gravity = Gravity.CENTER_HORIZONTAL
        }, LinearLayout.LayoutParams(-1, -2))
    }

    private fun edit(hint: String, password: Boolean = false): EditText = EditText(this).apply {
        this.hint = hint
        isSingleLine = true
        if (password) inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        setPadding(16, 12, 16, 12)
    }

    private fun showAuth(serverUrl: String) {
        poll = false
        val root = baseLayout("Simple E2EE Chat")
        root.addView(TextView(this).apply {
            text = "Private one-to-one encrypted messaging"
            textSize = 16f
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(0, 8, 0, 24)
        })
        val url = edit("Server URL").apply { setText(serverUrl) }
        val id = edit("Messenger ID (login only)")
        val password = edit("Password", true)
        val display = edit("Display name (new account only)")
        root.addView(url)
        root.addView(id)
        root.addView(password)
        root.addView(display)
        val login = Button(this).apply { text = "Log in" }
        val signup = Button(this).apply { text = "Create new account" }
        root.addView(login)
        root.addView(signup)
        val note = TextView(this).apply {
            text = "Messages are encrypted on this phone before they are sent. The server receives encrypted message envelopes."
            setPadding(0, 24, 0, 0)
        }
        root.addView(note)
        setContentView(root)

        fun busy(b: Boolean) {
            login.isEnabled = !b
            signup.isEnabled = !b
        }

        login.setOnClickListener {
            val base = url.text.toString().trim().trimEnd('/')
            val uid = id.text.toString().trim().uppercase()
            val pw = password.text.toString()
            if (!base.startsWith("https://") && !base.startsWith("http://")) {
                toast("Enter a valid server URL")
                return@setOnClickListener
            }
            if (uid.isBlank() || pw.length < 8) {
                toast("Enter your ID and an 8+ character password")
                return@setOnClickListener
            }
            saveServer(base)
            busy(true)
            note.text = "Logging in securely…"
            executor.execute {
                try {
                    val r = ApiClient.login(base, uid, pw)
                    val privateKey = store.privateKeyBlob()
                        ?: error("This account's private key is not on this phone. Register this device again with a new Messenger ID.")
                    store.saveAccount(r.id, r.token, privateKey, r.publicKey)
                    api = ApiClient(base, r.token)
                    main.post { showChat(r.id) }
                } catch (e: Exception) {
                    main.post {
                        busy(false)
                        note.text = e.message ?: "Login failed"
                    }
                }
            }
        }

        signup.setOnClickListener {
            val base = url.text.toString().trim().trimEnd('/')
            val name = display.text.toString().trim()
            val pw = password.text.toString()
            if (!base.startsWith("https://") && !base.startsWith("http://")) {
                toast("Enter a valid server URL")
                return@setOnClickListener
            }
            if (name.isBlank() || pw.length < 8) {
                toast("Enter a display name and an 8+ character password")
                return@setOnClickListener
            }
            saveServer(base)
            busy(true)
            note.text = "Creating encrypted identity…"
            executor.execute {
                try {
                    val keys = Crypto.generateKeyPair()
                    val r = ApiClient.register(base, name, pw, keys.publicKey)
                    store.saveAccount(r.id, r.token, keys.privateKey, r.publicKey)
                    api = ApiClient(base, r.token)
                    main.post {
                        showChat(r.id)
                        Toast.makeText(this, "Your Messenger ID is ${r.id}", Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    main.post {
                        busy(false)
                        note.text = e.message ?: "Registration failed"
                    }
                }
            }
        }
    }

    private fun showChat(myId: String) {
        poll = true
        val root = baseLayout("Messages")
        root.addView(TextView(this).apply {
            text = "You: $myId"
            textSize = 15f
            setPadding(0, 8, 0, 12)
        })
        val peer = edit("Friend's Messenger ID (E2E-XXXXXXXX)")
        root.addView(peer)
        val open = Button(this).apply { text = "Open conversation" }
        root.addView(open)
        messagesBox = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 12, 0, 12)
        }
        root.addView(ScrollView(this).apply {
            addView(messagesBox)
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        })
        status = TextView(this).apply {
            text = "Choose a friend to start chatting."
            setPadding(0, 6, 0, 8)
        }
        root.addView(status)
        messageInput = edit("Type an encrypted message…")
        root.addView(messageInput)
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val send = Button(this).apply {
            text = "Send securely"
            isEnabled = false
        }
        val logout = Button(this).apply { text = "Log out" }
        row.addView(send, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(logout, LinearLayout.LayoutParams(0, -2, 1f))
        root.addView(row)
        setContentView(root)

        open.setOnClickListener {
            currentPeer = peer.text.toString().trim().uppercase()
            if (!currentPeer.matches(Regex("E2E-[A-Z0-9]{8}"))) {
                toast("Enter a valid Messenger ID")
                return@setOnClickListener
            }
            executor.execute {
                try {
                    api?.getUser(currentPeer) ?: error("Not logged in")
                    main.post {
                        send.isEnabled = true
                        status.text = "Conversation with $currentPeer"
                        loadMessages(myId)
                    }
                } catch (e: Exception) {
                    main.post {
                        status.text = e.message ?: "Friend not found"
                        send.isEnabled = false
                    }
                }
            }
        }

        send.setOnClickListener {
            val text = messageInput.text.toString().trim()
            if (currentPeer.isBlank() || text.isBlank()) return@setOnClickListener
            send.isEnabled = false
            status.text = "Encrypting and sending…"
            executor.execute {
                try {
                    val a = api ?: error("Not logged in")
                    val recipient = a.getUser(currentPeer)
                    val envelope = Crypto.encrypt(text, recipient.publicKey)
                    val messageId = a.sendMessage(currentPeer, myId, envelope)
                    sentPlaintext[messageId] = text
                    main.post {
                        messageInput.setText("")
                        status.text = "Encrypted message sent ✓"
                        send.isEnabled = true
                        loadMessages(myId)
                    }
                } catch (e: Exception) {
                    main.post {
                        status.text = e.message ?: "Send failed"
                        send.isEnabled = true
                    }
                }
            }
        }

        logout.setOnClickListener {
            poll = false
            store.clear()
            api = null
            showAuth(getServerUrl())
        }
    }

    private fun loadMessages(myId: String) {
        if (!poll || currentPeer.isBlank()) return
        executor.execute {
            try {
                val list = api?.conversation(currentPeer) ?: emptyList()
                val out = list.map { m ->
                    val sender = if (m.from == myId) "You" else m.from
                    val text = if (m.from == myId) {
                        sentPlaintext[m.id] ?: "[Sent encrypted message]"
                    } else {
                        try {
                            Crypto.decrypt(m.envelope, store.privateKeyBlob() ?: error("private key missing"))
                        } catch (_: Exception) {
                            "[Unable to decrypt message]"
                        }
                    }
                    "${m.createdAt.take(19).replace('T', ' ')}  $sender: $text"
                }
                main.post {
                    renderMessages(out)
                    status.text = if (out.isEmpty()) "No messages yet." else "End-to-end encrypted conversation"
                }
            } catch (e: Exception) {
                main.post { status.text = e.message ?: "Unable to fetch messages" }
            }
            main.postDelayed({ loadMessages(myId) }, 3000)
        }
    }

    private fun renderMessages(lines: List<String>) {
        messagesBox.removeAllViews()
        for (line in lines.takeLast(100)) {
            messagesBox.addView(TextView(this).apply {
                text = line
                textSize = 16f
                setPadding(10, 10, 10, 10)
            })
        }
    }

    private fun saveServer(url: String) {
        getPreferences(Context.MODE_PRIVATE).edit().putString("server", url).apply()
    }

    private fun getServerUrl(): String =
        getPreferences(Context.MODE_PRIVATE).getString("server", DEFAULT_SERVER) ?: DEFAULT_SERVER

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()

    override fun onDestroy() {
        poll = false
        executor.shutdownNow()
        super.onDestroy()
    }
}
