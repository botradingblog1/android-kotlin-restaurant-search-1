package com.mobiledeveloperblog.restaurantsearch1

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log.d
import android.util.Log.e
import android.view.LayoutInflater
import android.view.View
import android.view.View.INVISIBLE
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ZoomControls
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.util.CollectionUtils.listOf
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationResult
import com.google.android.gms.maps.*
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.*
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import com.google.maps.android.SphericalUtil
import com.mancj.materialsearchbar.MaterialSearchBar
import com.mancj.materialsearchbar.adapter.SuggestionsAdapter
import com.mobiledeveloperblog.restaurantsearch1.location.GeoLocationManager


class FirstFragment : Fragment(), OnMapReadyCallback, MaterialSearchBar.OnSearchActionListener, SuggestionsAdapter.OnItemViewClickListener {
    private val TAG = "FirstFragment"
    private lateinit var searchBar: MaterialSearchBar
    private lateinit var map: GoogleMap
    private lateinit var mapView: MapView
    private lateinit var locationManager: GeoLocationManager
    private lateinit var placesClient: PlacesClient
    private lateinit var predictionsList: List<AutocompletePrediction>
    private lateinit var  zoomControls:  ZoomControls
    private val autoCompleteSessionToken = AutocompleteSessionToken.newInstance()
    private val MAP_ZOOM = 18F

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        super.onCreateView(inflater, container, savedInstanceState)
        val view: View = inflater.inflate(R.layout.fragment_first, container, false)

        // Create GeoLocationManager
        locationManager = GeoLocationManager(activity as Context)

        // Gets the MapView from the XML layout and creates it
        mapView = view.findViewById(R.id.mapview)
        mapView.onCreate(savedInstanceState)
        mapView.visibility = INVISIBLE

        try {
            MapsInitializer.initialize(this.activity)
        } catch (e: GooglePlayServicesNotAvailableException) {
            e.printStackTrace()
        }

        // Initialize Places client
        val apiKey: String = BuildConfig.GOOGLE_MAPS_API_KEY
        if (!Places.isInitialized()) {
            Places.initialize(requireActivity().applicationContext, apiKey)
        }
        placesClient = Places.createClient(activity as Context)

