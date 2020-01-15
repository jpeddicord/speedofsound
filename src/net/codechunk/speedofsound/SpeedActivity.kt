package net.codechunk.speedofsound

import android.Manifest
import androidx.appcompat.app.AlertDialog
import android.app.Dialog
import android.content.*
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import androidx.core.view.MenuItemCompat
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import net.codechunk.speedofsound.service.SoundService
import net.codechunk.speedofsound.util.SpeedConversions

/**
 * Main status activity. Displays the current speed and set volume. Does not
 * actually track the volume itself; that is handled in SoundService.
 */
class SpeedActivity : AppCompatActivity(), View.OnClickListener {

    private var uiState: UIState? = null

    /**
     * Application's shared preferences.
     */
    private var settings: SharedPreferences? = null

    /**
     * The main "Enable Speed of Sound" checkbox.
     */
    private var enabledCheckBox: CheckBox? = null

    /**
     * Whether we're bound to the background service or not. If everything is
     * working, this should be true.
     */
    private var bound = false

    /**
     * The background service.
     */
    private var service: SoundService? = null

    /**
     * Handle incoming broadcasts from the service.
     */
    private val messageReceiver = object : BroadcastReceiver() {
        /**
         * Receive a speed/sound status update.
         */
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.v(TAG, "Received broadcast " + action!!)

            if (SoundService.TRACKING_STATE_BROADCAST == action) {
                val tracking = intent.getBooleanExtra("tracking", false)
                this@SpeedActivity.enabledCheckBox!!.isChecked = tracking
                this@SpeedActivity.updateStatusState(if (tracking) UIState.ACTIVE else UIState.INACTIVE)
            } else if (SoundService.LOCATION_UPDATE_BROADCAST == action) {
                // unpack the speed/volume
                val speed = intent.getFloatExtra("speed", -1.0f)
                val volume = intent.getIntExtra("volumePercent", -1)

                // convert the speed to the appropriate units
                val units = this@SpeedActivity.settings!!.getString("speed_units", "")
                val localizedSpeed = SpeedConversions.localizedSpeed(units, speed)

                this@SpeedActivity.updateStatusState(UIState.TRACKING)

                // display the speed
                val speedView = findViewById<View>(R.id.speed_value) as TextView
                speedView.text = String.format("%.1f %s", localizedSpeed, units)

                // display the volume as well
                val volumeView = findViewById<View>(R.id.volume_value) as TextView
                volumeView.text = String.format("%d%%", volume)

                // ui goodies
                val volumeDesc = findViewById<View>(R.id.volume_description) as TextView
                val lowVolume = this@SpeedActivity.settings!!.getInt("low_volume", 0)
                val highVolume = this@SpeedActivity.settings!!.getInt("high_volume", 100)

                // show different text values depending on the limits hit
                when {
                    volume <= lowVolume -> volumeDesc.text = getString(R.string.volume_header_low)
                    volume >= highVolume -> volumeDesc.text = getText(R.string.volume_header_high)
                    else -> volumeDesc.text = getText(R.string.volume_header_scaled)
                }
            }// new location data
        }
    }

    /**
     * Attach to the sound service.
     */
    private val serviceConnection = object : ServiceConnection {
        /**
         * Trigger service and UI actions once we have a connection.
         */
        override fun onServiceConnected(className: ComponentName, service: IBinder) {
            Log.v(TAG, "ServiceConnection connected")

            val binder = service as SoundService.LocalBinder
            this@SpeedActivity.service = binder.service
            this@SpeedActivity.bound = true

            // update the enabled check box
            this@SpeedActivity.enabledCheckBox!!.isChecked = this@SpeedActivity.service!!.isTracking
            this@SpeedActivity.updateStatusState(if (this@SpeedActivity.service!!.isTracking) UIState.ACTIVE else UIState.INACTIVE)

            // start tracking if preference set
            if (this@SpeedActivity.settings!!.getBoolean("enable_on_launch", false)) {
                this@SpeedActivity.service!!.startTracking()
            }
        }

        /**
         * Mark the service as unbound on disconnect.
         */
        override fun onServiceDisconnected(arg: ComponentName) {
            Log.v(TAG, "ServiceConnection disconnected")
            this@SpeedActivity.bound = false
        }
    }

    private enum class UIState {
        INACTIVE, ACTIVE, TRACKING
    }

    /**
     * Load the view and attach a checkbox listener.
     */
    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(R.layout.main)

        // hook up the checkbox
        this.settings = PreferenceManager.getDefaultSharedPreferences(this)
        this.enabledCheckBox = findViewById<View>(R.id.checkbox_enabled) as CheckBox
        this.enabledCheckBox!!.setOnClickListener(this)
        this.uiState = UIState.INACTIVE

        // show disclaimer and/or GPS nag
        this.startupMessages()
    }

    /**
     * Start the service with the view, if it wasn't already running.
     */
    public override fun onStart() {
        super.onStart()
        Log.d(TAG, "View starting")

        // bind to our service after explicitly starting it
        val intent = Intent(this, SoundService::class.java)
        startService(intent)
        bindService(intent, this.serviceConnection, 0)
    }

    /**
     * Stop the view and unbind from the service (but don't stop that).
     */
    public override fun onStop() {
        super.onStop()
        Log.d(TAG, "View stopping")

        // unbind from our service
        if (this.bound) {
            unbindService(this.serviceConnection)
            this.bound = false
        }
    }

    /**
     * Pause the view and stop listening to broadcasts.
     */
    public override fun onPause() {
        super.onPause()
        Log.d(TAG, "Paused, unsubscribing from updates")

        LocalBroadcastManager.getInstance(this).unregisterReceiver(this.messageReceiver)
    }

    /**
     * Resume the view and subscribe to broadcasts.
     */
    public override fun onResume() {
        super.onResume()
        Log.d(TAG, "Resumed, subscribing to service updates")

        val lbm = LocalBroadcastManager.getInstance(this)
        val filter = IntentFilter()
        filter.addAction(SoundService.LOCATION_UPDATE_BROADCAST)
        filter.addAction(SoundService.TRACKING_STATE_BROADCAST)
        lbm.registerReceiver(this.messageReceiver, filter)
    }

    /**
     * Create disclaimer/gps dialogs to show.
     */
    override fun onCreateDialog(id: Int): Dialog? {
        val dialog: Dialog?
        val builder = AlertDialog.Builder(this)
        when (id) {
            DIALOG_DISCLAIMER -> {
                builder.setMessage(getString(R.string.launch_disclaimer))
                    .setTitle(getString(R.string.warning))
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.launch_disclaimer_accept)
                    ) { _, _ -> this@SpeedActivity.checkGPS() }
                dialog = builder.create()
            }

            DIALOG_GPS -> {
                builder.setMessage(getString(R.string.gps_warning))
                    .setCancelable(false)
                    .setPositiveButton(getString(R.string.location_settings)
                    ) { _, _ -> startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }
                    .setNegativeButton(getString(R.string.gps_warning_dismiss), null)
                dialog = builder.create()
            }

            else -> dialog = null
        }
        return dialog
    }

    /**
     * Show some startup messages. Will show the disclaimer dialog if this is
     * the first run. If GPS is disabled, will show another dialog to ask to
     * enable it.
     */
    private fun startupMessages() {
        // only show disclaimers and the like once
        val runonce = this.settings!!.getBoolean("runonce", false)

        // firstrun things
        if (!runonce) {
            val editor = this.settings!!.edit()
            editor.putBoolean("runonce", true)
            editor.apply()

            // driving disclaimer (followed by GPS)
            this.showDialog(DIALOG_DISCLAIMER)
        } else {
            // GPS notice
            this.checkGPS()
        }
    }

    /**
     * Check if GPS is enabled. If it isn't, bug the user to turn it on.
     */
    private fun checkGPS() {
        val locationManager = this.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        if (!locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            this.showDialog(DIALOG_GPS)
        }
    }

    /**
     * Start or stop tracking in the service on checked/unchecked.
     */
    override fun onClick(view: View) {
        val isChecked = (view as CheckBox).isChecked
        Log.d(TAG, "Checkbox changed to $isChecked")

        // uh-oh
        if (!this.bound) {
            Log.e(TAG, "Service is unavailable")
            return
        }

        // go get 'em buddy
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (isChecked && hasPermission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST)
            return
        }

        this.toggleTracking()
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<String>, results: IntArray) {
        val enabled = findViewById<View>(R.id.checkbox_enabled) as CheckBox

        when (code) {
            LOCATION_PERMISSION_REQUEST -> if (results.isNotEmpty() && results[0] != PackageManager.PERMISSION_GRANTED) {
                SoundService.showNeedLocationToast(this)
                enabled.isChecked = false
            } else {
                this.toggleTracking()
            }
        }
    }

    /**
     * Start or stop the service depending on the checkbox state.
     */
    private fun toggleTracking() {
        val isChecked = (findViewById<View>(R.id.checkbox_enabled) as CheckBox).isChecked

        // start up the service
        if (isChecked) {
            this.service!!.startTracking()

            // update the UI
            this.updateStatusState(UIState.ACTIVE)

            // reset speed/volume to waiting state.
            // we don't do this in updateStatusState as that would happen too
            // frequently.
            val speed = findViewById<View>(R.id.speed_value) as TextView
            val volume = findViewById<View>(R.id.volume_value) as TextView
            speed.text = getString(R.string.waiting)
            volume.text = getString(R.string.waiting)
        } else {
            this.service!!.stopTracking()

            // update the UI
            this.updateStatusState(UIState.INACTIVE)
        }// stop the service
    }

    /**
     * Switch between the intro message and the speed details.
     */
    private fun updateStatusState(state: UIState) {
        val trackingDetails = findViewById<View>(R.id.tracking_details)
        trackingDetails.visibility = if (state == UIState.TRACKING) View.VISIBLE else View.GONE

        val waitingForGps = findViewById<View>(R.id.waiting_for_gps)
        waitingForGps.visibility = if (state == UIState.ACTIVE) View.VISIBLE else View.GONE

        val inactiveIntro = findViewById<View>(R.id.inactive_intro)
        inactiveIntro.visibility = if (state == UIState.INACTIVE) View.VISIBLE else View.GONE
    }

    /**
     * Show a menu on menu button press. Where supported, show an action item
     * instead.
     */
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val inflater = menuInflater
        inflater.inflate(R.menu.speed_menu, menu)
        MenuItemCompat.setShowAsAction(menu.findItem(R.id.preferences), MenuItemCompat.SHOW_AS_ACTION_ALWAYS)

        return true
    }

    /**
     * Handle actions from the menu/action bar.
     */
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.preferences -> startActivity(Intent(this, PreferencesActivity::class.java))
        }
        return true
    }

    companion object {
        private const val TAG = "SpeedActivity"

        /**
         * Disclaimer dialog unique ID.
         */
        private const val DIALOG_DISCLAIMER = 1

        /**
         * GPS nag dialog unique ID.
         */
        private const val DIALOG_GPS = 2

        /**
         * Location permission identifier.
         */
        private const val LOCATION_PERMISSION_REQUEST = 3
    }
}
