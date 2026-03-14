package com.example.modrinthforandroid.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

// ─── Tutorial step data ───────────────────────────────────────────────────────

data class TutorialStep(
    val emoji: String,
    val title: String,
    val body: String,
    val tip: String? = null   // optional highlighted tip box
)

private val TUTORIAL_STEPS = listOf(

    TutorialStep(
        emoji = "👋",
        title = "Welcome to Mojorinth!",
        body  = "This quick tour will show you everything the app can do. It only takes about a minute — let's go!",
    ),

    // ── INSTANCE SETUP (most important, most detail) ─────────────────────────
    TutorialStep(
        emoji = "📦",
        title = "Step 1 — Pick Your Instance Folder",
        body  = "The very first thing you need to do is point Mojorinth at your Modrinth launcher's instances folder.\n\n" +
                "Tap the instance card on the Home screen and use the folder picker. You need to navigate to:\n\n" +
                "   Internal Storage → games → com.modrinth.theseus → profiles\n\n" +
                "That \"profiles\" folder is the PARENT directory — it contains one subfolder per instance (e.g. \"Fabric 1.21\", \"Vanilla+\", etc.).\n\n" +
                "Select the \"profiles\" folder itself, NOT one of the instance subfolders inside it.",
        tip   = "⚠️ Common mistake: don't navigate INTO an instance subfolder. Select the folder that CONTAINS your instances."
    ),

    TutorialStep(
        emoji = "🗂️",
        title = "Step 1b — Select an Active Instance",
        body  = "Once you've picked the profiles folder, the app will list every instance it finds inside.\n\n" +
                "Tap an instance to make it \"active\". The active instance is where files will be managed — you can see its name shown in the top bar and the side drawer.\n\n" +
                "You can switch instances any time from the Home screen card.",
        tip   = "💡 If you use multiple modpacks, just tap a different instance in the card whenever you switch modpacks."
    ),

    // ── BROWSE ───────────────────────────────────────────────────────────────
    TutorialStep(
        emoji = "🔍",
        title = "Step 2 — Browse & Search",
        body  = "The Home screen shows trending Mods, Modpacks, and Shaders pulled live from Modrinth.\n\n" +
                "To search for something specific, tap the 🔍 Search icon in the top-right, then pick a category:\n\n" +
                "  🧩 Mods  •  🎨 Resource Packs  •  🌍 Data Packs\n" +
                "  ✨ Shaders  •  🗺 Modpacks  •  🔌 Plugins\n\n" +
                "You can also filter by Minecraft version, loader (Fabric, Forge, etc.), and sort by downloads or date.",
        tip   = "💡 Tap any mod card to see its full description, screenshots, and all available versions before downloading."
    ),

    // ── DOWNLOADS ────────────────────────────────────────────────────────────
    TutorialStep(
        emoji = "⬇️",
        title = "Step 3 — Downloading Files",
        body  = "Found something you like? Tap the download button on any version — Mojorinth will automatically put the file in the right subfolder of your active instance.\n\n" +
                "  • Mods → .minecraft/mods\n" +
                "  • Shaders → shaderpacks\n" +
                "  • Resource Packs → resourcepacks\n" +
                "  • Plugins → plugins\n\n" +
                "hi",
        tip   = "💡 If you download a file you already have, the app will warn you before overwriting it (toggle in Settings)."
    ),

    // ── SIDE DRAWER ──────────────────────────────────────────────────────────
    TutorialStep(
        emoji = "☰",
        title = "Step 4 — The Side Drawer",
        body  = "Swipe from the left edge or tap the ☰ hamburger icon to open the side drawer. It has three items:\n\n" +
                "  📂 Instance Manager — Browse, add, or remove mods/shaders inside your active instance directly from the app.\n\n" +
                "  🐛 Log Analyzer — Paste or import a Minecraft crash log and let the app highlight the likely cause.\n\n" +
                "  ⚙️ Settings — Change the app theme (Dark / Light / AMOLED) and other preferences.",
    ),

    // ── DONE ─────────────────────────────────────────────────────────────────
    TutorialStep(
        emoji = "🎉",
        title = "You're all set!",
        body  = "That's everything! Remember:\n\n" +
                "  1. Set the profiles folder (games › com.modrinth.theseus › profiles)\n" +
                "  2. Pick an active instance\n" +
                "  3. Browse, search, and download\n\n" +
                "You can replay this tutorial any time from Settings → Help.",
    )
)

// ─── Public composable ────────────────────────────────────────────────────────

@Composable
fun TutorialOverlay(
    onDismiss: () -> Unit
) {
    var stepIndex by remember { mutableIntStateOf(0) }
    val step      = TUTORIAL_STEPS[stepIndex]
    val isLast    = stepIndex == TUTORIAL_STEPS.lastIndex
    val progress  = (stepIndex + 1).toFloat() / TUTORIAL_STEPS.size

    Dialog(
        onDismissRequest = onDismiss,
        properties       = DialogProperties(
            dismissOnBackPress    = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape         = RoundedCornerShape(20.dp),
            color         = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            modifier      = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
        ) {
            Column(modifier = Modifier.padding(24.dp)) {

                // ── Progress dots ─────────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment     = Alignment.CenterVertically
                ) {
                    TUTORIAL_STEPS.forEachIndexed { i, _ ->
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 3.dp)
                                .size(if (i == stepIndex) 10.dp else 7.dp)
                                .clip(CircleShape)
                                .background(
                                    if (i <= stepIndex)
                                        MaterialTheme.colorScheme.primary
                                    else
                                        MaterialTheme.colorScheme.outlineVariant
                                )
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ── Emoji + title ─────────────────────────────────────────
                Text(
                    step.emoji,
                    fontSize  = 40.sp,
                    modifier  = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    step.title,
                    style      = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color      = MaterialTheme.colorScheme.onSurface,
                    textAlign  = TextAlign.Center,
                    modifier   = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(14.dp))

                // ── Scrollable body ───────────────────────────────────────
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        step.body,
                        style     = MaterialTheme.typography.bodyMedium,
                        color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        lineHeight = 22.sp
                    )

                    // ── Optional tip box ──────────────────────────────────
                    step.tip?.let { tip ->
                        Spacer(Modifier.height(12.dp))
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        ) {
                            Text(
                                tip,
                                modifier   = Modifier.padding(10.dp),
                                style      = MaterialTheme.typography.bodySmall,
                                color      = MaterialTheme.colorScheme.onPrimaryContainer,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // ── Progress bar ──────────────────────────────────────────
                LinearProgressIndicator(
                    progress      = { progress },
                    modifier      = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp)),
                    color         = MaterialTheme.colorScheme.primary,
                    trackColor    = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                )

                Spacer(Modifier.height(16.dp))

                // ── Navigation buttons ────────────────────────────────────
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Skip / Back
                    if (stepIndex == 0) {
                        OutlinedButton(
                            onClick  = onDismiss,
                            modifier = Modifier.weight(1f)
                        ) { Text("Skip") }
                    } else {
                        OutlinedButton(
                            onClick  = { stepIndex-- },
                            modifier = Modifier.weight(1f)
                        ) { Text("← Back") }
                    }

                    // Next / Done
                    Button(
                        onClick  = { if (isLast) onDismiss() else stepIndex++ },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(if (isLast) "Let's go! 🚀" else "Next →")
                    }
                }

                // Step counter
                Spacer(Modifier.height(8.dp))
                Text(
                    "Step ${stepIndex + 1} of ${TUTORIAL_STEPS.size}",
                    style     = MaterialTheme.typography.labelSmall,
                    color     = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
                    modifier  = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}