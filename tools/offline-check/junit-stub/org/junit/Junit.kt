package org.junit
import kotlin.reflect.KClass
@Retention(AnnotationRetention.RUNTIME) @Target(AnnotationTarget.FUNCTION)
annotation class Test(val expected: KClass<out Throwable> = None::class)
class None : Throwable()
object Assert {
    @JvmStatic fun assertEquals(e: Any?, a: Any?) { if (e != a) throw AssertionError("expected <$e> but was <$a>") }
    @JvmStatic fun assertEquals(e: Long, a: Long) { if (e != a) throw AssertionError("expected <$e> but was <$a>") }
    /** Mirrors JUnit 4: the delta-less double overload always fails. */
    @JvmStatic fun assertEquals(e: Double, a: Double) { throw AssertionError("Use assertEquals(expected, actual, delta) to compare floating-point numbers") }
    @JvmStatic fun assertEquals(e: Double, a: Double, delta: Double) { if (Math.abs(e - a) > delta) throw AssertionError("expected <$e> but was <$a>") }
    @JvmStatic fun assertTrue(msg: String, c: Boolean) { if (!c) throw AssertionError(msg) }
    @JvmStatic fun assertTrue(c: Boolean) { if (!c) throw AssertionError("expected true") }
    @JvmStatic fun assertFalse(c: Boolean) { if (c) throw AssertionError("expected false") }
    @JvmStatic fun assertNull(v: Any?) { if (v != null) throw AssertionError("expected null, was $v") }
}

@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.FIELD, AnnotationTarget.FUNCTION, AnnotationTarget.PROPERTY_GETTER)
annotation class Rule

object Assume {
    @JvmStatic fun assumeTrue(c: Boolean) { if (!c) throw AssertionError("assumption failed") }
    @JvmStatic fun assumeTrue(msg: String, c: Boolean) { if (!c) throw AssertionError(msg) }
}
