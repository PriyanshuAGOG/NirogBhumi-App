package com.nirogbhumi.app.appcheck

import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

/**
 * DEBUG variant App Check provider. This file lives in src/debug and is
 * compiled only into the tester (debug / Firebase App Distribution) build.
 *
 * The debug App Check provider (firebase-appcheck-debug) is a
 * debugImplementation dependency - the DebugAppCheckProviderFactory class is
 * not on the release classpath at all - so it must NOT be referenced from
 * main/, which is compiled into every variant. src/release supplies its own
 * installVariantAppCheckProvider that uses Play Integrity instead.
 *
 * A sideloaded debug build can't do Play Integrity attestation, so without a
 * provider it installs none. If App Check enforcement is ever turned on for
 * any Firebase service (Functions/Firestore/Storage) in the console, every
 * call from this build would then be rejected before it even reaches our own
 * auth/rules checks - surfacing to the client as a plain "unauthenticated"
 * indistinguishable from a real auth problem. The debug provider fixes that;
 * it prints a one-time debug secret to Logcat ("DebugAppCheckProviderFactory")
 * that must be added to Firebase Console > App Check > this app's debug-token
 * allow-list the first time the build runs on a given device.
 */
fun installVariantAppCheckProvider(app: FirebaseApp) {
    FirebaseAppCheck.getInstance(app).installAppCheckProviderFactory(
        DebugAppCheckProviderFactory.getInstance()
    )
}
