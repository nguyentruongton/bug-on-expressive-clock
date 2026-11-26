package app.expressiveclock.ui.screens

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

@Composable
fun OnboardingScreen(onComplete: () -> Unit) {
    LaunchedEffect(Unit) {
        onComplete()
    }
}
