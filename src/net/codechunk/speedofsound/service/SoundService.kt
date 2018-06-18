package net.codechunk.speedofsound.service

import android.Manifest
import android.app.*
import android.content.*
import android.content.pm.PackageManager
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.widget.Toast
import com.google.android.gms.location.*
import net.codechunk.speedofsound.R
import net.codechunk.speedofsound.SpeedActivity
import net.codechunk.speedofsound.util.AppPreferences

/**
 * The main sound control service.
 *
 * Responsible for adjusting the volume based on the current speed. Can be
 * started and stopped externally, but is largely independent.
 */
class SoundService : Service() {

    /**
     * The current tracking state.
     */
    var isTracking = false
        private set

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var localBroadcastManager: LocalBroadcastManager? = null

    private var settings: SharedPreferences? = null
    private var volumeThread: VolumeThread? = null
    private val binder = LocalBinder()
    private var volumeConversion: VolumeConversion? = null

    /**
     * Build a fancy-pants notification.
     */
    private val notification: Notification
        get() {
            @RequiresApi(Build.VERSION_CODES.O)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val importance = NotificationManager.IMPORTANCE_LOW
                val name = getString(R.string.app_name)
                val channel = NotificationChannel("main", name, importance)
                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }

            val notificationIntent = Intent(this, SpeedActivity::class.java)
            val builder = NotificationCompat.Builder(this, "main")
            builder.setContentTitle(getString(R.string.app_name))
            builder.setContentText(getString(R.string.notification_text))
            builder.setContentIntent(PendingIntent.getActivity(this, 0, notificationIntent, 0))
            builder.setTicker(getString(R.string.ticker_text))
            builder.setSmallIcon(R.drawable.ic_notification)
            builder.setWhen(System.currentTimeMillis())

            val stopIntent = Intent(this, SoundServiceManager::class.java)
            stopIntent.putExtra(SoundService.SET_TRACKING_STATE, false)
            builder.addAction(R.drawable.ic_stop, getString(R.string.stop),
                PendingIntent.getBroadcast(this, 0, stopIntent, PendingIntent.FLAG_ONE_SHOT))

