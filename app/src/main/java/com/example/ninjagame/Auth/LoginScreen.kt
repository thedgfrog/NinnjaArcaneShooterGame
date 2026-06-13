package com.example.ninjagame.Auth

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ninjagame.data.FirestoreRepository
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onNavigateRegister: () -> Unit,
    onNavigateForgot: () -> Unit
) {
    val context = LocalContext.current
    val auth = FirebaseAuth.getInstance()
    val repository = remember { FirestoreRepository() }
    val scope = rememberCoroutineScope()

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var msg by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var showResendVerify by remember { mutableStateOf(false) }

    val googleSignInClient = remember {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("714915977702-8j80vglknpifvtddsmlcljhc278d3aqm.apps.googleusercontent.com")
            .requestEmail()
            .build()
        GoogleSignIn.getClient(context, gso)
    }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.result
            val idToken = account.idToken
            val credential = GoogleAuthProvider.getCredential(idToken, null)
            
            isLoading = true
            auth.signInWithCredential(credential).addOnCompleteListener { task2 ->
                if (task2.isSuccessful) {
                    scope.launch {
                        repository.syncGoogleSignIn() // Đồng bộ profile sau khi đăng nhập Google
                        onLoginSuccess()
                    }
                } else {
                    msg = task2.exception?.message ?: "Google login failed"
                    isLoading = false
                }
            }
        } catch (e: Exception) {
            msg = e.message ?: "Google sign in error"
            isLoading = false
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = Color(0xFF0F0F0F)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "NINJA GAME",
                fontSize = 32.sp,
                fontWeight = FontWeight.Light,
                color = Color.White,
                letterSpacing = 4.sp
            )
            
            Text(
                "AUTHENTICATION",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                letterSpacing = 2.sp
            )

            if (isLoading) {
                Spacer(Modifier.height(24.dp))
                CircularProgressIndicator(color = Color.White)
            }

            Spacer(Modifier.height(48.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("EMAIL", fontSize = 12.sp) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray
                )
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("PASSWORD", fontSize = 12.sp) },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    val image = if (passwordVisible) Icons.Filled.Visibility else Icons.Filled.VisibilityOff
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, contentDescription = null, tint = Color.Gray)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = Color.White,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.1f),
                    focusedLabelColor = Color.White,
                    unfocusedLabelColor = Color.Gray
                )
            )

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = {
                    if (email.isBlank() || password.isBlank()) {
                        msg = "Fill all fields"
                        return@Button
                    }
                    isLoading = true
                    auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                val user = auth.currentUser
                                if (user != null && !user.isEmailVerified) {
                                    msg = "Please verify your email via Gmail link."
                                    showResendVerify = true
                                    isLoading = false
                                } else {
                                    onLoginSuccess()
                                }
                            } else {
                                msg = task.exception?.message ?: "Login failed"
                                isLoading = false
                            }
                        }
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("LOGIN", fontWeight = FontWeight.Bold)
            }

            if (showResendVerify) {
                TextButton(onClick = {
                    auth.currentUser?.sendEmailVerification()?.addOnCompleteListener {
                        msg = if (it.isSuccessful) "Verification email resent! Check your inbox." else "Failed to resend."
                    }
                }) {
                    Text("RESEND VERIFICATION EMAIL", color = Color.White, fontSize = 11.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            OutlinedButton(
                onClick = {
                    val signInIntent = googleSignInClient.signInIntent
                    launcher.launch(signInIntent)
                },
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
            ) {
                Text("SIGN IN WITH GMAIL", fontSize = 12.sp)
            }

            Spacer(Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton(onClick = onNavigateForgot) {
                    Text("FORGOT PASSWORD?", color = Color.Gray, fontSize = 11.sp)
                }
                TextButton(onClick = onNavigateRegister) {
                    Text("CREATE ACCOUNT", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            if (msg.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                Text(
                    msg, 
                    color = if (msg.contains("sent") || msg.contains("resent")) Color.Green else Color.Red, 
                    fontSize = 12.sp, 
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
