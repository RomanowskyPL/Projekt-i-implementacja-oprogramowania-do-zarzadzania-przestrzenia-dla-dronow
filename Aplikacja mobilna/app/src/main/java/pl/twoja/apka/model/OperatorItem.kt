package pl.twoja.apka.model

data class OperatorItem(
    val id: Int,
    val imie: String,
    val nazwisko: String,
    val email: String
) {
    val fullName: String get() = listOf(imie, nazwisko).filter { it.isNotBlank() }.joinToString(" ")
}
