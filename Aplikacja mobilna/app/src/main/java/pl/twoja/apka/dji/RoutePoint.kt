package pl.twoja.apka.dji

data class RoutePoint(
    val lat: Double,
    val lon: Double,
    val heightM: Double,
    val speedMS: Double? = null
)
