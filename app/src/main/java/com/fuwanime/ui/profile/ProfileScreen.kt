package com.fuwanime.ui.profile

import androidx.annotation.StringRes
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fuwanime.R
import com.fuwanime.data.Claimed
import com.fuwanime.data.ProfileRepository
import com.fuwanime.data.ProfileRules
import com.fuwanime.data.ProfileUi
import com.fuwanime.data.QuestUi
import com.fuwanime.util.fuwaViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(private val repo: ProfileRepository) : ViewModel() {
    val ui: StateFlow<ProfileUi?> = repo.observe().stateIn(viewModelScope, SharingStarted.Eagerly, null)
    val levelUps: Flow<Int> = repo.levelUps

    fun rename(name: String) = viewModelScope.launch { repo.setName(name) }

    fun claim(questId: String, onResult: (Claimed?) -> Unit) = viewModelScope.launch { onResult(repo.claim(questId)) }

    fun equip(borderId: String?, onResult: (Boolean) -> Unit) = viewModelScope.launch { onResult(repo.setEquippedBorder(borderId)) }
}

@StringRes
private fun titleRes(level: Int): Int = when (level) {
    1 -> R.string.profile_title_1
    2 -> R.string.profile_title_2
    3 -> R.string.profile_title_3
    4 -> R.string.profile_title_4
    5 -> R.string.profile_title_5
    6 -> R.string.profile_title_6
    7 -> R.string.profile_title_7
    8 -> R.string.profile_title_8
    9 -> R.string.profile_title_9
    else -> R.string.profile_title_10
}

@StringRes
private fun questRes(id: String): Int = when (id) {
    ProfileRules.QUEST_WATCH_EPISODES -> R.string.profile_quest_watch_episodes
    ProfileRules.QUEST_READ_CHAPTERS -> R.string.profile_quest_read_chapters
    else -> R.string.profile_quest_watch_time
}

@StringRes
private fun borderRes(id: String): Int = when (id) {
    ProfileRules.BORDER_SAKURA -> R.string.profile_border_sakura
    ProfileRules.BORDER_OCEAN -> R.string.profile_border_ocean
    else -> R.string.profile_border_gold
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRoute(onBack: () -> Unit) {
    val vm: ProfileViewModel = fuwaViewModel { c, _ -> ProfileViewModel(c.profileRepository) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val res = LocalContext.current.resources
    val say: (String) -> Unit = { msg -> scope.launch { snackbar.currentSnackbarData?.dismiss(); snackbar.showSnackbar(msg) } }
    // Level-ups from a claim and from watching share this one message.
    LaunchedEffect(vm) { vm.levelUps.collect { say(res.getString(R.string.profile_level_up, it)) } }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.profile_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.mine_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val p = ui ?: return@Scaffold
        var editing by remember { mutableStateOf(false) }
        val displayName = p.name.ifBlank { stringResource(R.string.profile_default_name) }
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding() + 8.dp, bottom = 32.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Header(p, displayName, onEdit = { editing = true }) }
            item { Text(stringResource(R.string.profile_quests_header), style = MaterialTheme.typography.titleMedium) }
            quests(p.quests) { q ->
                vm.claim(q.def.id) { result ->
                    say(
                        if (result == null) res.getString(R.string.profile_error_claim)
                        else res.getString(R.string.profile_border_unlocked, res.getString(borderRes(result.borderId))),
                    )
                }
            }
            item { Text(stringResource(R.string.profile_borders_header), style = MaterialTheme.typography.titleMedium) }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = p.equippedBorder == null,
                        onClick = { vm.equip(null) {} },
                        label = { Text(stringResource(R.string.profile_border_none)) },
                    )
                    ProfileRules.borders.forEach { b ->
                        val unlocked = b.id in p.unlockedBorders
                        FilterChip(
                            selected = p.equippedBorder == b.id,
                            onClick = {
                                vm.equip(b.id) { ok -> if (!ok) say(res.getString(R.string.profile_error_equip)) }
                            },
                            enabled = unlocked,
                            label = { Text(stringResource(borderRes(b.id))) },
                            leadingIcon = {
                                if (unlocked) Box(Modifier.size(12.dp).clip(CircleShape).background(Color(b.color)))
                                else Icon(Icons.Rounded.Lock, stringResource(R.string.profile_border_locked), Modifier.size(14.dp))
                            },
                        )
                    }
                }
            }
        }
        if (editing) NameDialog(p.name, onDismiss = { editing = false }) { vm.rename(it); editing = false }
    }
}

private fun LazyListScope.quests(list: List<QuestUi>, onClaim: (QuestUi) -> Unit) {
    items(list, key = { it.def.id }) { q -> QuestCard(q) { onClaim(q) } }
}

@Composable
private fun Header(p: ProfileUi, displayName: String, onEdit: () -> Unit) {
    val levelColor = Color(ProfileRules.levels[p.level - 1].color)
    val ring = p.equippedBorder?.let { ProfileRules.border(it) }?.let { Color(it.color) } ?: MaterialTheme.colorScheme.outlineVariant
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Box(
            Modifier.size(96.dp).border(BorderStroke(4.dp, ring), CircleShape).padding(6.dp).clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) { Text(AVATAR, fontSize = 44.sp) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(displayName, style = MaterialTheme.typography.headlineSmall)
            IconButton(onClick = onEdit) { Icon(Icons.Rounded.Edit, stringResource(R.string.profile_edit_name)) }
        }
        Text(
            stringResource(R.string.profile_level_title, p.level, stringResource(titleRes(p.level))),
            color = levelColor,
            style = MaterialTheme.typography.titleMedium,
        )
        val next = p.nextLevelXp
        if (next == null) {
            Text(stringResource(R.string.profile_xp_max, p.xp.toInt()), style = MaterialTheme.typography.bodyMedium)
        } else {
            LinearProgressIndicator(
                progress = { ((p.xp - p.levelStartXp).toFloat() / (next - p.levelStartXp)).coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = levelColor,
            )
            Text(stringResource(R.string.profile_xp, p.xp.toInt(), next.toInt()), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun QuestCard(q: QuestUi, onClaim: () -> Unit) {
    val minutes = q.def.id == ProfileRules.QUEST_WATCH_SECONDS
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(questRes(q.def.id)), style = MaterialTheme.typography.titleSmall)
            LinearProgressIndicator(progress = { (q.progress.toFloat() / q.def.target).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    val label = if (minutes) {
                        stringResource(R.string.profile_quest_progress_minutes, q.progress / 60, q.def.target / 60)
                    } else {
                        stringResource(R.string.profile_quest_progress, q.progress, q.def.target)
                    }
                    Text(label, style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.profile_quest_reward, q.def.rewardXp.toInt()), style = MaterialTheme.typography.bodySmall)
                }
                Button(onClick = onClaim, enabled = q.done && !q.claimed) {
                    Text(stringResource(if (q.claimed) R.string.profile_claimed else R.string.profile_claim))
                }
            }
        }
    }
}

@Composable
private fun NameDialog(current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.profile_edit_name)) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(ProfileRepository.MAX_NAME) },
                label = { Text(stringResource(R.string.profile_name_label)) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(text) }) { Text(stringResource(R.string.profile_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.mine_cancel)) } },
    )
}

/** Default avatar; the profile has no photo because nothing is uploaded anywhere. */
private const val AVATAR = "\uD83D\uDE3A"
