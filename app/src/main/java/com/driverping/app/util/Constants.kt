package com.driverping.app.util

/**
 * Constantes globales de la aplicación YoPrimero.
 * Centraliza direcciones DNS, hosts de prueba, lista blanca de paquetes
 * y mapas de nombres legibles para el conductor.
 */
object Constants {
    // DNS de Cloudflare para baja latencia
    const val DNS_PRIMARY = "1.1.1.1"
    const val DNS_SECONDARY = "1.0.0.1"

    // Host de prueba para medir latencia
    const val PING_HOST = "1.1.1.1"
    const val PING_PORT = 53

    // Configuración del túnel VPN
    const val MTU = 1500
    const val TUN_VIRTUAL_IP = "10.10.10.10"
    const val TUN_NETMASK = 24
    const val SESSION_KEY = "YoPrimeroVPN"

    // Intervalo de medición en ms
    const val PING_INTERVAL_MS = 3000L

    /**
     * Lista blanca oficial de paquetes permitidos en la VPN y priorizados para trabajo.
     */
    val WHITELIST = listOf(
        "com.yummyrides",
        "com.yummy.driver",
        "com.yandex.yango",
        "com.yango.driver",
        "com.ridery",
        "com.ridery.driver",
        "com.ubercab.driver",
        "com.didiglobal.driver",
        "com.pedidosya",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.location",
        "com.google.android.apps.maps",
        "com.google.android.webview",
        "com.android.webview",
        "com.google.android.networkstack",
        "com.android.vending",
        "com.android.chrome"
    )

    /**
     * Mapeo de identificadores de paquetes a nombres amigables para el conductor.
     */
    val APP_NAME_MAP = mapOf(
        "com.yummyrides" to "Yummy Rides",
        "com.yummy.driver" to "Yummy Conductor",
        "com.yandex.yango" to "Yango",
        "com.yango.driver" to "Yango Pro",
        "com.ridery" to "Ridery",
        "com.ridery.driver" to "Ridery Conductor",
        "com.ubercab.driver" to "Uber Conductor",
        "com.didiglobal.driver" to "DiDi Conductor",
        "com.pedidosya" to "PedidosYa",
        "com.google.android.gms" to "Servicios de Google Play",
        "com.google.android.apps.maps" to "Google Maps",
        "com.android.chrome" to "Google Chrome"
    )

    /**
     * Nombres de etiquetas de apps para el servicio de accesibilidad.
     */
    val BATTERY_APP_LABELS = listOf(
        "Yummy Rides",
        "Yummy",
        "Yango",
        "Ridery",
        "Uber",
        "DiDi",
        "PedidosYa"
    )

    fun getFriendlyAppName(packageName: String): String {
        return APP_NAME_MAP[packageName] ?: packageName
    }
}
