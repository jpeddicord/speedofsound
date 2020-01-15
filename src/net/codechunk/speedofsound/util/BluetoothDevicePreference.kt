package net.codechunk.speedofsound.util

import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.preference.DialogPreference
import android.preference.PreferenceManager
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ListView
import net.codechunk.speedofsound.R
import java.util.*

/**
 * A preference dialog listing saved and paired Bluetooth devices.
 */
class BluetoothDevicePreference(context: Context, attrs: AttributeSet) : DialogPreference(context, attrs) {

    private var value: Set<String>? = null

    private var view: View? = null
    private val adapterDevices = ArrayList<PrettyBluetoothDevice>()

    private val persistedDevices: Set<String>
        get() = PreferenceManager.getDefaultSharedPreferences(context)
            .getStringSet(key, HashSet())!!

    /**
     * Get a set of checked devices from this dialog's ListView.
     */
    /**
     * Set the checked devices in the displayed ListView.
     */
    private var checkedDevices: Set<String>
        get() {
            val list = this.view!!.findViewById<View>(R.id.bluetooth_preference_listview) as ListView
            val devices = HashSet<String>()

            val checked = list.checkedItemPositions
            val size = list.adapter.count
            for (i in 0 until size) {
                if (!checked.get(i)) {
                    continue
                }

                val device = this.adapterDevices[i]
                devices.add(device.address)
            }

            return devices
        }
        set(devices) {
            val list = this.view!!.findViewById<View>(R.id.bluetooth_preference_listview) as ListView

            val size = list.adapter.count
            for (i in 0 until size) {
                if (devices.contains(this.adapterDevices[i].address)) {
                    list.setItemChecked(i, true)
                }
            }
        }

    override fun onSetInitialValue(restorePersistedValue: Boolean, defaultValue: Any?) {
        if (restorePersistedValue) {
            this.value = persistedDevices
        } else {
            this.value = HashSet()
            persistDevices(this.value!!)
        }
    }

    override fun onCreateDialogView(): View {
        super.onCreateDialogView()

        // reload persisted values (onSetInitialValue only called for first init)
        this.value = persistedDevices

        // inflate it all
        val inflater = context.getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        this.view = inflater.inflate(R.layout.bluetooth_preference_dialog, null)

        // get our paired music bluetooth devices
        this.adapterDevices.clear()
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter != null) {
            val deviceSet = adapter.bondedDevices
            for (bluetoothDevice in deviceSet) {
                this.adapterDevices.add(PrettyBluetoothDevice(bluetoothDevice))
            }
        }

        // add them to the list
        val list = this.view!!.findViewById<View>(R.id.bluetooth_preference_listview) as ListView
        list.choiceMode = ListView.CHOICE_MODE_MULTIPLE
        val listAdapter = ArrayAdapter(context, android.R.layout.simple_list_item_multiple_choice, this.adapterDevices)
        list.adapter = listAdapter
        checkedDevices = this.value!!

        return this.view!!
    }

    public override fun onDialogClosed(positiveResult: Boolean) {
        super.onDialogClosed(positiveResult)

        if (!positiveResult) {
            return
        }

        if (shouldPersist()) {
            persistDevices(checkedDevices)
        }

        notifyChanged()
    }

    private fun persistDevices(items: Set<String>) {
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val editor = prefs.edit()
        editor.putStringSet(key, items)
        editor.apply()
    }

    /**
     * Bluetooth device with a nice toString().
     */
    private inner class PrettyBluetoothDevice internal constructor(private val device: BluetoothDevice) {

        val address: String
            get() = this.device.address

        override fun toString(): String {
            return this.device.name
        }
    }

    companion object {

        const val KEY = "enable_bluetooth_devices"
    }

}
