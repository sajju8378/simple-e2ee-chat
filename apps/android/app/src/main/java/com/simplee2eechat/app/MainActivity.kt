package com.simplee2eechat.app

import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 48, 32, 32)
        }

        val title = TextView(this).apply {
            text = "Simple E2EE Chat"
            textSize = 26f
        }

        val subtitle = TextView(this).apply {
            text = "Private ID-based text messenger"
            textSize = 16f
            setPadding(0, 8, 0, 24)
        }

        val id = EditText(this).apply {
            hint = "Your Messenger ID"
            isSingleLine = true
        }

        val peer = EditText(this).apply {
            hint = "Friend's Messenger ID"
            isSingleLine = true
        }

        val message = EditText(this).apply {
            hint = "Type a message"
            minLines = 3
        }

        val send = Button(this).apply {
            text = "Send securely"
            isEnabled = false
        }

        val note = TextView(this).apply {
            text = "Android shell is ready. Networking and production E2EE session handling are added in the next build stage."
            setPadding(0, 24, 0, 0)
        }

        root.addView(title)
        root.addView(subtitle)
        root.addView(id)
        root.addView(peer)
        root.addView(message)
        root.addView(send)
        root.addView(note)
        setContentView(root)
    }
}
