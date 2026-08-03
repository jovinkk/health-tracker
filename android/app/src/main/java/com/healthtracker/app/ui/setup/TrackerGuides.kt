package com.healthtracker.app.ui.setup

/**
 * Setup instructions per fitness app.
 *
 * Menu wording shifts between app versions, so each guide ends at the same
 * reliable fallback: Health Connect itself lists every app that has requested
 * access, and sharing can always be switched on from there.
 */
data class TrackerGuide(
    val name: String,
    val packageName: String?,
    val steps: String,
)

val TRACKER_GUIDES = listOf(
    TrackerGuide(
        name = "Samsung Health (Galaxy Watch)",
        packageName = "com.sec.android.app.shealth",
        steps = "1. Open Samsung Health\n" +
            "2. Go to Settings (the ⋮ or gear icon)\n" +
            "3. Tap Health Connect\n" +
            "4. Turn on the data types you want shared — steps, heart rate, sleep, oxygen\n\n" +
            "Samsung Health only shares data recorded from the moment you switch this on. " +
            "Earlier history stays in Samsung Health and won't appear here.",
    ),
    TrackerGuide(
        name = "Fitbit / Pixel Watch",
        packageName = "com.fitbit.FitbitMobile",
        steps = "1. Open the Fitbit app\n" +
            "2. Tap your profile picture, then Fitbit Settings\n" +
            "3. Tap Health Connect\n" +
            "4. Enable it and pick the data types to share",
    ),
    TrackerGuide(
        name = "Garmin",
        packageName = "com.garmin.android.apps.connectmobile",
        steps = "1. Open Garmin Connect\n" +
            "2. Tap More, then Settings\n" +
            "3. Tap Health Connect (may be under Connected Apps)\n" +
            "4. Enable sharing for the data types you want",
    ),
    TrackerGuide(
        name = "Google Fit",
        packageName = "com.google.android.apps.fitness",
        steps = "1. Open Google Fit\n" +
            "2. Tap Profile, then the Settings gear\n" +
            "3. Tap Manage connected apps, then Health Connect\n" +
            "4. Allow the data types you want shared",
    ),
    TrackerGuide(
        name = "Oura",
        packageName = "com.ouraring.oura",
        steps = "1. Open the Oura app\n" +
            "2. Go to Settings, then Integrations (or Apps)\n" +
            "3. Tap Health Connect and enable sharing",
    ),
    TrackerGuide(
        name = "Whoop",
        packageName = "com.whoop.android",
        steps = "1. Open the Whoop app\n" +
            "2. Tap More, then Integrations\n" +
            "3. Tap Health Connect and enable sharing",
    ),
    TrackerGuide(
        name = "Polar",
        packageName = "fi.polar.polarflow",
        steps = "1. Open Polar Flow\n" +
            "2. Go to Settings\n" +
            "3. Tap Health Connect and enable sharing",
    ),
    TrackerGuide(
        name = "Withings",
        packageName = "com.withings.wiscale2",
        steps = "1. Open Health Mate\n" +
            "2. Tap Profile, then Health Connect\n" +
            "3. Enable sharing for the data types you want",
    ),
    TrackerGuide(
        name = "Amazfit / Zepp",
        packageName = "com.huami.watch.hmwatchmanager",
        steps = "1. Open the Zepp app\n" +
            "2. Tap Profile, then Settings\n" +
            "3. Tap Health Connect (or Third-party access) and enable sharing",
    ),
    TrackerGuide(
        name = "Phone only (no watch)",
        packageName = null,
        steps = "Your phone can record steps on its own, but something has to write them " +
            "to Health Connect — usually Google Fit or your phone maker's health app.\n\n" +
            "Open Health Connect below and check whether anything appears under Steps. " +
            "If nothing does, install Google Fit and enable its Health Connect sharing.",
    ),
    TrackerGuide(
        name = "Something else / not sure",
        packageName = null,
        steps = "Open Health Connect below and look at Data and access, then Steps or " +
            "Heart rate. Any app already writing data will be listed there.\n\n" +
            "If the list is empty, no app is sharing yet — open your fitness app's " +
            "settings and look for a Health Connect option.",
    ),
)
