package tv.newtv.data.models

data class VodCategory<T>(
    val title: String,
    val items: List<T>
)
