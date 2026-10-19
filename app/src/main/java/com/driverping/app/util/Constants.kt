package com.driverping.app.util

/**
 * Constantes globales de la aplicación DriverPing.
 * Centraliza direcciones DNS, hosts de prueba, lista blanca de paquetes
 * y todas las claves usadas por los servicios.
 */
object Constants {
    // DNS de Cloudflare para baja latencia
    const val DNS_PRIMARY = "1.1.1.1"
    const val DNS_SECONDARY = "1.0.0.1"

    // Host de prueba para medir el ping
    const val PING_HOST = "1.1.1.1"
    const val PING_PORT = 53

    // Tamaños de paquete para el túnel VPN
    const val MAX_PACKET_SIZE = 65535
    const val MTU = 1500

    // IP virtual del túnel
    const val TUN_VIRTUAL_IP = "10.10.10.10"
    const val TUN_NETMASK = 24

    // Clave de sesión que muestra el sistema al activar la VPN
    const val SESSION_KEY = "DriverPingVPN"

    // Intervalo de actualización del ping en ms
    const val PING_INTERVAL_MS = 3000L

    /**
     * Lista blanca de paquetes de aplicaciones cuyo tráfico PERMITIMOS.
     * Incluye los paquetes reales verificados: Yummy Rides (com.yummyrides) y Yango (com.yandex.yango).
     */
    val WHITELIST = listOf(
        "com.yummyrides",
        "com.yummy.driver",
        "com.yandex.yango",
        "com.yango.driver",
        "com.yandex.taximeter",
        "com.ridery",
        "com.ridery.driver",
        "com.ridery.passenger",
        "com.google.android.gms",
        "com.google.android.gsf",
        "com.google.android.location",
        "com.google.android.apps.maps",
        "com.android.vending",
        "com.android.chrome"
    )

    /**
     * Lista de nombres de apps (para el AccessibilityService) a las que
     * se les desactiva la optimización de batería automáticamente.
     */
    val BATTERY_APP_LABELS = listOf(
        "Yummy Rides",
        "Yummy",
        "Yango",
        "Ridery"
    )

    /**
     * IDs de aplicaciones que se limpiarán del background con el RAM Cleaner.
     * Si es null/vacío, se usan los paquetes instalados excepto la whitelist.
     */
    val FORCE_STOP_ON_WORK = listOf(
        "com.facebook.katana",
        "com.whatsapp",
        "com.google.android.youtube"
    )
}
