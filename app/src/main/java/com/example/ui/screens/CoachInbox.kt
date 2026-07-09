package com.nirogbhumi.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.google.firebase.Timestamp
import com.nirogbhumi.app.data.CloudDocument
import com.nirogbhumi.app.data.CloudResult
import com.nirogbhumi.app.ui.NirogState
import com.nirogbhumi.app.ui.canManageProgram
import com.nirogbhumi.app.ui.components.RowCard
import com.nirogbhumi.app.ui.components.SectionLabel
import com.nirogbhumi.app.ui.theme.NirogColor
import com.nirogbhumi.app.ui.theme.NirogRadius
import com.nirogbhumi.app.ui.theme.NirogSpace
import com.nirogbhumi.app.ui.theme.NirogType

/**
 * Async "ask your coach" inbox - a private, non-urgent question thread
 * between one member and their program's coach, deliberately separate from
 * the live group chat (where a personal question would sit in front of the
 * whole batch) and from consultations (which are scheduled sessions).
 *
 * Members see exactly one thread: their own. Program staff see a thread
 * list for their program (grouped client-side from the same collection)
 * and can open any thread to reply. Rules keep every member's thread
 * invisible to every other member.
 */
@Composable
fun CoachInboxScreen(state: NirogState) {
    val myUid = state.repository.userId
    // A coach opening this may not be enrolled in a program themselves -
    // fall back to the first program they actually manage.
    val inboxProgramId = state.activeProgramId.ifBlank { state.coachProgramIds.firstOrNull().orEmpty() }
    val isStaffView = state.canManageProgram(inboxProgramId)
    // Staff: null = thread list; non-null = the member whose thread is open.
    // Members: always their own uid.
    var openThreadUid by remember { mutableStateOf(if (isStaffView) null else myUid) }
    var openThreadName by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(NirogColor.surface)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = NirogSpace.sm, vertical = NirogSpace.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = {
                if (isStaffView && openThreadUid != null) { openThreadUid = null; openThreadName = "" }
                else state.currentScreen = "chat_hub"
            }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = NirogColor.forest)
            }
            Column {
                Text(
                    when {
                        isStaffView && openThreadUid == null -> "Member questions"
                        isStaffView -> openThreadName.ifBlank { "Member" }
                        else -> "Ask your coach"
                    },
                    style = NirogType.sectionHeading, color = NirogColor.forest,
                )
                Text(
                    if (isStaffView && openThreadUid == null) "Private threads · ${state.activeProgramName.ifBlank { "your program" }}"
                    else "Private · not visible to your batch · replies within a day or two",
                    style = NirogType.caption, color = NirogColor.inkMuted,
                )
            }
        }

        when {
            inboxProgramId.isBlank() || myUid == null -> Box(Modifier.fillMaxSize().padding(NirogSpace.xl), contentAlignment = Alignment.Center) {
                Text(
                    "Ask-your-coach unlocks when you join a Care+ program.",
                    style = NirogType.body, color = NirogColor.inkSecondary,
                )
            }
            isStaffView && openThreadUid == null -> CoachThreadList(state, inboxProgramId) { uid, name ->
                openThreadUid = uid; openThreadName = name
            }
            else -> CoachInboxThread(
                state = state,
                programId = inboxProgramId,
                memberUid = openThreadUid!!,
                myUid = myUid,
                senderRole = if (isStaffView) "coach" else "member",
            )
        }
    }
}

