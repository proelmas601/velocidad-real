package com.velocidadreal.app.datos

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun tipoConexion(context: Context): String {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return "Desconocida"
    val caps = cm.getNetworkCapabilities(cm.activeNetwork) ?: return "Sin conexión"
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "Cable"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "Datos móviles"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "VPN"
        else -> "Otra"
    }
}
