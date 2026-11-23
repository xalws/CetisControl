package com.cetisasiste.data

import android.util.Log
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class Personal(val id: String, val nombre: String, val correo: String, val rol: String)

data class Registro(
    val nombre: String,
    val grupo: String,
    val noControl: String,
    val tipo: String,
    val fecha: Date
)

data class AlertaPrefecto(
    val id: String,
    val nombre: String,
    val grupo: String,
    val motivo: String,
    val fecha: Date
)

data class SalidaAutorizada(
    val nombre: String,
    val grupo: String,
    val motivo: String,
    val fecha: Date
)

data class GrupoTurno(
    val grupo: String,
    val turno: String,
    val claseActual: String?
)

data class ResultadoAcceso(
    val ok: Boolean,
    val tipo: String,
    val nombre: String,
    val grupo: String,
    val noControl: String,
    val hora: Date,
    val alerta: String?,
    val motivo: String?,
    val aviso: String
)

/* Estado en memoria de la sesión del prefecto*/
object SesionPrefecto {
    var personal: Personal? = null
    var tipoRegistro: String = RepoPrefecto.ENTRADA
    var resultado: ResultadoAcceso? = null

    var periodoId: String = ""
}

object RepoPrefecto {
    private val db get() = FirebaseFirestore.getInstance()
    const val ENTRADA = "entrada"
    const val SALIDA = "salida"

    fun periodoPorFecha(): String {
        val c = Calendar.getInstance()
        val m = c.get(Calendar.MONTH)
        val y = c.get(Calendar.YEAR)
        return when {
            m >= Calendar.AUGUST -> "$y-${y + 1}-1"
            m == Calendar.JANUARY -> "${y - 1}-$y-1"
            else -> "${y - 1}-$y-2"
        }
    }

    private fun periodo() = SesionPrefecto.periodoId.ifBlank { periodoPorFecha() }

    private fun registrosDe(periodo: String) =
        db.collection("periodos").document(periodo).collection("registros")

    private fun inicioDeHoy(): Date = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.time

    private fun esHoy(t: Timestamp?) = t != null && !t.toDate().before(inicioDeHoy())

    fun cargarPersonal(ok: (Personal) -> Unit, error: (String) -> Unit) {
        val u = Repo.auth.currentUser ?: return error("Sin sesión")
        db.collection("usuarios").document(u.uid).get()
            .addOnSuccessListener { d ->
                if (!d.exists()) return@addOnSuccessListener error("Tu cuenta no tiene perfil.")
                val p = Personal(
                    id = u.uid,
                    nombre = d.getString("nombre").orEmpty(),
                    correo = u.email.orEmpty(),
                    rol = d.getString("rol").orEmpty().lowercase()
                )
                SesionPrefecto.personal = p
                ok(p)
            }
            .addOnFailureListener { error(it.localizedMessage ?: "Error de red") }
    }

    //  registrar entrada o salida
    private fun buscarAlumno(
        codigo: String,
        cb: (DocumentSnapshot?) -> Unit,
        fallo: (String) -> Unit
    ) {
        val c = codigo.trim()
        fun porNoControl() {
            db.collection("alumnos").whereEqualTo("noControl", c).limit(1).get()
                .addOnSuccessListener { cb(it.documents.firstOrNull()) }
                .addOnFailureListener { fallo(it.localizedMessage ?: "Error de red") }
        }
        if (c.isEmpty() || c.contains("/")) return porNoControl()
        db.collection("alumnos").document(c).get()
            .addOnSuccessListener { d -> if (d.exists()) cb(d) else porNoControl() }
            .addOnFailureListener { fallo(it.localizedMessage ?: "Error de red") }
    }

    private fun salidaAutorizada(
        d: DocumentSnapshot,
        periodo: String,
        grupoId: String,
        cb: (Boolean) -> Unit,
        fallo: (String) -> Unit
    ) {
        fun deHoy(s: DocumentSnapshot) =
            esHoy(s.getTimestamp("fecha") ?: s.getTimestamp("creadoEn"))

        db.collection("periodos").document(periodo).collection("salidasDeGrupo")
            .whereEqualTo("grupoId", grupoId).get()
            .addOnSuccessListener { grupo ->
                if (grupo.documents.any(::deHoy)) return@addOnSuccessListener cb(true)
                d.reference.collection("estatus").whereEqualTo("tipo", "pase_salud").get()
                    .addOnSuccessListener { pases -> cb(pases.documents.any(::deHoy)) }
                    .addOnFailureListener { fallo(it.localizedMessage ?: "Error de red") }
            }
            .addOnFailureListener { fallo(it.localizedMessage ?: "Error de red") }
    }

