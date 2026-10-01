package com.nirogbhumi.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nirogbhumi.app.health.domain.HealthUiState
import kotlinx.coroutines.delay

/**
 * The member's health records as one lifecycle-aware value. Every screen that shows a reading calls
 * this instead of opening its own Firestore listener, so they can never disagree.
 */
@Composable
fun NirogState.collectHealth(): HealthUiState = health.ui.collectAsStateWithLifecycle().value

/**
 * Keeps the store bound to whoever is signed in and keeps "today" honest: re-derived every minute
 * and whenever the app returns to the foreground (covers midnight and a changed time zone).
 */
@Composable
fun HealthDataLifecycle(state: NirogState) {
    LaunchedEffect(state.repository.userId) { state.health.start() }
    LifecycleResumeEffect(Unit) {
        state.health.start()
        state.health.recompute()
        onPauseOrDispose { }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            state.health.recompute()
        }
    }
}
