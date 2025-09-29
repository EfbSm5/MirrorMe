package com.example.castapp.ui

import android.content.Context.BIND_AUTO_CREATE
import android.content.Intent
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.castapp.ReceiverService
import com.example.castapp.TAG

@Composable
fun AppSurface() {
    var showSurface by remember { mutableStateOf(false) }
    val ipAddress = getIpAddress()
    val gatewayIp = getGatewayIpAddress()

    Surface(
        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
    ) {
        if (showSurface) {
            val context = LocalContext.current
            DisposableEffect(Unit) {
                val intent = Intent(context, ReceiverService::class.java).apply {
                    putExtra(ReceiverService.EXTRA_SENDER_IP, gatewayIp)
                }
                context.startService(intent)
                context.bindService(intent, serviceConnection, BIND_AUTO_CREATE)

                onDispose {
                    if (isReceiverServiceBound) {
                        context.unbindService(serviceConnection)
                        isReceiverServiceBound = false
                    }
                }
            }

            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                receiverService?.setSurface(holder.surface)
                                Log.d(
                                    TAG, "surfaceCreated:        receiverServiceStatus:${
                                        if (receiverService == null) "null" else "normal"
                                    }"
                                )
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder, format: Int, width: Int, height: Int
                            ) {
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {}
                        })
                    }
                }, modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Your IP Address: $ipAddress")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    screenCaptureLauncher.launch(mediaProjectionManager.createScreenCaptureIntent())
                }) {
                    Text("Send Screen")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Hotspot Gateway: $gatewayIp")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    if (gatewayIp.isNotBlank()) {
                        showSurface = true
                    }
                }) {
                    Text("Receive Screen")
                }
            }
        }
    }
}