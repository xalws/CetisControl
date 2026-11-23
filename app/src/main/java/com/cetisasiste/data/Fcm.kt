package com.cetisasiste.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.cetisasiste.Main
import com.cetisasiste.R
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage

object Fcm {
    const val CANAL = "cetis_avisos"
    const val TOPIC_PREFECTOS = "prefectos"
    private val db get() = FirebaseFirestore.getInstance()

    fun registrarToken() {
        FirebaseMessaging.getInstance().token.addOnSuccessListener { guardarToken(it) }
    }

    fun guardarToken(token: String) {
        val uid = Repo.auth.currentUser?.uid ?: return
        db.collection("usuarios").document(uid)
            .update("fcmTokens", FieldValue.arrayUnion(token))
    }

    fun suscribirPrefecto() {
        FirebaseMessaging.getInstance().subscribeToTopic(TOPIC_PREFECTOS)
    }

    /* Llamar antes de cerrar sesión para que el dispositivo deje de recibir avisos de esa cuenta */
    fun limpiar(listo: () -> Unit) {
        val fm = FirebaseMessaging.getInstance()
        fm.unsubscribeFromTopic(TOPIC_PREFECTOS)
        val uid = Repo.auth.currentUser?.uid
        fm.token
            .addOnSuccessListener { t ->
                if (uid == null) return@addOnSuccessListener listo()
                db.collection("usuarios").document(uid)
                    .update("fcmTokens", FieldValue.arrayRemove(t))
                    .addOnCompleteListener { listo() }
            }
            .addOnFailureListener { listo() }
    }

    fun crearCanal(ctx: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CANAL, "Avisos CETIS", NotificationManager.IMPORTANCE_HIGH)
        )
    }
}

class CetisMessagingService : FirebaseMessagingService() {

    override fun onNewToken(token: String) {
        Fcm.guardarToken(token)
    }

    override fun onMessageReceived(m: RemoteMessage) {
        val titulo = m.notification?.title ?: m.data["titulo"] ?: return
        val texto = m.notification?.body ?: m.data["texto"].orEmpty()

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return

        Fcm.crearCanal(this)
        val abrir = PendingIntent.getActivity(
            this, 0,
            Intent(this, Main::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(this, Fcm.CANAL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setAutoCancel(true)
            .setContentIntent(abrir)
            .build()
        getSystemService(NotificationManager::class.java)
            .notify(System.currentTimeMillis().toInt(), n)
    }
}