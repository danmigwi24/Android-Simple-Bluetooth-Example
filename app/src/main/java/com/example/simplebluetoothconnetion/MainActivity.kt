package com.example.simplebluetoothconnetion

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.util.Log
import android.widget.*
import android.widget.AdapterView.OnItemClickListener
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.*

class MainActivity : AppCompatActivity() {

    // GUI Components
    private var mBluetoothStatus: TextView? = null
    private var mReadBuffer: TextView? = null
    private var mListPairedDevicesBtn: Button? = null
    private lateinit var mDevicesListView: ListView

    private lateinit var mBluetoothAdapter: BluetoothAdapter
    private lateinit var mPairedDevices: MutableSet<BluetoothDevice>
    private lateinit var mBTArrayAdapter: ArrayAdapter<String>

    private lateinit var mHandler: Handler // Our main handler that will receive callback notifications
    private lateinit var mConnectedThread: ConnectedThread // bluetooth background worker thread to send and receive data
    private lateinit var mBTSocket: BluetoothSocket // bi-directional client-to-client data path

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mBluetoothStatus = findViewById<TextView>(R.id.bluetooth_status)
        mReadBuffer = findViewById<TextView>(R.id.read_buffer)

        mListPairedDevicesBtn = findViewById<Button>(R.id.paired_btn)
        mBTArrayAdapter = ArrayAdapter(this, android.R.layout.simple_list_item_1)
        mBluetoothAdapter =
            BluetoothAdapter.getDefaultAdapter() // get a handle on the bluetooth radio
        mDevicesListView = findViewById<ListView>(R.id.devices_list_view)
        mDevicesListView.adapter = mBTArrayAdapter // assign model to view
        mDevicesListView.onItemClickListener = mDeviceClickListener

        // Ask for location permission if not already allowed
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) !== PackageManager.PERMISSION_GRANTED
        ) ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            1
        )
        mHandler = object : Handler(Looper.getMainLooper()) {
            override fun handleMessage(msg: Message) {
                if (msg.what == MESSAGE_READ) {
                    var readMessage: String? = null
                    readMessage = String((msg.obj as ByteArray), StandardCharsets.UTF_8)
                    mReadBuffer!!.text = readMessage
                }
                if (msg.what == CONNECTING_STATUS) {
                    var sConnected: CharArray
                    if (msg.arg1 == 1) {
                        mBluetoothStatus!!.text =
                            getString(R.string.BTConnected) + msg.obj
                    } else {
                        mBluetoothStatus!!.text =
                            getString(R.string.BTconnFail)
                    }
                }
            }
        }
        if (mBTArrayAdapter == null) {
            // Device does not support Bluetooth
            mBluetoothStatus!!.text = getString(R.string.sBTstaNF)
            Toast.makeText(this@MainActivity, getString(R.string.sBTdevNF), Toast.LENGTH_SHORT)
                .show()
        } else {
            listPairedDevices()
            mListPairedDevicesBtn!!.setOnClickListener { listPairedDevices() }
        }
    }

    // Enter here after user selects "yes" or "no" to enabling radio
    override fun onActivityResult(requestCode: Int, resultCode: Int, Data: Intent?) {
        super.onActivityResult(requestCode, resultCode, Data)
        // Check which request we're responding to
        if (requestCode == REQUEST_ENABLE_BT) {
            // Make sure the request was successful
            if (resultCode == RESULT_OK) {
                // The user picked a contact.
                // The Intent's data Uri identifies which contact was selected.
                mBluetoothStatus!!.text = getString(R.string.sEnabled)
            } else mBluetoothStatus!!.text = getString(R.string.sDisabled)
        }
    }

    private fun listPairedDevices() {
        mBTArrayAdapter.clear()

        mPairedDevices = mBluetoothAdapter.bondedDevices
        if (mBluetoothAdapter.isEnabled) {
            // put it's one to the adapter
            for (device in mPairedDevices) {
                mBTArrayAdapter.add(device.name + "\n" + device.address)
            }
            Toast.makeText(
                this@MainActivity,
                getString(R.string.show_paired_devices),
                Toast.LENGTH_SHORT
            ).show()
        } else Toast.makeText(
            this@MainActivity,
            getString(R.string.BTnotOn),
            Toast.LENGTH_SHORT
        )
            .show()
    }

    private val mDeviceClickListener =
        OnItemClickListener { parent, view, position, id ->
            if (!mBluetoothAdapter.isEnabled) {
                Toast.makeText(this, getString(R.string.BTnotOn), Toast.LENGTH_SHORT).show()
                return@OnItemClickListener
            }
            mBluetoothStatus!!.text = getString(R.string.cConnet)
            // Get the device MAC address, which is the last 17 chars in the View
            val info = (view as TextView).text.toString()
            val address = info.substring(info.length - 17)
            val name = info.substring(0, info.length - 17)

            Log.e("", "Address $address and Name is $name")
            // Spawn a new thread to avoid blocking the GUI one
            object : Thread() {
                override fun run() {
                    var fail = false
                    val device = mBluetoothAdapter.getRemoteDevice(address)
                    try {
                        mBTSocket = createBluetoothSocket(device)
                    } catch (e: IOException) {
                        fail = true
                        Toast.makeText(
                            this@MainActivity,
                            getString(R.string.ErrSockCrea),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // Establish the Bluetooth socket connection.
                    try {
                        mBTSocket.connect()
                    } catch (e: IOException) {
                        try {
                            fail = true
                            mBTSocket.close()
                            mHandler.obtainMessage(CONNECTING_STATUS, -1, -1)
                                .sendToTarget()
                        } catch (e2: IOException) {
                            // insert code to deal with this
                            Toast.makeText(
                                this@MainActivity,
                                getString(R.string.ErrSockCrea),
                                Toast.LENGTH_SHORT
                            ).show()
                        }
                    }
                    if (!fail) {
                        mConnectedThread = ConnectedThread(mBTSocket, mHandler)
                        mConnectedThread.start()
                        mHandler.obtainMessage(CONNECTING_STATUS, 1, -1, name)
                            .sendToTarget()
                    }
                }
            }.start()
        }

    @Throws(IOException::class)
    private fun createBluetoothSocket(device: BluetoothDevice): BluetoothSocket {
        try {
            val m = device.javaClass.getMethod(
                "createInsecureRfcommSocketToServiceRecord",
                UUID::class.java
            )
            return m.invoke(device, BT_MODULE_UUID) as BluetoothSocket
        } catch (e: Exception) {
            Log.e("TAG", "Could not create Insecure RFComm Connection", e)
        }

        return device.createRfcommSocketToServiceRecord(BT_MODULE_UUID)
    }
}


