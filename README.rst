Speed of Sound
==============

A project that began as a CS project at The Ohio State University.
Now a full-fledged application on Google Play.

This project is open-source! See COPYING for license information.

Requirements
------------

You must have the Android SDK installed, along with Google APIs 17.
You also must have ``maven``.

Quick Build Notes
-----------------

Just run ``mvn clean install`` for your APK.

TODO: Google Maps API key notes, extended build instructions

Maven SDK deployer
https://github.com/mosabua/maven-android-sdk-deployer


Building Instructions
---------------------

First, ensure you have the Android SDK set up properly. You can test
this by running ``android``. If it isn't found, it isn't on your path.
Follow the directions on `Installing the SDK`_, specifically the
section labeled "How to update your PATH." Alternatively, if you know
where your SDK is and don't mind referencing the full path to it every
time, you can do that instead.

.. _`Installing the SDK`: http://developer.android.com/sdk/installing.html

Start off by either cloning this repository or downloading a zip
archive::

    git clone git://github.com/jpeddicord/speedofsound.git

TODO TODO TODO

####### XXXXX: keep?
Now, if you have a Google Maps API key you want to use, you can set it
in your ``local.properties`` inside the speedofsound directory::

    maps.debug.key=YOUR_DEBUG_KEY_HERE
    maps.release.key=YOUR_DEBUG_KEY_HERE
####### XXXXX

