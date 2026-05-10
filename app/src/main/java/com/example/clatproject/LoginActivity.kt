package com.example.clatproject

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.firestore.FirebaseFirestore

class LoginActivity : AppCompatActivity() {

    private var isSignUpMode = false
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    private lateinit var tilEmail: TextInputLayout
    private lateinit var tilUsername: TextInputLayout
    private lateinit var tilPassword: TextInputLayout
    private lateinit var btnLogin: Button
    private lateinit var tvSwitchAuth: TextView
    private lateinit var tvHeader: TextView
    private lateinit var tvSubHeader: TextView

    private lateinit var etEmail: TextInputEditText
    private lateinit var etUsername: TextInputEditText
    private lateinit var etPassword: TextInputEditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_login)

        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()

        tilEmail = findViewById(R.id.tilEmail)
        tilUsername = findViewById(R.id.tilUsername)
        tilPassword = findViewById(R.id.tilPassword)
        
        findViewById<TextInputLayout>(R.id.tilOtp).visibility = View.GONE

        etEmail = findViewById(R.id.etEmail)
        etUsername = findViewById(R.id.etUsername)
        etPassword = findViewById(R.id.etPassword)

        btnLogin = findViewById(R.id.btnLogin)
        tvSwitchAuth = findViewById(R.id.tvSwitchAuth)
        tvHeader = findViewById(R.id.tvLoginHeader)
        tvSubHeader = findViewById(R.id.tvLoginSubHeader)

        updateUI()

        tvSwitchAuth.setOnClickListener {
            isSignUpMode = !isSignUpMode
            updateUI()
        }

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString().trim()
            val username = etUsername.text.toString().trim()

            if (isSignUpMode) {
                if (email.isEmpty() || password.isEmpty() || username.isEmpty()) {
                    Toast.makeText(this, "Please fill all fields", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                val isCreator = email.equals("jithendarsangeetha@gmail.com", ignoreCase = true)
                if (!email.endsWith("@vit.ac.in") && !isCreator) {
                    Toast.makeText(this, "Use your @vit.ac.in email", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                btnLogin.text = "Creating Account..."
                btnLogin.isEnabled = false

                auth.createUserWithEmailAndPassword(email, password)
                    .addOnCompleteListener { task ->
                        if (task.isSuccessful) {
                            auth.currentUser?.sendEmailVerification()?.addOnCompleteListener { verifyTask ->
                                if (verifyTask.isSuccessful) {
                                    val lowerEmail = email.lowercase()
                                    val role = when {
                                        lowerEmail.contains("hod") || 
                                        lowerEmail.contains("dean") || 
                                        isCreator -> "admin"
                                        else -> "faculty"
                                    }

                                    val userData = mapOf(
                                        "username" to username,
                                        "email" to email,
                                        "role" to role
                                    )
                                    db.collection("users").document(username).set(userData)
                                    Toast.makeText(this, "Verification link sent! Check your inbox.", Toast.LENGTH_LONG).show()
                                    isSignUpMode = false
                                    updateUI()
                                }
                            }
                        } else {
                            if (task.exception is FirebaseAuthUserCollisionException) {
                                Toast.makeText(this, "This email is already registered. Please Login.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(this, "Error: ${task.exception?.message}", Toast.LENGTH_LONG).show()
                            }
                        }
                        btnLogin.isEnabled = true
                        btnLogin.text = if (isSignUpMode) "Sign Up" else "Login"
                    }
            } else {
                if (username.isEmpty() || password.isEmpty()) {
                    Toast.makeText(this, "Enter username and password", Toast.LENGTH_SHORT).show()
                    return@setOnClickListener
                }

                // 👑 MASTER CREATOR BYPASS
                if (username.lowercase() == "jithendar" && password == "Jithuu") {
                    startActivity(Intent(this, MainActivity::class.java))
                    finish()
                    return@setOnClickListener
                }

                btnLogin.text = "Logging in..."
                btnLogin.isEnabled = false

                db.collection("users").document(username).get()
                    .addOnSuccessListener { doc ->
                        if (doc.exists()) {
                            val userEmail = doc.getString("email") ?: ""
                            auth.signInWithEmailAndPassword(userEmail, password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val user = auth.currentUser
                                        if (user != null && (user.isEmailVerified || userEmail == "jithendarsangeetha@gmail.com")) {
                                            startActivity(Intent(this, MainActivity::class.java))
                                            finish()
                                        } else {
                                            Toast.makeText(this, "Verify your email first. Check your inbox.", Toast.LENGTH_LONG).show()
                                            auth.signOut()
                                        }
                                    } else {
                                        Toast.makeText(this, "Incorrect Password", Toast.LENGTH_SHORT).show()
                                    }
                                    btnLogin.isEnabled = true
                                    btnLogin.text = "Login"
                                }
                        } else {
                            btnLogin.isEnabled = true
                            btnLogin.text = "Login"
                            Toast.makeText(this, "Username not found", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .addOnFailureListener {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Login"
                        Toast.makeText(this, "Connection Error", Toast.LENGTH_SHORT).show()
                    }
            }
        }
    }

    private fun updateUI() {
        if (isSignUpMode) {
            tvHeader.text = "Create Account"
            tvSubHeader.text = "Join VIT Scheduler"
            tilEmail.visibility = View.VISIBLE
            tilUsername.visibility = View.VISIBLE
            btnLogin.text = "Sign Up"
            tvSwitchAuth.text = "Already have an account? Login"
        } else {
            tvHeader.text = "Welcome Back"
            tvSubHeader.text = "Log in to continue"
            tilEmail.visibility = View.GONE 
            tilUsername.visibility = View.VISIBLE
            btnLogin.text = "Login"
            tvSwitchAuth.text = "Don't have an account? Sign Up"
        }
    }
}