package com.example.castapp.ui

import android.view.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun AppSurface(
    myIp: String,
    gatewayIp: String,
    onStartSend: () -> Unit,
    onStartReceive: (surface: Surface) -> Unit,
    onStopReceive: () -> Unit
) {
    var receiving by remember { mutableStateOf(false) }

    Surface(
        modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background
    ) {
        if (receiving) {
            DisposableEffect(Unit) {
                onDispose { onStopReceive() }
            }

            AndroidView(
                factory = { ctx ->
                    SurfaceView(ctx).apply {
                        holder.addCallback(object : SurfaceHolder.Callback {
                            override fun surfaceCreated(holder: SurfaceHolder) {
                                onStartReceive(holder.surface)
                            }

                            override fun surfaceChanged(
                                holder: SurfaceHolder,
                                format: Int,
                                width: Int,
                                height: Int
                            ) {
                            }

                            override fun surfaceDestroyed(holder: SurfaceHolder) {}
                        })
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Your IP Address: $myIp")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onStartSend) { Text("Send Screen") }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Hotspot Gateway: $gatewayIp")
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { if (gatewayIp.isNotBlank()) receiving = true }) {
                    Text("Receive Screen")
                }
            }
        }
    }
}