package com.karaz.library


import android.Manifest
import android.app.AlertDialog
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.*
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import com.karaz.library.BluetoothHandler.Companion.getInstance
import com.welie.blessed.BluetoothPeripheral
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.consumeAsFlow
import timber.log.Timber
import java.util.*


abstract class BlueToothActivity : AppCompatActivity() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val enableBluetoothRequest =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            if (it.resultCode == RESULT_OK) {
                // Bluetooth has been enabled
                checkPermissions()
            } else {
                // Bluetooth has not been enabled, try again
                askToEnableBluetooth()
            }
        }

    fun askToEnableBluetooth() {
        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
        enableBluetoothRequest.launch(enableBtIntent)
    }

    override fun onResume() {
        super.onResume()
        if (BluetoothAdapter.getDefaultAdapter() != null) {
            if (!isBluetoothEnabled) {
                askToEnableBluetooth()
            } else {
                checkPermissions()
            }
        } else {
            Timber.e("This device has no Bluetooth hardware")
        }
    }

    val isBluetoothEnabled: Boolean
        get() {
            val bluetoothAdapter = BluetoothAdapter.getDefaultAdapter() ?: return false
            return bluetoothAdapter.isEnabled
        }


    fun initBluetoothHandler() {
        val bluetoothHandler = getInstance(applicationContext)
        collectGlucose(bluetoothHandler)
    }

    private fun collectGlucose(bluetoothHandler: BluetoothHandler) {
        scope.launch {
            bluetoothHandler.glucoseChannel.consumeAsFlow().collect {
                withContext(Dispatchers.Main) {
                    setGlucoseMeasurement(it)
                }
            }
        }
    }

    abstract fun setGlucoseMeasurement(glucoseMeasurement: GlucoseMeasurement)

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(locationServiceStateReceiver)
    }

    val locationServiceStateReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (action != null && action == LocationManager.MODE_CHANGED_ACTION) {
                val isEnabled = areLocationServicesEnabled()
                Timber.i("Location service state changed to: %s", if (isEnabled) "on" else "off")
                checkPermissions()
            }
        }
    }

    fun getPeripheral(peripheralAddress: String): BluetoothPeripheral {
        val central = getInstance(applicationContext).central
        return central.getPeripheral(peripheralAddress)
    }

    fun checkPermissions() {
        val missingPermissions = getMissingPermissions(requiredPermissions)
        if (missingPermissions.isNotEmpty()) {
            requestPermissions(missingPermissions, ACCESS_LOCATION_REQUEST)
        } else {
            permissionsGranted()
        }
    }

    private fun getMissingPermissions(requiredPermissions: Array<String>): Array<String> {
        val missingPermissions: MutableList<String> = ArrayList()
        for (requiredPermission in requiredPermissions) {
            if (applicationContext.checkSelfPermission(requiredPermission) != PackageManager.PERMISSION_GRANTED) {
                missingPermissions.add(requiredPermission)
            }
        }
        return missingPermissions.toTypedArray()
    }

    private val requiredPermissions: Array<String>
        get() {
            val targetSdkVersion = applicationInfo.targetSdkVersion
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && targetSdkVersion >= Build.VERSION_CODES.Q) arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION
            ) else arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        }

    private fun permissionsGranted() {
        // Check if Location services are on because they are required to make scanning work
        if (checkLocationServices()) {
            initBluetoothHandler()
        }
    }

    private fun areLocationServicesEnabled(): Boolean {
        val locationManager =
            applicationContext.getSystemService(LOCATION_SERVICE) as LocationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return locationManager.isLocationEnabled
        } else {
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled =
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
            return isGpsEnabled || isNetworkEnabled
        }
    }

    private fun checkLocationServices(): Boolean {
        return if (!areLocationServicesEnabled()) {
            AlertDialog.Builder(this@BlueToothActivity)
                .setTitle("Location services are not enabled")
                .setMessage("Scanning for Bluetooth peripherals requires locations services to be enabled.") // Want to enable?
                .setPositiveButton("Enable") { dialogInterface, _ ->
                    dialogInterface.cancel()
                    startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
                .setNegativeButton("Cancel") { dialog, _ ->
                    // if this button is clicked, just close
                    // the dialog box and do nothing
                    dialog.cancel()
                }
                .create()
                .show()
            false
        } else {
            true
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        // Check if all permission were granted
        var allGranted = true
        for (result in grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                allGranted = false
                break
            }
        }
        if (allGranted) {
            permissionsGranted()
        } else {
            AlertDialog.Builder(this@BlueToothActivity)
                .setTitle("Location permission is required for scanning Bluetooth peripherals")
                .setMessage("Please grant permissions")
                .setPositiveButton("Retry") { dialogInterface, _ ->
                    dialogInterface.cancel()
                    checkPermissions()
                }
                .create()
                .show()
        }
    }

    companion object {
        private const val REQUEST_ENABLE_BT = 1
        private const val ACCESS_LOCATION_REQUEST = 2
    }
}

//class MainActivity : AppCompatActivity() {
//
//    lateinit var pinNo: EditText
//    lateinit var output: EditText
//    val BluetoothDevices = ArrayList<Button>()
//
//    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
//    private var measurementValue: TextView? = null
//    private val dateFormat: DateFormat = SimpleDateFormat("dd-MM-yyyy HH:mm:ss", Locale.ENGLISH)
//    private val enableBluetoothRequest = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
//        if (it.resultCode == RESULT_OK) {
//            // Bluetooth has been enabled
//            checkPermissions()
//        } else {
//            // Bluetooth has not been enabled, try again
//            askToEnableBluetooth()
//        }
//    }
//
//    private fun askToEnableBluetooth() {
//        val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
//        enableBluetoothRequest.launch(enableBtIntent)
//    }
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//        setContentView(R.layout.activity_main)
//        // Initializing pin code to read from the text field
////        pinNo = findViewById<EditText>(R.id.PinNo)
//        // To display information in the bottom Multi-line text field
//        output = findViewById<EditText>(R.id.Output)
//        // Initializing button listeners
//        var button = findViewById<Button>(R.id.SearchAndGetData)
//        button.setOnClickListener(listener)
//
//        val layout = findViewById<LinearLayout>(R.id.ble_button_set)
//
//        // val listDevices = findViewById(R.id.list_devices) as ListView
//        //listDevices.adapter = ListDevicesAdapter(this, )
//
//        // Add buttons to main layout
////        for (i in 1..3) {
////          //  for (j in 1..3){
////                val button = Button(this)
////                // Setting text of the button
////                button.text = "lol"
////                button.id = i
////                BluetoothDevices.add(button)
////
////                layout.addView(button)
////          //  }
////        }
//    }
//
//    private val listener = View.OnClickListener { view ->
//        when (view.id) {
//            R.id.SearchAndGetData -> {
//                // For testing read data from Pin No and display
//                // in the ID
//                //val pinNoInput = pinNo.text
//                // TODO: get data from Bluetooth low energy interface for ACCU -Check Instant
//                //output.text = "Test"
//            }
//        }
//    }
//
//}

