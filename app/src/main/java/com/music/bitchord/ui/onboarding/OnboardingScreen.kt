package com.music.bitchord.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AlternateEmail
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Email
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.music.bitchord.R
import com.music.bitchord.data.settings.AppSettings
import com.music.bitchord.data.taste.UserTasteProfile
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

private enum class OnboardingStep {
    AUTH,
    ARTISTS,
    LANGUAGES,
}

private enum class AuthMode {
    LOGIN,
    SIGNUP,
}

private data class OnboardingArtist(
    val name: String,
    val genre: String,
    val colorStart: Long,
    val colorEnd: Long,
)

private val POPULAR_ONBOARDING_ARTISTS = listOf(
    OnboardingArtist("Arijit Singh", "Bollywood", 0xFFE53935, 0xFFFF7043),
    OnboardingArtist("Sidhu Moose Wala", "Punjabi", 0xFF1E88E5, 0xFF42A5F5),
    OnboardingArtist("Karan Aujla", "Punjabi", 0xFF8E24AA, 0xFFBA68C8),
    OnboardingArtist("Diljit Dosanjh", "Punjabi", 0xFFFF8F00, 0xFFFFCA28),
    OnboardingArtist("Shreya Ghoshal", "Melody", 0xFFD81B60, 0xFFF06292),
    OnboardingArtist("AP Dhillon", "Punjabi Wave", 0xFF00897B, 0xFF4DB6AC),
    OnboardingArtist("Badshah", "Desi Hip Hop", 0xFF3949AB, 0xFF5C6BC0),
    OnboardingArtist("Yo Yo Honey Singh", "Hip Hop", 0xFFF4511E, 0xFFFF8A65),
    OnboardingArtist("Atif Aslam", "Sufi Pop", 0xFF00ACC1, 0xFF26C6DA),
    OnboardingArtist("Anuv Jain", "Indie Acoustic", 0xFF5E35B1, 0xFF7E57C2),
    OnboardingArtist("King", "Pop / Rap", 0xFFC2185B, 0xFFE91E63),
    OnboardingArtist("Divine", "Gully Rap", 0xFF43A047, 0xFF66BB6A),
    OnboardingArtist("Seedhe Maut", "DHH", 0xFFE53935, 0xFFEF5350),
    OnboardingArtist("Neha Kakkar", "Bollywood", 0xFFD81B60, 0xFFFF4081),
    OnboardingArtist("Sonu Nigam", "Classic Pop", 0xFF1E88E5, 0xFF64B5F6),
    OnboardingArtist("Jubin Nautiyal", "Romantic", 0xFF00897B, 0xFF80CBC4),
    OnboardingArtist("Vishal Mishra", "Soulful", 0xFFFB8C00, 0xFFFFB74D),
    OnboardingArtist("Mohit Chauhan", "Indie Pop", 0xFF6D4C41, 0xFF8D6E63),
    OnboardingArtist("Prateek Kuhad", "Acoustic", 0xFF546E7A, 0xFF78909C),
    OnboardingArtist("B Praak", "Punjabi Soul", 0xFFC62828, 0xFFE57373),
    OnboardingArtist("Talwiinder", "Punjabi Alt", 0xFF6A1B9A, 0xFFAB47BC),
    OnboardingArtist("Shubh", "Punjabi", 0xFF2E7D32, 0xFF66BB6A),
    OnboardingArtist("Taylor Swift", "Global Pop", 0xFFAD1457, 0xFFF48FB1),
    OnboardingArtist("The Weeknd", "R&B / Synth", 0xFFBF360C, 0xFFFF7043),
    OnboardingArtist("Drake", "Hip Hop", 0xFF283593, 0xFF5C6BC0),
    OnboardingArtist("Justin Bieber", "Pop", 0xFF00695C, 0xFF4DB6AC),
    OnboardingArtist("Ed Sheeran", "Acoustic Pop", 0xFFE65100, 0xFFFFB74D),
    OnboardingArtist("Billie Eilish", "Alt Pop", 0xFF1B5E20, 0xFF81C784),
    OnboardingArtist("Post Malone", "Hip Hop / Rock", 0xFF4A148C, 0xFF9C27B0),
    OnboardingArtist("Bruno Mars", "Funk Pop", 0xFFC2185B, 0xFFFF80AB),
    OnboardingArtist("Anirudh", "South / Rock", 0xFFE53935, 0xFFFF5252),
    OnboardingArtist("A.R. Rahman", "Legend", 0xFF0D47A1, 0xFF2196F3),
    OnboardingArtist("Sid Sriram", "South Melody", 0xFF00695C, 0xFF26A69A),
    OnboardingArtist("Masoom Sharma", "Haryanvi", 0xFFEF6C00, 0xFFFFA726),
    OnboardingArtist("Pawan Singh", "Bhojpuri", 0xFFC2185B, 0xFFFF4081),
    OnboardingArtist("Khesari Lal", "Bhojpuri", 0xFF2E7D32, 0xFF4CAF50),
)

