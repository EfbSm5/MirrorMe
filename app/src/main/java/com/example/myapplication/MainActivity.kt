package com.example.myapplication

import android.content.ComponentName
import android.content.Intent
import android.content.ServiceConnection
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import com.example.myapplication.ui.theme.MyApplicationTheme
import java.util.Locale

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {

    private var receiverService: ReceiverService? = null
    private var isReceiverServiceBound = false

    private val mediaProjectionManager by lazy {
        getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    }

    private val screenCaptureLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val intent = Intent(this, SenderService::class.java).apply {
                action = SenderService.ACTION_START
                putExtra(SenderService.EXTRA_RESULT_CODE, result.resultCode)
                putExtra(SenderService.EXTRA_RESULT_DATA, result.data)
            }
            startForegroundService(intent)
        }
    }

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as ReceiverService.LocalBinder
            receiverService = binder.getService()
            isReceiverServiceBound = true
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            isReceiverServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MyApplicationTheme {
                AppSurface()
            }
        }
    }

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

    private fun getIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
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

    private fun getGatewayIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
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

    override fun onDestroy() {
        super.onDestroy()
        if (isReceiverServiceBound) {
            unbindService(serviceConnection)
            isReceiverServiceBound = false
        }
        stopService(Intent(this, SenderService::class.java))
        stopService(Intent(this, ReceiverService::class.java))
    }
}