    /* Avisa a los padres del alumno */
    private fun avisarPadres(d: DocumentSnapshot, tipo: String, titulo: String, mensaje: String) {
        val yo = Repo.auth.currentUser?.uid ?: return
        val padres = (d.get("padres") as? List<*>)?.filterIsInstance<String>().orEmpty()
        padres.forEach { padreUid ->
            db.collection("usuarios").document(padreUid).collection("notificaciones").add(
                mapOf(
                    "tipo" to tipo,
                    "titulo" to titulo,
                    "mensaje" to mensaje,
                    "fecha" to FieldValue.serverTimestamp(),
                    "leida" to false,
                    "alumnoId" to d.id,
                    "creadaPor" to yo
                )
            ).addOnFailureListener { Log.e("Aviso", "No se avisó a $padreUid: ${it.message}") }
        }
    }

    fun registrarAcceso(
        codigo: String,
        tipo: String,
        ok: (ResultadoAcceso) -> Unit,
        error: (String) -> Unit
    ) {
        buscarAlumno(codigo, { d ->
            if (d == null) {
                error("Alumno no encontrado")
                return@buscarAlumno
            }
            val uid = Repo.auth.currentUser?.uid.orEmpty()
            val nombre = d.getString("nombre").orEmpty()
            val noControl = d.getString("noControl") ?: d.id
            val grupoId = d.getString("grupoId") ?: d.getString("grupo").orEmpty()
            val grupo = d.getString("grupo") ?: grupoId
            val ahora = Date()
            val etiqueta = if (tipo == SALIDA) "Salida" else "Entrada"

            // El periodo sale del grupo
            val periodo = if (grupoId.contains("_")) {
                grupoId.substringBefore("_").also { SesionPrefecto.periodoId = it }
            } else periodo()

            fun registro(permitido: Boolean, motivo: String?): Map<String, Any> {
                val m = mutableMapOf<String, Any>(
                    "alumnoId" to d.id,
                    "noControl" to noControl,
                    "nombre" to nombre,
                    "grupo" to grupo,
                    "grupoId" to grupoId,
                    "tipo" to tipo,
                    "permitido" to permitido,
                    "registradoPor" to uid,
                    "registradoPorNombre" to SesionPrefecto.personal?.nombre.orEmpty(),
                    "fecha" to FieldValue.serverTimestamp()
                )
                if (motivo != null) m["motivo"] = motivo
                return m
            }

            fun bloquear(alerta: String, motivo: String) {
                registrosDe(periodo).add(registro(false, motivo))
                    .addOnFailureListener { Log.e("Registro", "Bloqueo sin guardar: ${it.message}") }
                ok(
                    ResultadoAcceso(
                        false, tipo, nombre, grupo, noControl, ahora, alerta, motivo,
                        "No se registró la ${etiqueta.lowercase()}."
                    )
                )
            }

            fun registrar() {
                registrosDe(periodo).add(registro(true, null))
                    .addOnSuccessListener {
                        val hora = SimpleDateFormat("h:mm a", Locale("es", "MX")).format(ahora)
                        avisarPadres(
                            d, tipo, "$etiqueta registrada",
                            "$nombre ${if (tipo == SALIDA) "salió" else "entró"} del plantel a las $hora."
                        )
                        ok(
                            ResultadoAcceso(
                                true, tipo, nombre, grupo, noControl, ahora, null, null,
                                "$etiqueta registrada. Se avisó a su familia."
                            )
                        )
                    }
                    .addOnFailureListener { error(it.localizedMessage ?: "No se pudo registrar") }
            }

            if (d.getBoolean("activo") == false) {
                return@buscarAlumno bloquear("Alumno inactivo", "El alumno está dado de baja o suspendido.")
            }
            if (tipo == ENTRADA) {
                if (d.getBoolean("activoPorPago") == false) {
                    return@buscarAlumno bloquear("Pago pendiente", "Su entrada está deshabilitada por pagos. Debe acudir a Control Escolar.")
                }
                if (d.getBoolean("activoPorSalud") == false) {
                    return@buscarAlumno bloquear("Restricción de salud", "Su entrada está deshabilitada por salud. Debe acudir a Enfermería.")
                }
            }

            if (tipo == SALIDA) {
                salidaAutorizada(d, periodo, grupoId, { autorizada ->
                    if (autorizada) registrar()
                    else bloquear(
                        "Salida sin autorización",
                        "No hay salida de grupo ni pase de salud de hoy. Debe autorizarlo su profesor o Enfermería."
                    )
                }, error)
            } else {
                registrar()
            }
        }, error)
    }

    /* Entradas y salidas de hoy (de todos los prefectos) */
    fun registrosHoy(cb: (List<Registro>) -> Unit) {
        registrosDe(periodo())
            .whereGreaterThanOrEqualTo("fecha", Timestamp(inicioDeHoy()))
            .orderBy("fecha", Query.Direction.DESCENDING)
            .limit(30).get()
            .addOnSuccessListener { s ->
                cb(s.documents.filter { it.getBoolean("permitido") != false }.map {
                    Registro(
                        it.getString("nombre").orEmpty(),
                        it.getString("grupo").orEmpty(),
                        it.getString("noControl").orEmpty(),
                        it.getString("tipo").orEmpty(),
                        it.getTimestamp("fecha")?.toDate() ?: Date()
                    )
                })
            }
            .addOnFailureListener { Log.e("Registro", "registrosHoy: ${it.message}"); cb(emptyList()) }
    }