            return builder.build()
        }

    /**
     * Custom location listener. Triggers volume changes based on the current
     * average speed.
     */
    private val locationUpdater = object : LocationCallback() {
        private var previousLocation: Location? = null

        /**
         * Change the volume based on the current average speed. If speed is not
         * available from the current location provider, calculate it from the
         * previous location. After updating the average and updating the
         * volume, send out a broadcast notifying of the changes.
         */
        override fun onLocationResult(locations: LocationResult?) {
            val location = locations?.lastLocation ?: return

            // during shut-down, the volume thread might have finished.
            // ignore location updates from this point on.
            this@SoundService.volumeThread ?: return

            // use the GPS-provided speed if available
            val speed: Float
            if (location.hasSpeed()) {
                speed = location.speed
            } else {
                // speed fall-back (mostly for the emulator)
                speed = if (this.previousLocation != null) {
                    // get the distance between this and the previous update
                    val meters = previousLocation!!.distanceTo(location)
                    val timeDelta = (location.time - previousLocation!!.time).toFloat()

                    Log.v(TAG, "Location distance: $meters")

                    // convert to meters/second
                    1000 * meters / timeDelta
                } else {
                    0f
                }

                this.previousLocation = location
            }

            val volume = this@SoundService.volumeConversion!!.speedToVolume(speed)
            this@SoundService.volumeThread!!.setTargetVolume(volume)

            // send out a local broadcast with the details
            val intent = Intent(SoundService.LOCATION_UPDATE_BROADCAST)
            intent.putExtra("location", location)
            intent.putExtra("speed", speed)
            intent.putExtra("volumePercent", (volume * 100).toInt())
            this@SoundService.localBroadcastManager!!.sendBroadcast(intent)
        }
    }

    /**
     * Start up the service and initialize some values. Does not start tracking.
     */
    override fun onCreate() {
        Log.d(TAG, "Service starting up")

        this.fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // set up preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        this.settings = PreferenceManager.getDefaultSharedPreferences(this)
        AppPreferences.runUpgrade(this)
        AppPreferences.updateNativeSpeeds(this.settings!!)

        // register handlers & audio
        this.localBroadcastManager = LocalBroadcastManager.getInstance(this)
        this.volumeConversion = VolumeConversion()
        this.volumeConversion!!.onSharedPreferenceChanged(this.settings!!, "") // set initial
    }

    /**
     * Handle a start command.
     *
     * Return sticky mode to tell Android to keep the service active.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Start command received")

        // register pref watching
        this.settings!!.registerOnSharedPreferenceChangeListener(this.volumeConversion)
        LocalBroadcastManager
            .getInstance(this)
            .registerReceiver(this.messageReceiver, IntentFilter(SoundService.SET_TRACKING_STATE))

        // check if we've been commanded to start tracking;
        // we may be started only for the activity view and don't want to start
        // anything up implicitly (note: don't handle stop requests here)
        if (intent?.extras?.containsKey(SoundService.SET_TRACKING_STATE) != null) {
            Log.v(TAG, "Start command included tracking intent; starting tracking")
            this.startTracking()
        }

        return Service.START_STICKY
    }

    /**
     * Service shut-down.
     */
    override fun onDestroy() {
        Log.d(TAG, "Service shutting down")

        this.settings!!.unregisterOnSharedPreferenceChangeListener(this.volumeConversion)
        LocalBroadcastManager.getInstance(this).unregisterReceiver(this.messageReceiver)
    }

    /**
     * The only way to stop tracking is by sending a local broadcast to this service
     * or by binding to it (for UI); stopping the service during onStartCommand can cause an
     * ANR in 8.0+ due to the fact that a foreground service _must_ present itself.
     */
    private val messageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.extras?.containsKey(SoundService.SET_TRACKING_STATE) != null) {
                Log.v(TAG, "Commanded to change state")
                val wanted = intent.extras.getBoolean(SoundService.SET_TRACKING_STATE)
                if (wanted) {
                    this@SoundService.startTracking()
                } else {
                    this@SoundService.stopTracking()
                }
            }
        }
    }

    /**
     * Start tracking. Find the best location provider (likely GPS), create an
     * ongoing notification, and request location updates.
     */
    fun startTracking() {
        // ignore requests when we're already tracking
        if (this.isTracking) {
            return
        }

        // check runtime permission
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (hasPermission != PackageManager.PERMISSION_GRANTED) {
            SoundService.showNeedLocationToast(this)
            return
        }

        // request location updates
        val req = LocationRequest.create().apply {
            interval = 1000
            fastestInterval = 500
            priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        }
        this.fusedLocationClient.requestLocationUpdates(req, this.locationUpdater, null)

        // start up the volume thread
        if (this.volumeThread == null) {
            this.volumeThread = VolumeThread(this)
            this.volumeThread!!.start()
        }

        // show the notification
        startForeground(R.string.notification_text, notification)

        // let everyone know
        val intent = Intent(SoundService.TRACKING_STATE_BROADCAST)
        intent.putExtra("tracking", true)
        this@SoundService.localBroadcastManager!!.sendBroadcast(intent)

        this.isTracking = true
        Log.d(TAG, "Tracking started")
    }

    /**
     * Stop tracking. Remove the location updates and notification.
     */
    fun stopTracking() {
        // don't do anything if we're not tracking
        if (!this.isTracking) {
            return
        }

        // stop location updates
        this.fusedLocationClient.removeLocationUpdates(this.locationUpdater)

        // shut off the volume thread
        if (this.volumeThread != null) {
            this.volumeThread!!.interrupt()
            this.volumeThread = null
        }

        // remove notification and go to background
        stopForeground(true)

        // let everyone know
        val intent = Intent(SoundService.TRACKING_STATE_BROADCAST)
        intent.putExtra("tracking", false)
        this@SoundService.localBroadcastManager!!.sendBroadcast(intent)

        this.isTracking = false
        Log.d(TAG, "Tracking stopped")
    }

    /**
     * Service-level access for external classes and activities.
     */
    inner class LocalBinder : Binder() {
        /**
         * Return the service associated with this binder.
         */
        val service: SoundService
            get() = this@SoundService
    }

    /**
     * Return the binder associated with this service.
     */
    override fun onBind(intent: Intent): IBinder? {
        return this.binder
    }

    companion object {
        private const val TAG = "SoundService"

        /**
         * Intent extra to set the tracking state.
         */
        const val SET_TRACKING_STATE = "set-tracking-state"

        /**
         * Broadcast to notify that tracking has started or stopped.
         */
        const val TRACKING_STATE_BROADCAST = "tracking-state-changed"

        /**
         * Location, speed, and sound update broadcast.
         */
        const val LOCATION_UPDATE_BROADCAST = "location-update"

        fun showNeedLocationToast(ctx: Context) {
            val toast = Toast.makeText(ctx, ctx.getString(R.string.no_location_providers), Toast.LENGTH_LONG)
            toast.show()
        }
    }

}
