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
 *
 * Flujo (máquina de estados):
 *  STATE_WAITING_MODEL  -> espera la lista de apps
 *  STATE_WAITING_DETAIL -> dentro de la pantalla de detalle de una app
 *
 * 1. Al conectarse abre "Ignorar optimización de batería".
 * 2. En la lista busca el nombre de una app whitelist y la toca.
 * 3. En el detalle activa el toggle "Permitir" y vuelve atrás.
 * 4. Repite hasta agotar la whitelist.
 *
 * Es robusto frente a variaciones de UI: busca por textos visibles,
 * contentDescription y sube por los padres hasta encontrar un nodo cliqueable.
 * Los switches se detectan por isCheckable o className "Switch".
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

    /**
     * Estamos en la lista de apps con optimización de batería.
     * Buscamos la próxima app de la whitelist no procesada y la tocamos.
     */
    private fun handleModelList(root: AccessibilityNodeInfo) {
        val nextLabel = whitelistLabels.firstOrNull { it !in processed }
        if (nextLabel == null) {
            // Terminamos: nada más que hacer
            state = State.WAITING_MODEL
            return
        }
        if (clickNodeByText(root, nextLabel)) {
            processed.add(nextLabel)
            state = State.WAITING_DETAIL
        }
    }

    /**
     * Estamos en la pantalla de detalle de una app. Activamos el toggle
     * "Permitir" si está apagado, y volvemos a la lista.
     */
    private fun handleDetail(root: AccessibilityNodeInfo) {
        val toggled = toggleWhitelistBattery(root)
        if (toggled) {
            // Cierto margen para que se aplique antes de retroceder
            performGlobalAction(GLOBAL_ACTION_BACK)
            state = State.WAITING_MODEL
        }
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

    /**
     * Intenta teclear un nodo subiendo por sus padres hasta encontrar uno
     * clickeable y visible.
     */
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
