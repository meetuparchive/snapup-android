Snapup
======

Snapup is an Android application for posting photos to [Meetup][meetup] and browsing attendee lists. If you'd like to install it on an Android device, search for "snapup" in the Market.

This project is primarily a demonstration application for the [Meetup API][api] and its functionality is limited. If you'd like to see it do more, please fork it!

[meetup]: http://www.meetup.com/
[api]: http://www.meetup.com/meetup_api/

Underpinnings
-------------

Snapup is implemented in [Scala][scala], currently 2.8.Beta1. It uses the [Dispatch][dispatch] library to communicate with the Meetup API over [OAuth][oauth]-signed requests, and the [android-plugin for sbt][asbt] to build and package itself for the Android Market.

[scala]: http://www.scala-lang.org/
[dispatch]: http://dispatch.databinder.net/
[oauth]: http://oauth.net/
[asbt]: http://github.com/jberkel/android-plugin

Building
--------

If you'd like to build and contribute to Snapup or just play around with it to get some ideas for own Meetup API / Android / Scala / whatever application, you'll first need to install [simple-build-tool][sbt]. Once sbt is on your executable path, you should be able to type `sbt` in your project directory and eventually (things have to download, compile...) enter the sbt console. 

[sbt]: http://code.google.com/p/simple-build-tool/

The android-plugin uses the regular [Android SDK][sdk] installed on your system, but it needs to know where to find it. Set an environment variable something like this one, to where you actually have the SDK:

    export ANDROID_SDK_HOME=/usr/local/android-sdk/

You'll need the Android 1.6 platform installed, as that is what Snapup currently builds against. When these things are in place, run `update` in the sbt console to pull down Dispatch and its dependencies. Finally, try `compile`. If that succeeds, then a dozen tiny things happened behind the scenes and you're in good shape to package and transfer the application to an Android emulator or device.

To initiate or verify your adb connection, run `adb logcat` in another system terminal. When that is connected to a device or emulator it will chatter away harmlessly. Finally, run `install-device` or `install-emulator` in your sbt console to package and transfer the application. It will take a little while, because it invokes [Proguard][pg], but a homebuilt Snapup should eventually be available in your applications listing.

[sdk]: http://developer.android.com/sdk/
[pg]: http://proguard.sourceforge.net/