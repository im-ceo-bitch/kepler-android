package kotlinx.serialization
import kotlin.reflect.KClass
@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class Serializable(val with: KClass<*> = Any::class)
@Target(AnnotationTarget.PROPERTY, AnnotationTarget.FIELD)
annotation class SerialName(val value: String)
