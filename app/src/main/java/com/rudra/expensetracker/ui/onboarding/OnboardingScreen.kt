package com.rudra.expensetracker.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * First-launch setup.
 *
 * Every permission step states plainly why it is needed, what it enables and
 * what still works without it, and every one of them can be skipped.
 */
@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var step by remember { mutableIntStateOf(0) }

    val smsPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { viewModel.refresh() }

    val notificationPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.refresh() }

    val steps = buildList {
        add(OnboardingStep.WELCOME)
        add(OnboardingStep.HOW_IT_WORKS)
        add(OnboardingStep.SMS_PERMISSION)
        add(OnboardingStep.NOTIFICATIONS)
        if (state.showQuickAddStep) add(OnboardingStep.QUICK_ADD)
        add(OnboardingStep.APP_LOCK)
        add(OnboardingStep.DONE)
    }
    val current = steps[step.coerceIn(0, steps.lastIndex)]

    Scaffold { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            LinearProgressIndicator(
                progress = { (step + 1f) / steps.size },
                modifier = Modifier.fillMaxWidth(),
            )

            AnimatedContent(targetState = current, label = "onboarding") { value ->
                Column(
                    Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(value.title, style = MaterialTheme.typography.headlineMedium)
                    Text(value.body, style = MaterialTheme.typography.bodyLarge)

                    when (value) {
                        OnboardingStep.SMS_PERMISSION -> PermissionDetail(
                            granted = state.smsGranted,
                            why = "The app reads incoming bank alerts to pull out the amount, the account and " +
                                "who was paid, so you only have to say what it was for.",
                            enables = "Automatic transaction detection.",
                            ifDenied = "Nothing is detected automatically, but you can still add every " +
                                "transaction yourself and use the whole app.",
                        )

                        OnboardingStep.NOTIFICATIONS -> PermissionDetail(
                            granted = state.notificationsGranted,
                            why = "A detected transaction needs to ask you what it was for.",
                            enables = "One-tap categorisation from the notification shade and lock screen.",
                            ifDenied = "Detected transactions collect under \"Waiting for a category\" " +
                                "on the home screen instead.",
                        )

                        OnboardingStep.QUICK_ADD -> Text(
                            state.quickAddDescription,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        else -> Unit
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (step > 0 && current != OnboardingStep.DONE) {
                    TextButton(onClick = { step-- }) { Text("Back") }
                } else {
                    Spacer(Modifier.size(1.dp))
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    when (current) {
                        OnboardingStep.SMS_PERMISSION -> {
                            if (!state.smsGranted) {
                                OutlinedButton(onClick = { step++ }) { Text("Skip") }
                                Button(onClick = {
                                    smsPermission.launch(
                                        arrayOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS),
                                    )
                                }) { Text("Allow") }
                            } else {
                                Button(onClick = { step++ }) { Text("Next") }
                            }
                        }

                        OnboardingStep.NOTIFICATIONS -> {
                            if (!state.notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                OutlinedButton(onClick = { step++ }) { Text("Skip") }
                                Button(onClick = {
                                    notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }) { Text("Allow") }
                            } else {
                                Button(onClick = { step++ }) { Text("Next") }
                            }
                        }

                        OnboardingStep.APP_LOCK -> {
                            OutlinedButton(onClick = { step++ }) { Text("Not now") }
                            Button(onClick = { step++ }) { Text("Set up later in Settings") }
                        }

                        OnboardingStep.DONE -> Button(onClick = {
                            viewModel.complete()
                            onFinished()
                        }) { Text("Start tracking") }

                        else -> Button(onClick = { step++ }) { Text("Next") }
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionDetail(granted: Boolean, why: String, enables: String, ifDenied: String) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (granted) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.size(8.dp))
                Text("Already granted", style = MaterialTheme.typography.bodyMedium)
            }
        }
        LabelledParagraph("Why it is needed", why)
        LabelledParagraph("What it enables", enables)
        LabelledParagraph("If you say no", ifDenied)
    }
}

@Composable
private fun LabelledParagraph(label: String, text: String) {
    Column {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}

enum class OnboardingStep(val title: String, val body: String) {
    WELCOME(
        "Track your money automatically",
        "Your bank already texts you every time money moves. This app reads those messages on your phone " +
            "and turns them into a clear picture of where your money goes.",
    ),
    HOW_IT_WORKS(
        "You answer one question",
        "When a bank message arrives, the app works out the amount, the account, the bank and who was paid. " +
            "All it asks you is what the payment was for. Over time it learns your regular payees and suggests " +
            "the answer -- which you can always change.",
    ),
    SMS_PERMISSION("Reading bank messages", "This is the permission that makes detection possible."),
    NOTIFICATIONS("Asking you about a payment", "This is how the app asks what a payment was for."),
    QUICK_ADD("Quick Add", "Ways to record a payment without opening the app."),
    APP_LOCK(
        "Lock the app",
        "Your transaction history is sensitive. You can require a PIN, or your fingerprint, before the app opens. " +
            "You can turn this on at any time from Settings.",
    ),
    DONE(
        "You are set up",
        "Nothing runs in the background draining your battery -- the app wakes only when a message arrives. " +
            "Everything stays on this device: the app has no internet permission at all.",
    ),
}
