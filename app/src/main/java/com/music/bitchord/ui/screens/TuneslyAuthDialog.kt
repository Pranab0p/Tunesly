package com.music.bitchord.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.music.bitchord.R
import com.music.bitchord.data.tuneslycloud.TuneslyCloudManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun TuneslyAuthDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var isRegister by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var loginOrEmail by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var isCheckingUsername by remember { mutableStateOf(false) }
    var usernameAvailable by remember { mutableStateOf<Boolean?>(null) }
    var usernameStatusText by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(username, isRegister) {
        if (!isRegister) {
            usernameAvailable = null
            usernameStatusText = null
            isCheckingUsername = false
            return@LaunchedEffect
        }
        val clean = username.trim()
        if (clean.isEmpty()) {
            usernameAvailable = null
            usernameStatusText = null
            isCheckingUsername = false
            return@LaunchedEffect
        }
        if (clean.length < 3) {
            usernameAvailable = false
            usernameStatusText = "At least 3 characters required"
            isCheckingUsername = false
            return@LaunchedEffect
        }
        if (!clean.matches(Regex("^[a-zA-Z0-9_]+$"))) {
            usernameAvailable = false
            usernameStatusText = "Letters, numbers and '_' only"
            isCheckingUsername = false
            return@LaunchedEffect
        }

        delay(400)
        isCheckingUsername = true
        TuneslyCloudManager.checkUsernameAvailable(clean).fold(
            onSuccess = { available ->
                isCheckingUsername = false
                usernameAvailable = available
                usernameStatusText = if (available) "Username is available" else "Username is already taken"
            },
            onFailure = {
                isCheckingUsername = false
            },
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Rounded.CloudSync,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp),
            )
        },
        title = {
            Text(
                text = if (isRegister) "Create Tunesly Account" else "Sign In to Tunesly Cloud",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SegmentedControl(
                    options = listOf("Sign In", "Register"),
                    selectedIndex = if (isRegister) 1 else 0,
                    onSelect = {
                        isRegister = (it == 1)
                        errorMessage = null
                    },
                )

                if (isRegister) {
                    OutlinedTextField(
                        value = username,
                        onValueChange = {
                            username = it.lowercase().filter { ch -> ch.isLetterOrDigit() || ch == '_' }.take(30)
                            errorMessage = null
                        },
                        label = { Text("Username (@handle)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        trailingIcon = {
                            if (isCheckingUsername) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            } else if (usernameAvailable == true) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Available",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp),
                                )
                            } else if (usernameAvailable == false) {
                                Icon(
                                    imageVector = Icons.Rounded.Cancel,
                                    contentDescription = "Not Available",
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                        },
                        supportingText = {
                            if (usernameStatusText != null) {
                                Text(
                                    text = usernameStatusText!!,
                                    color = if (usernameAvailable == true) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        },
                        isError = usernameAvailable == false,
                    )
                }

                OutlinedTextField(
                    value = loginOrEmail,
                    onValueChange = { loginOrEmail = it; errorMessage = null },
                    label = { Text(if (isRegister) "Email" else "Username or Email") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = null },
                    label = { Text("Password") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (errorMessage != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = errorMessage ?: "",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (isLoading) return@Button
                    isLoading = true
                    errorMessage = null
                    coroutineScope.launch {
                        val result = if (isRegister) {
                            TuneslyCloudManager.register(username, loginOrEmail, password)
                        } else {
                            TuneslyCloudManager.login(loginOrEmail, password)
                        }
                        isLoading = false
                        result.fold(
                            onSuccess = { user ->
                                Toast.makeText(
                                    context,
                                    "Welcome to Tunesly, ${user.username}!",
                                    Toast.LENGTH_SHORT,
                                ).show()
                                onDismiss()
                            },
                            onFailure = {
                                errorMessage = it.message ?: "Authentication failed. Check your network or credentials."
                            },
                        )
                    }
                },
                enabled = !isLoading && !isCheckingUsername && password.isNotBlank() && loginOrEmail.isNotBlank() && (!isRegister || (username.isNotBlank() && usernameAvailable == true)),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text(if (isRegister) "Create Account" else "Sign In")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}
