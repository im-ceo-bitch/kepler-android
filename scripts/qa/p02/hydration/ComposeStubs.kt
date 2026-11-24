package androidx.compose.runtime
import kotlin.reflect.KProperty
annotation class Composable
class FakeState<T>(var value:T)
fun <T> mutableStateOf(v:T)=FakeState(v)
fun mutableIntStateOf(v:Int)=FakeState(v)
operator fun <T> FakeState<T>.getValue(instance:Any?,prop:KProperty<*>)=value
operator fun <T> FakeState<T>.setValue(instance:Any?,prop:KProperty<*>,v:T) {value=v}
fun <T> remember(vararg inputs:Any?,calculate:()->T)=calculate()
fun SideEffect(block:()->Unit)=block()
fun LaunchedEffect(vararg inputs:Any?, block:suspend()->Unit) = Unit
