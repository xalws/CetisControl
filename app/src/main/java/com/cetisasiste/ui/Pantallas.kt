package com.cetisasiste.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.cetisasiste.R
import com.cetisasiste.data.Notificacion
import com.cetisasiste.data.Repo
import com.cetisasiste.data.Sesion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class InicioFragment : Fragment(R.layout.alumno_inicio) {
    override fun onViewCreated(v: View, s: Bundle?) {
        conectarBarra(v)
        val a = Sesion.alumno ?: return
        v.findViewById<TextView>(R.id.txt_nombre).text = a.nombre
        v.findViewById<TextView>(R.id.txt_rol).text = a.rol
        v.findViewById<TextView>(R.id.txt_grupo).text = "Grupo ${a.grupo}"

        v.findViewById<View>(R.id.tarjeta_perfil).setOnClickListener { main.ir(PerfilFragment()) }
        v.findViewById<View>(R.id.card_qr).setOnClickListener { main.ir(QrFragment()) }
        v.findViewById<View>(R.id.card_credencial).setOnClickListener { main.ir(CredencialFragment()) }
        v.findViewById<View>(R.id.card_horario).setOnClickListener { main.ir(HorarioFragment()) }
        v.findViewById<View>(R.id.card_boletin).setOnClickListener { main.ir(BoletinFragment()) }
    }
}

class NotificacionesFragment : Fragment(R.layout.comun_notificaciones) {
    private val fmtHora = SimpleDateFormat("h:mm a", Locale("es", "MX"))
    private val fmtFecha = SimpleDateFormat("d MMM", Locale("es", "MX"))