        // Inflate the layout for this fragment
        return view
    }

    private fun buildRectangleBounds(from: LatLng, distance: Double): RectangularBounds {
        val southWest = SphericalUtil.computeOffset(from, distance, 225.0)
        val northEast = SphericalUtil.computeOffset(from, distance, 45.0)

        return RectangularBounds.newInstance(southWest, northEast)
    }

    private fun fetchPlacesPredictions(searchString: String) {
        if (latLng == null) {
            return
        }

        // Create search bounds based on current location
        val distance = 10000.0 // in meters
        val bounds = buildRectangleBounds(latLng, distance)

        var predictionsRequest = FindAutocompletePredictionsRequest.builder()
            .setCountry("us")
            .setLocationBias(bounds)
            .setOrigin(latLng)
            .setSessionToken(autoCompleteSessionToken)
            .setTypeFilter(TypeFilter.ESTABLISHMENT)
            .setQuery(searchString)
            .build()

        placesClient.findAutocompletePredictions(predictionsRequest).addOnSuccessListener {
                    // Get response
                    val response = it
                    if (response != null) {
                        predictionsList = response.autocompletePredictions
                        val suggestionList = ArrayList<String>()
                        for (i in 1 until predictionsList.size) {
                            val prediction = predictionsList[i]

                            // Filter predictions by restaurant
                            if (prediction.placeTypes.contains(Place.Type.RESTAURANT)) {
                                suggestionList.add(prediction.getPrimaryText(null).toString())
                            }
                        }

                        // Update search bar suggestions
                        searchBar.lastSuggestions = suggestionList

                        if (!searchBar.isSuggestionsVisible) {
                            searchBar.showSuggestionsList()
                        }
                    }
                }
                .addOnFailureListener {
                    e("tag", "Google places prediction request not successful. " + it.localizedMessage)
                    showAlert("Google places search not successful")
                }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Configure search bar
        searchBar = view?.findViewById(R.id.searchbar_keyword)
        searchBar.setSpeechMode(true);
        searchBar.setOnSearchActionListener(this)
        searchBar.setSuggestionsClickListener(this)
        searchBar.addTextChangeListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
            }

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                // Start searching after 3 character input
                if (s.toString().length < 3) {
                    return
                }

                // Start Google Places search
                fetchPlacesPredictions(s.toString())
            }
        })

        mapView = view?.findViewById(R.id.mapview)
        mapView.onCreate(savedInstanceState)
        mapView.onResume()
        mapView.getMapAsync(this)

        val permissionGranted = requestLocationPermission();
        if (permissionGranted) {
            locationManager.startLocationTracking(locationCallback)
            locationTrackingRequested = true
        }
    }


    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap

        // Configure map
        map.uiSettings.isZoomControlsEnabled = true
        map.uiSettings.isScrollGesturesEnabled = true
    }

    private var locationTrackingRequested: Boolean = false
    private val LOCATION_PERMISSION_CODE = 1000
    private fun requestLocationPermission(): Boolean {
        var permissionGranted = false

        // If system os is Marshmallow or Above, we need to request runtime permission
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M){
            val cameraPermissionNotGranted = ContextCompat.checkSelfPermission(
                    activity as Context,
                    Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_DENIED
            if (cameraPermissionNotGranted){
                val permission = arrayOf(
                        Manifest.permission.ACCESS_COARSE_LOCATION,
                        Manifest.permission.ACCESS_FINE_LOCATION
                )

                // Display permission dialog
                requestPermissions(permission, LOCATION_PERMISSION_CODE)
            }
            else{
                // Permission already granted
                permissionGranted = true
            }
        }
        else{
            // Android version earlier than M -&gt; no need to request permission
            permissionGranted = true
        }

        return permissionGranted
    }

    override fun onRequestPermissionsResult(
            requestCode: Int,
            permissions: Array<String>,
            grantResults: IntArray
    ) {
        if (requestCode === LOCATION_PERMISSION_CODE) {
            if (grantResults.size === 2 &&
                grantResults[0] == PackageManager.PERMISSION_GRANTED &&
                grantResults[1] == PackageManager.PERMISSION_GRANTED){
                // Permission was granted
                locationManager.startLocationTracking(locationCallback)
            }
            else{
                // Permission was denied
                showAlert("Location permission was denied. Unable to track location.")
            }
        }
    }

    override fun onPause() {
        super.onPause()
        locationManager.stopLocationTracking()
    }

    override fun onResume() {
        super.onResume()

        if  (locationTrackingRequested) {
            locationManager.startLocationTracking(locationCallback)
        }
    }

    private lateinit var latLng: LatLng
    private var currentLocationSet = false
    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(locationResult: LocationResult?) {
            locationResult ?: return
            for (location in locationResult.locations){
                val latitude = location.latitude
                val longitude = location.longitude
                latLng = LatLng(latitude, longitude)
                d(TAG, "location: lat: $latitude, lon: $longitude")

                // Move map to location
                if (map != null && !currentLocationSet) {
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(latLng, MAP_ZOOM))
                    mapView.visibility = VISIBLE
                    currentLocationSet = true
                }
            }
        }
    }

    private fun showAlert(message: String) {
        val builder = AlertDialog.Builder(activity as Context)
        builder.setMessage(message)
        builder.setTitle("Alert")

        val dialog = builder.create()
        dialog.show()
    }

    // Material design interface implementation
    override fun onSearchStateChanged(enabled: Boolean) {
        d(TAG, "onSearchStateChanged")
    }

    override fun onSearchConfirmed(text: CharSequence?) {
        d(TAG, "onSearchConfirmed")
        if (searchBar.isSuggestionsVisible) {
            searchBar.hideSuggestionsList()
        }
    }

    override fun onButtonClicked(buttonCode: Int) {
        d(TAG, "onButtonClicked")
        if (buttonCode == MaterialSearchBar.BUTTON_NAVIGATION) {
            // Control navigation drawer
        }
        else if (buttonCode == MaterialSearchBar.BUTTON_BACK) {
            searchBar.closeSearch()
        }
    }

    private fun fetchPlaceDetails(placeId: String) {
        val placeFields = listOf(Place.Field.LAT_LNG, Place.Field.NAME)

        val fetchPlaceRequest = FetchPlaceRequest.builder(placeId, placeFields).build()
        placesClient.fetchPlace(fetchPlaceRequest).addOnSuccessListener {
            val place = it.place
            if (place != null) {
                val placeLatLng = place.latLng
                if (placeLatLng != null) {
                    // Move map to position
                    map.moveCamera(CameraUpdateFactory.newLatLngZoom(placeLatLng, MAP_ZOOM))
                }
            }
            // Hide suggestions
            searchBar.hideSuggestionsList()

        }.addOnFailureListener {
            if (it is ApiException) {
                val apiException = it
                val statusCode = apiException.statusCode
                e(TAG, "Place not found, statusCode: $statusCode")
            }
        }
    }

    // Search bar suggestion click handler
    override fun OnItemClickListener(position: Int, v: View?) {
        // Get selected prediction
        if (position < predictionsList.size - 1) {
            val selectedPrediction = predictionsList[position]
            // Get the details for this place
            fetchPlaceDetails(selectedPrediction.placeId)
        }
    }

    override fun OnItemDeleteListener(position: Int, v: View?) {
        TODO("Not yet implemented")
    }
}


