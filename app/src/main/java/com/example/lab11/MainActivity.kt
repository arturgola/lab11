package com.example.lab11

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.lab11.ui.theme.Lab11Theme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MyViewModel : ViewModel() {
    companion object {
        private const val SCAN_PERIOD: Long = 3000
    }

    val scanResults = MutableLiveData<List<ScanResult>>(null)
    val isScanning = MutableLiveData<Boolean>(false)
    private val scanResultsMap = mutableMapOf<String, ScanResult>()

    fun startDeviceScan(scanner: BluetoothLeScanner) {
        viewModelScope.launch(Dispatchers.IO) {
            isScanning.postValue(true)
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0)
                .build()

            scanner.startScan(null, scanSettings, scanCallback)
            delay(SCAN_PERIOD)
            scanner.stopScan(scanCallback)
            scanResults.postValue(scanResultsMap.values.toList())
            isScanning.postValue(false)
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            super.onScanResult(callbackType, result)
            val deviceAddress = result.device.address
            scanResultsMap[deviceAddress] = result
            Log.d("DBG", "Device address: $deviceAddress (${result.isConnectable})")
        }
    }
}

@Composable
fun DeviceScanner(mBluetoothAdapter: BluetoothAdapter, viewModel: MyViewModel = viewModel()) {
    val context = LocalContext.current
    val scanResults by viewModel.scanResults.observeAsState(null)
    val isScanning by viewModel.isScanning.observeAsState(false)

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ScanButton(isScanning) { viewModel.startDeviceScan(mBluetoothAdapter.bluetoothLeScanner) }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp), thickness = 1.dp, color = Color.Gray)

        if (scanResults.isNullOrEmpty()) {
            Text(text = "No devices found", modifier = Modifier.padding(8.dp))
        } else {
            DeviceList(scanResults!!)
        }
    }
}

@Composable
fun ScanButton(isScanning: Boolean, onScan: () -> Unit) {
    Button(
        onClick = onScan,
        enabled = !isScanning,
        modifier = Modifier.padding(8.dp).height(35.dp).width(320.dp)
    ) {
        Text(if (isScanning) "Scanning" else "Scan Now")
    }
}

@Composable
fun DeviceList(scanResults: List<ScanResult>) {
    LazyColumn(modifier = Modifier.fillMaxSize().padding(2.dp)) {
        items(scanResults) { result ->
            DeviceItem(result)
        }
    }
}

@Composable
fun DeviceItem(result: ScanResult) {
    val deviceName = result.device.name ?: "UNKNOWN"
    val deviceAddress = result.device.address
    val deviceStrength = result.rssi

    Row(
        modifier = Modifier.padding(2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "${deviceStrength}dBm",
            modifier = Modifier.padding(end = 10.dp)
        )

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = deviceName,
                modifier = Modifier.padding(4.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Bold,
            )
            Text(text = deviceAddress)
        }

        ConnectButton(result)
    }
}

@Composable
fun ConnectButton(result: ScanResult) {
    Button(
        enabled = result.isConnectable(),
        onClick = { Log.d("DBG", "Clicked ${result.device.address} ${result.device.name}") },
        modifier = Modifier.padding(8.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Blue,
            contentColor = Color.White
        )
    ) {
        Text("Connect")
    }
}


class MainActivity : ComponentActivity() {
    private var bluetoothAdapter: BluetoothAdapter? = null

    private fun hasRequiredPermissions(): Boolean {
        if (bluetoothAdapter == null || !bluetoothAdapter!!.isEnabled) {
            Log.e("DBG", "No Bluetooth LE capability")
            return false
        }

        val permissionsRequired = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_ADMIN
        )

        if (permissionsRequired.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissions(permissionsRequired, 1)
        }
        return true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        setContent {
            Lab11Theme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Column(modifier = Modifier.padding(innerPadding)) {
                        Log.i("DBG", "Device has Bluetooth support: ${hasRequiredPermissions()}")
                        when {
                            bluetoothAdapter == null -> Text("Bluetooth is not supported on this device")
                            !bluetoothAdapter!!.isEnabled -> Text("Bluetooth is turned off")
                            else -> DeviceScanner(bluetoothAdapter!!, viewModel())
                        }
                    }
                }
            }
        }
    }
}