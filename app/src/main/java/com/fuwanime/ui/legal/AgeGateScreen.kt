package com.fuwanime.ui.legal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/**
 * One-time age confirmation, shown before the catalogue is reachable.
 * Deliberately simple: no pre-ticked boxes, no "skip" trap, and the decline path exits the app
 * instead of nagging. The answer is stored locally in DataStore.
 */
@Composable
fun AgeGateScreen(onConfirm: () -> Unit, onExit: () -> Unit) {
    var showPrivacy by remember { mutableStateOf(false) }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 28.dp, vertical = 40.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "FuwaStream",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                "Sebelum mulai",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Aplikasi ini menampilkan katalog anime dari layanan pihak ketiga. Sebagian judul " +
                    "mungkin tidak cocok untuk anak-anak.\n\n" +
                    "FuwaStream ditujukan untuk pengguna berusia 13 tahun ke atas. Aplikasi tidak " +
                    "membuat akun dan tidak mengumpulkan data pribadimu — semua riwayat, favorit, " +
                    "dan pengaturan hanya tersimpan di perangkat ini.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            Button(onClick = onConfirm, modifier = Modifier.fillMaxWidth()) {
                Text("Saya berusia 13 tahun atau lebih — Lanjutkan")
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onExit, modifier = Modifier.fillMaxWidth()) {
                Text("Saya belum cukup umur — Keluar")
            }
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = { showPrivacy = true }) {
                Text("Baca Kebijakan Privasi")
            }
        }
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            confirmButton = { TextButton(onClick = { showPrivacy = false }) { Text("Tutup") } },
            title = { Text("Kebijakan Privasi") },
            text = {
                LazyColumn(contentPadding = PaddingValues(vertical = 4.dp)) {
                    items(LegalText.privacy.split("\n\n")) { paragraph ->
                        Text(
                            paragraph,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                }
            },
        )
    }
}
