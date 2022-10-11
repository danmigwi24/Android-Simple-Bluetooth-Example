package com.example.simplebluetoothconnetion

import java.util.*

 val BT_MODULE_UUID =
    UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // "random" unique identifier


// #defines for identifying shared types between calling functions
 const val REQUEST_ENABLE_BT = 1 // used to identify adding bluetooth names

 val MESSAGE_READ = 2 // used in bluetooth handler to identify message update

 const val CONNECTING_STATUS = 3 // used in bluetooth handler to identify message status
