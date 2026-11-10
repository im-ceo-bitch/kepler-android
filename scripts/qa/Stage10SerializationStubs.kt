// JVM-only compile-time stand-ins. They are deliberately OUTSIDE app/src and NEVER shipped.
// Production builds use the genuine kotlinx.serialization runtime/compiler plugin.
package kotlinx.serialization

@Target(AnnotationTarget.CLASS, AnnotationTarget.PROPERTY)
annotation class Serializable
@Target(AnnotationTarget.PROPERTY)
annotation class SerialName(val value: String)