    private fun esHoy(d: Date): Boolean {
        val a = Calendar.getInstance().apply { time = d }
        val b = Calendar.getInstance()
        return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        conectarBarra(v)
        val a = Sesion.alumno ?: return
        val listaHoy = v.findViewById<LinearLayout>(R.id.lista_hoy)
        val listaAnt = v.findViewById<LinearLayout>(R.id.lista_anteriores)
        val vacio = v.findViewById<TextView>(R.id.txt_vacio_hoy)
        val tituloAnt = v.findViewById<TextView>(R.id.txt_anteriores)
        var actuales = emptyList<Notificacion>()

        fun pintar(n: Notificacion, padre: LinearLayout, conFecha: Boolean) {
            val item = LayoutInflater.from(requireContext())
                .inflate(R.layout.comun_item_notificacion, padre, false)
            item.findViewById<TextView>(R.id.txt_titulo).text = n.titulo
            item.findViewById<TextView>(R.id.txt_texto).text = n.texto
            item.findViewById<TextView>(R.id.txt_hora).text =
                if (conFecha) fmtFecha.format(n.fecha) else fmtHora.format(n.fecha)
            item.findViewById<View>(R.id.img_sin_leer).visibility =
                if (n.leida) View.GONE else View.VISIBLE
            item.findViewById<ImageView>(R.id.img_icono).setImageResource(R.drawable.ic_log_in)
            padre.addView(item)
        }

        fun cargar() {
            Repo.notificaciones(a.id) { lista ->
                if (!isAdded) return@notificaciones
                actuales = lista
                listaHoy.removeAllViews(); listaAnt.removeAllViews()
                val (nuevas, leidas) = lista.partition { !it.leida }
                nuevas.forEach { pintar(it, listaHoy, !esHoy(it.fecha)) }
                leidas.forEach { pintar(it, listaAnt, !esHoy(it.fecha)) }
                vacio.visibility = if (nuevas.isEmpty()) View.VISIBLE else View.GONE
                tituloAnt.visibility = if (leidas.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        v.findViewById<View>(R.id.txt_marcar_leidas).setOnClickListener {
            val ids = actuales.filter { !it.leida }.map { it.id }
            if (ids.isNotEmpty()) Repo.marcarLeidas(ids) { if (isAdded) cargar() }
        }
        cargar()
    }
}

class PerfilFragment : Fragment(R.layout.alumno_perfil) {
    override fun onViewCreated(v: View, s: Bundle?) {
        conectarBarra(v)
        val a = Sesion.alumno ?: return
        v.findViewById<TextView>(R.id.txt_nombre).text = a.nombre
        v.findViewById<TextView>(R.id.txt_rol_grupo).text = "${a.rol} · Grupo ${a.grupo}"
        v.findViewById<TextView>(R.id.txt_no_control).text = a.noControl
        v.findViewById<TextView>(R.id.txt_grupo).text = a.grupo
        v.findViewById<TextView>(R.id.txt_correo).text = a.correo
        v.findViewById<View>(R.id.opcion_qr).setOnClickListener { main.ir(QrFragment()) }
        v.findViewById<View>(R.id.opcion_credencial).setOnClickListener { main.ir(CredencialFragment()) }
        v.findViewById<View>(R.id.opcion_contrasena).setOnClickListener { main.ir(CambiarContrasenaFragment()) }
        v.findViewById<View>(R.id.opcion_cerrar_sesion).setOnClickListener { main.confirmarCerrarSesion() }
    }
}

class QrFragment : Fragment(R.layout.alumno_codigo_qr) {
    override fun onViewCreated(v: View, s: Bundle?) {
        conectarBarra(v)
        val a = Sesion.alumno ?: return
        v.findViewById<TextView>(R.id.txt_nombre).text = a.nombre
        v.findViewById<TextView>(R.id.txt_rol_grupo).text = "${a.rol} · Grupo ${a.grupo}"
        v.findViewById<ImageView>(R.id.img_qr).setImageBitmap(generarQr(a.id))
    }
}

class CredencialFragment : Fragment(R.layout.alumno_credencial_digital) {
    override fun onViewCreated(v: View, s: Bundle?) {
        conectarBarra(v)
        val a = Sesion.alumno ?: return
        v.findViewById<TextView>(R.id.txt_nombre).text = a.nombre
        v.findViewById<TextView>(R.id.txt_rol).text = a.rol
        v.findViewById<TextView>(R.id.txt_grupo).text = a.grupo
        v.findViewById<TextView>(R.id.txt_no_control).text = a.noControl
        v.findViewById<TextView>(R.id.txt_vigencia).text = a.vigencia
    }
}

class HorarioFragment : Fragment(R.layout.alumno_horario) {
    private val dias = listOf("Lunes", "Martes", "Miércoles", "Jueves", "Viernes")

    override fun onViewCreated(v: View, s: Bundle?) {
        conectarBarra(v)
        val a = Sesion.alumno ?: return
        val chips = listOf(R.id.chip_lunes, R.id.chip_martes, R.id.chip_miercoles, R.id.chip_jueves, R.id.chip_viernes)
            .map { v.findViewById<TextView>(it) }
        val encabezado = v.findViewById<TextView>(R.id.txt_encabezado)
        val adapter = ClaseAdapter(requireContext())
        v.findViewById<ListView>(R.id.lista_clases).adapter = adapter

        fun seleccionar(i: Int) {
            chips.forEachIndexed { j, c ->
                val activo = j == i
                c.setBackgroundResource(if (activo) R.drawable.shape_chip_activo else R.drawable.shape_chip)
                c.setTextColor(ContextCompat.getColor(requireContext(),
                    if (activo) R.color.cetis_text_on_brand else R.color.cetis_brand_900))
            }
            encabezado.text = "Grupo ${a.grupo} · ${dias[i]}"
            Repo.horario(a.grupo, i + 1) { if (isAdded) adapter.set(it) }
        }
        chips.forEachIndexed { i, c -> c.setOnClickListener { seleccionar(i) } }

        val hoy = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        seleccionar(if (hoy in Calendar.MONDAY..Calendar.FRIDAY) hoy - Calendar.MONDAY else 0)
    }
}

class BoletinFragment : Fragment(R.layout.alumno_boletin) {
    override fun onViewCreated(v: View, s: Bundle?) {
        conectarBarra(v)
        val adapter = AnuncioAdapter(requireContext())
        v.findViewById<ListView>(R.id.lista_anuncios).adapter = adapter
        Repo.anuncios { if (isAdded) adapter.set(it) }
    }
}