package app.pocketful.ui.nav

import androidx.compose.runtime.Composable

/**
 * The platform back gesture.
 *
 * Declared as an expect rather than reaching for the Android API directly because
 * `androidx.activity.compose.BackHandler` lives in the Android source set, and the screens
 * that need to intercept back all live in common code. On a platform with no back gesture
 * the actual is a no-op and the on-screen affordances carry the same job.
 */
@Composable
expect fun SystemBackHandler(enabled: Boolean, onBack: () -> Unit)
