package com.cetisasiste.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageButton
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.cetisasiste.Main
import com.cetisasiste.R
import com.cetisasiste.data.Anuncio
import com.cetisasiste.data.Clase
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

val Fragment.main get() = requireActivity() as Main

fun Fragment.conectarBarra(v: View) {
    val menu = v.findViewById<ImageButton>(R.id.btn_menu)
    if (parentFragmentManager.backStackEntryCount > 0) {
        // Regresar a la pantalla anterior
        menu?.apply {
            setImageResource(R.drawable.ic_arrow_left)
            contentDescription = "Regresar"
            setOnClickListener { parentFragmentManager.popBackStack() }
        }
    } else {
        menu?.setOnClickListener { main.abrirMenu() }
    }
    v.findViewById<ImageButton>(R.id.btn_regresar)?.setOnClickListener {
        parentFragmentManager.popBackStack()
    }
    v.findViewById<View>(R.id.btn_notificaciones)?.setOnClickListener {
        main.ir(NotificacionesFragment())
    }
}

fun generarQr(texto: String, px: Int = 600): Bitmap {
    val m = QRCodeWriter().encode(texto, BarcodeFormat.QR_CODE, px, px)
    val pixeles = IntArray(px * px) { i -> if (m[i % px, i / px]) Color.BLACK else Color.WHITE }
    return Bitmap.createBitmap(pixeles, px, px, Bitmap.Config.ARGB_8888)
}

class ClaseAdapter(private val ctx: Context) : BaseAdapter() {
    private var datos = emptyList<Clase>()
    fun set(d: List<Clase>) { datos = d; notifyDataSetChanged() }
    override fun getCount() = datos.size
    override fun getItem(p: Int) = datos[p]
    override fun getItemId(p: Int) = p.toLong()
    override fun getView(p: Int, conv: View?, parent: ViewGroup): View {
        val v = conv ?: LayoutInflater.from(ctx).inflate(R.layout.alumno_item_clase, parent, false)
        val c = datos[p]
        v.findViewById<TextView>(R.id.txt_materia).text = c.materia
        v.findViewById<TextView>(R.id.txt_detalle).text = c.horario
        return v
    }
}

class AnuncioAdapter(private val ctx: Context) : BaseAdapter() {
    private var datos = emptyList<Anuncio>()
    fun set(d: List<Anuncio>) { datos = d; notifyDataSetChanged() }
    override fun getCount() = datos.size
    override fun getItem(p: Int) = datos[p]
    override fun getItemId(p: Int) = p.toLong()
    override fun getView(p: Int, conv: View?, parent: ViewGroup): View {
        val v = conv ?: LayoutInflater.from(ctx).inflate(R.layout.alumno_item_anuncio, parent, false)
        val a = datos[p]
        v.findViewById<TextView>(R.id.txt_etiqueta).text = a.etiqueta
        v.findViewById<TextView>(R.id.txt_fecha).text = a.fecha
        v.findViewById<TextView>(R.id.txt_titulo).text = a.titulo
        v.findViewById<TextView>(R.id.txt_texto).text = a.texto
        return v
    }
}