    /* intentos bloqueados de hoy */
    fun alertasHoy(cb: (List<AlertaPrefecto>) -> Unit) {
        registrosDe(periodo()).whereEqualTo("permitido", false).limit(100).get()
            .addOnSuccessListener { s ->
                cb(s.documents
                    .filter { esHoy(it.getTimestamp("fecha")) }
                    .map {
                        AlertaPrefecto(
                            it.id,
                            it.getString("nombre").orEmpty(),
                            it.getString("grupo").orEmpty(),
                            it.getString("motivo").orEmpty(),
                            it.getTimestamp("fecha")?.toDate() ?: Date()
                        )
                    }.sortedByDescending { it.fecha })
            }
            .addOnFailureListener { Log.e("Registro", "alertasHoy: ${it.message}"); cb(emptyList()) }
    }

    /* Salidas de grupo de hoy */
    fun salidasAutorizadas(cb: (List<SalidaAutorizada>) -> Unit) {
        db.collection("periodos").document(periodo()).collection("salidasDeGrupo").limit(50).get()
            .addOnSuccessListener { s ->
                cb(s.documents
                    .filter { esHoy(it.getTimestamp("fecha") ?: it.getTimestamp("creadoEn")) }
                    .map {
                        SalidaAutorizada(
                            "Salida de grupo",
                            it.getString("grupoId").orEmpty(),
                            it.getString("motivo").orEmpty(),
                            (it.getTimestamp("fecha") ?: it.getTimestamp("creadoEn"))?.toDate() ?: Date()
                        )
                    }.sortedByDescending { it.fecha })
            }
            .addOnFailureListener { Log.e("Registro", "salidas: ${it.message}"); cb(emptyList()) }
    }

    /* Todos los grupos con su turno y la clase que llevan en este momento */
    @Suppress("UNCHECKED_CAST")
    fun gruposConClase(cb: (List<GrupoTurno>) -> Unit) {
        val ahora = Calendar.getInstance()
        val dia = ahora.get(Calendar.DAY_OF_WEEK) - Calendar.MONDAY + 1 // lunes = 1
        val hora = SimpleDateFormat("HH:mm", Locale.US).format(ahora.time)
        db.collection("horarios").get()
            .addOnSuccessListener { s ->
                cb(s.documents.map { d ->
                    val bloques = d.get("bloques") as? List<Map<String, Any>> ?: emptyList()
                    val primero = bloques.mapNotNull { it["inicio"] as? String }.minOrNull() ?: "00:00"
                    val turno = d.getString("turno")?.lowercase()
                        ?: if (primero < "13:00") "matutino" else "vespertino"
                    val actual = bloques.firstOrNull {
                        (it["dia"] as? Number)?.toInt() == dia &&
                                (it["inicio"] as? String ?: "99:99") <= hora &&
                                hora < (it["fin"] as? String ?: "00:00")
                    }
                    GrupoTurno(
                        d.id, turno,
                        actual?.let { "${it["materia"]} · ${it["inicio"]} - ${it["fin"]}" }
                    )
                }.sortedBy { it.grupo })
            }
            .addOnFailureListener { cb(emptyList()) }
    }

    /** Obtiene las alertas (registros con permitido == false) que no han sido atendidas */
    fun alertasPendientes(cb: (List<AlertaPrefecto>) -> Unit) {
        registrosDe(periodo()).whereEqualTo("permitido", false).get()
            .addOnSuccessListener { s ->
                cb(s.documents
                    .filter { esHoy(it.getTimestamp("fecha")) && it.getBoolean("atendida") != true }
                    .map {
                        AlertaPrefecto(
                            it.id,
                            it.getString("nombre").orEmpty(),
                            it.getString("grupo").orEmpty(),
                            it.getString("motivo").orEmpty(),
                            it.getTimestamp("fecha")?.toDate() ?: Date()
                        )
                    }.sortedByDescending { it.fecha })
            }
            .addOnFailureListener { Log.e("RepoPrefecto", "alertasPendientes: ${it.message}"); cb(emptyList()) }
    }

    /** Marca una alerta como atendida por el prefecto */
    fun marcarAtendida(id: String, listo: () -> Unit, error: (String) -> Unit) {
        registrosDe(periodo()).document(id)
            .update("atendida", true)
            .addOnSuccessListener { listo() }
            .addOnFailureListener { error(it.localizedMessage ?: "No se pudo marcar como atendida") }
    }
}