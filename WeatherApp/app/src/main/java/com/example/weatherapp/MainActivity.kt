package com.example.weatherapp

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.location.Address
import android.location.Geocoder
import android.location.LocationManager
import android.location.LocationRequest
import android.media.MediaParser.create
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.os.Environment
import android.telephony.MbmsGroupCallSession.create
import android.text.Editable
import android.util.Log
import android.view.LayoutInflater
import android.view.MenuItem
import android.view.View
import android.view.animation.CycleInterpolator
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.app.ActivityCompat.requestPermissions
import androidx.core.content.ContextCompat
import com.example.weatherapp.databinding.ActivityMainBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationRequest.create
import com.google.android.gms.location.LocationServices
import com.google.android.material.badge.BadgeDrawable.create
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.lang.Exception
import java.lang.ref.WeakReference
import java.net.URI.create
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import java.util.jar.Manifest
import kotlin.coroutines.CoroutineContext

private lateinit var binding: ActivityMainBinding

class MainActivity : AppCompatActivity() {
    var city: String = "New York,US"
    var tempUni: String = "imperial"
    var tempShowUnit = "°F"
    var lang: String = "en"
    val apiKey: String = "673ea3053974d87ac69ae9d19c3ea19e"

    var workingCity: String = "London,UK"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.loader.visibility = View.VISIBLE
        binding.mainContainer.visibility = View.GONE

        m_LocationProvider = LocationServices.getFusedLocationProviderClient(this)

        //Retrieve presets from last usage
        if(!loadSettings()) {
            getLastLocation()
        }

        weatherRequest()

        binding.searchButton.setOnClickListener {
            cityDialog()
        }

        binding.settingsBtn.setOnClickListener {
            settingsPopup(binding.settingsBtn)
        }

