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
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.util.UUID
import kotlin.concurrent.thread
import android.os.Handler

class MainActivity : AppCompatActivity() {

    private lateinit var txtEstado: TextView
    private lateinit var txtSenal: TextView
    private lateinit var txtUltimoDato: TextView
    private lateinit var btnConectar: Button

    private lateinit var edtTelefono: EditText
    private lateinit var btnAgregarContacto: Button
    private lateinit var btnSeleccionarContacto: Button
    private lateinit var btnLimpiarContactos: Button
    private lateinit var txtListaContactos: TextView

    private lateinit var contactPickerLauncher: ActivityResultLauncher<Intent>

    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var inputStream: InputStream? = null

    private var conectado = false

    private val nombreDispositivo = "Brazalete-01"

    private val uuidSPP: UUID =
        UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private val requestPermisos = 1001

    private val contactosEmergencia = mutableListOf<String>()
    private val prefsName = "BrazaletePrefs"
    private val contactosKey = "contactos_emergencia"

    private var ultimoEnvioSms = 0L
    private val tiempoMinimoEntreSms = 60000L // 60 segundos

    private val handler = Handler(Looper.getMainLooper())
    private val tiempoMostrarDato = 3000L // 3 segundos

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        txtEstado = findViewById(R.id.txtEstado)
        txtSenal = findViewById(R.id.txtSenal)
        txtUltimoDato = findViewById(R.id.txtUltimoDato)
        btnConectar = findViewById(R.id.btnConectar)

        edtTelefono = findViewById(R.id.edtTelefono)
        btnAgregarContacto = findViewById(R.id.btnAgregarContacto)
        btnSeleccionarContacto = findViewById(R.id.btnSeleccionarContacto)
        btnLimpiarContactos = findViewById(R.id.btnLimpiarContactos)
        txtListaContactos = findViewById(R.id.txtListaContactos)

        configurarSelectorContactos()

        bluetoothAdapter = obtenerBluetoothAdapter()

        if (bluetoothAdapter == null) {
            txtEstado.text = "Este celular no tiene Bluetooth"
            btnConectar.isEnabled = false
            return
        }

        cargarContactos()
        actualizarListaContactos()
        pedirPermisosNecesarios()

