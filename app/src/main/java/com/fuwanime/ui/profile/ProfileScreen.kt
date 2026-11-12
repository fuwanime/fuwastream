package com.fuwanime.ui.profile

import com.fuwanime.R
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.LargeTopAppBar
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import coil3.compose.AsyncImage
import com.fuwanime.data.LevelProgress
import com.fuwanime.data.ProfileRepository
import com.fuwanime.data.RankTags
import com.fuwanime.data.XpCurve
import com.fuwanime.data.local.WatchHistoryEntity
import com.fuwanime.data.LibraryRepository
import com.fuwanime.ui.components.frostedStyle
import com.fuwanime.util.fuwaViewModel
import java.io.File
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ProfileViewModel(
    private val profileRepo: ProfileRepository,
    private val library: LibraryRepository,
    private val quests: com.fuwanime.data.QuestRepository,
    private val appScope: kotlinx.coroutines.CoroutineScope,
) : ViewModel() {
    val profile = profileRepo.profile
        .combine(library.continueWatching(6)) { p, history -> ProfileUi(p, history) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    data class ProfileUi(val profile: com.fuwanime.data.local.ProfileEntity, val recent: List<WatchHistoryEntity>)

    val questsFlow = quests.quests

    fun rename(name: String, bio: String) = appScope.launch { profileRepo.rename(name, bio) }

    fun claim(quest: com.fuwanime.data.QuestEntity, onResult: (String?) -> Unit) = appScope.launch {
        val border = quests.claim(quest)
        val msg = if (border != null) "+${quest.xpReward} XP · Border ${border.title} terbuka!" else "+${quest.xpReward} XP"
        onResult(msg)
    }

    fun equipBorder(id: String) = appScope.launch { profileRepo.setEquippedBorder(id) }

    fun pickImage(context: android.content.Context, uri: Uri, avatar: Boolean, onDone: () -> Unit) = appScope.launch {
        runCatching {
            val prefix = if (avatar) "avatar" else "banner"
            // Nama file unik tiap ganti -> path berubah -> Coil memuat ulang gambar langsung (tanpa relog).
            val dest = File(context.filesDir, "${prefix}_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input -> dest.outputStream().use { input.copyTo(it) } }
            if (avatar) profileRepo.setAvatar(dest.absolutePath) else profileRepo.setBanner(dest.absolutePath)
            // Hapus file lama agar penyimpanan tidak menumpuk.
            context.filesDir.listFiles { f -> f.name.startsWith("${prefix}_") || f.name == "$prefix.jpg" }
                ?.filter { it.absolutePath != dest.absolutePath }
                ?.forEach { it.delete() }
        }
        onDone()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileRoute(onBack: () -> Unit) {
    val vm: ProfileViewModel = fuwaViewModel { c, _ ->
        ProfileViewModel(c.profileRepository, c.libraryRepository, c.questRepository, c.appScope)
    }
    val ui by vm.profile.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showEdit by remember { mutableStateOf(false) }
    var pickingAvatar by remember { mutableStateOf(true) }

    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) vm.pickImage(context, uri, avatar = pickingAvatar) {}
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }, containerColor = MaterialTheme.colorScheme.background) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            val p = ui
            if (p == null) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            } else {
            ProfileContent(
                profile = p.profile,
                recent = p.recent,
                progress = XpCurve.progress(p.profile.totalXp),
                quests = vm.questsFlow.collectAsStateWithLifecycle(emptyList()).value,
                onBack = onBack,
                onEdit = { showEdit = true },
                onPickAvatar = { pickingAvatar = true; pickImage.launch("image/*") },
                onPickBanner = { pickingAvatar = false; pickImage.launch("image/*") },
                onClaim = { q ->
                    vm.claim(q) { msg -> scope.launch { msg?.let { snackbar.showSnackbar(it) } } }
                },
                onEquipBorder = { vm.equipBorder(it) },
            )
            if (showEdit) {
                EditProfileSheet(
                    initialName = p.profile.name,
                    initialBio = p.profile.bio,
                    onDismiss = { showEdit = false },
                    onSave = { name, bio ->
                        vm.rename(name, bio)
                        showEdit = false
                    },
                )
            }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun ProfileContent(
    profile: com.fuwanime.data.local.ProfileEntity,
    recent: List<WatchHistoryEntity>,
    progress: LevelProgress,
    quests: List<com.fuwanime.data.QuestEntity>,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onPickAvatar: () -> Unit,
    onPickBanner: () -> Unit,
    onClaim: (com.fuwanime.data.QuestEntity) -> Unit,
    onEquipBorder: (String) -> Unit,
) {
    val tag = RankTags.safe(profile.highestTagIndex)
    val tagColor = Color(tag.color)
    val nextTag = RankTags.safe(RankTags.indexForLevel(progress.level + 1))
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        // Banner
        Box(Modifier.fillMaxWidth().height(190.dp)) {
            if (profile.bannerPath.isNotBlank()) {
                AsyncImage(File(profile.bannerPath), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(listOf(Color(0xFF1C1C1C), Color(0xFF0A0A0A))),
                    ),
                )
            }
            // Pembatas hitam di bawah banner (scrim) supaya teks "Ganti Banner" terbaca.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(56.dp)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f)))),
            )
            Row(Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null, tint = Color.White) }
            }
            Row(
                Modifier.align(Alignment.BottomEnd).clickable(onClick = onPickBanner).padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(stringResource(R.string.profile_change_banner), color = Color.White, style = MaterialTheme.typography.labelLarge)
                Icon(Icons.Rounded.CameraAlt, null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
        }
        // Avatar + identity
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Box(Modifier.offset(y = (-44).dp).size(104.dp)) {
                val equipped = com.fuwanime.data.AvatarBorders.byId(profile.equippedBorder)
                val equippedColor = Color(equipped.color)
                // Outer glow ring (the earned border), then the avatar inside.
                Box(
                    Modifier
                        .fillMaxSize()
                        .clip(CircleShape)
                        .then(
                            if (profile.equippedBorder == "none")
                                Modifier.background(MaterialTheme.colorScheme.surfaceContainerHigh)
                            else
                                Modifier.background(Brush.sweepGradient(listOf(equippedColor, Color(equipped.glow), equippedColor)))
                        )
                        .border(4.dp, if (profile.equippedBorder == "none") Color.Transparent else equippedColor.copy(alpha = 0.55f), CircleShape)
                        .padding(6.dp)
                        .clickable(onClick = onPickAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.fillMaxSize().clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainerLow).border(3.dp, MaterialTheme.colorScheme.background, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (profile.avatarPath.isNotBlank()) {
                            AsyncImage(File(profile.avatarPath), null, Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Text(profile.name.take(1).uppercase(), style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                // Ikon ganti foto: di LUAR lingkaran foto profil (pojok kanan bawah).
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .offset(x = 4.dp, y = 4.dp)
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF2E2E2E))
                        .border(2.dp, MaterialTheme.colorScheme.background, CircleShape)
                        .clickable(onClick = onPickAvatar),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.CameraAlt, null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
            }
            Column(Modifier.offset(y = (-36).dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(profile.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                    RankTagBadge(tag)
                }
                if (profile.bio.isNotBlank()) Text(profile.bio, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F), contentColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Icon(Icons.Rounded.Edit, null, Modifier.size(16.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(stringResource(R.string.profile_edit))
                }
            }
        }
        // Level card
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.WorkspacePremium, null, tint = Color(0xFFB0B0B0))
                    Text(stringResource(R.string.profile_my_level), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(stringResource(R.string.profile_total_xp, profile.totalXp), color = Color.White, fontWeight = FontWeight.Bold)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    Box(
                        Modifier.size(64.dp).clip(CircleShape).background(Brush.linearGradient(listOf(Color(0xFF3A3A3A), Color(0xFF161616)))),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${progress.level}", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(stringResource(R.string.profile_next_rank, nextTag.title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(stringResource(R.string.profile_xp_to_next, progress.xpForNext - progress.xpIntoLevel), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                // Progress bar
                Box(Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                    Box(Modifier.fillMaxWidth(progress.fraction.coerceIn(0.02f, 1f)).height(10.dp).clip(RoundedCornerShape(5.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF6E6E6E), Color(0xFF2E2E2E)))))
                }
                Text("${(progress.fraction * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, color = Color.White, modifier = Modifier.align(Alignment.End))
            }
        }
        // Quest board (resets every 3 days)
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.EmojiEvents, null, tint = Color(0xFFB0B0B0))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.quest_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.quest_reset_info), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                quests.forEach { q ->
                    val border = q.borderReward.takeIf { it.isNotBlank() }?.let { com.fuwanime.data.AvatarBorders.byId(it) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Column(Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(q.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                    border?.let {
                                        Box(Modifier.size(10.dp).clip(CircleShape).background(Color(it.color)))
                                    }
                                }
                                Text("${q.progress}/${q.target} · +${q.xpReward} XP", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (q.claimed) {
                                Text(stringResource(R.string.quest_claimed), style = MaterialTheme.typography.labelMedium, color = Color(0xFFB0B0B0), fontWeight = FontWeight.Bold)
                            } else if (q.done) {
                                Button(
                                    onClick = { onClaim(q) },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F), contentColor = Color.White),
                                    shape = RoundedCornerShape(999.dp),
                                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                                ) { Text(stringResource(R.string.quest_claim), fontWeight = FontWeight.Bold) }
                            } else {
                                Box(Modifier.size(70.dp, 8.dp).clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surfaceContainerHigh)) {
                                    Box(Modifier.fillMaxWidth((q.progress.toFloat() / q.target).coerceIn(0f, 1f)).height(8.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(Color(0xFF6E6E6E), Color(0xFF2E2E2E)))))
                                }
                            }
                        }
                    }
                }
            }
        }
        // Border collection
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(stringResource(R.string.borders_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(stringResource(R.string.borders_info), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val owned = profile.ownedBorders.split(",").filter { it.isNotBlank() }.toSet()
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    com.fuwanime.data.AvatarBorders.ALL.forEach { b ->
                        val isOwned = b.id == "none" || owned.contains(b.id)
                        val isEquipped = profile.equippedBorder == b.id
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(
                                    when {
                                        isEquipped -> Color(b.color).copy(alpha = 0.25f)
                                        isOwned -> MaterialTheme.colorScheme.surfaceContainerHigh
                                        else -> Color.Transparent
                                    },
                                )
                                .border(
                                    1.dp,
                                    when {
                                        isEquipped -> Color(b.color)
                                        isOwned -> Color(b.color).copy(alpha = 0.5f)
                                        else -> MaterialTheme.colorScheme.outlineVariant
                                    },
                                    RoundedCornerShape(999.dp),
                                )
                                .clickable(enabled = isOwned) { onEquipBorder(b.id) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Box(Modifier.size(12.dp).clip(CircleShape).background(if (isOwned) Color(b.color) else MaterialTheme.colorScheme.outlineVariant))
                            Text(
                                if (isOwned) b.title else "???",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = if (isEquipped) FontWeight.Bold else FontWeight.Medium,
                                color = if (isOwned) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
        // Recently watched
        Card(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
            shape = RoundedCornerShape(18.dp),
        ) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Rounded.History, null, tint = Color(0xFFB0B0B0))
                    Text(stringResource(R.string.profile_recent), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
                if (recent.isEmpty()) {
                    Text(stringResource(R.string.profile_recent_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    recent.take(5).forEach { h ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            AsyncImage(h.animePoster.ifBlank { h.animeCover }, null, Modifier.size(38.dp, 52.dp).clip(RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                            Column {
                                Text(h.animeTitle, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                Text(stringResource(R.string.profile_episode_n, h.episodeNumber), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(90.dp))
    }
}

/** The rank tag pill shown next to the name. */
@Composable
fun RankTagBadge(tag: RankTags.Tag, modifier: Modifier = Modifier) {
    val color = Color(0xFFB0B0B0) // monokrom: semua gelar tampil abu-abu netral
    Box(
        modifier
            .clip(RoundedCornerShape(999.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color, RoundedCornerShape(999.dp))
            .padding(horizontal = 10.dp, vertical = 3.dp),
    ) {
        Text(tag.title, color = color, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun EditProfileSheet(initialName: String, initialBio: String, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initialName) }
    var bio by remember { mutableStateOf(initialBio) }
    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.6f)).clickable(onClick = onDismiss), contentAlignment = Alignment.Center) {
        Card(
            Modifier.padding(24.dp).clickable(enabled = false) {},
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
            shape = RoundedCornerShape(20.dp),
        ) {
            Column(Modifier.padding(20.dp).clickable(enabled = false) {}, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(stringResource(R.string.profile_edit), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                OutlinedTextField(name, { name = it }, label = { Text(stringResource(R.string.profile_name)) }, singleLine = true)
                OutlinedTextField(bio, { bio = it }, label = { Text(stringResource(R.string.profile_bio)) }, minLines = 3, maxLines = 5)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.align(Alignment.End)) {
                    Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)) { Text(stringResource(R.string.profile_cancel)) }
                    Button(onClick = { onSave(name, bio) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1F1F1F), contentColor = Color.White)) { Text(stringResource(R.string.profile_save)) }
                }
            }
        }
    }
}
