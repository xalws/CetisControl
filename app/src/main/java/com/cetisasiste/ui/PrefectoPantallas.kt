package com.cetisasiste.ui

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.cetisasiste.R
import com.cetisasiste.data.RepoPrefecto
import com.cetisasiste.data.SesionPrefecto
import com.google.zxing.BarcodeFormat
import com.google.zxing.ResultPoint
import com.journeyapps.barcodescanner.BarcodeCallback
import com.journeyapps.barcodescanner.BarcodeResult
import com.journeyapps.barcodescanner.BarcodeView
import com.journeyapps.barcodescanner.DefaultDecoderFactory
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val fmtHora = SimpleDateFormat("h:mm a", Locale("es", "MX"))
private val fmtFechaHora = SimpleDateFormat("d MMM, h:mm a", Locale("es", "MX"))

/** "2026-2027-1_102" -> "102" */
private fun nombreGrupo(g: String) = g.substringAfter("_")

/** Fila reutilizando comun_item_notificacion */
private fun Fragment.fila(
    padre: LinearLayout,
    titulo: String,
    texto: String,
    hora: String,
    destacar: Boolean,
    alTocar: (() -> Unit)? = null
) {
    val item = LayoutInflater.from(requireContext())
        .inflate(R.layout.comun_item_notificacion, padre, false)
    item.findViewById<TextView>(R.id.txt_titulo).text = titulo
    item.findViewById<TextView>(R.id.txt_texto).text = texto
    item.findViewById<TextView>(R.id.txt_hora).text = hora
    item.findViewById<View>(R.id.img_sin_leer).visibility = if (destacar) View.VISIBLE else View.GONE
    item.findViewById<ImageView>(R.id.img_icono).setImageResource(R.drawable.ic_log_in)
    if (alTocar != null) item.setOnClickListener { alTocar() }
    padre.addView(item)
}

/** Selector Entrada / Salida compartido por escáner y captura */
private fun Fragment.segmento(v: View, alCambiar: () -> Unit = {}) {
    val ent = v.findViewById<TextView>(R.id.opcion_entrada)
    val sal = v.findViewById<TextView>(R.id.opcion_salida)
    fun pintar() {
        val esEntrada = SesionPrefecto.tipoRegistro == RepoPrefecto.ENTRADA
        listOf(ent to esEntrada, sal to !esEntrada).forEach { (t, activo) ->
            if (activo) t.setBackgroundResource(R.drawable.shape_chip_activo) else t.background = null
            t.setTextColor(
                ContextCompat.getColor(
                    requireContext(),
                    if (activo) R.color.cetis_text_on_brand else R.color.cetis_brand_900
                )
            )
        }
        alCambiar()
    }
    ent.setOnClickListener { SesionPrefecto.tipoRegistro = RepoPrefecto.ENTRADA; pintar() }
    sal.setOnClickListener { SesionPrefecto.tipoRegistro = RepoPrefecto.SALIDA; pintar() }
    pintar()
}

