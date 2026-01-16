package pl.twoja.apka.model

data class DroneModelItem(
    val id: Int,
    val producent: String,
    val nazwaModelu: String,
    val klasaDrona: String?,
    val masaG: Int?,
    val liczbaEgzemplarzy: Int
)
