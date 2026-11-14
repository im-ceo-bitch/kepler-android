package kotlinx.coroutines.flow
inline fun <T> MutableStateFlow<T>.update(function: (T) -> T) { value = function(value) }
