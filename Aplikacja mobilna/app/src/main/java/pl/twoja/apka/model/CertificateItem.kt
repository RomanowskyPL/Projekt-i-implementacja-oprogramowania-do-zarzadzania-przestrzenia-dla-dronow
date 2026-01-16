package pl.twoja.apka.model

data class CertificateItem(
    val id: Int,
    val nazwa: String,
    val dataWydania: String?,
    val dataWygasniecia: String?,
    val photoUrl: String?
)
