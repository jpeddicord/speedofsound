package net.codechunk.speedofsound

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.preference.Preference
import android.preference.PreferenceActivity
import android.preference.PreferenceManager
import android.util.Log

import net.codechunk.speedofsound.util.AppPreferences


/**
 * Speed and volume preferences screen.
 */
class PreferencesActivity : PreferenceActivity() {

    private var prefs: SharedPreferences? = null
    private val listener = AppPreferences()

    public override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // sadly, the newer fragment preference API is
        // not yet in the support library.
        addPreferencesFromResource(R.xml.preferences)

        this.prefs = PreferenceManager.getDefaultSharedPreferences(this)

        registerClickables()
    }

    public override fun onResume() {
        super.onResume()
        this.prefs!!.registerOnSharedPreferenceChangeListener(this.listener)
    }

    public override fun onPause() {
        super.onPause()
        this.prefs!!.unregisterOnSharedPreferenceChangeListener(this.listener)
    }

    private fun registerClickables() {
        // get version number
        val pi: PackageInfo
        try {
            pi = this.packageManager.getPackageInfo(this.packageName, 0)
        } catch (e: PackageManager.NameNotFoundException) {
            Log.e(TAG, "Couldn't get package information?!")
            return
        }

        val version = findPreference("about_version")
        version.summary = pi.versionName
        version.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            try {
                val uri = Uri.parse("market://details?id=net.codechunk.speedofsound")
                this@PreferencesActivity.startActivity(Intent(Intent.ACTION_VIEW, uri))
            } catch (e: ActivityNotFoundException) {
                val uri = Uri.parse("https://play.google.com/store/apps/details?id=net.codechunk.speedofsound")
                this@PreferencesActivity.startActivity(Intent(Intent.ACTION_VIEW, uri))
            }

            true
        }

        val contact = findPreference("about_contact")
        contact.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            val email = Intent(Intent.ACTION_SEND)
            email.type = "plain/text"
            email.putExtra(Intent.EXTRA_EMAIL, arrayOf("mobile@octet.cc"))
            email.putExtra(Intent.EXTRA_SUBJECT, "Speed of Sound")
            this@PreferencesActivity.startActivity(email)
            true
        }

        val translate = findPreference("about_translate")
        translate.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            this@PreferencesActivity.startActivity(Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://www.transifex.com/projects/p/speedofsound/")
            ))
            true
        }

        val source = findPreference("about_source")
        source.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            this@PreferencesActivity.startActivity(Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/jpeddicord/speedofsound")
            ))
            true
        }

        val headphones = findPreference("enable_headphones")
        headphones.onPreferenceClickListener = Preference.OnPreferenceClickListener {
            this@PreferencesActivity.startActivity(Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse("https://github.com/jpeddicord/speedofsound/wiki/Tasker-Headphone-Detection")
            ))
            true
        }
    }

    companion object {
        private val TAG = "PreferencesActivity"
    }
}
