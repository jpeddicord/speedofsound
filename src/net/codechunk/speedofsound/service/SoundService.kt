package net.codechunk.speedofsound.service

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.os.Binder
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.preference.PreferenceManager
import android.support.annotation.RequiresApi
import android.support.v4.app.NotificationCompat
import android.support.v4.content.ContextCompat
import android.support.v4.content.LocalBroadcastManager
import android.util.Log
import android.widget.Toast
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.api.GoogleApiClient
import com.google.android.gms.location.LocationListener
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import net.codechunk.speedofsound.R
import net.codechunk.speedofsound.SpeedActivity
import net.codechunk.speedofsound.util.AppPreferences

/**
 * The main sound control service.
 *
 * Responsible for adjusting the volume based on the current speed. Can be
 * started and stopped externally, but is largely independent.
 */
class SoundService : Service(), GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    /**
     * The current tracking state.
     */
    var isTracking = false
        private set

    private var pendingStart = false
    private var googleApiClient: GoogleApiClient? = null
    private var localBroadcastManager: LocalBroadcastManager? = null
    private val soundServiceManager = SoundServiceManager()

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

            val stopIntent = Intent(this, SoundService::class.java)
            stopIntent.putExtra(SoundService.SET_TRACKING_STATE, false)
            builder.addAction(R.drawable.ic_stop, getString(R.string.stop),
                    PendingIntent.getService(this, 0, stopIntent, PendingIntent.FLAG_ONE_SHOT))

            return builder.build()
        }

    /**
     * Custom location listener. Triggers volume changes based on the current
     * average speed.
     */
    private val locationUpdater = object : LocationListener {
        private var previousLocation: Location? = null

        /**
         * Change the volume based on the current average speed. If speed is not
         * available from the current location provider, calculate it from the
         * previous location. After updating the average and updating the
         * volume, send out a broadcast notifying of the changes.
         */
        override fun onLocationChanged(location: Location?) {
            // some stupid phones occasionally send a null location.
            // who does that, seriously.
            if (location == null)
                return

            // during shut-down, the volume thread might have finished.
            // ignore location updates from this point on.
            if (this@SoundService.volumeThread == null)
                return

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

        // connect to google api stuff, because for some reason you need to do that for location
        this.googleApiClient = GoogleApiClient.Builder(this)
                .addApi(LocationServices.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build()

        // set up preferences
        PreferenceManager.setDefaultValues(this, R.xml.preferences, false)
        this.settings = PreferenceManager.getDefaultSharedPreferences(this)
        AppPreferences.runUpgrade(this)
        AppPreferences.updateNativeSpeeds(this.settings!!)

        // register handlers & audio
        this.localBroadcastManager = LocalBroadcastManager.getInstance(this)
        this.volumeConversion = VolumeConversion()
        this.volumeConversion!!.onSharedPreferenceChanged(this.settings!!, "") // set initial

        // activation broadcasts
        val activationFilter = this.soundServiceManager.activationIntents()
        this.registerReceiver(this.soundServiceManager, activationFilter)
    }

    /**
     * Handle a start command.
     *
     * Return sticky mode to tell Android to keep the service active.
     */
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Start command received")

        this.googleApiClient!!.connect()

        // register pref watching
        this.settings!!.registerOnSharedPreferenceChangeListener(this.volumeConversion)

        // check if we've been commanded to start or stop tracking
        if (intent != null) {
            val extras = intent.extras
            if (extras != null && extras.containsKey(SoundService.SET_TRACKING_STATE)) {
                Log.v(TAG, "Commanded to change state")
                if (extras.getBoolean(SoundService.SET_TRACKING_STATE)) {
                    this.startTracking()
                } else {
                    this.stopTracking()
                }
            }
        }

        return Service.START_STICKY
    }

    /**
     * Service shut-down.
     */
    override fun onDestroy() {
        Log.d(TAG, "Service shutting down")

        this.settings!!.unregisterOnSharedPreferenceChangeListener(this.volumeConversion)
        this.googleApiClient!!.disconnect()
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

        // if we're still connecting, then defer this (see onConnected)
        if (this.googleApiClient!!.isConnecting) {
            Log.i(TAG, "Google API client not yet connected; waiting to start")
            this.pendingStart = true
            return
        }

        // check runtime permission
        val hasPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        if (hasPermission != PackageManager.PERMISSION_GRANTED) {
            SoundService.showNeedLocationToast(this)
            return
        }

        // request location updates
        val req = LocationRequest()
        req.interval = 1000
        req.fastestInterval = 500
        req.priority = LocationRequest.PRIORITY_HIGH_ACCURACY
        LocationServices.FusedLocationApi.requestLocationUpdates(
                this.googleApiClient, req, this.locationUpdater)

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

        // shut off the volume thread
        if (this.volumeThread != null) {
            this.volumeThread!!.interrupt()
            this.volumeThread = null
        }

        // disable location updates
        LocationServices.FusedLocationApi.removeLocationUpdates(
                this.googleApiClient, this.locationUpdater)

        // remove notification and go to background
        stopForeground(true)

        // let everyone know
        val intent = Intent(SoundService.TRACKING_STATE_BROADCAST)
        intent.putExtra("tracking", false)
        this@SoundService.localBroadcastManager!!.sendBroadcast(intent)

        this.isTracking = false
        Log.d(TAG, "Tracking stopped")
    }

    override fun onConnected(bundle: Bundle?) {
        Log.i(TAG, "Google API client connected")
        if (this.pendingStart) {
            this.startTracking()
            this.pendingStart = false
        }
    }

    override fun onConnectionSuspended(i: Int) {
        // no-op
    }

    override fun onConnectionFailed(connectionResult: ConnectionResult) {
        Log.e(TAG, "Google API Connection failed: " + connectionResult.errorMessage!!)
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