private data class OnboardingLanguage(
    val id: String,
    val name: String,
    val nativeScript: String,
)

private val ONBOARDING_LANGUAGES = listOf(
    OnboardingLanguage("hindi", "Hindi", "हिंदी"),
    OnboardingLanguage("punjabi", "Punjabi", "ਪੰਜਾਬੀ"),
    OnboardingLanguage("english", "English", "English"),
    OnboardingLanguage("bhojpuri", "Bhojpuri", "भोजपुरी"),
    OnboardingLanguage("haryanvi", "Haryanvi", "हरियाणवी"),
    OnboardingLanguage("bengali", "Bengali", "বাংলা"),
    OnboardingLanguage("telugu", "Telugu", "తెలుగు"),
    OnboardingLanguage("tamil", "Tamil", "தமிழ்"),
    OnboardingLanguage("malayalam", "Malayalam", "മലയാളം"),
    OnboardingLanguage("marathi", "Marathi", "मराठी"),
    OnboardingLanguage("gujarati", "Gujarati", "ગુજરાતી"),
    OnboardingLanguage("urdu", "Urdu", "اردو"),
    OnboardingLanguage("kannada", "Kannada", "ಕನ್ನಡ"),
    OnboardingLanguage("rajasthani", "Rajasthani", "राजस्थानी"),
)

/**
 * Ultra-smooth, animated first-launch experience for Tunesly.
 *
 * Sequence:
 * 1. Authentication (Login / Register / Guest / Google)
 * 2. Artist Following (Interactive search, avatar badges, multi-selection)
 * 3. Music Languages (Bilingual chips, multi-selection for personalized feed)
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
    var currentStep by rememberSaveable { mutableStateOf(OnboardingStep.AUTH) }
    var selectedArtists by rememberSaveable { mutableStateOf(setOf<String>()) }
    var selectedLanguages by rememberSaveable { mutableStateOf(setOf("Hindi", "Punjabi", "English")) }

    val focusManager = LocalFocusManager.current

    // Back handling: Step 3 -> Step 2 -> Step 1
    BackHandler(enabled = currentStep != OnboardingStep.AUTH) {
        if (currentStep == OnboardingStep.LANGUAGES) {
            currentStep = OnboardingStep.ARTISTS
        } else if (currentStep == OnboardingStep.ARTISTS) {
            currentStep = OnboardingStep.AUTH
        }
    }

    // Auto-advance if Google sign-in completes on step 1
    LaunchedEffect(signedIn) {
        if (signedIn && currentStep == OnboardingStep.AUTH) {
            currentStep = OnboardingStep.ARTISTS
        }
    }

    // Dynamic mesh animation across the entire onboarding flow
    val palette = cyclingMeshPalette(WelcomeMeshPalettes)

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) { detectTapGestures { focusManager.clearFocus() } },
    ) {
        MeshGradientBackground(
            palette = palette,
            trackKey = currentStep.ordinal,
            continuous = true,
            driftMillis = 7_000,
            colorFadeMillis = MESH_CYCLE_MILLIS,
        )

        // Gradient overlay for high legibility
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Black.copy(alpha = 0.35f),
                        0.40f to Color.Black.copy(alpha = 0.65f),
                        1f to Color.Black.copy(alpha = 0.92f),
                    ),
                ),
        )

        // Animated step transition
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val forward = targetState.ordinal > initialState.ordinal
                (slideInHorizontally(tween(300)) { if (forward) it else -it } + fadeIn(tween(260)))
                    .togetherWith(slideOutHorizontally(tween(260)) { if (forward) -it / 2 else it / 2 } + fadeOut(tween(180)))
            },
            label = "onboardingStepAnimation",
            modifier = Modifier.fillMaxSize(),
        ) { step ->
            when (step) {
                OnboardingStep.AUTH -> {
                    AuthStepView(
                        initialName = initialName,
                        onNameChosen = onNameChosen,
                        onSignIn = onSignIn,
                        onProceedToArtists = { currentStep = OnboardingStep.ARTISTS },
                    )
                }
                OnboardingStep.ARTISTS -> {
                    ArtistSelectionStepView(
                        selectedArtists = selectedArtists,
                        onToggleArtist = { name ->
                            selectedArtists = if (selectedArtists.contains(name)) {
                                selectedArtists - name
                            } else {
                                selectedArtists + name
                            }
                        },
                        onContinue = {
                            AppSettings.setFollowedArtists(selectedArtists)
                            UserTasteProfile.setFollowedArtists(selectedArtists)
                            currentStep = OnboardingStep.LANGUAGES
                        },
                        onSkip = {
                            currentStep = OnboardingStep.LANGUAGES
                        },
                    )
                }
                OnboardingStep.LANGUAGES -> {
                    LanguageSelectionStepView(
                        selectedLanguages = selectedLanguages,
                        onToggleLanguage = { lang ->
                            selectedLanguages = if (selectedLanguages.contains(lang)) {
                                selectedLanguages - lang
                            } else {
                                selectedLanguages + lang
                            }
                        },
                        onBack = { currentStep = OnboardingStep.ARTISTS },
                        onDone = {
                            val finalLangs = selectedLanguages.ifEmpty { setOf("Hindi", "Punjabi", "English") }
                            AppSettings.setPreferredMusicLanguages(finalLangs)
                            AppSettings.setFollowedArtists(selectedArtists)
                            UserTasteProfile.setFollowedArtists(selectedArtists)
                            onFinish()
                        },
                    )
                }
            }
        }
    }
}

/**
 * Step 1: Authentication View (Login / Sign Up / Guest / Google).
 */