        binding.btnMisc.setOnClickListener {
            handleLocationStatus()
        }
    }

    private fun loadSettings(): Boolean {
        val file = File(this.filesDir, "presets.txt")
        lateinit var line: String
        var priorCity: Boolean = false //If there was a city already loaded into the program, don't want to get the user's location

        if(file.canRead()) { //File already exist
            try {
                line = file.readText()
                val list: List<String> = line.split("~")
                city = list[0]
                tempUni = list[1]
                tempShowUnit = list[2]
                lang = list[3]
                priorCity = true
            }catch (e: Exception) {
                println("Error 402: Failed reading file settings: ${e.message}")
            }
        }else {
            saveSettings()
        }

        if(locationSaved(binding.address.text.toString())) {
            m_CitySaved = true
            binding.ivMisc.setImageResource(R.drawable.remove)
        }else {
            m_CitySaved = false
            binding.ivMisc.setImageResource(R.drawable.save)
        }

        return priorCity
    }

    private fun saveSettings() {
        try {
            this.openFileOutput("presets.txt", Context.MODE_PRIVATE).use {
                it.write("$city~$tempUni~$tempShowUnit~$lang".toByteArray())
            }
        }catch(e: Exception) {
            println("Error 400: Failed saving presets: ${e.message}")
        }
    }

    private fun handleLocationStatus() {
        CoroutineScope(IO).launch {
            val file = File(this@MainActivity.filesDir, "locations.txt")
            lateinit var line: String

            if(file.canRead()) {
                try {
                    if(m_CitySaved) {
                        val tempFile = File(this@MainActivity.filesDir, "tempLocations.txt")

                        file.readLines().forEach {
                            if(it != city) {
                                tempFile.appendText("$it\n")
                            }
                        }

                        file.delete()
                        tempFile.renameTo(file)
                        m_CitySaved = false

                        withContext(Main) {
                            binding.ivMisc.setImageResource(R.drawable.save)
                            val translatedToast = when(lang) {
                                "en" -> "$city has been removed"
                                "es" -> "$city ha sido eliminado"
                                "fr" -> "$city a été supprimé"
                                else -> "Unknown Language"
                            }

                            Toast.makeText(this@MainActivity, translatedToast, Toast.LENGTH_SHORT).show()
                        }
                    }else {
                        file.appendText("${binding.address.text}\n")
                        m_CitySaved = true

                        withContext(Main) {
                            binding.ivMisc.setImageResource(R.drawable.remove)
                            val translatedToast = when(lang) {
                                "en" -> "$city has been saved"
                                "es" -> "$city Ha sido salvado"
                                "fr" -> "$city a été sauvé"
                                else -> "Unknown Language"
                            }

                            Toast.makeText(this@MainActivity, translatedToast, Toast.LENGTH_SHORT).show()
                        }
                    }
                }catch(e: Exception) {
                    println("Error 403: Failed reading location file: ${e.message}")
                }
            }else {
                file.appendText("${binding.address.text}\n")
                m_CitySaved = true

                withContext(Main) {
                    binding.ivMisc.setImageResource(R.drawable.remove)
                    val translatedToast = when(lang) {
                        "en" -> "$city has been saved"
                        "es" -> "$city Ha sido salvado"
                        "fr" -> "$city a été sauvé"
                        else -> "Unknown Language"
                    }

                    Toast.makeText(this@MainActivity, translatedToast, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun locationSaved(citySearching: String) : Boolean {
        val file = File(this.filesDir, "locations.txt")

        if(file.canRead()) {
            file.readLines().forEach {
                if(it == citySearching) {
                    return true
                }
            }
            return false
        }else {
            return false
        }
    }

    private fun cityDialog() {
        val translations: MutableList<String> = getWeatherTranslations(2)
        val dialogView = LayoutInflater.from(this).inflate(R.layout.layout_citysrch, null)
        val builder = AlertDialog.Builder(this).setView(dialogView).setTitle(translations[4])
        val alertDialog = builder.show()

        //Translations
        dialogView.findViewById<EditText>(R.id.etCity).hint = translations[0]
        dialogView.findViewById<EditText>(R.id.etCountry).hint = translations[1]
        dialogView.findViewById<Button>(R.id.btnSearch).text = translations[2]
        dialogView.findViewById<Button>(R.id.btnCancel).text = translations[3]
        dialogView.findViewById<Button>(R.id.btnCurrent).text = translations[5]

        dialogView.findViewById<Button>(R.id.btnSearch).setOnClickListener {
            val srchCity: String = dialogView.findViewById<EditText>(R.id.etCity).text.toString()
            val srchCountry: String = dialogView.findViewById<EditText>(R.id.etCountry).text.toString()

            if(srchCity.isEmpty() || srchCountry.isEmpty()) {
                alertDialog.window?.decorView?.animate()?.translationX(16f)?.setInterpolator(CycleInterpolator(7f))
            }else {
                alertDialog.dismiss()
                city = "$srchCity, $srchCountry"
                weatherRequest()
            }
        }

        dialogView.findViewById<Button>(R.id.btnCurrent).setOnClickListener {
            alertDialog.dismiss()
            getLastLocation()
        }

        dialogView.findViewById<Button>(R.id.btnCancel).setOnClickListener {
            alertDialog.dismiss()
        }
    }

    private fun weatherRequest() {
        binding.loader.visibility = View.VISIBLE
        binding.mainContainer.visibility = View.GONE
        binding.errortext.visibility = View.GONE

        CoroutineScope(IO).launch {
            weatherAPIRequest()
        }
    }

    private suspend fun weatherAPIRequest() {
        var response: String?
        try {
            response = URL("https://api.openweathermap.org/data/2.5/weather?q=$city&units=$tempUni&appid=$apiKey&lang=$lang").readText(Charsets.UTF_8)
        }catch (e: Exception) {
            println("Error 401: " + e.message)
            response = null
        }

        withContext(Main) {
            setWeatherInformation(response)
        }
    }

    private fun setWeatherInformation(jsonStr: String?) {
        if(jsonStr == null) {
            var translations: MutableList<String> = getWeatherTranslations(3)
            MaterialAlertDialogBuilder(this).setTitle(translations[0]).setMessage(translations[1]).setNeutralButton(translations[2], null).show()
            city = workingCity
            weatherRequest()
        }else {
            try {
                val langList: MutableList<String> = getWeatherTranslations(0)

                val jsonObj = JSONObject(jsonStr)
                val main = jsonObj.getJSONObject("main")
                val sys = jsonObj.getJSONObject("sys")
                val wind = jsonObj.getJSONObject("wind")
                val weather = jsonObj.getJSONArray("weather").getJSONObject(0)
                val lastUpdated = jsonObj.getLong("dt")
                val updatedAtText = langList[0] + " " + SimpleDateFormat("MM/dd/yyyy hh:mm a", Locale.ENGLISH).format(Date(lastUpdated * 1000))
                val temp = main.getString("temp") + tempShowUnit
                val tempMin = "Min Temp " + main.getString("temp_min") + tempShowUnit
                val tempMax = "Max Temp " + main.getString("temp_max") + tempShowUnit
                val pressure = main.getString("pressure")
                val humidity = main.getString("humidity")
                val sunrise: Long = sys.getLong("sunrise")
                val sunset: Long = sys.getLong("sunset")
                val windSpeed = wind.getString("speed")
                val weatherDescription = weather.getString("description")
                val weatherID: Int = weather.getString("id").toInt()
                val address = jsonObj.getString("name") + ", " + sys.getString("country")

                //For translating the labels to the current language
                binding.sunriseLabel.text = langList[1]
                binding.sunsetLabel.text = langList[2]
                binding.windLabel.text = langList[3]
                binding.pressureLabel.text = langList[4]
                binding.humidityLabel.text = langList[5]
                binding.searchButton.text = langList[6]

                //Set the information to the textviews
                setWeatherImage(weatherID)
                binding.address.text = address
                binding.updatedAt.text = updatedAtText
                binding.temp.text = temp
                binding.tempMin.text = tempMin
                binding.tempMax.text = tempMax
                binding.pressure.text = pressure
                binding.humidity.text = humidity
                binding.sunrise.text = SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(sunrise * 1000))
                binding.sunset.text =  SimpleDateFormat("hh:mm a", Locale.ENGLISH).format(Date(sunset * 1000))
                binding.wind.text = windSpeed
                binding.status.text = weatherDescription.capitalize()

                binding.loader.visibility = View.GONE
                binding.mainContainer.visibility = View.VISIBLE
                workingCity = city
                saveSettings()

                if(locationSaved(address)) {
                    m_CitySaved = true
                    binding.ivMisc.setImageResource(R.drawable.remove)
                }else {
                    m_CitySaved = false
                    binding.ivMisc.setImageResource(R.drawable.save)
                }
            }catch(e: Exception) {
                binding.loader.visibility = View.GONE
                binding.errortext.visibility = View.VISIBLE
                println("Error 404: " + e.message)
                city = workingCity
                weatherRequest()
            }
        }
    }

    private fun getWeatherTranslations(section: Int) : MutableList<String> {
        //0: Weather translations 1: Popup menu translations 2: Search city dialog translations 3: Invalid location alert dialog

        lateinit var list: MutableList<String>
        if(section == 0) {
            list = when (lang) {
                "en" -> {
                    mutableListOf("Updated", "Sunrise", "Sunset", "Wind", "Pressure", "Humidity", "Search City")
                }
                "es" -> {
                    mutableListOf("Hasta", "Amanecer", "Puesta del sol", "Viento", "Presión", "Humedad", "Buscar Ciudad")
                }
                else -> {
                    mutableListOf("À partir de", "Lever du soleil", "coucher de soleil", "Vent", "Pression", "Humidité", "Rechercher une ville")
                }
            }
        }else if(section == 1) {
            list = when(lang) {
                "en" -> {
                    mutableListOf("Language", "English", "French", "Spanish", "Temperature", "Fahrenheit", "Celsius", "Kelvin", "Saved")
                }
                "es" -> {
                    mutableListOf("Idioma", "Inglés", "Francés", "Español", "Temperatura", "Fahrenheit", "Celsius", "Kelvin", "Guardado")
                }
                else -> {
                    mutableListOf("Langue", "Anglais", "Français", "Espagnol", "Température", "Fahrenheit", "Celsius", "Kelvin", "Sauvé")
                }
            }
        }else if(section == 2) {
            list = when(lang) {
                "en" -> {
                    mutableListOf("City", "Country", "Search", "Cancel", "Search City", "Current")
                }
                "es" -> {
                    mutableListOf("Ciudad", "País", "Buscar", "Cancelar", "Buscar Ciudad", "Actual")
                }
                else -> {
                    mutableListOf("Ville", "Pays", "Rechercher", "Annuler", "Rechercher Une Ville", "Courant")
                }
            }
        }else if(section == 3) {
            list = when(lang) {
                "en" -> {
                    mutableListOf("Invalid Location", "The location given was not able to be found", "Ok")
                }
                "es" -> {
                    mutableListOf("Ubicación no válida", "La ubicación dada no se pudo encontrar", "Ok")
                }
                else -> {
                    mutableListOf("Emplacement non valide", "L'emplacement indiqué n'a pas pu être trouvé", "D'accord")
                }
            }
        }

        return list
    }

    private fun setWeatherImage(code: Int) {
        val weatherImage: ImageView = binding.weatherImage
        if(code == 800) { //Clear is the only ID in conflict with another (Cloudy 8xx)
            weatherImage.setImageResource(R.drawable.clear)
            return
        }

        when(code / 100) {
            2 -> weatherImage.setImageResource(R.drawable.thunder)
            3 -> weatherImage.setImageResource(R.drawable.rain)
            5 -> weatherImage.setImageResource(R.drawable.rain)
            6 -> weatherImage.setImageResource(R.drawable.snow)
            7 -> weatherImage.setImageResource(R.drawable.mist)
            8 -> weatherImage.setImageResource(R.drawable.cloudy)
        }
    }

    private fun settingsPopup(view: View) {
        var popupMenu: PopupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.settings_menu, popupMenu.menu)

        val translations: MutableList<String> = getWeatherTranslations(1)
        popupMenu.menu.getItem(0).title = translations[0]
        popupMenu.menu.getItem(0).subMenu.getItem(0).title = translations[1]
        popupMenu.menu.getItem(0).subMenu.getItem(1).title = translations[2]
        popupMenu.menu.getItem(0).subMenu.getItem(2).title = translations[3]
        popupMenu.menu.getItem(1).title = translations[4]
        popupMenu.menu.getItem(1).subMenu.getItem(0).title = translations[5]
        popupMenu.menu.getItem(1).subMenu.getItem(1).title = translations[6]
        popupMenu.menu.getItem(1).subMenu.getItem(2).title = translations[7]
        popupMenu.menu.getItem(2).title = translations[8]

        //Put all the saved locations as options under the saved submenu
        val locationFile = File(this.filesDir, "locations.txt")

        if(locationFile.canRead()) {
            try {
                locationFile.readLines().forEach {
                    popupMenu.menu.getItem(2).subMenu.add(0, 0, 0, it)
                }
            }catch(e: Exception) {
                println("Error 405: Failed reading each city from location file: ${e.message}")
            }
        }

        popupMenu.setOnMenuItemClickListener { item ->
            var refresh: Boolean = true

            println("Item ID: ${item.itemId.toString()}")

            when(item.title) {
                translations[1] -> lang = "en"
                translations[2] -> lang = "fr"
                translations[3] -> lang = "es"
                translations[5] -> {
                    tempUni = "imperial"
                    tempShowUnit = "°F"
                }
                translations[6] -> {
                    tempUni = "metric"
                    tempShowUnit = "°C"
                }
                translations[7] -> {
                    tempUni = "standard"
                    tempShowUnit = "K"
                }
                else -> {
                    //If this is true that means the item clicked was a saved city (dynamically added to not have set ID)
                    if(item.itemId == 0) {
                        city = item.title.toString()
                    }else {
                        refresh = false
                    }
                }
            }

            if(refresh) {
                weatherRequest()
            }
            true
        }

        popupMenu.show()
    }

    private fun checkPermission() : Boolean {
        return ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestPermission() {
        ActivityCompat.requestPermissions(this, arrayOf(android.Manifest.permission.ACCESS_FINE_LOCATION, android.Manifest.permission.ACCESS_COARSE_LOCATION), PERMISSION_ID)
        getLastLocation() //Reattempt to get the location incase permission was granted
    }

    private fun isLocationEnabled(): Boolean {
        var locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) || locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    /*override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        //TODO: Don't need this method but going to keep it here for learning purposes
        if(requestCode == PERMISSION_ID && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            println("You have location permission")
        }
    }*/

    private fun getLastLocation() {
        //In case I don't get permission to get the user's current location, I will just use the default New York one
        if(checkPermission()) {
            if(isLocationEnabled()) {
                m_LocationProvider.lastLocation.addOnCompleteListener {task ->
                    var location = task.result
                    if(location == null) {
                        getNewLocation()
                    }else {
                        var geoCoder = Geocoder(this, Locale.getDefault())
                        var address = geoCoder.getFromLocation(location.latitude, location.longitude, 1)
                        var cityName = address.get(0).locality //City Name
                        var countryCode = address.get(0).countryCode
                        city = "$cityName, $countryCode"
                        weatherRequest() //Have to call it here as by the time the user grants permission, the inital weatherRequest would have already been fulfilled
                    }
                }
            }
        }else {
            requestPermission()
        }
    }

    private fun getNewLocation() {
        //TODO: LocationRequest just does not give me anything for some odd reason
        //val locationRequest = LocationRequest.create()
    }

    var m_WorkingExample: Boolean = false //If this is false, that means the location is invalid, revert to a working example
    var m_CitySaved: Boolean = false //If the current city is in the save file have to change the misc button to reflect that

    var PERMISSION_ID = 10
    lateinit var m_LocationProvider: FusedLocationProviderClient
    lateinit var m_LocationRequester: LocationRequest
}