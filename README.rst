Speed of Sound
==============

A project for CSE 694 at The Ohio State University.
By Team 9001: Andrew Buelow, Chris Hofer, Jacob Peddicord.

This project is open-source! See COPYING for license information.

Requirements
------------

You must have the Android SDK installed, along with Google APIs 15.
You also must have ``ant`` or be willing to build with Eclipse.

If you're building with Eclipse, having ADT installed is a given.

Quick Build Notes
-----------------

For full instructions, see the next section.

While an Eclipse project is included, building with Ant instead is
recommended. Eclipse doesn't use the custom rules we have set up for
Ant build targets with respect to Google Maps API keys. You can still
test with Eclipse, but the map portion of the application may appear
as an empty grid.

If you still really want to use Eclipse, you must at least run ant
at least once to generate keys.xml::

    ant debug

Eclipse will then use whatever debug Maps key you have set for future
builds. You can also use ``ant release`` if you want to use a release
key.

On that note, be sure to create a local.properties (you can base it off
of ant.properties) and set maps.debug.key and maps.release.key to the
Google Maps API keys you want to use.


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

Then, set up the project and ActionBarSherlock::

    cd speedofsound
    android update project -p actionbarsherlock
    android update project -p speedofsound

Now, if you have a Google Maps API key you want to use, you can set it
in your ``local.properties`` inside the speedofsound directory::

    maps.debug.key=YOUR_DEBUG_KEY_HERE
    maps.release.key=YOUR_DEBUG_KEY_HERE

You can build without API keys, but the map will be blank when shown.
At this point, you need to build with Ant at least once, even if using
Eclipse::

    cd speedofsound
    ant debug

If you've done the previous steps correctly, this should end with a
"BUILD SUCCESSFUL" message. If you just want to build with Ant, you're
done; just keep building with ``ant debug`` (or ``ant release``).
Packages built with Ant can be pushed to the emulator or your device
with ``ant installd`` (for a debug build) or ``ant installr`` (for a
release build).

Eclipse Setup
~~~~~~~~~~~~~

You *must* follow the previous steps before beginning Eclipse setup.

Here's where the fun begins. Create a new workspace for speedofsound.
Then, go to **File > Import**. Select **Existing Projects into
Workspace** under General and hit next. Then, select the root
directory of the repository that you cloned/downloaded earlier.

You should then see two projects: actionbarsherlock and speedofsound.
Check the boxes next to both of them, and make sure **Copy projects
into workspace** is **not** selected. (If you do select it,
contributing changes into Git will become a lot more painful.)

Hit Finish, and wait a moment while Eclipse churns. If all went well,
you should see two blue folders in the project listing and no errors.
You can then proceed to build and run speedofsound like any other
Eclipse android project.

Missing maps_api_key
````````````````````

You didn't build with Ant first. Even if you are not using Google Maps
for testing, you *must* build with Ant at least once to generate
``keys.xml``.
