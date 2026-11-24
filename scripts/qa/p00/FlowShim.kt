// Only the system Kotlin CLI's old coroutines artifact needs this compatibility
// extension. NOT included in the Android app sources.
package kotlinx.coroutines.flow
inline fun <T> MutableStateFlow<T>.update(function: (T) -> T) { value = function(value) }
