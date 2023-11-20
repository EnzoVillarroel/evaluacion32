package com.evillarroel.evaluacion3

import android.content.Context
import android.graphics.BitmapFactory
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCapture.OnImageSavedCallback
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.camera.core.ImageCaptureException
import androidx.camera.view.CameraController
import androidx.camera.view.LifecycleCameraController
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File
import java.time.LocalDateTime


enum class Pantalla {
    FORM,
    FOTO,
    MAPA
}

class CameraAppViewModel : ViewModel() {
    val pantalla = mutableStateOf(Pantalla.FORM)

    // callbacks
    var onPermisoCamaraOk: () -> Unit = { }
    var onPermisoUbicacionOk: () -> Unit = { }

    // lanzador permisos
    var lanzadorPermisos:ActivityResultLauncher<Array<String>>? = null

    fun cambiarPantallaFoto() { pantalla.value = Pantalla.FOTO }
    fun cambiarPantallaForm() { pantalla.value = Pantalla.FORM }
    fun cambiarPantallaMapa() { pantalla.value = Pantalla.MAPA }
}

class FormRecepcionViewModel : ViewModel() {
    val receptor = mutableStateOf("")
    val latitud = mutableStateOf(0.0)
    val longitud = mutableStateOf(0.0)
    val fotoRecepcion = mutableStateOf<Uri?>(null)
    val listaImagenes = mutableStateListOf<ImageItem>()
    val mostrarImagenCompleta = mutableStateOf(false)
    val imagenCompletaUri = mutableStateOf<Uri?>(null)
}

data class ImageItem(val imagePath: String, val thumbnailPath: String)

class MainActivity : ComponentActivity() {
    val cameraAppVm:CameraAppViewModel by viewModels()

    lateinit var cameraController:LifecycleCameraController

    val lanzadorPermisos =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            when {
                (it[android.Manifest.permission.ACCESS_FINE_LOCATION] ?: false) or
                        (it[android.Manifest.permission.ACCESS_COARSE_LOCATION] ?: false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso ubicacion granted")
                    cameraAppVm.onPermisoUbicacionOk()
                }
                (it[android.Manifest.permission.CAMERA] ?: false) -> {
                    Log.v("callback RequestMultiplePermissions", "permiso camara granted")
                    cameraAppVm.onPermisoCamaraOk()
                }
                else -> {

                }
            }
        }

    private fun setupCamara() {
        cameraController = LifecycleCameraController(this)
        cameraController.bindToLifecycle(this)
        cameraController.cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraAppVm.lanzadorPermisos = lanzadorPermisos
        setupCamara()

        setContent {
            AppUI(cameraController)
        }
    }
}

fun generarNombreSegunFechaHastaSegundo():String = LocalDateTime
    .now().toString().replace(Regex("[T:.-]"), "").substring(0, 14)

fun crearArchivoImagenPrivado(contexto:Context):File = File(
    contexto.getExternalFilesDir(Environment.DIRECTORY_PICTURES),
    "${generarNombreSegunFechaHastaSegundo()}.jpg"
)

fun uri2imageBitmap(uri:Uri, contexto: Context) =
    BitmapFactory.decodeStream(
        contexto.contentResolver.openInputStream(uri)
    ).asImageBitmap()

fun tomarFotografia(cameraController: CameraController, archivo:File, contexto: Context, imagenGuardadaOk:(uri:Uri) -> Unit) {
    val outputFileOptions = OutputFileOptions.Builder(archivo).build()
    cameraController.takePicture(outputFileOptions, ContextCompat.getMainExecutor(contexto), object:OnImageSavedCallback {
        override  fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
            outputFileResults.savedUri?.also {
                Log.v("tomarFotografia()::onImageSaved", "Foto guardada en ${it.toString()}")
                imagenGuardadaOk(it)
            }
        }

        override  fun onError(exception: ImageCaptureException) {
            Log.e("tomarFotografia()", "Error: ${exception.message}")
        }
    })
}

class SinPermisoException(mensaje:String) : Exception(mensaje)

fun getUbicacion(contexto: Context, onUbicacionOk:(location:Location) -> Unit) : Unit {
    try {
        val servicio =
            LocationServices.getFusedLocationProviderClient(contexto)
        val tarea =
            servicio.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
        tarea.addOnSuccessListener {
            onUbicacionOk(it)
        }
    } catch (e:SecurityException) {
        throw SinPermisoException(e.message?:"No tiene permisos para conseguir la ubicacion")
    }
}

