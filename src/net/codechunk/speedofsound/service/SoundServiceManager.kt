package net.codechunk.speedofsound.service

import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.BatteryManager
import android.preference.PreferenceManager
import android.support.v4.content.ContextCompat
import android.util.Log
import net.codechunk.speedofsound.util.BluetoothDevicePreference
import java.util.*

/**
 * Sound service activation manager. Used to start the service at boot;
 * referenced in the manifest.
 */
class SoundServiceManager : BroadcastReceiver() {

    /**
     * Keep track of the bluetooth state here, as the undocumented broadcasts
     * might not be sticky. Not sure if the official ones are either.
     */
    private var bluetoothConnected = false

    /**
     * Receive a broadcast and start the service or update the tracking state.
     */
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received intent " + action!!)

        // resume tracking if we're also in a satisfactory mode
        if (action == Intent.ACTION_POWER_CONNECTED ||
                action == Intent.ACTION_POWER_DISCONNECTED ||
                action == Intent.ACTION_HEADSET_PLUG) {
            SoundServiceManager.setTracking(context, this.shouldTrack(context))
        } else if (action == android.bluetooth.BluetoothA2dp.ACTION_CONNECTION_STATE_CHANGED) {
            Log.d(TAG, "A2DP API11+ event")

            // grab the device address and check it against our list of things
            val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
            var shouldCare = false
            if (device != null) {
                shouldCare = isSelectedBluetoothDevice(context, device.address)
            }
            if (!shouldCare) {
                return
            }

            // set the bluetooth state
            val state = intent.getIntExtra(android.bluetooth.BluetoothA2dp.EXTRA_STATE, -1)
            if (state == android.bluetooth.BluetoothA2dp.STATE_CONNECTED) {
                Log.v(TAG, "A2DP active")
                this.bluetoothConnected = true
            } else if (state == android.bluetooth.BluetoothA2dp.STATE_DISCONNECTED) {
                Log.v(TAG, "A2DP inactive")
                this.bluetoothConnected = false
            }

            // start or stop tracking
            SoundServiceManager.setTracking(context, this.shouldTrack(context))
        } else {
            if (action == SoundServiceManager.LOCALE_FIRE) {
                val bundle = intent.getBundleExtra(SoundServiceManager.LOCALE_BUNDLE)
                if (bundle != null) {
                    val state = bundle.getBoolean(SoundService.SET_TRACKING_STATE, true)
                    SoundServiceManager.setTracking(context, state)
                }
            }
        }
    }

    /**
     * Determine whether we should be tracking.
     *
     * @param context Application context
     * @return suggested state
     */
    private fun shouldTrack(context: Context): Boolean {
        // load preferences
        val prefs = PreferenceManager.getDefaultSharedPreferences(context)
        val powerPreference = prefs.getBoolean("enable_only_charging", false)
        //val headphonePreference = prefs.getBoolean("enable_headphones", false)
        val bluetoothPreference = prefs.getBoolean("enable_bluetooth", false)

        // get power status
        val plugFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val powerStatus = context.applicationContext.registerReceiver(null, plugFilter)
        var powerConnected = false
        if (powerStatus != null) {
            val plugState = powerStatus.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
            powerConnected = plugState == BatteryManager.BATTERY_PLUGGED_AC || plugState == BatteryManager.BATTERY_PLUGGED_USB
        } else {
            Log.e(TAG, "Power status was null")
        }

        // don't track if power is disconnected and we care
        if (powerPreference && !powerConnected) {
            Log.v(TAG, "Power preference active & disconnected")
            return false
        }

        /* XXX: No longer functions in Android 8.0, see preferences.xml comment
        // get headphone status
        val headsetFilter = IntentFilter(Intent.ACTION_HEADSET_PLUG)
        val headphoneStatus = context.applicationContext.registerReceiver(null, headsetFilter)
        var headphoneConnected = false
        if (headphoneStatus != null) {
            headphoneConnected = headphoneStatus.getIntExtra("state", 0) == 1
        } else {
            Log.e(TAG, "Headphone status was null")
        }

        // activate if headphones are plugged in
        if (headphonePreference && headphoneConnected) {
            Log.v(TAG, "Headphone connected")
            return true
        }
        */

        // also activate if bluetooth is connected
        if (bluetoothPreference && this.bluetoothConnected) {
            Log.v(TAG, "Bluetooth connected")
            return true
        }

        // anything else is a no-go
        return false
    }

    /**
     * Return whether the given Bluetooth address is enabled for tracking.
     *
     * Loaded from user preferences.
     */
    private fun isSelectedBluetoothDevice(context: Context, address: String): Boolean {
        // fetched saved devices
        val addresses = PreferenceManager.getDefaultSharedPreferences(context)
                .getStringSet(BluetoothDevicePreference.KEY, HashSet())

        // no selected devices means that *any* bluetooth device is valid
        return if (addresses!!.size == 0) {
            true
        } else addresses.contains(address)

    }

    companion object {
        private const val TAG = "SoundServiceManager"

        const val LOCALE_BUNDLE = "com.twofortyfouram.locale.intent.extra.BUNDLE"
        const val LOCALE_BLURB = "com.twofortyfouram.locale.intent.extra.BLURB"
        const val LOCALE_FIRE = "com.twofortyfouram.locale.intent.action.FIRE_SETTING"

        /**
         * Set the tracking state by sending a service start command.
         *
         * @param context Application context.
         * @param state   Turn tracking on or off.
         */
        private fun setTracking(context: Context, state: Boolean) {
            Log.d(TAG, "Setting tracking state: $state")
            val serviceIntent = Intent(context, SoundService::class.java)
            serviceIntent.putExtra(SoundService.SET_TRACKING_STATE, state)
            ContextCompat.startForegroundService(context, serviceIntent)
        }
    }

}
