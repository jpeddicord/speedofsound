package net.codechunk.speedofsound.service

import android.bluetooth.BluetoothDevice
import android.content.*
import android.os.BatteryManager
import android.preference.PreferenceManager
import android.util.Log
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
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
        Log.d(TAG, "Received intent with action $action")

        // resume tracking if we're also in a satisfactory mode
        if (action == Intent.ACTION_POWER_CONNECTED ||
            action == Intent.ACTION_POWER_DISCONNECTED ||
            action == Intent.ACTION_HEADSET_PLUG) {
            setTracking(context, this.shouldTrack(context))
        } else if (action == BluetoothDevice.ACTION_ACL_CONNECTED ||
            action == BluetoothDevice.ACTION_ACL_DISCONNECTED) {
            Log.d(TAG, "Bluetooth ACL connect/disconnect event")

            // check whether we care about this event at all
            val prefs = PreferenceManager.getDefaultSharedPreferences(context)
            if (!prefs.getBoolean("enable_bluetooth", false)) {
                Log.v(TAG, "Bluetooth pref disabled; ignoring")
                return
            }

            // grab the device address and check it against our list of things
            val shouldCare = when (val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)) {
                null -> false
                else -> isSelectedBluetoothDevice(prefs, device.address)
            }
            if (!shouldCare) {
                Log.v(TAG, "Bluetooth pref enabled but we don't care about this device")
                return
            }

            // set the bluetooth state
            this.bluetoothConnected = action == BluetoothDevice.ACTION_ACL_CONNECTED
            Log.v(TAG, "Bluetooth active: ${this.bluetoothConnected}")

            // start or stop tracking
            setTracking(context, this.shouldTrack(context))
        } else if (action == LOCALE_FIRE) {
            // Tasker!
            val bundle = intent.getBundleExtra(LOCALE_BUNDLE)
            if (bundle != null) {
                val state = bundle.getBoolean(SoundService.SET_TRACKING_STATE, true)
                setTracking(context, state)
            }
        } else {
            // external intent (likely from notification "Stop" action)
            val state = intent.extras?.getBoolean(SoundService.SET_TRACKING_STATE)
            if (state != null) {
                setTracking(context, state)
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
    private fun isSelectedBluetoothDevice(prefs: SharedPreferences, address: String): Boolean {
        // fetched saved devices
        val addresses = prefs.getStringSet(BluetoothDevicePreference.KEY, HashSet())

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
         * Set the tracking state by sending a service start command or broadcast.
         */
        private fun setTracking(context: Context, desiredState: Boolean) {
            Log.d(TAG, "Setting tracking desiredState: $desiredState")

            // only call startForegroundService if we want to start tracking;
            // in 8.0+ the service *must* start up and present a notification
            // within 5 seconds or Android will kill the whole app
            if (desiredState) {
                val serviceIntent = Intent(context, SoundService::class.java)
                serviceIntent.putExtra(SoundService.SET_TRACKING_STATE, desiredState)
                ContextCompat.startForegroundService(context, serviceIntent)
            } else {
                val commandIntent = Intent(SoundService.SET_TRACKING_STATE)
                commandIntent.putExtra(SoundService.SET_TRACKING_STATE, desiredState)
                LocalBroadcastManager.getInstance(context).sendBroadcast(commandIntent)
            }

        }
    }

}
