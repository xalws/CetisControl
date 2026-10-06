package com.cetiscontrol

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.FirebaseException
import android.view.View
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        auth = FirebaseAuth.getInstance()

        val inputEmail = findViewById<EditText>(R.id.inputEmail)
        val inputPassword = findViewById<EditText>(R.id.inputPassword)

        findViewById<Button>(R.id.btnLogin).setOnClickListener {
            val email = inputEmail.text.toString().trim()
            val pass = inputPassword.text.toString()
            if (email.isEmpty() || pass.isEmpty()) {
                toast("Completa los campos"); return@setOnClickListener
            }
            auth.signInWithEmailAndPassword(email, pass)
                .addOnSuccessListener { toast("Bienvenido ${it.user?.email}") }
                .addOnFailureListener { toast("Error: ${it.message}") }
        }

        findViewById<Button>(R.id.btnRegister).setOnClickListener {
            val email = inputEmail.text.toString().trim()
            val pass = inputPassword.text.toString()
            if (email.isEmpty() || pass.length < 6) {
                toast("Correo válido y contraseña de 6+ caracteres"); return@setOnClickListener
            }
            auth.createUserWithEmailAndPassword(email, pass)
                .addOnSuccessListener { toast("Cuenta creada") }
                .addOnFailureListener { toast("Error: ${it.message}") }
        }
    }

    override fun onStart() {
        super.onStart()
        // Si ya hay sesión iniciada ir a pantalla principal
        if (auth.currentUser != null) toast("Sesión activa")
    }

    private fun toast(msg: String) =
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}