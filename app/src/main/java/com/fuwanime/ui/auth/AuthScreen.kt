package com.fuwanime.ui.auth

import com.fuwanime.R
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.fuwanime.FuwaApp
import com.fuwanime.data.AccountRepository
import com.fuwanime.data.ProfileRepository
import com.fuwanime.util.fuwaViewModel
import kotlinx.coroutines.launch

class AuthViewModel(
    private val accounts: AccountRepository,
    private val profile: ProfileRepository,
) : ViewModel() {
    val loggedIn = accounts.isLoggedIn

    fun register(email: String, password: String, name: String, onResult: (String?) -> Unit) =
        viewModelScope.launch {
            accounts.register(email, password, name)
                .onSuccess { profile.rename(it.displayName, null, keepBio = true); onResult(null) }
                .onFailure { onResult(it.message) }
        }

    fun login(email: String, password: String, onResult: (String?) -> Unit) =
        viewModelScope.launch {
            accounts.login(email, password)
                .onSuccess { onResult(null) }
                .onFailure { onResult(it.message) }
        }

    fun logout() = viewModelScope.launch { accounts.logout() }
}

@Composable
fun AuthRoute(onDone: () -> Unit) {
    val vm: AuthViewModel = fuwaViewModel { c, _ -> AuthViewModel(c.accountRepository, c.profileRepository) }
    val loggedIn by vm.loggedIn.collectAsStateWithLifecycle(initialValue = false)
    var mode by remember { mutableStateOf(AuthMode.LOGIN) }
    var message by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    if (loggedIn) {
        Box(Modifier.fillMaxSize()) {
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("✅", style = MaterialTheme.typography.displayMedium)
                Text(stringResource(R.string.auth_already_logged), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Button(onClick = onDone, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899))) { Text(stringResource(R.string.auth_continue)) }
                TextButton(onClick = {
                    vm.logout()
                    mode = AuthMode.LOGIN
                }) { Text(stringResource(R.string.auth_logout)) }
            }
        }
        return
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF1E1B2E), Color(0xFF2A1B33), Color(0xFF160F1F))),
        ).verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier.fillMaxWidth().padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                Modifier.size(72.dp).clip(RoundedCornerShape(20.dp)).background(Brush.linearGradient(listOf(Color(0xFFEC4899), Color(0xFF8B5CF6)))),
                contentAlignment = Alignment.Center,
            ) {
                Text("F", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold, color = Color.White)
            }
            Text("FuwaStream", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.ExtraBold, color = Color.White)
            Text(
                if (mode == AuthMode.LOGIN) stringResource(R.string.auth_login_subtitle) else stringResource(R.string.auth_register_subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFB8AFC9),
            )

            var email by remember { mutableStateOf("") }
            var password by remember { mutableStateOf("") }
            var name by remember { mutableStateOf("") }

            if (mode == AuthMode.REGISTER) {
                OutlinedTextField(
                    name, { name = it },
                    label = { Text(stringResource(R.string.auth_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Rounded.Person, null) },
                )
            }
            OutlinedTextField(
                email, { email = it },
                label = { Text(stringResource(R.string.auth_email)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                password, { password = it },
                label = { Text(stringResource(R.string.auth_password)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                leadingIcon = { Icon(Icons.Rounded.Lock, null) },
            )

            message?.let { Text(it, color = Color(0xFFFB7185), style = MaterialTheme.typography.bodySmall) }

            Button(
                onClick = {
                    val cb: (String?) -> Unit = { err -> message = err; if (err == null) onDone() }
                    if (mode == AuthMode.LOGIN) vm.login(email, password, cb) else vm.register(email, password, name, cb)
                },
                modifier = Modifier.fillMaxWidth().height(50.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFEC4899)),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(if (mode == AuthMode.LOGIN) stringResource(R.string.auth_login) else stringResource(R.string.auth_register), fontWeight = FontWeight.Bold)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.weight(1f).height(1.dp).background(Color(0xFF3A3350)))
                Text(stringResource(R.string.auth_or), color = Color(0xFF8A8494), modifier = Modifier.padding(horizontal = 12.dp))
                Spacer(Modifier.weight(1f).height(1.dp).background(Color(0xFF3A3350)))
            }

            Text(stringResource(R.string.auth_google_removed), style = MaterialTheme.typography.labelMedium, color = Color(0xFF8A8494))

            TextButton(onClick = { mode = if (mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN; message = null }) {
                Text(
                    if (mode == AuthMode.LOGIN) stringResource(R.string.auth_switch_register) else stringResource(R.string.auth_switch_login),
                    color = Color(0xFFEC4899),
                )
            }

            Text(stringResource(R.string.auth_local_note), style = MaterialTheme.typography.labelSmall, color = Color(0xFF8A8494))
        }
    }
}

private enum class AuthMode { LOGIN, REGISTER }
