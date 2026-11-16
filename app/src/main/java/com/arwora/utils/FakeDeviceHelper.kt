package com.arwora.utils

import android.content.Context
import android.content.DialogInterface
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import android.app.AlertDialog

object FakeDeviceHelper {
    private const val TAG = "FakeDeviceHelper"

    val fakeDeviceList: Array<String>
        get() = FakeDeviceManager.fakeDeviceList

    /**
     * 2. DIALOG IMPLEMENTATION:
     * Builds and displays the Fake Device dialog using AlertDialog.Builder.
     */
    fun showFakeDeviceDialog(context: Context) {
        var selectedItem = 0
        val builder = AlertDialog.Builder(context)
        builder.setTitle("Select Fake Device")
        
        builder.setSingleChoiceItems(fakeDeviceList, selectedItem) { _: DialogInterface?, which: Int ->
            selectedItem = which
        }
        
        builder.setPositiveButton("Apply") { dialog: DialogInterface, _: Int ->
            val selectedDevice = fakeDeviceList[selectedItem]
            applyFakeDeviceName(context, selectedDevice)
            dialog.dismiss()
        }
        
        builder.setNegativeButton("Cancel") { dialog: DialogInterface, _: Int ->
            dialog.dismiss()
        }
        
        val dialog = builder.create()
        dialog.show()
    }

    fun isAdbReady(): Boolean = FakeDeviceManager.isAdbReady()

    fun executeCommandViaAdb(command: String): Boolean = FakeDeviceManager.executeCommandViaAdb(command)

    fun applyFakeDeviceName(context: Context, newName: String) {
        FakeDeviceManager.applyFakeDeviceName(context, newName)
    }

    fun applyFakeDevice(context: Context, deviceName: String): Boolean {
        return FakeDeviceManager.applyDeviceProfile(
            context,
            FakeDeviceManager.devices.firstOrNull { it.name.equals(deviceName, ignoreCase = true) }
                ?: FakeDeviceProfile(deviceName, "Android", "GENERIC_MODEL", "generic_board", "15", 35)
        )
    }
}
