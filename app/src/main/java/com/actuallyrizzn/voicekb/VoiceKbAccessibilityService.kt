/*
 * Voice KB — Android dictation with Venice AI cleanup
 * Copyright (C) 2026 actuallyrizzn
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.actuallyrizzn.voicekb

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityManager
import android.view.accessibility.AccessibilityNodeInfo
import java.lang.ref.WeakReference
import kotlin.math.max
import kotlin.math.min

class VoiceKbAccessibilityService : AccessibilityService() {
    private var lastFocusedNode: WeakReference<AccessibilityNodeInfo>? = null

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return
        if ((event.eventType and AccessibilityEvent.TYPE_VIEW_FOCUSED) == 0 &&
            (event.eventType and AccessibilityEvent.TYPE_VIEW_ACCESSIBILITY_FOCUSED) == 0
        ) {
            return
        }
        val source = event.source ?: return
        if (!source.isEditable) return
        lastFocusedNode = WeakReference(source)
    }

    override fun onInterrupt() {}

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceRef = WeakReference(this)
        val info = serviceInfo ?: AccessibilityServiceInfo()
        info.feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
        info.flags = AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        serviceInfo = info
    }

    override fun onDestroy() {
        super.onDestroy()
        lastFocusedNode = null
        serviceRef = null
    }

    private fun insertIntoFocusedField(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: lastFocusedNode?.get() ?: return false
        if (!focused.isEditable) return false

        if (insertViaSetText(focused, text)) return true
        if (insertViaPaste(focused, text)) return true
        return false
    }

    private fun insertViaSetText(node: AccessibilityNodeInfo, text: String): Boolean {
        val current = node.text?.toString().orEmpty()
        val selectionStart = node.textSelectionStart
        val selectionEnd = node.textSelectionEnd

        val start = when {
            selectionStart in 0..current.length -> selectionStart
            else -> current.length
        }
        val end = when {
            selectionEnd in 0..current.length -> selectionEnd
            else -> start
        }

        val left = min(start, end)
        val right = max(start, end)
        val updatedText = current.substring(0, left) + text + current.substring(right)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, updatedText)
        }
        return node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    private fun insertViaPaste(node: AccessibilityNodeInfo, text: String): Boolean {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val previous = clipboard.primaryClip
        return try {
            clipboard.setPrimaryClip(ClipData.newPlainText("Voice KB", text))
            node.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        } finally {
            previous?.let {
                clipboard.setPrimaryClip(previous)
            }
        }
    }

    companion object {
        private var serviceRef: WeakReference<VoiceKbAccessibilityService>? = null

        fun isEnabled(context: Context): Boolean {
            val manager = context.getSystemService(Context.ACCESSIBILITY_SERVICE) as AccessibilityManager
            return manager.getEnabledAccessibilityServiceList(AccessibilityServiceInfo.FEEDBACK_ALL_MASK).any {
                it.resolveInfo.serviceInfo.packageName == context.packageName &&
                    it.resolveInfo.serviceInfo.name == VoiceKbAccessibilityService::class.java.name
            }
        }

        fun insertTextIntoFocusedField(context: Context, text: String): Boolean {
            if (!isEnabled(context)) return false
            val service = serviceRef?.get() ?: return false
            return service.insertIntoFocusedField(text)
        }
    }
}
