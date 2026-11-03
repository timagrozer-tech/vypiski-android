fun main(args: Array<String>) {
    var fail = 0; var pass = 0
    for (cn in args) {
        val c = Class.forName(cn)
        for (m in c.declaredMethods.filter { it.isAnnotationPresent(org.junit.Test::class.java) }.sortedBy { it.name }) {
            val exp = m.getAnnotation(org.junit.Test::class.java).expected.java
            try {
                m.invoke(c.getDeclaredConstructor().newInstance())
                if (exp != org.junit.None::class.java) { fail++; println("FAIL ${c.simpleName}.${m.name}: no ${exp.simpleName}") } else { pass++; println("ok   ${c.simpleName}.${m.name}") }
            } catch (e: java.lang.reflect.InvocationTargetException) {
                if (exp.isInstance(e.targetException)) { pass++; println("ok   ${c.simpleName}.${m.name}") } else { fail++; println("FAIL ${c.simpleName}.${m.name}: ${e.targetException}") }
            }
        }
    }
    println("passed=$pass failed=$fail"); if (fail > 0) kotlin.system.exitProcess(1)
}