@Composable
fun AppUI(cameraController: CameraController) {
    val contexto = LocalContext.current

    val formRecepcionVm:FormRecepcionViewModel = viewModel()
    val cameraAppViewModel:CameraAppViewModel = viewModel()

    when(cameraAppViewModel.pantalla.value) {
        Pantalla.FORM -> {
            PantallaFormUI (
                formRecepcionVm,
                tomarFotoOnCLick = {
                    cameraAppViewModel.cambiarPantallaFoto()

                    cameraAppViewModel.lanzadorPermisos?.launch(arrayOf(android.Manifest.permission.CAMERA))
                },
                actualizarUbicacionOnClick = {
                    cameraAppViewModel.onPermisoUbicacionOk = {
                        getUbicacion(contexto) {
                            formRecepcionVm.latitud.value = it.latitude
                            formRecepcionVm.longitud.value = it.longitude
                        }
                    }
                    cameraAppViewModel.lanzadorPermisos?.launch(arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION))
                }
            )
        }
        Pantalla.FOTO -> {
            PantallaFotoUI(formRecepcionVm, cameraAppViewModel, cameraController)
        }
        Pantalla.MAPA -> {
            PantallaMapaUI(formRecepcionVm.latitud.value, formRecepcionVm.longitud.value) {
                cameraAppViewModel.cambiarPantallaForm()
            }
        }
        else -> {
            Log.v("AppUI()", "when else, no deberia entrar aqui")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PantallaFormUI (
    formRecepcionVm: FormRecepcionViewModel,
    tomarFotoOnCLick: () -> Unit = { },
    actualizarUbicacionOnClick: () -> Unit = { }

) {
    val contexto = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp), // Ajusta el valor de padding según sea necesario
    ) {
        TextField(
            label = { Text("Lugar Visitado") },
            value = formRecepcionVm.receptor.value,
            onValueChange = { formRecepcionVm.receptor.value = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp)
        )
        Text("Fotografias lugares visitados:")
        formRecepcionVm.listaImagenes.forEach { imageItem ->
            // Muestra la imagen en pantalla completa al hacer clic en ella
            Box(
                modifier = Modifier.clickable {
                    formRecepcionVm.imagenCompletaUri.value = Uri.parse(imageItem.imagePath)
                    formRecepcionVm.mostrarImagenCompleta.value = true
                }
            ) {
                Image(
                    painter = BitmapPainter(uri2imageBitmap(Uri.parse(imageItem.thumbnailPath), contexto)),
                    contentDescription = "Thumbnail Image"
                )
            }
        }
        Button(onClick = {
            tomarFotoOnCLick()
        }) {
            Text("Tomar Fotografia")
        }

        Text("La ubicacion es: lat: ${formRecepcionVm.latitud.value} y long: ${formRecepcionVm.longitud.value}")
        Button(onClick = {
            actualizarUbicacionOnClick()
        }) {
            Text("Actualizar Ubicacion")
        }
        Spacer(Modifier.height(100.dp))
        MapaOsmUI(formRecepcionVm.latitud.value, formRecepcionVm.longitud.value)
    }
}


@Composable
fun PantallaFotoUI(formRecepcionVm: FormRecepcionViewModel, appViewModel: CameraAppViewModel, cameraController: CameraController) {
    val contexto = LocalContext.current

    AndroidView(
        factory = {
            PreviewView(it).apply {
                controller = cameraController
            }
        },
        modifier = Modifier.fillMaxSize()
    )
    Button(onClick = {
        tomarFotografia(
            cameraController,
            crearArchivoImagenPrivado(contexto),
            contexto
        ) {uri ->
            formRecepcionVm.fotoRecepcion.value = uri
            formRecepcionVm.listaImagenes.add(ImageItem(uri.toString(), uri.toString())) // Agregar a la lista
            appViewModel.cambiarPantallaForm()
        }
    }) {
        Text("Tomar Foto")
    }
}

@Composable
fun MapaOsmUI(latitud:Double, longitud:Double) {
    val contexto = LocalContext.current

    AndroidView(
        factory = {
            MapView(it).also {
                it.setTileSource(TileSourceFactory.MAPNIK)
                Configuration.getInstance().userAgentValue = contexto.packageName
            }
        },
        update = {
            it.overlays.removeIf { true }
            it.invalidate()

            it.controller.setZoom(18.0)

            val geoPoint = GeoPoint(latitud, longitud)
            it.controller.animateTo(geoPoint)

            val marcador = Marker(it)
            marcador.position = geoPoint
            marcador.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
            it.overlays.add(marcador)
        }
    )
}

@Composable
fun PantallaMapaUI(latitud: Double, longitud: Double, onVolverClick: () -> Unit) {
    MapaOsmUI(latitud, longitud)

    Button(onClick = onVolverClick) {
        Text("Volver al formulario")
    }
}