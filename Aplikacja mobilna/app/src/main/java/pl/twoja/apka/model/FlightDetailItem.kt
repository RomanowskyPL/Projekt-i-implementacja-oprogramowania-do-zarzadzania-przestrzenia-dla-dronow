package pl.twoja.apka.model

data class FlightDetailItem(
    val nazwa: String? = null,
    val start: String? = null,
    val ladowanie: String? = null,
    val dlugoscLotuM: Double? = null,
    val czasLotuS: Int? = null,
    val typLotu: String? = null,
    val status: String? = null,
    val operator: String? = null,
    val uidOperatora: Int? = null,
    val nazwaDrona: String? = null,
    val numerSeryjnyDrona: String? = null
)
