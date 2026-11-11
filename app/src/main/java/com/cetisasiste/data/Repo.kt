package com.cetisasiste.data

import com.google.firebase.Timestamp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class Alumno(
    val id: String,
    val nombre: String,
    val grupo: String,
    val noControl: String,
    val correo: String,
    val vigencia: String,
    val rol: String = "Alumno"
)

data class Clase(
    val materia: String,
    val horario: String)

data class Anuncio(
    val etiqueta: String,
    val fecha: String,
    val titulo: String,
    val texto: String)

data class Notificacion(
    val id: String,
    val titulo: String,
    val texto: String,
    val fecha: Date,
    val leida: Boolean,
    val tipo: String = ""
)

/** Sesion del usuario */
object Sesion {
    var alumno: Alumno? = null
}

object Repo {
    private val db get() = FirebaseFirestore.getInstance()
    val auth get() = FirebaseAuth.getInstance()
    const val DOMINIO = "@cetis61.mx"

    fun correoDe(texto: String): String {
        val t = texto.trim()
        return if (t.contains("@")) t else t + DOMINIO
    }

    object Roles {
        const val ALUMNO = "alumno"
        const val PADRE = "padre"
        const val PREFECTO = "prefecto"
        const val MAESTRO = "maestro"
    }

    fun cargarRol(ok: (String) -> Unit, error: (String) -> Unit) {
        val usuario = auth.currentUser ?: return error("Sin sesión")
        val correo = usuario.email ?: return error("Sin correo")
        val uid = usuario.uid

        db.collection("usuarios").document(uid).get()
            .addOnSuccessListener { documento ->
                val rol = documento.getString("rol")
                if (rol != null) {
                    return@addOnSuccessListener ok(rol.lowercase())
                }

                // Si no existe un perfil, buscar al alumno
                db.collection("alumnos")
                    .whereEqualTo("correo", correo)
                    .limit(1)
                    .get()
                    .addOnSuccessListener { alumnos ->
                        if (alumnos.isEmpty) {
                            error("Tu cuenta no tiene un rol asignado.")
                        } else {
                            ok(Roles.ALUMNO)
                        }
                    }
                    .addOnFailureListener {
                        error(it.localizedMessage ?: "Error al buscar alumno")
                    }
            }
            .addOnFailureListener {
                error(it.localizedMessage ?: "Error al consultar usuario")
            }
    }

    fun cargarAlumno(ok: (Alumno) -> Unit, error: (String) -> Unit) {
        val correo = auth.currentUser?.email ?: return error("Sin sesión")
        db.collection("alumnos").whereEqualTo("correo", correo).limit(1).get()
            .addOnSuccessListener { s ->
                val d = s.documents.firstOrNull()
                    ?: return@addOnSuccessListener error("Tu correo no tiene un perfil de alumno registrado.")
                ok(
                    Alumno(
                        id = d.id,
                        nombre = d.getString("nombre").orEmpty(),
                        grupo = d.getString("grupo").orEmpty(),
                        noControl = d.getString("noControl").orEmpty(),
                        correo = correo,
                        vigencia = d.getString("vigencia").orEmpty()
                    )
                )
            }
            .addOnFailureListener { error(it.localizedMessage ?: "Error de red") }
    }

    @Suppress("UNCHECKED_CAST")
    fun horario(grupo: String, dia: Int, cb: (List<Clase>) -> Unit) {
        db.collection("horarios").document(grupo).get()
            .addOnSuccessListener { d ->
                val bloques = d.get("bloques") as? List<Map<String, Any>> ?: emptyList()
                cb(bloques
                    .filter { (it["dia"] as? Number)?.toInt() == dia }
                    .sortedBy { it["inicio"] as? String }
                    .map {
                        Clase(
                            it["materia"] as? String ?: "",
                            "${it["inicio"]} - ${it["fin"]}"
                        )
                    })
            }
            .addOnFailureListener { cb(emptyList()) }
    }

    fun anuncios(cb: (List<Anuncio>) -> Unit) {
        val fmt = SimpleDateFormat("d MMM", Locale("es", "MX"))
        db.collection("anuncios").orderBy("fecha", Query.Direction.DESCENDING).limit(50).get()
            .addOnSuccessListener { s ->
                cb(s.documents
                    .filter { (it.get("dirigidoA") as? List<*>)?.contains("alumno") == true }
                    .map {
                        Anuncio(
                            it.getString("area").orEmpty(),
                            it.getTimestamp("fecha")?.toDate()?.let(fmt::format).orEmpty(),
                            it.getString("titulo").orEmpty(),
                            it.getString("texto").orEmpty()
                        )
                    })
            }
            .addOnFailureListener { cb(emptyList()) }
    }

    fun notificaciones(alumnoId: String, cb: (List<Notificacion>) -> Unit) {
        android.util.Log.d("Notif", "Buscando alumnoId = $alumnoId")
        db.collection("notificaciones").whereEqualTo("alumnoId", alumnoId).limit(100).get()
            .addOnSuccessListener { s ->
                cb(s.documents.map {
                    Notificacion(
                        it.id,
                        it.getString("titulo").orEmpty(),
                        it.getString("texto").orEmpty(),
                        (it.getTimestamp("fecha") ?: Timestamp.now()).toDate(),
                        it.getBoolean("leida") ?: false,
                        it.getString("tipo").orEmpty()
                    )
                }.sortedByDescending { it.fecha })
            }
            .addOnFailureListener {
                android.util.Log.e("Notif", "Error: ${it.message}")
                cb(emptyList())
            }
    }

    fun crearNotificacion(alumnoIds: List<String>, tipo: String, titulo: String, texto: String) {
        val batch = db.batch()
        alumnoIds.forEach { id ->
            batch.set(
                db.collection("notificaciones").document(),
                mapOf(
                    "alumnoId" to id, "tipo" to tipo, "titulo" to titulo,
                    "texto" to texto, "leida" to false,
                    "fecha" to FieldValue.serverTimestamp()
                )
            )
        }
        batch.commit()
    }

    /** Avisa a todo un grupo */
    fun notificarGrupo(grupo: String?, tipo: String, titulo: String, texto: String) {
        val q = if (grupo == null) db.collection("alumnos")
        else db.collection("alumnos").whereEqualTo("grupo", grupo)
        q.get().addOnSuccessListener { s ->
            crearNotificacion(s.documents.map { it.id }, tipo, titulo, texto)
        }
    }

    fun marcarLeidas(ids: List<String>, listo: () -> Unit) {
        val batch = db.batch()
        ids.forEach { batch.update(db.collection("notificaciones").document(it), "leida", true) }
        batch.commit().addOnCompleteListener { listo() }
    }
}