package com.nirogbhumi.app.appcheck

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * RELEASE variant App Check provider. This file lives in src/release and is
 * compiled only into the release/Play (AAB) build.
 *
 * The Play build attests via Play Integrity - the real, production App Check
 * provider. The debug provider used by src/debug's counterpart is a
 * debugImplementation dependency that does not exist here, which is exactly
 * why the choice is split across variant source sets instead of a
 * BuildConfig.DEBUG branch in main/ (that failed to compile the release build).
 */
fun installVariantAppCheckProvider(app: FirebaseApp) {
    FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
        PlayIntegrityAppCheckProviderFactory.getInstance()
    )
}
