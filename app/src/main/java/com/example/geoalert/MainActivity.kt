package com.example.geoalert

import android.Manifest
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.speech.RecognizerIntent
import android.speech.tts.TextToSpeech
import android.telephony.SmsManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.ActivityCompat
import androidx.core.content.edit
import com.google.android.gms.location.LocationServices
import kotlinx.coroutines.delay
import java.util.*

enum class Screen {
    LOGIN, SPLASH, PROFILE_SETUP, MAIN, PROFILE_EDIT
}

class MainActivity : ComponentActivity(), TextToSpeech.OnInitListener {

    private lateinit var tts: TextToSpeech
    private val prefs by lazy { getSharedPreferences("user_prefs", MODE_PRIVATE) }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}

    private val speechLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val text = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.get(0)?.lowercase()
                if (text != null) detectKeywords(text)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        tts = TextToSpeech(this, this)
        requestPermissions()

        setContent {
            var screen by remember { mutableStateOf(Screen.LOGIN) }

            when (screen) {
                Screen.LOGIN -> LoginScreen { screen = Screen.SPLASH }

                Screen.SPLASH -> {
                    LaunchedEffect(Unit) {
                        delay(2000)
                        screen =
                            if (prefs.getBoolean("profile_done", false))
                                Screen.MAIN else Screen.PROFILE_SETUP
                    }
                    SplashScreen()
                }

                Screen.PROFILE_SETUP -> ProfileScreen(false, prefs) { n, a, c ->
                    saveProfile(n, a, c)
                    screen = Screen.MAIN
                }

                Screen.MAIN -> MainScreen(
                    onMicClick = { startSpeechRecognition() },
                    onProfileClick = { screen = Screen.PROFILE_EDIT }
                )

                Screen.PROFILE_EDIT -> ProfileScreen(true, prefs) { n, a, c ->
                    saveProfile(n, a, c)
                    screen = Screen.MAIN
                }
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            tts.language = Locale.US
        }
    }

    private fun requestPermissions() {
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.SEND_SMS,
                Manifest.permission.CALL_PHONE
            )
        )
    }

    private fun saveProfile(name: String, address: String, contact: String) {
        prefs.edit {
            putString("name", name)
            putString("home_address", address)
            putString("emergency_contact", contact)
            putBoolean("profile_done", true)
        }
    }

    private fun startSpeechRecognition() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        intent.putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
        )
        intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
        speechLauncher.launch(intent)
    }

    private fun detectKeywords(text: String) {
        when {
            text.contains("lost") || text.contains("go home") -> {
                tts.speak("Helping you go home", TextToSpeech.QUEUE_FLUSH, null, null)
                sendLostSms()
                openDirectionsToHome()
            }

            text.contains("help") || text.contains("emergency") -> {
                tts.speak("Calling emergency contact", TextToSpeech.QUEUE_FLUSH, null, null)
                callEmergencyContact()
            }

            else -> {
                tts.speak("Please say help or lost", TextToSpeech.QUEUE_FLUSH, null, null)
            }
        }
    }

    private fun callEmergencyContact() {
        val number = prefs.getString("emergency_contact", null) ?: return
        startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$number")))
    }

    private fun openDirectionsToHome() {
        val address = prefs.getString("home_address", null) ?: return
        val uri =
            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(address)}")
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun sendLostSms() {
        val contact = prefs.getString("emergency_contact", null) ?: return
        val fused = LocationServices.getFusedLocationProviderClient(this)

        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) return

        fused.lastLocation.addOnSuccessListener { loc: Location? ->
            loc?.let {
                SmsManager.getDefault().sendTextMessage(
                    contact,
                    null,
                    "I am LOST: https://maps.google.com/?q=${it.latitude},${it.longitude}",
                    null,
                    null
                )
            }
        }
    }
}

/* ---------- UI ---------- */

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF4FACFE), Color(0xFF00F2FE))
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = true,
            enter = scaleIn() + fadeIn()
        ) {
            Text(
                text = "Mind Anchor",
                fontSize = 40.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
    }
}

@Composable
fun ProfileScreen(
    isEdit: Boolean,
    prefs: SharedPreferences,
    onSave: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var contact by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        name = prefs.getString("name", "") ?: ""
        address = prefs.getString("home_address", "") ?: ""
        contact = prefs.getString("emergency_contact", "") ?: ""
    }

    GradientBg {
        Card(shape = RoundedCornerShape(24.dp)) {
            Column(Modifier.padding(20.dp)) {
                Text(
                    if (isEdit) "Edit Profile" else "Setup Profile",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(Modifier.height(12.dp))
                OutlinedTextField(name, { name = it }, label = { Text("Name") })
                OutlinedTextField(address, { address = it }, label = { Text("Home Address") })
                OutlinedTextField(contact, { contact = it }, label = { Text("Emergency Contact") })

                Spacer(Modifier.height(16.dp))
                Button(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        if (name.isNotEmpty() && address.isNotEmpty() && contact.isNotEmpty()) {
                            onSave(name, address, contact)
                        }
                    }
                ) {
                    Text("Save Profile")
                }
            }
        }
    }
}

@Composable
fun MainScreen(onMicClick: () -> Unit, onProfileClick: () -> Unit) {
    val context = LocalContext.current

    GradientBg {

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .background(Color.White, CircleShape)
                    .clickable { onProfileClick() },
                contentAlignment = Alignment.Center
            ) {
                Text("👤")
            }
        }

        Spacer(Modifier.weight(1f))

        Column(horizontalAlignment = Alignment.CenterHorizontally) {

            Text(
                "Voice Help Assistant",
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onMicClick,
                shape = CircleShape,
                modifier = Modifier.size(140.dp)
            ) {
                Text("🎤", fontSize = 32.sp)
            }

            Spacer(Modifier.height(24.dp))

            // ✅ GEO ALERT BUTTON
            Button(
                onClick = {
                    context.startActivity(
                        Intent(context, GeoAlertActivity::class.java)
                    )
                },
                modifier = Modifier.fillMaxWidth(0.8f)
            ) {
                Text("📍 Open Geo Alert")
            }
        }

        Spacer(Modifier.weight(1f))
    }
}

@Composable
fun LoginScreen(onLoginSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }
    var isPasswordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF4FACFE), Color(0xFF00F2FE))
                )
            )
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Logo/Title
        Text(
            text = "🔐 Mind Anchor",
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Login Card
        Card(
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Login",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                // Username Field
                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it; errorMessage = "" },
                    label = { Text("Username") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    leadingIcon = { Text("👤") }
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Password Field
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it; errorMessage = "" },
                    label = { Text("Password") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    visualTransformation = if (isPasswordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    leadingIcon = { Text("🔑") },
                    trailingIcon = {
                        IconButton(onClick = { isPasswordVisible = !isPasswordVisible }) {
                            Text(if (isPasswordVisible) "👁" else "🙈")
                        }
                    }
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Error Message
                if (errorMessage.isNotEmpty()) {
                    Text(
                        text = errorMessage,
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Login Button
                Button(
                    onClick = {
                        when {
                            username.isEmpty() -> errorMessage = "Username cannot be empty"
                            password.isEmpty() -> errorMessage = "Password cannot be empty"
                            username == "Mind" && password == "Mind" -> {
                                errorMessage = ""
                                onLoginSuccess()
                            }
                            else -> errorMessage = "Invalid username or password"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Login", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Demo credentials hint
                Text(
                    text = "Demo: Mind / Mind",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
fun GradientBg(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF4FACFE), Color(0xFF00F2FE))
                )
            )
            .padding(24.dp),
        content = content
    )
}
