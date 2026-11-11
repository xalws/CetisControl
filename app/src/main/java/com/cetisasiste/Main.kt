package com.cetisasiste

import android.os.Bundle
import android.view.LayoutInflater
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.drawerlayout.widget.DrawerLayout
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import com.cetisasiste.data.Repo
import com.cetisasiste.data.Sesion
import com.cetisasiste.ui.BoletinFragment
import com.cetisasiste.ui.CredencialFragment
import com.cetisasiste.ui.HorarioFragment
import com.cetisasiste.ui.InicioFragment
import com.cetisasiste.ui.LoginFragment
import com.cetisasiste.ui.NotificacionesFragment
import com.cetisasiste.ui.PerfilFragment
import com.cetisasiste.ui.QrFragment

class Main : AppCompatActivity() {

    // Menu lateral
    private lateinit var drawer: DrawerLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        // contenedor
        setContentView(R.layout.activity_main)
        drawer = findViewById(R.id.drawer)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                when {
                    drawer.isDrawerOpen(androidx.core.view.GravityCompat.START) -> drawer.closeDrawers()
                    supportFragmentManager.backStackEntryCount > 0 -> supportFragmentManager.popBackStack()
                    else -> finish()
                }
            }
        })

        if (savedInstanceState == null) {
            if (Repo.auth.currentUser == null) mostrarLogin() else cargarPerfil()
        }
    }

    //  Suma a la pila
    fun ir(f: Fragment) {
        supportFragmentManager.commit { replace(R.id.contenedor, f); addToBackStack(null) }
    }

    // Limpia la pila
    fun irRaiz(f: Fragment) {
        supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        supportFragmentManager.commit { replace(R.id.contenedor, f) }
        drawer.closeDrawers()
    }

    fun abrirMenu() = drawer.openDrawer(androidx.core.view.GravityCompat.START)

    fun mostrarLogin() {
        drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_LOCKED_CLOSED)
        irRaiz(LoginFragment())
    }

    fun cargarPerfil() {
        Repo.cargarRol(ok = ::irAHomeSegunRol, error = ::fallarSesion)
    }

    private fun irAHomeSegunRol(rol: String) {
        when (rol) {
            Repo.Roles.ALUMNO -> Repo.cargarAlumno(
                ok = { alumno ->
                    Sesion.alumno = alumno
                    findViewById<TextView>(R.id.txt_nombre).text = alumno.nombre
                    findViewById<TextView>(R.id.txt_rol).text = alumno.rol
                    armarOpciones(rol)
                    drawer.setDrawerLockMode(DrawerLayout.LOCK_MODE_UNLOCKED)
                    irRaiz(InicioFragment())
                },
                error = ::fallarSesion
            )
            Repo.Roles.PADRE -> Toast.makeText(this, "Pantalla de padre aun no esta lista", Toast.LENGTH_LONG).show()
            Repo.Roles.PREFECTO -> Toast.makeText(this, "Pantalla de prefecto aun no esta lista", Toast.LENGTH_LONG).show()
            Repo.Roles.MAESTRO -> Toast.makeText(this, "Pantalla de maestro aun no esta lista", Toast.LENGTH_LONG).show()
        }
    }

    private fun fallarSesion(mensaje: String) {
        Toast.makeText(this, mensaje, Toast.LENGTH_LONG).show()
        Repo.auth.signOut()
        Sesion.alumno = null
        mostrarLogin()
    }

    //  Menu hamburgesa
    private fun armarOpciones(rol: String) {
        val lista = findViewById<LinearLayout>(R.id.lista_opciones)
        lista.removeAllViews()
        val opciones =
            when (rol) {
                Repo.Roles.ALUMNO -> listOf(
                    Triple("Inicio", R.drawable.ic_home) { irRaiz(InicioFragment()) },
                    Triple("Horario", R.drawable.ic_clock) { irRaiz(HorarioFragment()) },
                    Triple("Boletín", R.drawable.ic_announcements) { irRaiz(BoletinFragment()) },
                    Triple("Código QR", R.drawable.ic_qr) { irRaiz(QrFragment()) },
                    Triple("Credencial digital", R.drawable.ic_id_card) { irRaiz(CredencialFragment()) },
                    Triple("Notificaciones", R.drawable.ic_bell) { irRaiz(NotificacionesFragment()) }
                )

            else -> emptyList()
        }
        opciones.forEach { (texto, icono, accion) ->
            val item = LayoutInflater.from(this).inflate(R.layout.comun_item_menu, lista, false)
            item.findViewById<TextView>(R.id.txt_texto).text = texto
            item.findViewById<ImageView>(R.id.img_icono).setImageResource(icono)
            item.setOnClickListener { accion() }
            lista.addView(item)
        }
        findViewById<LinearLayout>(R.id.opcion_cerrar_sesion).setOnClickListener {
            drawer.closeDrawers(); confirmarCerrarSesion()
        }
    }


    fun confirmarCerrarSesion() {
        val v = layoutInflater.inflate(R.layout.alumno_dialogo_cerrar_sesion, null)
        val dialogo = AlertDialog.Builder(this).setView(v).create()
        dialogo.window?.setBackgroundDrawableResource(android.R.color.transparent)
        v.findViewById<Button>(R.id.btn_cancelar).setOnClickListener { dialogo.dismiss() }
        v.findViewById<Button>(R.id.btn_confirmar).setOnClickListener {
            dialogo.dismiss()
            Repo.auth.signOut()
            Sesion.alumno = null
            mostrarLogin()
        }
        dialogo.show()
    }
}
