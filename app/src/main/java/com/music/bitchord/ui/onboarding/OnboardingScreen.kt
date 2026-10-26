package com.music.bitchord.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.tuneslycloud.TuneslyCloudManager
import com.music.bitchord.sharedui.resources.Res
import com.music.bitchord.sharedui.resources.ic_google_g
import com.music.bitchord.sharedui.resources.ic_logo
import com.music.bitchord.ui.player.MESH_CYCLE_MILLIS
import com.music.bitchord.ui.player.MeshGradientBackground
import com.music.bitchord.ui.player.WelcomeMeshPalettes
import com.music.bitchord.ui.player.cyclingMeshPalette
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.painterResource

private enum class AuthMode {
    LOGIN,
    SIGNUP,
}

/**
 * Ultra-smooth, animated first-launch welcome & authentication screen for Tunesly.
 *
 * Features fluid spring animations, sliding tab pill indicator, animated form transitions,
 * glassmorphic inputs with glowing focus borders, and tactile button feedback.
 */
@Composable
fun OnboardingScreen(
    signedIn: Boolean,
    initialName: String,
    onNameChosen: (String) -> Unit,
    onSignIn: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var authMode by rememberSaveable { mutableStateOf(AuthMode.LOGIN) }
    val focusManager = LocalFocusManager.current
    val coroutineScope = rememberCoroutineScope()
    val scrollState = rememberScrollState()

    // Sign Up Fields
    var fullName by rememberSaveable { mutableStateOf(initialName) }
    var signupUsername by rememberSaveable { mutableStateOf("") }
    var signupEmail by rememberSaveable { mutableStateOf("") }
    var signupPassword by rememberSaveable { mutableStateOf("") }
    var signupPasswordVisible by rememberSaveable { mutableStateOf(false) }

    // Log In Fields
    var loginIdentifier by rememberSaveable { mutableStateOf("") }
    var loginPassword by rememberSaveable { mutableStateOf("") }
    var loginPasswordVisible by rememberSaveable { mutableStateOf(false) }

    // Status
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Auto-dismiss if Google sign-in completes
    LaunchedEffect(signedIn) {
        if (signedIn) onFinish()
    }

    // Dynamic mesh animation
    val palette = cyclingMeshPalette(WelcomeMeshPalettes)

    // Subtle breathing animation for brand logo
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val logoGlowAlpha by infiniteTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.85f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "logoGlow",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
    ) {
        MeshGradientBackground(
            palette = palette,
            trackKey = authMode.ordinal,
            continuous = true,
            driftMillis = 7_000,
            colorFadeMillis = MESH_CYCLE_MILLIS,
        )

        // Gradient overlay for smooth readability
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.28f),
                        0.40f to Color.Black.copy(alpha = 0.58f),
                        1f to Color.Black.copy(alpha = 0.90f),
                    ),
                ),
        )

        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 480.dp)
                    .systemBarsPadding()
                    .imePadding()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Spacer(Modifier.height(20.dp))

                // Brand Header with Icon & Name
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(bottom = 8.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        // Subtle animated radial glow behind logo
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .scale(1.2f)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = logoGlowAlpha * 0.18f)),
                        )
                        Image(
                            painter = painterResource(Res.drawable.ic_logo),
                            contentDescription = "Tunesly Logo",
                            modifier = Modifier.size(38.dp),
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "TUNESLY",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 3.sp,
                        color = Color.White,
                    )
                }

                // Animated Title & Subtitle Transition
                AnimatedContent(
                    targetState = authMode,
                    transitionSpec = {
                        (fadeIn(tween(260)) + slideInHorizontally { if (targetState == AuthMode.SIGNUP) 40 else -40 })
                            .togetherWith(fadeOut(tween(180)) + slideOutHorizontally { if (targetState == AuthMode.SIGNUP) -40 else 40 })
                    },
                    label = "headerText",
                ) { mode ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = if (mode == AuthMode.LOGIN) "Welcome Back" else "Create Account",
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontSize = 31.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = (-0.8).sp,
                                color = Color.White,
                            ),
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (mode == AuthMode.LOGIN) {
                                stringResource(R.string.onboarding_auth_welcome_subtitle)
                            } else {
                                "Sign up to sync music across devices, chat with friends & personalize your feed"
                            },
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = 13.5.sp,
                                lineHeight = 19.sp,
                                color = Color.White.copy(alpha = 0.72f),
                            ),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Smooth Sliding Capsule Switcher (Log In | Sign Up)
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(4.dp),
                ) {
                    val tabWidth = (maxWidth) / 2
                    val sliderOffset by animateDpAsState(
                        targetValue = if (authMode == AuthMode.LOGIN) 0.dp else tabWidth,
                        animationSpec = spring(stiffness = 600f, dampingRatio = 0.82f),
                        label = "sliderPill",
                    )

                    // Sliding Active White Indicator
                    Box(
                        modifier = Modifier
                            .offset(x = sliderOffset)
                            .width(tabWidth)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .background(Color.White),
                    )

                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    authMode = AuthMode.LOGIN
                                    errorMessage = null
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            val textColor by animateColorAsState(
                                if (authMode == AuthMode.LOGIN) Color.Black else Color.White.copy(alpha = 0.75f),
                                tween(180),
                                label = "loginTextCol",
                            )
                            Text(
                                text = stringResource(R.string.auth_tab_login),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    authMode = AuthMode.SIGNUP
                                    errorMessage = null
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            val textColor by animateColorAsState(
                                if (authMode == AuthMode.SIGNUP) Color.Black else Color.White.copy(alpha = 0.75f),
                                tween(180),
                                label = "signupTextCol",
                            )
                            Text(
                                text = stringResource(R.string.auth_tab_signup),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(18.dp))

                // Error Banner with slide/fade animation
                AnimatedVisibility(
                    visible = errorMessage != null,
                    enter = fadeIn(tween(200)) + slideInHorizontally { -20 },
                    exit = fadeOut(tween(150)) + slideOutHorizontally { 20 },
                ) {
                    errorMessage?.let { msg ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color(0xFFE53935).copy(alpha = 0.28f))
                                .border(1.dp, Color(0xFFFF5252).copy(alpha = 0.45f), RoundedCornerShape(14.dp))
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFFF8A80),
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                    }
                }

                // Animated Form Content (Smooth Left/Right Slide)
                AnimatedContent(
                    targetState = authMode,
                    transitionSpec = {
                        val forward = targetState == AuthMode.SIGNUP
                        (slideInHorizontally(tween(280)) { if (forward) it / 2 else -it / 2 } + fadeIn(tween(260)))
                            .togetherWith(slideOutHorizontally(tween(240)) { if (forward) -it / 2 else it / 2 } + fadeOut(tween(180)))
                    },
                    label = "formContent",
                ) { mode ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        if (mode == AuthMode.LOGIN) {
                            // Log In Form
                            SmoothAuthInputField(
                                value = loginIdentifier,
                                onValueChange = {
                                    loginIdentifier = it
                                    errorMessage = null
                                },
                                placeholder = "Username or Email",
                                leadingIcon = Icons.Rounded.Person,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                ),
                            )

                            Spacer(Modifier.height(12.dp))

                            SmoothAuthInputField(
                                value = loginPassword,
                                onValueChange = {
                                    loginPassword = it
                                    errorMessage = null
                                },
                                placeholder = stringResource(R.string.auth_field_password),
                                leadingIcon = Icons.Rounded.Lock,
                                isPassword = true,
                                passwordVisible = loginPasswordVisible,
                                onTogglePassword = { loginPasswordVisible = !loginPasswordVisible },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            )

                            Spacer(Modifier.height(20.dp))

                            AnimatedPillButton(
                                label = stringResource(R.string.auth_btn_login),
                                onClick = {
                                    focusManager.clearFocus()
                                    val cleanLogin = loginIdentifier.trim()
                                    val cleanPass = loginPassword
                                    if (cleanLogin.isBlank() || cleanPass.isBlank()) {
                                        errorMessage = "Please enter both username/email and password"
                                        return@AnimatedPillButton
                                    }
                                    isLoading = true
                                    errorMessage = null
                                    coroutineScope.launch {
                                        val result = TuneslyCloudManager.login(cleanLogin, cleanPass)
                                        isLoading = false
                                        result.fold(
                                            onSuccess = { user ->
                                                onNameChosen(user.username)
                                                onFinish()
                                            },
                                            onFailure = { err ->
                                                errorMessage = err.message ?: "Login failed. Check your credentials."
                                            },
                                        )
                                    }
                                },
                                primary = true,
                                enabled = !isLoading && loginIdentifier.isNotBlank() && loginPassword.isNotBlank(),
                                loading = isLoading,
                            )

                            Spacer(Modifier.height(14.dp))

                            Text(
                                text = stringResource(R.string.auth_switch_to_signup),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        authMode = AuthMode.SIGNUP
                                        errorMessage = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        } else {
                            // Sign Up Form (Name, Username, Email, Password)
                            SmoothAuthInputField(
                                value = fullName,
                                onValueChange = {
                                    fullName = it
                                    errorMessage = null
                                },
                                placeholder = stringResource(R.string.auth_field_fullname) + " (e.g. Aarav Sharma)",
                                leadingIcon = Icons.Rounded.Person,
                                keyboardOptions = KeyboardOptions(
                                    capitalization = KeyboardCapitalization.Words,
                                    imeAction = ImeAction.Next,
                                ),
                            )

                            Spacer(Modifier.height(12.dp))

                            SmoothAuthInputField(
                                value = signupUsername,
                                onValueChange = {
                                    signupUsername = it.filterNot { c -> c.isWhitespace() }.lowercase()
                                    errorMessage = null
                                },
                                placeholder = stringResource(R.string.auth_field_username) + " (e.g. aarav)",
                                leadingIcon = Icons.Rounded.AlternateEmail,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Ascii,
                                    imeAction = ImeAction.Next,
                                ),
                            )

                            Spacer(Modifier.height(12.dp))

                            SmoothAuthInputField(
                                value = signupEmail,
                                onValueChange = {
                                    signupEmail = it.trim()
                                    errorMessage = null
                                },
                                placeholder = stringResource(R.string.auth_field_email) + " (e.g. aarav@gmail.com)",
                                leadingIcon = Icons.Rounded.Email,
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Email,
                                    imeAction = ImeAction.Next,
                                ),
                            )

                            Spacer(Modifier.height(12.dp))

                            SmoothAuthInputField(
                                value = signupPassword,
                                onValueChange = {
                                    signupPassword = it
                                    errorMessage = null
                                },
                                placeholder = stringResource(R.string.auth_field_password) + " (min 6 characters)",
                                leadingIcon = Icons.Rounded.Lock,
                                isPassword = true,
                                passwordVisible = signupPasswordVisible,
                                onTogglePassword = { signupPasswordVisible = !signupPasswordVisible },
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            )

                            Spacer(Modifier.height(20.dp))

                            AnimatedPillButton(
                                label = stringResource(R.string.auth_btn_signup),
                                onClick = {
                                    focusManager.clearFocus()
                                    val cleanName = fullName.trim()
                                    val cleanUser = signupUsername.trim()
                                    val cleanEmail = signupEmail.trim()
                                    val cleanPass = signupPassword

                                    if (cleanUser.length < 3) {
                                        errorMessage = "Username must be at least 3 characters"
                                        return@AnimatedPillButton
                                    }
                                    if (!cleanEmail.contains("@") || !cleanEmail.contains(".")) {
                                        errorMessage = "Please enter a valid email address"
                                        return@AnimatedPillButton
                                    }
                                    if (cleanPass.length < 6) {
                                        errorMessage = "Password must be at least 6 characters"
                                        return@AnimatedPillButton
                                    }

                                    isLoading = true
                                    errorMessage = null
                                    coroutineScope.launch {
                                        val result = TuneslyCloudManager.register(
                                            username = cleanUser,
                                            email = cleanEmail,
                                            password = cleanPass,
                                            displayName = cleanName.ifBlank { cleanUser },
                                        )
                                        isLoading = false
                                        result.fold(
                                            onSuccess = { user ->
                                                onNameChosen(cleanName.ifBlank { user.username })
                                                onFinish()
                                            },
                                            onFailure = { err ->
                                                errorMessage = err.message ?: "Registration failed. Try a different username."
                                            },
                                        )
                                    }
                                },
                                primary = true,
                                enabled = !isLoading && signupUsername.isNotBlank() && signupEmail.isNotBlank() && signupPassword.isNotBlank(),
                                loading = isLoading,
                            )

                            Spacer(Modifier.height(14.dp))

                            Text(
                                text = stringResource(R.string.auth_switch_to_login),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable {
                                        authMode = AuthMode.LOGIN
                                        errorMessage = null
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // OR Divider
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                ) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.16f)),
                    )
                    Text(
                        text = "OR",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.45f),
                        modifier = Modifier.padding(horizontal = 12.dp),
                    )
                    Box(
                        Modifier
                            .weight(1f)
                            .height(1.dp)
                            .background(Color.White.copy(alpha = 0.16f)),
                    )
                }

                Spacer(Modifier.height(18.dp))

                // Guest / Skip Option
                AnimatedPillButton(
                    label = stringResource(R.string.auth_continue_guest),
                    onClick = {
                        focusManager.clearFocus()
                        onNameChosen(initialName.ifBlank { "Guest" })
                        onFinish()
                    },
                    primary = false,
                )

                Spacer(Modifier.height(12.dp))

                // Optional Google Sign-In
                AnimatedPillButton(
                    label = stringResource(R.string.onboarding_sign_in_google),
                    onClick = {
                        focusManager.clearFocus()
                        onSignIn()
                    },
                    primary = false,
                    leading = {
                        Image(
                            painter = painterResource(Res.drawable.ic_google_g),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )

                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

/**
 * Animated input field with smooth glowing border on focus and icon accenting.
 */
@Composable
private fun SmoothAuthInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    leadingIcon: ImageVector,
    modifier: Modifier = Modifier,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onTogglePassword: (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    var isFocused by remember { mutableStateOf(false) }

    val borderColor by animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.65f) else Color.White.copy(alpha = 0.14f),
        animationSpec = tween(220),
        label = "inputBorder",
    )
    val bgColor by animateColorAsState(
        targetValue = if (isFocused) Color.White.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.12f),
        animationSpec = tween(220),
        label = "inputBg",
    )
    val iconTint by animateColorAsState(
        targetValue = if (isFocused) Color.White else Color.White.copy(alpha = 0.65f),
        animationSpec = tween(220),
        label = "iconTint",
    )

    val style = MaterialTheme.typography.bodyLarge.copy(
        fontSize = 16.sp,
        color = Color.White,
        fontWeight = FontWeight.Medium,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(54.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(bgColor)
            .border(1.2.dp, borderColor, RoundedCornerShape(18.dp))
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = leadingIcon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(20.dp),
        )

        Spacer(Modifier.width(12.dp))

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight(),
            contentAlignment = Alignment.CenterStart,
        ) {
            if (value.isEmpty()) {
                Text(
                    text = placeholder,
                    style = style,
                    color = Color.White.copy(alpha = 0.38f),
                    maxLines = 1,
                )
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                textStyle = style,
                cursorBrush = SolidColor(Color.White),
                visualTransformation = if (isPassword && !passwordVisible) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = keyboardOptions,
                keyboardActions = keyboardActions,
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { isFocused = it.isFocused },
            )
        }

        if (isPassword && onTogglePassword != null) {
            IconButton(
                onClick = onTogglePassword,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = "Toggle password visibility",
                    tint = iconTint,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

/**
 * Capsule pill action button with tactile spring animation.
 */
@Composable
private fun AnimatedPillButton(
    label: String,
    onClick: () -> Unit,
    primary: Boolean,
    enabled: Boolean = true,
    loading: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed && enabled && !loading) 0.96f else 1f,
        spring(stiffness = 800f, dampingRatio = 0.75f),
        label = "pillScale",
    )
    val alpha by animateFloatAsState(
        if (enabled && !loading) 1f else 0.5f,
        tween(180),
        label = "pillAlpha",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(54.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
            }
            .clip(CircleShape)
            .background(if (primary) Color.White else Color.White.copy(alpha = 0.16f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled && !loading,
                onClick = onClick,
            ),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (loading) {
            CircularProgressIndicator(
                color = if (primary) Color.Black else Color.White,
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.5.dp,
            )
        } else {
            if (leading != null) {
                leading()
                Spacer(Modifier.width(10.dp))
            }
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (primary) Color.Black else Color.White,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}