class PrefectoInicioFragment : Fragment(R.layout.prefecto_inicio) {
    override fun onViewCreated(v: View, s: Bundle?) {
        conectarBarra(v)
        // La campana del prefecto lleva a sus alertas
        v.findViewById<View>(R.id.btn_notificaciones).setOnClickListener { main.ir(PrefectoAlertasFragment()) }

        v.findViewById<TextView>(R.id.txt_nombre).text = SesionPrefecto.personal?.nombre.orEmpty()
        v.findViewById<TextView>(R.id.txt_fecha).text =
            SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "MX")).format(Date())
                .replaceFirstChar { it.uppercase() }

        v.findViewById<View>(R.id.card_escanear).setOnClickListener { main.ir(PrefectoEscanerFragment()) }
        v.findViewById<View>(R.id.card_no_control).setOnClickListener { main.ir(PrefectoCapturaFragment()) }
        v.findViewById<View>(R.id.card_horarios).setOnClickListener { main.ir(PrefectoHorariosFragment()) }
        v.findViewById<View>(R.id.card_calendario).setOnClickListener {
            Toast.makeText(requireContext(), "todavia no :(", Toast.LENGTH_SHORT).show()
        }
        val filaAlertas = v.findViewById<View>(R.id.fila_alertas)
        filaAlertas.findViewById<TextView>(R.id.txt_titulo).text = "Alertas pendientes"
        filaAlertas.findViewById<ImageView>(R.id.img_icono).setImageResource(R.drawable.ic_log_in)
        filaAlertas.findViewById<View>(R.id.img_flecha).visibility = View.VISIBLE
        val detalleAlertas = filaAlertas.findViewById<TextView>(R.id.txt_detalle)
        filaAlertas.setOnClickListener { main.ir(PrefectoAlertasFragment()) }
        RepoPrefecto.alertasPendientes { l ->
            if (!isAdded) return@alertasPendientes
            detalleAlertas.text = if (l.isEmpty()) "Sin alertas por atender" else "${l.size} sin atender"
        }

        val listaEsc = v.findViewById<LinearLayout>(R.id.lista_escaneos)
        val vacioEsc = v.findViewById<TextView>(R.id.txt_vacio_escaneos)
        RepoPrefecto.registrosHoy { lista ->
            if (!isAdded) return@registrosHoy
            vacioEsc.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            lista.forEach { r ->
                fila(
                    listaEsc, r.nombre,
                    "${r.tipo.replaceFirstChar { c -> c.uppercase() }} · Grupo ${nombreGrupo(r.grupo)}",
                    fmtHora.format(r.fecha), false
                )
            }
        }

        val listaSal = v.findViewById<LinearLayout>(R.id.lista_salidas)
        val vacioSal = v.findViewById<TextView>(R.id.txt_vacio_salidas)
        RepoPrefecto.salidasAutorizadas { lista ->
            if (!isAdded) return@salidasAutorizadas
            vacioSal.visibility = if (lista.isEmpty()) View.VISIBLE else View.GONE
            lista.forEach { x ->
                fila(
                    listaSal, x.nombre,
                    "Grupo ${nombreGrupo(x.grupo)}" + if (x.motivo.isBlank()) "" else " · ${x.motivo}",
                    fmtHora.format(x.fecha), true
                )
            }
        }
    }
}

class PrefectoEscanerFragment : Fragment(R.layout.prefecto_escaner) {
    private var camara: BarcodeView? = null
    private var aviso: View? = null
    private var estado: TextView? = null
    private var procesando = false

    private val pedirPermiso = registerForActivityResult(ActivityResultContracts.RequestPermission()) { actualizarPermiso() }

    private fun tienePermiso() = ContextCompat.checkSelfPermission(
        requireContext(), Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED

    private fun actualizarPermiso() {
        val ok = tienePermiso()
        aviso?.visibility = if (ok) View.GONE else View.VISIBLE
        if (ok) camara?.resume()
    }

    private fun mostrar(msg: String) {
        estado?.text = msg
        estado?.visibility = View.VISIBLE
    }

    override fun onViewCreated(v: View, s: Bundle?) {
        conectarBarra(v)
        camara = v.findViewById(R.id.camara)
        aviso = v.findViewById(R.id.aviso_permiso)
        estado = v.findViewById(R.id.txt_estado)
        val registrando = v.findViewById<TextView>(R.id.txt_registrando)
        procesando = false

        segmento(v) {
            registrando.text =
                if (SesionPrefecto.tipoRegistro == RepoPrefecto.ENTRADA) "Registrando: Entrada"
                else "Registrando: Salida"
        }

        v.findViewById<View>(R.id.btn_permiso).setOnClickListener { pedirPermiso.launch(Manifest.permission.CAMERA) }
        v.findViewById<View>(R.id.btn_escribir).setOnClickListener { main.ir(PrefectoCapturaFragment()) }

        camara?.decoderFactory = DefaultDecoderFactory(listOf(BarcodeFormat.QR_CODE))
        camara?.decodeContinuous(object : BarcodeCallback {
            override fun barcodeResult(r: BarcodeResult?) {
                val texto = r?.text ?: return
                if (procesando) return
                procesando = true
                mostrar("Procesando…")
                RepoPrefecto.registrarAcceso(
                    texto, SesionPrefecto.tipoRegistro,
                    ok = { res ->
                        if (!isAdded) return@registrarAcceso
                        SesionPrefecto.resultado = res
                        main.ir(PrefectoResultadoFragment())
                    },
                    error = { msg ->
                        if (!isAdded) return@registrarAcceso
                        mostrar(msg)
                        // pausa de 2 segundos
                        view?.postDelayed({ procesando = false }, 2000)
                    }
                )
            }

            override fun possibleResultPoints(resultPoints: MutableList<ResultPoint>?) {}
        })

        if (!tienePermiso()) pedirPermiso.launch(Manifest.permission.CAMERA)
        actualizarPermiso()
    }

    override fun onResume() {
        super.onResume()
        if (tienePermiso()) camara?.resume()
    }

    override fun onPause() {
        camara?.pause()
        super.onPause()
    }

    override fun onDestroyView() {
        camara = null; aviso = null; estado = null
        super.onDestroyView()
    }
}

class PrefectoCapturaFragment : Fragment(R.layout.prefecto_captura) {
    private val digitos = StringBuilder()