/** Staff view: one row per member who has ever written in, newest activity first. */
@Composable
private fun CoachThreadList(state: NirogState, programId: String, onOpen: (String, String) -> Unit) {
    var records by remember { mutableStateOf<List<CloudDocument>?>(null) }
    DisposableEffect(programId) {
        val sub = state.repository.listenCoachInboxForProgram(programId) { result ->
            records = when (result) {
                is CloudResult.Success -> result.value
                is CloudResult.Failure -> emptyList()
            }
        }
        onDispose { sub.cancel() }
    }

    val threads = remember(records) {
        (records ?: emptyList())
            .groupBy { it.values["memberUid"]?.toString().orEmpty() }
            .filterKeys { it.isNotBlank() }
            .map { (uid, messages) ->
                // Repo returns newest-first, so first() is the latest message;
                // the thread's display name comes from the member's own
                // messages, never a staff reply's name.
                val latest = messages.first()
                val memberName = messages.firstOrNull { it.values["senderRole"] == "member" }
                    ?.values?.get("senderName")?.toString() ?: "Member"
                Triple(uid, memberName, latest)
            }
            .sortedByDescending { (_, _, latest) -> (latest.values["createdAt"] as? Timestamp)?.toDate()?.time ?: 0L }
    }

    Column(Modifier.fillMaxSize().padding(horizontal = NirogSpace.lg)) {
        when {
            records == null -> Row(Modifier.padding(vertical = 24.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = NirogColor.forestSoft)
                Spacer(Modifier.size(NirogSpace.sm))
                Text("Loading...", style = NirogType.secondary, color = NirogColor.inkSecondary)
            }
            threads.isEmpty() -> {
                Spacer(Modifier.size(NirogSpace.md))
                SectionLabel("No questions yet")
                Spacer(Modifier.size(NirogSpace.sm))
                Text(
                    "When a member asks a private question, their thread appears here.",
                    style = NirogType.body, color = NirogColor.inkSecondary,
                )
            }
            else -> LazyColumn {
                items(threads, key = { it.first }) { (uid, name, latest) ->
                    val preview = latest.values["text"]?.toString().orEmpty()
                    val at = (latest.values["createdAt"] as? Timestamp)?.toDate()
                    Column {
                        Spacer(Modifier.size(NirogSpace.md))
                        RowCard(
                            title = name,
                            subtitle = preview.take(80) + (at?.let { " · ${relativeTimeLabel(it)}" } ?: ""),
                            onClick = { onOpen(uid, name) },
                            leading = {
                                Box(
                                    Modifier.size(44.dp).clip(RoundedCornerShape(NirogSpace.md)).background(NirogColor.surfaceSunken),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Filled.SupportAgent, contentDescription = null, tint = NirogColor.forest, modifier = Modifier.size(22.dp))
                                }
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CoachInboxThread(state: NirogState, programId: String, memberUid: String, myUid: String, senderRole: String) {
    var records by remember { mutableStateOf<List<CloudDocument>?>(null) }
    var input by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    DisposableEffect(programId, memberUid) {
        val sub = state.repository.listenCoachInboxThread(programId, memberUid) { result ->
            records = when (result) {
                is CloudResult.Success -> result.value
                is CloudResult.Failure -> emptyList()
            }
        }
        onDispose { sub.cancel() }
    }

    Column(Modifier.fillMaxSize().imePadding().navigationBarsPadding()) {
        when {
            records == null -> Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = NirogColor.forestSoft)
            }
            records!!.isEmpty() -> Box(Modifier.fillMaxWidth().weight(1f).padding(NirogSpace.xl), contentAlignment = Alignment.Center) {
                Text(
                    if (senderRole == "member")
                        "Have a question that doesn't need the whole batch? Ask it here - only your coach sees this thread."
                    else "No messages in this thread yet.",
                    style = NirogType.body, color = NirogColor.inkSecondary,
                )
            }
            // Repo returns newest-first, which is exactly what reverseLayout
            // wants: item 0 renders at the bottom, so the newest message sits
            // just above the input with history scrolling up.
            else -> LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = NirogSpace.lg),
                reverseLayout = true,
            ) {
                items(records!!, key = { it.id }) { message ->
                    val mine = message.values["fromUid"] == myUid
                    val fromCoach = message.values["senderRole"] == "coach"
                    val at = (message.values["createdAt"] as? Timestamp)?.toDate()
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = NirogSpace.xs),
                        horizontalAlignment = if (mine) Alignment.End else Alignment.Start,
                    ) {
                        Column(
                            Modifier
                                .widthIn(max = 300.dp)
                                .clip(NirogRadius.cardShape)
                                .background(if (mine) NirogColor.forest else NirogColor.surfaceSunken)
                                .padding(horizontal = NirogSpace.md, vertical = NirogSpace.sm),
                        ) {
                            if (!mine) {
                                Text(
                                    if (fromCoach) "Coach · ${message.values["senderName"]?.toString().orEmpty()}"
                                    else message.values["senderName"]?.toString() ?: "Member",
                                    style = NirogType.overline, color = NirogColor.forest,
                                )
                            }
                            Text(
                                message.values["text"]?.toString().orEmpty(),
                                style = NirogType.body,
                                color = if (mine) Color.White else NirogColor.inkPrimary,
                            )
                            if (at != null) {
                                Text(
                                    relativeTimeLabel(at),
                                    style = NirogType.overline,
                                    color = if (mine) Color.White.copy(alpha = 0.7f) else NirogColor.inkMuted,
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(NirogSpace.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text(if (senderRole == "member") "Ask your coach..." else "Reply privately...") },
                shape = NirogRadius.pillShape,
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = NirogColor.forest,
                    unfocusedBorderColor = NirogColor.outlineVariant,
                ),
            )
            Spacer(Modifier.size(NirogSpace.sm))
            IconButton(
                enabled = !sending && input.isNotBlank(),
                onClick = {
                    sending = true
                    state.repository.sendCoachInboxMessage(
                        programId = programId,
                        memberUid = memberUid,
                        text = input,
                        senderName = state.profileName.ifBlank { if (senderRole == "coach") "Coach" else "Member" },
                        senderRole = senderRole,
                    ) { result ->
                        sending = false
                        when (result) {
                            is CloudResult.Success -> input = ""
                            is CloudResult.Failure -> state.cloudMessage = result.message
                        }
                    }
                },
            ) {
                if (sending) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = NirogColor.forestSoft)
                else Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Send",
                    tint = if (input.isNotBlank()) NirogColor.forest else NirogColor.inkMuted,
                )
            }
        }
    }
}
