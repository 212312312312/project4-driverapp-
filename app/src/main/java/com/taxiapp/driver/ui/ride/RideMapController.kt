package com.taxiapp.driver.ui.ride

import android.animation.ValueAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.location.Location
import android.view.View
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.LinearInterpolator
import android.widget.Button
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.JointType
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MapStyleOptions
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.Polyline
import com.google.android.gms.maps.model.PolylineOptions
import com.google.android.gms.maps.model.RoundCap
import com.google.maps.android.PolyUtil
import com.taxiapp.driver.R
import com.taxiapp.driver.network.ApiClient
import com.taxiapp.driver.network.Order
import com.taxiapp.driver.utils.MapMarkerBitmapFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

/**
 * Контролер карти, плавної інтерполяції руху/повороту маркера авто,
 * динамічного відсікання пройденої полілінії маршруту та перевірки 300-метрової геозони
 * для екрану OrderProgressActivity.
 */
class RideMapController(
    private val context: Context,
    private val coroutineScope: CoroutineScope
) {
    private var map: GoogleMap? = null
    private var roadPolyline: Polyline? = null
    private var cachedDecodedRoute: List<LatLng>? = null
    private var lastClosestRouteIndex = 0
    private var lastProcessedOrderId: String? = null

    private var driverMarker: Marker? = null
    private var driverPositionAnimator: ValueAnimator? = null
    private var driverRotationAnimator: ValueAnimator? = null
    private var previousDriverLocation: Location? = null

    val isInitialized: Boolean
        get() = map != null

    fun attachMap(googleMap: GoogleMap) {
        map = googleMap
        googleMap.uiSettings.apply {
            isZoomControlsEnabled = false
            isCompassEnabled = false
            isMyLocationButtonEnabled = false
            isMapToolbarEnabled = false
            isIndoorLevelPickerEnabled = false
        }

        try {
            val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val styleRes = if (currentNightMode == Configuration.UI_MODE_NIGHT_YES) {
                R.raw.map_style_dark
            } else {
                R.raw.map_style_standard
            }
            googleMap.setMapStyle(MapStyleOptions.loadRawResourceStyle(context, styleRes))
        } catch (_: Exception) {}
    }

    fun updateDriverMarker(
        location: Location,
        order: Order?,
        isToDestination: Boolean
    ) {
        val googleMap = map ?: return
        val newLatLng = LatLng(location.latitude, location.longitude)
        val driverIcon = MapMarkerBitmapFactory.getVectorDescriptor(context, R.drawable.ic_driver_icon)

        val targetRotation = if (location.hasBearing()) {
            location.bearing
        } else if (previousDriverLocation != null && previousDriverLocation!!.distanceTo(location) > 1.5f) {
            previousDriverLocation!!.bearingTo(location)
        } else {
            driverMarker?.rotation ?: 0f
        }

        if (driverMarker == null) {
            driverMarker = googleMap.addMarker(
                MarkerOptions()
                    .position(newLatLng)
                    .title("Ви")
                    .anchor(0.5f, 0.5f)
                    .flat(true)
                    .icon(driverIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_AZURE))
            )
            driverMarker?.rotation = targetRotation
            previousDriverLocation = location
            return
        }

        driverPositionAnimator?.cancel()
        driverRotationAnimator?.cancel()

        val startLatLng = driverMarker?.position ?: newLatLng
        val startRotation = driverMarker?.rotation ?: 0f

        driverPositionAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1100
            interpolator = LinearInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                driverMarker?.position = interpolateLatLng(fraction, startLatLng, newLatLng)
            }
            start()
        }

        driverRotationAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 600
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { animation ->
                val fraction = animation.animatedValue as Float
                driverMarker?.rotation = interpolateRotation(fraction, startRotation, targetRotation)
            }
            start()
        }

        if (isToDestination && order != null) {
            cachedDecodedRoute?.let { points ->
                val targetLatLng = resolveActiveTargetLatLng(order)
                val driverLatLng = LatLng(location.latitude, location.longitude)
                val remaining = getCurrentLegRoutePoints(driverLatLng, targetLatLng, points)
                roadPolyline?.points = remaining
            }
        }

        previousDriverLocation = location
    }

    fun renderOrderRouteOnMap(
        order: Order,
        driverLocation: Location?,
        isPickupPhase: Boolean,
        isToDestination: Boolean
    ) {
        val googleMap = map ?: return
        googleMap.clear()
        driverMarker = null

        val effectiveLoc = driverLocation ?: Location("").apply {
            latitude = 50.45
            longitude = 30.52
        }
        val driverLoc = LatLng(effectiveLoc.latitude, effectiveLoc.longitude)
        updateDriverMarker(effectiveLoc, order, isToDestination)

        val clientIcon = MapMarkerBitmapFactory.getVectorDescriptor(
            context,
            R.drawable.ic_client_icon,
            ContextCompat.getColor(context, R.color.driver_text_primary),
            widthDp = 42,
            heightDp = 42
        )
        val waypointIcon = MapMarkerBitmapFactory.getVectorDescriptor(context, R.drawable.ic_marker_waypoint)
        val destIcon = MapMarkerBitmapFactory.getVectorDescriptor(context, R.drawable.ic_marker_to)

        if (isPickupPhase) {
            val originLoc = LatLng(order.originLat ?: 0.0, order.originLng ?: 0.0)
            googleMap.addMarker(
                MarkerOptions()
                    .position(originLoc)
                    .title("Клієнт")
                    .anchor(0.5f, 0.5f)
                    .icon(clientIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
            )
            drawRoadRoute(order, driverLoc, originLoc, R.color.driver_neon_teal)
        } else if (isToDestination) {
            val builder = LatLngBounds.Builder().include(driverLoc)

            if (lastProcessedOrderId != order.id) {
                lastProcessedOrderId = order.id
                cachedDecodedRoute = null
                lastClosestRouteIndex = 0
                roadPolyline?.remove()
                roadPolyline = null
            }

            val finalTarget = resolveActiveTargetLatLng(order)
            val isWaypointTarget = order.hasRemainingWaypoints() &&
                order.stops?.any { it.stopOrder == (order.currentStopOrder + 1) } == true

            val targetTitle = if (isWaypointTarget) "Проміжна точка" else "Фініш"
            val targetIconBitmap = if (isWaypointTarget) {
                waypointIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)
            } else {
                destIcon ?: BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
            }

            if (!order.polyline.isNullOrEmpty()) {
                if (cachedDecodedRoute == null) {
                    cachedDecodedRoute = PolyUtil.decode(order.polyline)
                    lastClosestRouteIndex = 0
                }

                cachedDecodedRoute?.let { points ->
                    val remainingPoints = getCurrentLegRoutePoints(driverLoc, finalTarget, points)
                    roadPolyline?.remove()

                    if (remainingPoints.size >= 2) {
                        roadPolyline = googleMap.addPolyline(
                            PolylineOptions()
                                .addAll(remainingPoints)
                                .width(14f)
                                .color(ContextCompat.getColor(context, R.color.driver_neon_teal))
                                .jointType(JointType.ROUND)
                                .endCap(RoundCap())
                        )
                        remainingPoints.forEach { builder.include(it) }
                    }
                }
            }

            googleMap.addMarker(
                MarkerOptions()
                    .position(finalTarget)
                    .title(targetTitle)
                    .anchor(0.5f, 0.5f)
                    .icon(targetIconBitmap)
            )
            builder.include(finalTarget)

            try {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 200))
            } catch (_: Exception) {
                googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(driverLoc, 15f))
            }
        }
    }

    fun updateProximityActionButton(
        driverLoc: Location,
        order: Order,
        isToClient: Boolean,
        isToDestination: Boolean,
        btnSaveAction: Button,
        btnContainerLayout: View
    ) {
        val targetLat: Double
        val targetLng: Double
        val targetText: String

        if (isToClient) {
            targetLat = order.originLat ?: 0.0
            targetLng = order.originLng ?: 0.0
            targetText = "НА МІСЦІ"
        } else if (isToDestination) {
            if (order.hasRemainingWaypoints()) {
                val currentWaypoint = order.stops?.sortedBy { it.stopOrder }
                    ?.find { it.stopOrder == (order.currentStopOrder + 1) }
                targetLat = currentWaypoint?.lat ?: 0.0
                targetLng = currentWaypoint?.lng ?: 0.0
                targetText = "НА МІСЦІ (ТОЧКА)"
            } else {
                targetLat = order.destLat ?: 0.0
                targetLng = order.destLng ?: 0.0
                targetText = "ЗАВЕРШИТИ"
            }
        } else {
            return
        }

        if (targetLat == 0.0 || targetLng == 0.0) return

        val targetLoc = Location("target").apply {
            latitude = targetLat
            longitude = targetLng
        }
        val distance = driverLoc.distanceTo(targetLoc)

        if (distance <= 300f) {
            btnSaveAction.isEnabled = true
            btnSaveAction.text = targetText
            btnSaveAction.setTextColor(ContextCompat.getColor(context, R.color.driver_text_black))
            btnContainerLayout.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.driver_neon_teal))
        } else {
            btnSaveAction.isEnabled = false
            btnSaveAction.text = "Ще їхати (${distance.toInt()} м)"
            btnSaveAction.setTextColor(Color.GRAY)
            btnContainerLayout.backgroundTintList =
                ColorStateList.valueOf(ContextCompat.getColor(context, R.color.driver_black_bg))
        }
    }

    fun release() {
        driverPositionAnimator?.cancel()
        driverRotationAnimator?.cancel()
    }

    private fun resolveActiveTargetLatLng(order: Order): LatLng {
        if (order.hasRemainingWaypoints()) {
            val currentWaypoint = order.stops?.sortedBy { it.stopOrder }
                ?.find { it.stopOrder == (order.currentStopOrder + 1) }
            if (currentWaypoint != null) {
                return LatLng(currentWaypoint.lat, currentWaypoint.lng)
            }
        }
        return LatLng(order.destLat ?: 0.0, order.destLng ?: 0.0)
    }

    private fun drawRoadRoute(order: Order, start: LatLng, end: LatLng, @ColorRes colorRes: Int) {
        val googleMap = map ?: return
        val precomputedPolyline = order.driverToPickupPolyline
        if (!precomputedPolyline.isNullOrEmpty()) {
            try {
                val path = PolyUtil.decode(precomputedPolyline)
                if (path.isNotEmpty()) {
                    googleMap.addPolyline(
                        PolylineOptions()
                            .addAll(path)
                            .width(14f)
                            .color(ContextCompat.getColor(context, colorRes))
                            .jointType(JointType.ROUND)
                    )
                    val builder = LatLngBounds.Builder()
                    path.forEach { builder.include(it) }
                    builder.include(start)
                    builder.include(end)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 200))
                    return
                }
            } catch (_: Exception) {}
        }

        coroutineScope.launch {
            try {
                val apiKey = getApiKeyFromManifest()
                val origin = "${start.latitude},${start.longitude}"
                val destination = "${end.latitude},${end.longitude}"
                val response = withContext(Dispatchers.IO) {
                    ApiClient.getInstance().getGoogleMapsApi().getDirections(
                        origin = origin,
                        destination = destination,
                        apiKey = apiKey
                    )
                }
                if (response.routes.isNotEmpty()) {
                    val points = response.routes[0].overview_polyline.points
                    val path = PolyUtil.decode(points)
                    googleMap.addPolyline(
                        PolylineOptions()
                            .addAll(path)
                            .width(14f)
                            .color(ContextCompat.getColor(context, colorRes))
                            .jointType(JointType.ROUND)
                    )
                    val builder = LatLngBounds.Builder()
                    path.forEach { builder.include(it) }
                    builder.include(start)
                    builder.include(end)
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(builder.build(), 200))
                }
            } catch (_: Exception) {
                try {
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngBounds(
                            LatLngBounds.Builder().include(start).include(end).build(),
                            200
                        )
                    )
                } catch (_: Exception) {}
            }
        }
    }

    private fun getCurrentLegRoutePoints(
        driverLatLng: LatLng,
        targetLatLng: LatLng,
        fullRoute: List<LatLng>
    ): List<LatLng> {
        if (fullRoute.isEmpty()) return emptyList()

        var minDistanceDriver = Double.MAX_VALUE
        var closestDriverIndex = lastClosestRouteIndex

        for (i in lastClosestRouteIndex until fullRoute.size) {
            val results = FloatArray(1)
            Location.distanceBetween(
                driverLatLng.latitude, driverLatLng.longitude,
                fullRoute[i].latitude, fullRoute[i].longitude,
                results
            )
            val distance = results[0].toDouble()
            if (distance < minDistanceDriver) {
                minDistanceDriver = distance
                closestDriverIndex = i
            }
        }
        lastClosestRouteIndex = closestDriverIndex

        var minDistanceTarget = Double.MAX_VALUE
        var endIndexForCurrentLeg = closestDriverIndex

        for (i in closestDriverIndex until fullRoute.size) {
            val results = FloatArray(1)
            Location.distanceBetween(
                targetLatLng.latitude, targetLatLng.longitude,
                fullRoute[i].latitude, fullRoute[i].longitude,
                results
            )
            val distance = results[0].toDouble()
            if (distance < minDistanceTarget) {
                minDistanceTarget = distance
                endIndexForCurrentLeg = i
            }
        }

        val safeEndIndex = min(endIndexForCurrentLeg + 1, fullRoute.size)
        if (closestDriverIndex <= endIndexForCurrentLeg) {
            return fullRoute.subList(closestDriverIndex, safeEndIndex)
        }
        return fullRoute.subList(closestDriverIndex, fullRoute.size)
    }

    private fun interpolateLatLng(fraction: Float, start: LatLng, end: LatLng): LatLng {
        val lat = (end.latitude - start.latitude) * fraction + start.latitude
        val lng = (end.longitude - start.longitude) * fraction + start.longitude
        return LatLng(lat, lng)
    }

    private fun interpolateRotation(fraction: Float, start: Float, end: Float): Float {
        var diff = end - start
        while (diff < -180f) diff += 360f
        while (diff >= 180f) diff -= 360f
        return start + fraction * diff
    }

    private fun getApiKeyFromManifest(): String {
        return try {
            context.packageManager
                .getApplicationInfo(context.packageName, PackageManager.GET_META_DATA)
                .metaData
                .getString("com.google.android.geo.API_KEY") ?: ""
        } catch (_: Exception) {
            ""
        }
    }
}