    override fun onViewCreated(v: View, s: Bundle?) {
        conectarBarra(v)
        val txt = v.findViewById<TextView>(R.id.txt_tecleado)
        val ayuda = v.findViewById<TextView>(R.id.txt_ayuda)
        val ayudaOriginal = ayuda.text
        val btn = v.findViewById<TextView>(R.id.btn_registrar)

        fun refrescar() {
            txt.text = digitos.toString()
            btn.text =
                if (SesionPrefecto.tipoRegistro == RepoPrefecto.ENTRADA) "Registrar entrada"
                else "Registrar salida"
            btn.isEnabled = digitos.isNotEmpty()
        }

        segmento(v) { refrescar() }

        listOf(
            R.id.tecla_0, R.id.tecla_1, R.id.tecla_2, R.id.tecla_3, R.id.tecla_4,
            R.id.tecla_5, R.id.tecla_6, R.id.tecla_7, R.id.tecla_8, R.id.tecla_9
        ).forEachIndexed { numero, id ->
            v.findViewById<View>(id).setOnClickListener {
                if (digitos.length < 20) digitos.append(numero)
                ayuda.text = ayudaOriginal
                refrescar()
            }
        }
        v.findViewById<View>(R.id.tecla_borrar).setOnClickListener {
            if (digitos.isNotEmpty()) digitos.deleteCharAt(digitos.length - 1)
            refrescar()
        }
        v.findViewById<View>(R.id.tecla_limpiar).setOnClickListener {
            digitos.clear()
            ayuda.text = ayudaOriginal
            refrescar()
        }

        btn.setOnClickListener {
            btn.isEnabled = false
            RepoPrefecto.registrarAcceso(
                digitos.toString(), SesionPrefecto.tipoRegistro,
                ok = { res ->
                    if (!isAdded) return@registrarAcceso
                    SesionPrefecto.resultado = res
                    main.ir(PrefectoResultadoFragment())
                },
                error = { msg ->
                    if (!isAdded) return@registrarAcceso
                    ayuda.text = msg
                    refrescar()
                }
            )
        }
        refrescar()
    }
}

class PrefectoResultadoFragment : Fragment(R.layout.prefecto_resultado) {
    override fun onViewCreated(v: View, s: Bundle?) {
        conectarBarra(v)
        val r = SesionPrefecto.resultado ?: return
        val etiqueta = if (r.tipo == RepoPrefecto.SALIDA) "Salida" else "Entrada"

        val titulo = v.findViewById<TextView>(R.id.txt_resultado)
        titulo.text = if (r.ok) "$etiqueta registrada" else "Acceso no permitido"
        titulo.setTextColor(
            ContextCompat.getColor(
                requireContext(),
                if (r.ok) R.color.cetis_status_success else R.color.cetis_status_danger
            )
        )
        // No hay icono de error en los recursos: si falla, se oculta el círculo de éxito
        v.findViewById<View>(R.id.circulo_resultado).visibility = if (r.ok) View.VISIBLE else View.GONE

        v.findViewById<TextView>(R.id.txt_cuando).text = fmtFechaHora.format(r.hora)
        v.findViewById<TextView>(R.id.txt_iniciales).text =
            r.nombre.split(" ").filter { it.isNotBlank() }.take(2).joinToString("") { it.first().uppercase() }
        v.findViewById<TextView>(R.id.txt_nombre).text = r.nombre
        v.findViewById<TextView>(R.id.txt_detalle).text = "Grupo ${nombreGrupo(r.grupo)} · No. de control"
        v.findViewById<TextView>(R.id.txt_no_control).text = r.noControl

        v.findViewById<View>(R.id.chip_alerta).visibility = if (r.alerta != null) View.VISIBLE else View.GONE
        v.findViewById<TextView>(R.id.txt_alerta).text = r.alerta.orEmpty()
        val motivo = v.findViewById<TextView>(R.id.txt_motivo)
        motivo.text = r.motivo.orEmpty()
        motivo.visibility = if (r.motivo != null) View.VISIBLE else View.GONE
        v.findViewById<TextView>(R.id.txt_aviso).text = r.aviso

        // Regresa a la pantalla que lo abrió (escáner o captura) sin apilar más pantallas
        v.findViewById<View>(R.id.btn_siguiente).setOnClickListener { parentFragmentManager.popBackStack() }
    }
}
class PrefectoAlertasFragment : Fragment(R.layout.prefecto_alertas) {
    override fun onViewCreated(v: View, s: Bundle?) {
        conectarBarra(v)
        val lista = v.findViewById<LinearLayout>(R.id.lista_alertas)
        val vacio = v.findViewById<TextView>(R.id.txt_vacio)
        val resumen = v.findViewById<TextView>(R.id.txt_resumen)
        val contador = v.findViewById<TextView>(R.id.txt_contador)

        fun cargar() {
            RepoPrefecto.alertasPendientes { alertas ->
                if (!isAdded) return@alertasPendientes
                lista.removeAllViews()
                vacio.visibility = if (alertas.isEmpty()) View.VISIBLE else View.GONE
                resumen.text = when (alertas.size) {
                    0 -> "Sin alertas pendientes"
                    1 -> "1 alerta pendiente"
                    else -> "${alertas.size} alertas pendientes"
                }
                contador.visibility = if (alertas.isEmpty()) View.GONE else View.VISIBLE
                contador.text = alertas.size.toString()
                alertas.forEach { a ->
                    fila(
                        lista, a.nombre,
                        "Grupo ${nombreGrupo(a.grupo)} · ${a.motivo}",
                        fmtFechaHora.format(a.fecha), true,
                    )
                }
            }
        }
        cargar()
    }
}
class PrefectoHorariosFragment : Fragment(R.layout.prefecto_horarios) {
    override fun onViewCreated(v: View, s: Bundle?) {
        conectarBarra(v)
        val chips = mapOf(
            "matutino" to v.findViewById<TextView>(R.id.chip_matutino),
            "vespertino" to v.findViewById<TextView>(R.id.chip_vespertino)
        )
        val lista = v.findViewById<LinearLayout>(R.id.lista_grupos)
        val vacio = v.findViewById<TextView>(R.id.txt_vacio)
        var grupos = emptyList<com.cetisasiste.data.GrupoTurno>()
        var turno = "matutino"

        fun pintar() {
            chips.forEach { (t, c) ->
                val activo = t == turno
                c.setBackgroundResource(if (activo) R.drawable.shape_chip_activo else R.drawable.shape_chip)
                c.setTextColor(
                    ContextCompat.getColor(
                        requireContext(),
                        if (activo) R.color.cetis_text_on_brand else R.color.cetis_brand_900
                    )
                )
            }
            lista.removeAllViews()
            val delTurno = grupos.filter { it.turno == turno }
            vacio.visibility = if (delTurno.isEmpty()) View.VISIBLE else View.GONE
            delTurno.forEach { g ->
                fila(
                    lista, "Grupo ${nombreGrupo(g.grupo)}",
                    g.claseActual ?: "Sin clase en este momento", "", g.claseActual != null
                )
            }
        }

        chips.forEach { (t, c) -> c.setOnClickListener { turno = t; pintar() } }
        pintar()
        RepoPrefecto.gruposConClase {
            if (!isAdded) return@gruposConClase
            grupos = it
            pintar()
        }
    }
}