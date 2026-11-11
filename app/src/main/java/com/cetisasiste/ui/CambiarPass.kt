package com.cetisasiste.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.cetisasiste.R
import com.cetisasiste.data.Repo
import com.google.firebase.auth.EmailAuthProvider


class CambiarContrasenaFragment : Fragment(R.layout.comun_cambiar_contrasena) {
    override fun onViewCreated(v: View, s: Bundle?) {
        conectarBarra(v)
        val actual = v.findViewById<EditText>(R.id.edit_actual)
        val nueva = v.findViewById<EditText>(R.id.edit_nueva)
        val repetir = v.findViewById<EditText>(R.id.edit_repetir)
        val mensaje = v.findViewById<TextView>(R.id.txt_mensaje)
        val guardar = v.findViewById<Button>(R.id.btn_guardar)

        fun error(t: String) { mensaje.text = t; mensaje.visibility = View.VISIBLE }

        guardar.setOnClickListener {
            val user = Repo.auth.currentUser ?: return@setOnClickListener
            when {
                actual.text.isBlank() || nueva.text.isBlank() -> error("Completa todos los campos")
                nueva.text.length < 6 -> error("La nueva contraseña debe tener al menos 6 caracteres")
                nueva.text.toString() != repetir.text.toString() -> error("Las contraseñas no coinciden")
                else -> {
                    guardar.isEnabled = false
                    val cred = EmailAuthProvider.getCredential(user.email.orEmpty(), actual.text.toString())
                    user.reauthenticate(cred)
                        .addOnSuccessListener {
                            user.updatePassword(nueva.text.toString())
                                .addOnSuccessListener {
                                    if (isAdded) parentFragmentManager.popBackStack()
                                }
                                .addOnFailureListener { guardar.isEnabled = true; error("No se pudo guardar") }
                        }
                        .addOnFailureListener { guardar.isEnabled = true; error("La contraseña actual es incorrecta") }
                }
            }
        }
    }
}
