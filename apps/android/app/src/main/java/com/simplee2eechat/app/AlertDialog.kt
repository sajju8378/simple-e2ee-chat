package com.simplee2eechat.app

import android.content.Context
import android.content.DialogInterface

object AlertDialog {
    class Builder(context: Context) {
        private val delegate = android.app.AlertDialog.Builder(context)
        fun setTitle(value: CharSequence): Builder { delegate.setTitle(value); return this }
        fun setMessage(value: CharSequence): Builder { delegate.setMessage(value); return this }
        fun setNegativeButton(value: CharSequence, listener: DialogInterface.OnClickListener?): Builder { delegate.setNegativeButton(value, listener); return this }
        fun setPositiveButton(value: CharSequence, listener: DialogInterface.OnClickListener?): Builder { delegate.setPositiveButton(value, listener); return this }
        fun show(): android.app.AlertDialog = delegate.show()
    }
}
