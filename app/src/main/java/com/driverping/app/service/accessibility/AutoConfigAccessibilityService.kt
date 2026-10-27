package com.driverping.app.service.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * AutoConfigAccessibilityService
 * ------------------------------
 * Servicio de accesibilidad que navega automáticamente por la UI de Android
 * para desactivar la "Optimización de batería" de las apps de la lista
 * blanca (Yummy, Ridery, Yango).
 */
class AutoConfigAccessibilityService : AccessibilityService() {

    private enum class State { WAITING_MODEL, WAITING_DETAIL }
    private var state = State.WAITING_MODEL
    private val processed = HashSet<String>()

    private val whitelistLabels = com.driverping.app.util.Constants.BATTERY_APP_LABELS

    // Términos comunes en diversas capas (MIUI, HyperOS, OneUI, XOS, genérico)
    private val toggleLabels = listOf(
        "Permitir", "Don't allow", "No permitir", "Allow", "Permit", "Allow battery",
        "Sin restricciones", "No restringir", "Aceptar", "Desactivar", "Sin límite", "Ilimitado"
    )

    override fun onServiceConnected() {
        super.onServiceConnected()
        processed.clear()
        state = State.WAITING_MODEL
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        if (event.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED &&
            event.eventType != AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
        ) {
            return
        }

        // Solo procesar eventos cuando el usuario esté dentro de las pantallas de Ajustes o Seguridad
        val pkg = event.packageName?.toString()?.lowercase() ?: ""
        if (!pkg.contains("settings") && !pkg.contains("security") && !pkg.contains("phonemanager") && !pkg.contains("perm")) {
            return
        }

        try {
            val root = rootInActiveWindow ?: return
            when (state) {
                State.WAITING_MODEL -> handleModelList(root)
                State.WAITING_DETAIL -> handleDetail(root)
            }
        } catch (_: Exception) {
            // Evita cualquier caída o excepción que provoque que Android desactive el servicio
        }
    }

    private fun handleModelList(root: AccessibilityNodeInfo) {
        val nextLabel = whitelistLabels.firstOrNull { it !in processed }
        if (nextLabel == null) {
            state = State.WAITING_MODEL
            return
        }
        if (clickNodeByText(root, nextLabel)) {
            processed.add(nextLabel)
            state = State.WAITING_DETAIL
        }
    }

    private fun handleDetail(root: AccessibilityNodeInfo) {
        toggleWhitelistBattery(root)
        // Retroceder de forma segura después de configurar o si ya estaba activa
        performGlobalAction(GLOBAL_ACTION_BACK)
        state = State.WAITING_MODEL
    }

    private fun toggleWhitelistBattery(root: AccessibilityNodeInfo): Boolean {
        for (label in toggleLabels) {
            val nodes = root.findAccessibilityNodeInfosByText(label)
            for (node in nodes) {
                val switch = node.findSwitchAncestor() ?: continue
                if (!switch.isChecked) {
                    switch.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
        }
        return false
    }

    private fun clickNodeByText(root: AccessibilityNodeInfo, text: String): Boolean {
        for (node in root.findAccessibilityNodeInfosByText(text)) {
            if (tryClick(node)) return true
        }
        return false
    }

    private fun tryClick(node: AccessibilityNodeInfo): Boolean {
        var current: AccessibilityNodeInfo? = node
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isClickable && current.isVisibleToUser) {
                return current.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            }
            current = current.parent
            depth++
        }
        return false
    }

    private fun AccessibilityNodeInfo.findSwitchAncestor(): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = this
        var depth = 0
        while (current != null && depth < 6) {
            if (current.isCheckable || current.className?.toString()?.contains("Switch") == true) {
                return current
            }
            current = current.parent
            depth++
        }
        return null
    }

    override fun onInterrupt() {}
}