@Composable
private fun AuthStepView(
    initialName: String,
    onNameChosen: (String) -> Unit,
    onSignIn: () -> Unit,
    onProceedToArtists: () -> Unit,
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

    // Brand logo pulse
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
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (mode == AuthMode.LOGIN) {
                            "Sign in to sync your library and stream anywhere"
                        } else {
                            "Join Tunesly to discover, share, and chat with friends"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.65f),
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Auth Tab Switcher
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .padding(4.dp),
            ) {
                val indicatorAlignment = if (authMode == AuthMode.LOGIN) Alignment.CenterStart else Alignment.CenterEnd
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .fillMaxHeight()
                        .align(indicatorAlignment)
                        .clip(CircleShape)
                        .background(Color.White),
                )

                Row(modifier = Modifier.fillMaxSize()) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(CircleShape)
                            .clickable {
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
                            .clickable {
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

            // Error Banner
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

            // Animated Form Content
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
                                            onProceedToArtists()
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
                        // Sign Up Form
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
                                            onProceedToArtists()
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
                    onProceedToArtists()
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

/**
 * Step 2: Follow Artists Screen with search, multi-selection circular cards, and spring check indicators.
 */
@Composable
private fun ArtistSelectionStepView(
    selectedArtists: Set<String>,
    onToggleArtist: (String) -> Unit,
    onContinue: () -> Unit,
    onSkip: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val focusManager = LocalFocusManager.current

    val filteredArtists = remember(searchQuery) {
        if (searchQuery.isBlank()) {
            POPULAR_ONBOARDING_ARTISTS
        } else {
            val q = searchQuery.trim().lowercase()
            POPULAR_ONBOARDING_ARTISTS.filter {
                it.name.lowercase().contains(q) || it.genre.lowercase().contains(q)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(12.dp))

            // Progress Indicator (Step 1 of 2)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 40.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                )
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.25f)),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = "Follow Your Favorite Artists",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp,
                    color = Color.White,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Pick 1 or more artists to tune your mixes and recommendations",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(16.dp))

            // Search Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.12f))
                    .border(1.dp, Color.White.copy(alpha = 0.20f), CircleShape)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.60f),
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(10.dp))
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search artists...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.40f),
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = Color.White,
                            fontWeight = FontWeight.Medium,
                        ),
                        cursorBrush = SolidColor(Color.White),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    IconButton(
                        onClick = { searchQuery = "" },
                        modifier = Modifier.size(28.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = Color.White.copy(alpha = 0.70f),
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // Artist Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(filteredArtists, key = { it.name }) { artist ->
                    val isSelected = selectedArtists.contains(artist.name)
                    ArtistGridItem(
                        artist = artist,
                        isSelected = isSelected,
                        onClick = { onToggleArtist(artist.name) },
                    )
                }
            }
        }

        // Bottom Action Bar with glass gradient
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to Color.Black.copy(alpha = 0.85f),
                        1f to Color.Black,
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedPillButton(
                    label = if (selectedArtists.isNotEmpty()) "Continue (${selectedArtists.size})" else "Continue",
                    onClick = onContinue,
                    primary = true,
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Skip for now",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onSkip)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * Individual Artist Circular Card in the Grid.
 */
@Composable
private fun ArtistGridItem(
    artist: OnboardingArtist,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1.0f,
        animationSpec = spring(stiffness = 600f, dampingRatio = 0.7f),
        label = "artistScale",
    )

    val initials = remember(artist.name) {
        artist.name.split(" ")
            .mapNotNull { it.firstOrNull()?.toString() }
            .take(2)
            .joinToString("")
            .uppercase()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier.size(76.dp),
            contentAlignment = Alignment.Center,
        ) {
            // Circular Avatar with vibrant gradient
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(artist.colorStart), Color(artist.colorEnd)),
                        ),
                    )
                    .border(
                        width = if (isSelected) 2.5.dp else 1.dp,
                        color = if (isSelected) Color.White else Color.White.copy(alpha = 0.15f),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White,
                    ),
                )
            }

            // Checkmark Badge
            if (isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 2.dp, bottom = 2.dp)
                        .size(24.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .border(1.5.dp, Color.Black, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selected",
                        tint = Color.Black,
                        modifier = Modifier.size(15.dp),
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Text(
            text = artist.name,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                color = if (isSelected) Color.White else Color.White.copy(alpha = 0.85f),
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(
            text = artist.genre,
            style = MaterialTheme.typography.labelSmall.copy(
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.50f),
            ),
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * Step 3: Music Language Preference Screen with bilingual chips and multi-selection.
 */
@Composable
private fun LanguageSelectionStepView(
    selectedLanguages: Set<String>,
    onToggleLanguage: (String) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(8.dp))

            // Navigation Row: Back button + Progress (Step 2 of 2)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = Color.White,
                    )
                }

                Spacer(Modifier.width(12.dp))

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(end = 36.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(4.dp)
                            .clip(CircleShape)
                            .background(Color.White),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            Text(
                text = "Which languages do you listen to?",
                style = MaterialTheme.typography.headlineMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 23.sp,
                    color = Color.White,
                ),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(4.dp))

            Text(
                text = "Select one or more languages for your personalized music feed",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.65f),
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(18.dp))

            // Language Cards Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(bottom = 90.dp, top = 4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(ONBOARDING_LANGUAGES, key = { it.id }) { lang ->
                    val isSelected = selectedLanguages.contains(lang.name)
                    LanguageGridCard(
                        language = lang,
                        isSelected = isSelected,
                        onClick = { onToggleLanguage(lang.name) },
                    )
                }
            }
        }

        // Bottom Action Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.4f to Color.Black.copy(alpha = 0.85f),
                        1f to Color.Black,
                    ),
                )
                .padding(horizontal = 24.dp, vertical = 18.dp),
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AnimatedPillButton(
                    label = "Start Listening",
                    onClick = onDone,
                    primary = true,
                    leading = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(18.dp),
                        )
                    },
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = "Skip and use defaults",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White.copy(alpha = 0.65f),
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = onDone)
                        .padding(horizontal = 14.dp, vertical = 4.dp),
                )
            }
        }
    }
}

/**
 * Modern Card for Language Selection with check indicator.
 */
@Composable
private fun LanguageGridCard(
    language: OnboardingLanguage,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (isSelected) Color.White.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.08f),
        animationSpec = tween(180),
        label = "langBg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) Color.White else Color.White.copy(alpha = 0.14f),
        animationSpec = tween(180),
        label = "langBorder",
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(if (isSelected) 1.5.dp else 1.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = language.name,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 15.sp,
                    color = Color.White,
                ),
                maxLines = 1,
            )
            Text(
                text = language.nativeScript,
                style = MaterialTheme.typography.bodySmall.copy(
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = if (isSelected) 0.85f else 0.55f),
                ),
                maxLines = 1,
            )
        }

        if (isSelected) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clip(CircleShape)
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "Selected",
                    tint = Color.Black,
                    modifier = Modifier.size(14.dp),
                )
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
