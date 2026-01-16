package pl.twoja.apka.model

data class FlightItem(
    val id: Int,
    val trasa: String,
    val czasStartu: String?,
    val czasKonca: String?,
    val operator: String?,
    val status: String?
)
