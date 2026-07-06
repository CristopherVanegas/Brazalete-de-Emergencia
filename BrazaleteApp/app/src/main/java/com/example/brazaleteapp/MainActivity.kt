package com.example.brazaleteapp

import android.Manifest
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private lateinit var txtEstado: TextView
    private lateinit var txtSenal: TextView
    private lateinit var txtUltimoDato: TextView
    private lateinit var btnConectar: Button
    private lateinit var txtIndicadorConexion: TextView

    private lateinit var edtTelefono: EditText
    private lateinit var btnAgregarContacto: Button
    private lateinit var btnSeleccionarContacto: Button
    private lateinit var btnLimpiarContactos: Button
    private lateinit var txtListaContactos: TextView
    private lateinit var btnIrAgregarContacto: Button

    private lateinit var screenInicio: View
    private lateinit var screenContactos: View
    private lateinit var screenInfo: View
    private lateinit var navInicio: LinearLayout
    private lateinit var navContactos: LinearLayout
    private lateinit var navInfo: LinearLayout
    private lateinit var iconInicio: ImageView
    private lateinit var textInicio: TextView
    private lateinit var iconContactos: ImageView
    private lateinit var textContactos: TextView
    private lateinit var iconInfo: ImageView
    private lateinit var textInfo: TextView

    private lateinit var contactPickerLauncher: ActivityResultLauncher<Intent>

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null
    private var dispositivoSeleccionado: BluetoothDevice? = null

    private var conectado = false

    private val uuidSPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    private val requestPermisos = 1001

    private val contactosEmergencia = mutableListOf<String>()
    private val prefsName = "BrazaletePrefs"
    private val contactosKey = "contactos_emergencia"

    private var ultimoEnvioSms = 0L
    private val tiempoMinimoEntreSms = 60000L

    private val handler = Handler(Looper.getMainLooper())
    private val tiempoMostrarDato = 3000L

    private val colorPrimario = Color.parseColor("#6D3DF5")
    private val colorInactivo = Color.parseColor("#8E8A9F")
    private val colorVerde = Color.parseColor("#2ECC71")
    private val colorGris = Color.parseColor("#D6D1E8")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        enlazarVistas()
        configurarSelectorContactos()
        configurarNavegacion()

        bluetoothAdapter = obtenerBluetoothAdapter()
        if (bluetoothAdapter == null) {
            txtEstado.text = "Este celular no tiene Bluetooth"
            txtUltimoDato.text = "No se puede usar el brazalete en este dispositivo."
            btnConectar.isEnabled = false
            return
        }

        cargarContactos()
        actualizarListaContactos()
        pedirPermisosNecesarios()
        mostrarPantalla("inicio")

        btnConectar.setOnClickListener {
            if (conectado) {
                conectado = false
                cerrarConexion()
                actualizarEstadoConexion(false, "Desconectado", "Conecta tu brazalete para comenzar.")
            } else {
                mostrarModalDispositivosBluetooth()
            }
        }

        btnIrAgregarContacto.setOnClickListener {
            mostrarPantalla("contactos")
        }

        btnAgregarContacto.setOnClickListener {
            agregarContactoManual()
        }

        btnSeleccionarContacto.setOnClickListener {
            abrirSelectorContactos()
        }

        btnLimpiarContactos.setOnClickListener {
            contactosEmergencia.clear()
            guardarContactos()
            actualizarListaContactos()
            Toast.makeText(this, "Contactos eliminados", Toast.LENGTH_SHORT).show()
        }
    }

    private fun enlazarVistas() {
        txtEstado = findViewById(R.id.txtEstado)
        txtSenal = findViewById(R.id.txtSenal)
        txtUltimoDato = findViewById(R.id.txtUltimoDato)
        btnConectar = findViewById(R.id.btnConectar)
        txtIndicadorConexion = findViewById(R.id.txtIndicadorConexion)

        edtTelefono = findViewById(R.id.edtTelefono)
        btnAgregarContacto = findViewById(R.id.btnAgregarContacto)
        btnSeleccionarContacto = findViewById(R.id.btnSeleccionarContacto)
        btnLimpiarContactos = findViewById(R.id.btnLimpiarContactos)
        txtListaContactos = findViewById(R.id.txtListaContactos)
        btnIrAgregarContacto = findViewById(R.id.btnIrAgregarContacto)

        screenInicio = findViewById(R.id.screenInicio)
        screenContactos = findViewById(R.id.screenContactos)
        screenInfo = findViewById(R.id.screenInfo)
        navInicio = findViewById(R.id.navInicio)
        navContactos = findViewById(R.id.navContactos)
        navInfo = findViewById(R.id.navInfo)
        iconInicio = findViewById(R.id.iconInicio)
        textInicio = findViewById(R.id.textInicio)
        iconContactos = findViewById(R.id.iconContactos)
        textContactos = findViewById(R.id.textContactos)
        iconInfo = findViewById(R.id.iconInfo)
        textInfo = findViewById(R.id.textInfo)
    }

    private fun configurarNavegacion() {
        navInicio.setOnClickListener { mostrarPantalla("inicio") }
        navContactos.setOnClickListener { mostrarPantalla("contactos") }
        navInfo.setOnClickListener { mostrarPantalla("info") }
    }

    private fun mostrarPantalla(pantalla: String) {
        screenInicio.visibility = if (pantalla == "inicio") View.VISIBLE else View.GONE
        screenContactos.visibility = if (pantalla == "contactos") View.VISIBLE else View.GONE
        screenInfo.visibility = if (pantalla == "info") View.VISIBLE else View.GONE

        val inicioActivo = pantalla == "inicio"
        val contactosActivo = pantalla == "contactos"
        val infoActivo = pantalla == "info"

        iconInicio.setColorFilter(if (inicioActivo) colorPrimario else colorInactivo)
        textInicio.setTextColor(if (inicioActivo) colorPrimario else colorInactivo)

        iconContactos.setColorFilter(if (contactosActivo) colorPrimario else colorInactivo)
        textContactos.setTextColor(if (contactosActivo) colorPrimario else colorInactivo)

        iconInfo.setColorFilter(if (infoActivo) colorPrimario else colorInactivo)
        textInfo.setTextColor(if (infoActivo) colorPrimario else colorInactivo)
    }

    private fun obtenerBluetoothAdapter(): BluetoothAdapter? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    private fun pedirPermisosNecesarios() {
        val permisos = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permisos.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permisos.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permisos.add(Manifest.permission.SEND_SMS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permisos.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permisos.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            permisos.add(Manifest.permission.READ_CONTACTS)
        }

        if (permisos.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permisos.toTypedArray(), requestPermisos)
        }
    }

    private fun tienePermisoBluetooth(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectOk = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
            val scanOk = ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
            connectOk && scanOk
        } else {
            true
        }
    }

    private fun tienePermisoSms(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
    }

    private fun tienePermisoUbicacion(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    private fun tienePermisoContactos(): Boolean {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
    }

    private fun mostrarModalDispositivosBluetooth() {
        if (!tienePermisoBluetooth()) {
            pedirPermisosNecesarios()
            Toast.makeText(this, "Debes permitir Dispositivos cercanos / Bluetooth", Toast.LENGTH_LONG).show()
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            actualizarEstadoConexion(false, "Bluetooth apagado", "Activa el Bluetooth del celular.")
            Toast.makeText(this, "Activa el Bluetooth del celular", Toast.LENGTH_LONG).show()
            return
        }

        val dispositivosEmparejados = try {
            bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            emptyList()
        }

        if (dispositivosEmparejados.isEmpty()) {
            Toast.makeText(this, "No hay dispositivos Bluetooth vinculados. Empareja primero el brazalete.", Toast.LENGTH_LONG).show()
            return
        }

        val nombresDispositivos = dispositivosEmparejados.map { dispositivo ->
            val nombre = obtenerNombreBluetooth(dispositivo)
            val mac = try { dispositivo.address ?: "" } catch (e: SecurityException) { "" }
            "$nombre\n$mac"
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Selecciona tu brazalete")
            .setItems(nombresDispositivos) { dialog, which ->
                val dispositivo = dispositivosEmparejados[which]
                dispositivoSeleccionado = dispositivo
                conectarBluetooth(dispositivo)
                dialog.dismiss()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }

    private fun obtenerNombreBluetooth(dispositivo: BluetoothDevice): String {
        return try {
            dispositivo.name ?: "Dispositivo sin nombre"
        } catch (e: SecurityException) {
            "Dispositivo Bluetooth"
        }
    }

    private fun conectarBluetooth(dispositivo: BluetoothDevice) {
        if (!tienePermisoBluetooth()) {
            pedirPermisosNecesarios()
            Toast.makeText(this, "Debes permitir Dispositivos cercanos / Bluetooth", Toast.LENGTH_LONG).show()
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            actualizarEstadoConexion(false, "Bluetooth apagado", "Activa el Bluetooth del celular.")
            Toast.makeText(this, "Activa el Bluetooth del celular", Toast.LENGTH_LONG).show()
            return
        }

        val nombreDispositivo = obtenerNombreBluetooth(dispositivo)
        actualizarEstadoConexion(false, "Conectando a $nombreDispositivo...", "Esperando conexión con el brazalete.")
        btnConectar.isEnabled = false

        thread {
            try {
                bluetoothAdapter?.cancelDiscovery()
                bluetoothSocket = dispositivo.createRfcommSocketToServiceRecord(uuidSPP)
                bluetoothSocket?.connect()
                inputStream = bluetoothSocket?.inputStream
                conectado = true

                runOnUiThread {
                    actualizarEstadoConexion(true, "Brazalete conectado", "Conectado a $nombreDispositivo. Esperando señal del Arduino...")
                    Toast.makeText(this, "Brazalete conectado", Toast.LENGTH_SHORT).show()
                }

                escucharDatos()
            } catch (e: SecurityException) {
                conectado = false
                runOnUiThread {
                    actualizarEstadoConexion(false, "Permiso Bluetooth denegado", "Activa el permiso de Dispositivos cercanos.")
                    Toast.makeText(this, "Falta permiso Bluetooth", Toast.LENGTH_LONG).show()
                }
            } catch (e: IOException) {
                conectado = false
                runOnUiThread {
                    actualizarEstadoConexion(false, "Error al conectar", e.message ?: "No se pudo conectar al brazalete.")
                    Toast.makeText(this, "No se pudo conectar al brazalete", Toast.LENGTH_LONG).show()
                }
                cerrarConexion()
            }
        }
    }

    private fun actualizarEstadoConexion(estaConectado: Boolean, titulo: String, detalle: String) {
        txtEstado.text = titulo
        txtUltimoDato.text = detalle
        btnConectar.text = if (estaConectado) "Desconectar" else "Conectar brazalete"
        btnConectar.isEnabled = true
        txtIndicadorConexion.setBackgroundColor(if (estaConectado) colorVerde else colorGris)
    }

    private fun escucharDatos() {
        val buffer = ByteArray(1024)

        while (conectado) {
            try {
                val bytesLeidos = inputStream?.read(buffer) ?: -1
                if (bytesLeidos > 0) {
                    val mensaje = String(buffer, 0, bytesLeidos).trim()
                    if (mensaje.isNotEmpty()) {
                        runOnUiThread { mostrarMensajeRecibido(mensaje) }
                    }
                }
            } catch (e: IOException) {
                conectado = false
                runOnUiThread {
                    actualizarEstadoConexion(false, "Conexión perdida", "Se desconectó el brazalete.")
                }
                cerrarConexion()
                break
            }
        }
    }

    private fun mostrarMensajeRecibido(mensaje: String) {
        txtSenal.text = mensaje
        txtUltimoDato.text = "Último dato recibido: $mensaje"

        when (mensaje) {
            "S" -> {
                txtEstado.text = "Señal S recibida"
                enviarAlertaEmergencia()
                limpiarDatoDespuesDeAccion()
            }
            "D" -> {
                txtEstado.text = "Señal de desconexión recibida"
                txtUltimoDato.text = "Bluetooth desconectado"
                conectado = false
                cerrarConexion()
                btnConectar.text = "Conectar brazalete"
                txtIndicadorConexion.setBackgroundColor(colorGris)
                handler.postDelayed({
                    txtSenal.text = "-"
                    txtUltimoDato.text = "Esperando señal..."
                }, tiempoMostrarDato)
            }
            else -> {
                txtEstado.text = "Dato recibido"
                limpiarDatoDespuesDeAccion()
            }
        }
    }

    private fun agregarContactoManual() {
        val telefono = edtTelefono.text.toString().trim()
        if (telefono.isEmpty()) {
            Toast.makeText(this, "Ingresa un número", Toast.LENGTH_SHORT).show()
            return
        }

        val telefonoLimpio = limpiarNumeroTelefono(telefono)
        if (!telefonoLimpio.startsWith("+")) {
            Toast.makeText(this, "Usa formato internacional. Ej: +593991234567", Toast.LENGTH_LONG).show()
            return
        }

        agregarContactoALista("Contacto manual", telefonoLimpio)
        edtTelefono.text.clear()
    }

    private fun configurarSelectorContactos() {
        contactPickerLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val uriContacto = result.data?.data
                if (uriContacto != null) {
                    obtenerTelefonoDesdeContacto(uriContacto)
                } else {
                    Toast.makeText(this, "No se pudo leer el contacto", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun abrirSelectorContactos() {
        if (!tienePermisoContactos()) {
            pedirPermisosNecesarios()
            Toast.makeText(this, "Acepta el permiso de contactos", Toast.LENGTH_LONG).show()
            return
        }

        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    private fun obtenerTelefonoDesdeContacto(uriContacto: Uri) {
        try {
            val cursor = contentResolver.query(
                uriContacto,
                arrayOf(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val nombreIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numeroIndex = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    val nombre = if (nombreIndex >= 0) it.getString(nombreIndex) else "Contacto"
                    val numeroOriginal = if (numeroIndex >= 0) it.getString(numeroIndex) else ""
                    val numeroLimpio = limpiarNumeroTelefono(numeroOriginal)

                    if (numeroLimpio.isBlank()) {
                        Toast.makeText(this, "El contacto no tiene número válido", Toast.LENGTH_SHORT).show()
                        return
                    }

                    agregarContactoALista(nombre, numeroLimpio)
                }
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Error leyendo contacto: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun limpiarNumeroTelefono(numero: String): String {
        var limpio = numero
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace(".", "")

        if (limpio.startsWith("09") && limpio.length == 10) {
            limpio = "+593" + limpio.substring(1)
        }

        return limpio
    }

    private fun agregarContactoALista(nombre: String, telefono: String) {
        if (contactosEmergencia.contains(telefono)) {
            Toast.makeText(this, "Ese contacto ya está agregado", Toast.LENGTH_SHORT).show()
            return
        }

        contactosEmergencia.add(telefono)
        guardarContactos()
        actualizarListaContactos()
        Toast.makeText(this, "$nombre agregado como contacto de emergencia", Toast.LENGTH_LONG).show()
    }

    private fun guardarContactos() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        prefs.edit().putString(contactosKey, contactosEmergencia.joinToString(",")).apply()
    }

    private fun cargarContactos() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val contactosTexto = prefs.getString(contactosKey, "") ?: ""
        contactosEmergencia.clear()

        if (contactosTexto.isNotBlank()) {
            contactosEmergencia.addAll(contactosTexto.split(",").map { it.trim() }.filter { it.isNotEmpty() })
        }
    }

    private fun actualizarListaContactos() {
        txtListaContactos.text = if (contactosEmergencia.isEmpty()) {
            "No hay contactos agregados."
        } else {
            contactosEmergencia.mapIndexed { index, contacto -> "${index + 1}. $contacto" }.joinToString("\n")
        }
    }

    private fun enviarAlertaEmergencia() {
        if (contactosEmergencia.isEmpty()) {
            txtUltimoDato.text = "No hay contactos para enviar SMS"
            Toast.makeText(this, "No hay contactos de emergencia agregados", Toast.LENGTH_LONG).show()
            return
        }

        if (!tienePermisoSms() || !tienePermisoUbicacion()) {
            pedirPermisosNecesarios()
            txtUltimoDato.text = "Faltan permisos de SMS o ubicación"
            Toast.makeText(this, "Acepta permisos de SMS y ubicación", Toast.LENGTH_LONG).show()
            return
        }

        val ahora = System.currentTimeMillis()
        if (ahora - ultimoEnvioSms < tiempoMinimoEntreSms) {
            txtUltimoDato.text = "Alerta ignorada para evitar spam de SMS"
            return
        }

        ultimoEnvioSms = ahora
        txtUltimoDato.text = "Obteniendo ubicación..."

        obtenerUbicacionActual { ubicacion ->
            if (ubicacion == null) {
                txtUltimoDato.text = "No se pudo obtener ubicación"
                Toast.makeText(this, "No se pudo obtener la ubicación", Toast.LENGTH_LONG).show()
                return@obtenerUbicacionActual
            }

            val latitud = ubicacion.latitude
            val longitud = ubicacion.longitude
            val linkMaps = "https://maps.google.com/?q=$latitud,$longitud"
            val mensajeSms = """
                ALERTA DEL BRAZALETE
                Se recibió una señal de emergencia.

                Ubicación exacta:
                $linkMaps
            """.trimIndent()

            enviarSmsAContactos(mensajeSms)
        }
    }

    private fun obtenerUbicacionActual(callback: (Location?) -> Unit) {
        if (!tienePermisoUbicacion()) {
            callback(null)
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        try {
            val gpsActivo = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val redActiva = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!gpsActivo && !redActiva) {
                Toast.makeText(this, "Activa la ubicación/GPS del celular", Toast.LENGTH_LONG).show()
                callback(null)
                return
            }

            val ultimaGps = if (gpsActivo) locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
            val ultimaRed = if (redActiva) locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null
            val ultimaUbicacion = elegirMejorUbicacion(ultimaGps, ultimaRed)

            if (ultimaUbicacion != null) {
                callback(ultimaUbicacion)
                return
            }

            val proveedor = when {
                gpsActivo -> LocationManager.GPS_PROVIDER
                redActiva -> LocationManager.NETWORK_PROVIDER
                else -> null
            }

            if (proveedor == null) {
                callback(null)
                return
            }

            txtUltimoDato.text = "Esperando ubicación del GPS..."

            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    callback(location)
                    locationManager.removeUpdates(this)
                }

                @Deprecated("Deprecated in Java")
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
                override fun onProviderEnabled(provider: String) {}
                override fun onProviderDisabled(provider: String) {}
            }

            locationManager.requestSingleUpdate(proveedor, listener, Looper.getMainLooper())
        } catch (e: SecurityException) {
            callback(null)
        } catch (e: Exception) {
            callback(null)
        }
    }

    private fun elegirMejorUbicacion(gps: Location?, red: Location?): Location? {
        if (gps == null && red == null) return null
        if (gps != null && red == null) return gps
        if (gps == null && red != null) return red
        return if ((gps?.time ?: 0L) > (red?.time ?: 0L)) gps else red
    }

    private fun enviarSmsAContactos(mensajeSms: String) {
        try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                @Suppress("DEPRECATION")
                SmsManager.getDefault()
            }

            contactosEmergencia.forEach { numero ->
                val partes = smsManager.divideMessage(mensajeSms)
                if (partes.size > 1) {
                    smsManager.sendMultipartTextMessage(numero, null, partes, null, null)
                } else {
                    smsManager.sendTextMessage(numero, null, mensajeSms, null, null)
                }
            }

            txtUltimoDato.text = "SMS enviado a ${contactosEmergencia.size} contacto(s)"
            Toast.makeText(this, "SMS de emergencia enviado", Toast.LENGTH_LONG).show()
        } catch (e: SecurityException) {
            txtUltimoDato.text = "Permiso SMS denegado"
            Toast.makeText(this, "Falta permiso para enviar SMS", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            txtUltimoDato.text = "Error enviando SMS: ${e.message}"
            Toast.makeText(this, "No se pudo enviar el SMS", Toast.LENGTH_LONG).show()
        }
    }

    private fun cerrarConexion() {
        try { inputStream?.close() } catch (_: IOException) {}
        try { bluetoothSocket?.close() } catch (_: IOException) {}
        inputStream = null
        bluetoothSocket = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        conectado = false
        cerrarConexion()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == requestPermisos) {
            if (grantResults.isEmpty()) {
                txtEstado.text = "Permisos no respondidos"
                return
            }

            val permisosDenegados = mutableListOf<String>()
            permissions.forEachIndexed { index, permiso ->
                val concedido = grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED
                if (!concedido) permisosDenegados.add(permiso)
            }

            if (permisosDenegados.isEmpty()) {
                txtEstado.text = "Permisos concedidos"
                txtUltimoDato.text = "Ya puedes conectar el brazalete"
            } else {
                txtEstado.text = "Faltan permisos"
                txtUltimoDato.text = "Activa Dispositivos cercanos, Ubicación, SMS y Contactos"
                Toast.makeText(this, "Algunos permisos fueron denegados", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun limpiarDatoDespuesDeAccion() {
        handler.postDelayed({
            txtSenal.text = "-"
            txtUltimoDato.text = if (conectado) "Esperando señal del Arduino..." else "Esperando señal..."
        }, tiempoMostrarDato)
    }
}
