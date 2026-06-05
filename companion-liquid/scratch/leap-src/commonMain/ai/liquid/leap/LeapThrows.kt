@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package ai.liquid.leap

/**
 * Multiplatform expect annotation for @LeapThrows. On JVM platforms, this maps to
 * kotlin.jvm.LeapThrows. On non-JVM platforms, this is a no-op annotation.
 */
@Target(
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY_GETTER,
  AnnotationTarget.PROPERTY_SETTER,
  AnnotationTarget.CONSTRUCTOR,
)
@Retention(AnnotationRetention.SOURCE)
expect annotation class LeapThrows(
  vararg val exceptionClasses: kotlin.reflect.KClass<out Throwable>
)
