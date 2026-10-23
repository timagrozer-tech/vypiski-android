// Минимальная совместимая с JUnit 4 заглушка ТОЛЬКО для локального прогона без Maven.
// В Gradle/CI используется настоящий junit:junit:4.13.2. В APK не попадает.
package org.junit

@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.RUNTIME) annotation class Test
@Target(AnnotationTarget.FUNCTION) @Retention(AnnotationRetention.RUNTIME) annotation class Before

object Assert {
    @JvmStatic fun fail(msg: String?): Nothing = throw AssertionError(msg)
    @JvmStatic fun fail(): Nothing = throw AssertionError()
    @JvmStatic fun assertTrue(cond: Boolean) { if (!cond) throw AssertionError("ожидалось true") }
    @JvmStatic fun assertTrue(msg: String?, cond: Boolean) { if (!cond) throw AssertionError(msg) }
    @JvmStatic fun assertFalse(cond: Boolean) { if (cond) throw AssertionError("ожидалось false") }
    @JvmStatic fun assertFalse(msg: String?, cond: Boolean) { if (cond) throw AssertionError(msg) }
    @JvmStatic fun assertEquals(expected: Any?, actual: Any?) { if (expected != actual) throw AssertionError("ожидалось <$expected>, получено <$actual>") }
    @JvmStatic fun assertEquals(msg: String?, expected: Any?, actual: Any?) { if (expected != actual) throw AssertionError("$msg: ожидалось <$expected>, получено <$actual>") }
    @JvmStatic fun assertEquals(expected: Double, actual: Double, delta: Double) { if (Math.abs(expected - actual) > delta) throw AssertionError("ожидалось <$expected±$delta>, получено <$actual>") }
    @JvmStatic fun assertEquals(msg: String?, expected: Double, actual: Double, delta: Double) { if (Math.abs(expected - actual) > delta) throw AssertionError("$msg: ожидалось <$expected±$delta>, получено <$actual>") }
    @JvmStatic fun assertNotEquals(a: Any?, b: Any?) { if (a == b) throw AssertionError("значения равны: <$a>") }
    @JvmStatic fun assertNull(o: Any?) { if (o != null) throw AssertionError("ожидалось null, получено <$o>") }
    @JvmStatic fun assertNull(msg: String?, o: Any?) { if (o != null) throw AssertionError("$msg: ожидалось null, получено <$o>") }
    @JvmStatic fun assertNotNull(o: Any?) { if (o == null) throw AssertionError("ожидалось не null") }
    @JvmStatic fun assertNotNull(msg: String?, o: Any?) { if (o == null) throw AssertionError(msg) }
}
