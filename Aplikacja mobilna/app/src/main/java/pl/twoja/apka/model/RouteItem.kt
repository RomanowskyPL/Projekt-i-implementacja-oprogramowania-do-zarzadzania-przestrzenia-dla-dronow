package pl.twoja.apka.model

data class RouteItem(
    val id: Int,
    val nazwa: String,
    val opis: String?,
    val planowanaDlugoscM: Double?,
    val planowanyCzasMin: Double?,
    val utworzono: String?,
    val pointsCount: Int
)