        btnConectar.setOnClickListener {
            if (conectado) {
                conectado = false
                cerrarConexion()

                txtEstado.text = "Desconectado"
                txtSenal.text = "-"
                txtUltimoDato.text = "Esperando señal..."
                btnConectar.text = "Conectar al HC-05"
            } else {
                conectarBluetooth()
            }
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

    private fun obtenerBluetoothAdapter(): BluetoothAdapter? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val bluetoothManager =
                getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
            bluetoothManager.adapter
        } else {
            @Suppress("DEPRECATION")
            BluetoothAdapter.getDefaultAdapter()
        }
    }

    private fun pedirPermisosNecesarios() {
        val permisos = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_CONNECT
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permisos.add(Manifest.permission.BLUETOOTH_CONNECT)
            }

            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.BLUETOOTH_SCAN
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permisos.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.SEND_SMS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permisos.add(Manifest.permission.SEND_SMS)
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permisos.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permisos.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_CONTACTS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permisos.add(Manifest.permission.READ_CONTACTS)
        }

        if (permisos.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permisos.toTypedArray(),
                requestPermisos
            )
        }
    }

    private fun tienePermisoBluetooth(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val connectOk = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) == PackageManager.PERMISSION_GRANTED

            val scanOk = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) == PackageManager.PERMISSION_GRANTED

            connectOk && scanOk
        } else {
            true
        }
    }

    private fun tienePermisoSms(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun tienePermisoUbicacion(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarse = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fine || coarse
    }

    private fun tienePermisoContactos(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.READ_CONTACTS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun conectarBluetooth() {
        if (!tienePermisoBluetooth()) {
            pedirPermisosNecesarios()
            Toast.makeText(
                this,
                "Debes permitir Dispositivos cercanos / Bluetooth",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (bluetoothAdapter?.isEnabled != true) {
            txtEstado.text = "Bluetooth apagado"
            Toast.makeText(this, "Activa el Bluetooth del celular", Toast.LENGTH_LONG).show()
            return
        }

        txtEstado.text = "Buscando HC-05 emparejado..."
        btnConectar.isEnabled = false

        thread {
            try {
                val dispositivo = buscarDispositivoEmparejado(nombreDispositivo)

                if (dispositivo == null) {
                    runOnUiThread {
                        txtEstado.text = "No se encontró $nombreDispositivo"
                        txtUltimoDato.text = "Empareja primero el HC-05 desde ajustes Bluetooth"
                        btnConectar.isEnabled = true

                        Toast.makeText(
                            this,
                            "Empareja primero el HC-05 desde ajustes Bluetooth",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@thread
                }

                runOnUiThread {
                    txtEstado.text = "Conectando a $nombreDispositivo..."
                }

                if (tienePermisoBluetooth()) {
                    bluetoothAdapter?.cancelDiscovery()
                }

                bluetoothSocket = dispositivo.createRfcommSocketToServiceRecord(uuidSPP)
                bluetoothSocket?.connect()

                inputStream = bluetoothSocket?.inputStream
                conectado = true

                runOnUiThread {
                    txtEstado.text = "Conectado a $nombreDispositivo"
                    txtUltimoDato.text = "Esperando señal del Arduino..."
                    btnConectar.text = "Desconectar"
                    btnConectar.isEnabled = true

                    Toast.makeText(this, "Bluetooth conectado", Toast.LENGTH_SHORT).show()
                }

                escucharDatos()

            } catch (e: SecurityException) {
                conectado = false

                runOnUiThread {
                    txtEstado.text = "Permiso Bluetooth denegado"
                    txtUltimoDato.text = "Activa el permiso de Dispositivos cercanos"
                    btnConectar.isEnabled = true

                    Toast.makeText(this, "Falta permiso Bluetooth", Toast.LENGTH_LONG).show()
                }

            } catch (e: IOException) {
                conectado = false

                runOnUiThread {
                    txtEstado.text = "Error al conectar"
                    txtUltimoDato.text = e.message ?: "No se pudo conectar al HC-05"
                    btnConectar.isEnabled = true

                    Toast.makeText(
                        this,
                        "No se pudo conectar al HC-05",
                        Toast.LENGTH_LONG
                    ).show()
                }

                cerrarConexion()
            }
        }
    }

    private fun buscarDispositivoEmparejado(nombre: String): BluetoothDevice? {
        if (!tienePermisoBluetooth()) return null

        val dispositivosEmparejados = bluetoothAdapter?.bondedDevices ?: return null

        return dispositivosEmparejados.firstOrNull { dispositivo ->
            dispositivo.name.equals(nombre, ignoreCase = true)
        }
    }

    private fun escucharDatos() {
        val buffer = ByteArray(1024)

        while (conectado) {
            try {
                val bytesLeidos = inputStream?.read(buffer) ?: -1

                if (bytesLeidos > 0) {
                    val mensaje = String(buffer, 0, bytesLeidos).trim()

                    if (mensaje.isNotEmpty()) {
                        runOnUiThread {
                            mostrarMensajeRecibido(mensaje)
                        }
                    }
                }

            } catch (e: IOException) {
                conectado = false

                runOnUiThread {
                    txtEstado.text = "Conexión perdida"
                    txtUltimoDato.text = "Se desconectó el HC-05"
                    btnConectar.text = "Conectar al HC-05"
                    btnConectar.isEnabled = true
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

                btnConectar.text = "Conectar al HC-05"
                btnConectar.isEnabled = true

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
            Toast.makeText(
                this,
                "Usa formato internacional. Ej: +593991234567",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        agregarContactoALista("Contacto manual", telefonoLimpio)
        edtTelefono.text.clear()
    }

    private fun configurarSelectorContactos() {
        contactPickerLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
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
            Toast.makeText(
                this,
                "Acepta el permiso de contactos",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        val intent = Intent(
            Intent.ACTION_PICK,
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI
        )

        contactPickerLauncher.launch(intent)
    }

    private fun obtenerTelefonoDesdeContacto(uriContacto: Uri) {
        try {
            val cursor = contentResolver.query(
                uriContacto,
                arrayOf(
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                    ContactsContract.CommonDataKinds.Phone.NUMBER
                ),
                null,
                null,
                null
            )

            cursor?.use {
                if (it.moveToFirst()) {
                    val nombreIndex = it.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
                    )

                    val numeroIndex = it.getColumnIndex(
                        ContactsContract.CommonDataKinds.Phone.NUMBER
                    )

                    val nombre = if (nombreIndex >= 0) {
                        it.getString(nombreIndex)
                    } else {
                        "Contacto"
                    }

                    val numeroOriginal = if (numeroIndex >= 0) {
                        it.getString(numeroIndex)
                    } else {
                        ""
                    }

                    val numeroLimpio = limpiarNumeroTelefono(numeroOriginal)

                    if (numeroLimpio.isBlank()) {
                        Toast.makeText(
                            this,
                            "El contacto no tiene número válido",
                            Toast.LENGTH_SHORT
                        ).show()
                        return
                    }

                    agregarContactoALista(nombre, numeroLimpio)
                }
            }

        } catch (e: Exception) {
            Toast.makeText(
                this,
                "Error leyendo contacto: ${e.message}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun limpiarNumeroTelefono(numero: String): String {
        var limpio = numero
            .replace(" ", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace(".", "")

        // Convierte números locales de Ecuador:
        // 0991234567 -> +593991234567
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

        Toast.makeText(
            this,
            "$nombre agregado como contacto de emergencia",
            Toast.LENGTH_LONG
        ).show()
    }

    private fun guardarContactos() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val contactosTexto = contactosEmergencia.joinToString(",")

        prefs.edit()
            .putString(contactosKey, contactosTexto)
            .apply()
    }

    private fun cargarContactos() {
        val prefs = getSharedPreferences(prefsName, MODE_PRIVATE)
        val contactosTexto = prefs.getString(contactosKey, "") ?: ""

        contactosEmergencia.clear()

        if (contactosTexto.isNotBlank()) {
            contactosEmergencia.addAll(
                contactosTexto.split(",")
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
            )
        }
    }

    private fun actualizarListaContactos() {
        if (contactosEmergencia.isEmpty()) {
            txtListaContactos.text = "No hay contactos agregados."
            return
        }

        txtListaContactos.text = contactosEmergencia
            .mapIndexed { index, contacto -> "${index + 1}. $contacto" }
            .joinToString("\n")
    }

    private fun enviarAlertaEmergencia() {
        if (contactosEmergencia.isEmpty()) {
            txtUltimoDato.text = "No hay contactos para enviar SMS"
            Toast.makeText(
                this,
                "No hay contactos de emergencia agregados",
                Toast.LENGTH_LONG
            ).show()
            return
        }

        if (!tienePermisoSms() || !tienePermisoUbicacion()) {
            pedirPermisosNecesarios()
            txtUltimoDato.text = "Faltan permisos de SMS o ubicación"

            Toast.makeText(
                this,
                "Acepta permisos de SMS y ubicación",
                Toast.LENGTH_LONG
            ).show()
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

                Toast.makeText(
                    this,
                    "No se pudo obtener la ubicación",
                    Toast.LENGTH_LONG
                ).show()

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
                Toast.makeText(
                    this,
                    "Activa la ubicación/GPS del celular",
                    Toast.LENGTH_LONG
                ).show()
                callback(null)
                return
            }

            val ultimaGps = if (gpsActivo) {
                locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            } else {
                null
            }

            val ultimaRed = if (redActiva) {
                locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            } else {
                null
            }

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
                override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
                }

                override fun onProviderEnabled(provider: String) {
                }

                override fun onProviderDisabled(provider: String) {
                }
            }

            locationManager.requestSingleUpdate(
                proveedor,
                listener,
                Looper.getMainLooper()
            )

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
                    smsManager.sendMultipartTextMessage(
                        numero,
                        null,
                        partes,
                        null,
                        null
                    )
                } else {
                    smsManager.sendTextMessage(
                        numero,
                        null,
                        mensajeSms,
                        null,
                        null
                    )
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
        try {
            inputStream?.close()
        } catch (_: IOException) {
        }

        try {
            bluetoothSocket?.close()
        } catch (_: IOException) {
        }

        inputStream = null
        bluetoothSocket = null
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacksAndMessages(null)
        conectado = false
        cerrarConexion()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == requestPermisos) {
            if (grantResults.isEmpty()) {
                txtEstado.text = "Permisos no respondidos"
                return
            }

            val permisosDenegados = mutableListOf<String>()

            permissions.forEachIndexed { index, permiso ->
                val concedido = grantResults.getOrNull(index) == PackageManager.PERMISSION_GRANTED

                if (!concedido) {
                    permisosDenegados.add(permiso)
                }
            }

            if (permisosDenegados.isEmpty()) {
                txtEstado.text = "Permisos concedidos"
                txtUltimoDato.text = "Ya puedes conectar el HC-05"
            } else {
                txtEstado.text = "Faltan permisos"
                txtUltimoDato.text =
                    "Activa Dispositivos cercanos, Ubicación, SMS y Contactos"

                Toast.makeText(
                    this,
                    "Algunos permisos fueron denegados",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun limpiarDatoDespuesDeAccion() {
        handler.postDelayed({
            txtSenal.text = "-"
            txtUltimoDato.text = "Esperando señal..."
        }, tiempoMostrarDato)
    }
}