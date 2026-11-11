package com.cetisasiste.ui

import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.cetisasiste.R
import com.cetisasiste.data.Repo

class LoginFragment : Fragment(R.layout.alumno_iniciar_sesion) {
    override fun onViewCreated(v: View, s: Bundle?) {
        val usuario = v.findViewById<EditText>(R.id.edit_no_control)
        val clave = v.findViewById<EditText>(R.id.edit_contrasena)
        val boton = v.findViewById<Button>(R.id.btn_iniciar_sesion)
        var visible = false

        v.findViewById<ImageButton>(R.id.btn_mostrar_contrasena).setOnClickListener {
            visible = !visible
            clave.inputType = InputType.TYPE_CLASS_TEXT or
                if (visible) InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
                else InputType.TYPE_TEXT_VARIATION_PASSWORD
            clave.setSelection(clave.text.length)
        }

        fun entrar() {
            if (usuario.text.isBlank() || clave.text.isBlank()) {
                Toast.makeText(context, "Escribe tu usuario y contraseña", Toast.LENGTH_SHORT).show()
                return
            }
            boton.isEnabled = false
            Repo.auth.signInWithEmailAndPassword(
                Repo.correoDe(usuario.text.toString()), clave.text.toString()
            ).addOnSuccessListener { main.cargarPerfil() }
                .addOnFailureListener {
                    boton.isEnabled = true
                    Toast.makeText(context, "Usuario o contraseña incorrectos", Toast.LENGTH_LONG).show()
                }
        }
        boton.setOnClickListener {
            entrar()
        }
        clave.setOnEditorActionListener { _, _, _ -> entrar(); true }

        v.findViewById<TextView>(R.id.txt_olvidaste_contrasena).setOnClickListener {
            if (usuario.text.isBlank()) {
                Toast.makeText(context, "Escribe primero tu usuario", Toast.LENGTH_SHORT).show()
            } else {
                Repo.auth.sendPasswordResetEmail(Repo.correoDe(usuario.text.toString()))
                Toast.makeText(context, "Te enviamos un correo", Toast.LENGTH_LONG).show()
            }
        }
    }
}
