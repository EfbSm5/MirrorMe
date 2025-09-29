package com.example.castapp

import android.content.Context.WIFI_SERVICE
import android.net.wifi.WifiManager
import java.util.Locale

fun getIpAddress(): String {
    val wifiManager = CastApp.getContext().getSystemService(WIFI_SERVICE) as WifiManager
    val ipAddress = wifiManager.connectionInfo.ipAddress
    return String.format(
        Locale.getDefault(),
        "%d.%d.%d.%d",
        ipAddress and 0xff,
        ipAddress shr 8 and 0xff,
        ipAddress shr 16 and 0xff,
        ipAddress shr 24 and 0xff
    )
}

fun getGatewayIpAddress(): String {
    val wifiManager = CastApp.getContext().getSystemService(WIFI_SERVICE) as WifiManager
    val dhcpInfo = wifiManager.dhcpInfo
    val gatewayIPInt = dhcpInfo.gateway
    return String.format(
        Locale.getDefault(),
        "%d.%d.%d.%d",
        gatewayIPInt and 0xff,
        gatewayIPInt shr 8 and 0xff,
        gatewayIPInt shr 16 and 0xff,
        gatewayIPInt shr 24 and 0xff
    )
}