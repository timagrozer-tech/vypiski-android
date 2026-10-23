// Локальный раннер: находит классы *Test, вызывает методы с @org.junit.Test, печатает отчёт.
import java.io.File
import java.io.PrintStream

fun main(args: Array<String>) {
    System.setOut(PrintStream(System.out, true, "UTF-8"))
    val classesDir = File(args[0])
    val loader = Thread.currentThread().contextClassLoader
    val names = classesDir.walkTopDown().filter { it.name.endsWith("Test.class") && !it.name.contains('$') }
        .map { it.relativeTo(classesDir).path.removeSuffix(".class").replace(File.separatorChar, '.') }.sorted().toList()
    var passed = 0; var failed = 0
    val failures = mutableListOf<String>()
    for (n in names) {
        val c = loader.loadClass(n)
        val tests = c.methods.filter { m -> m.annotations.any { it.annotationClass.qualifiedName == "org.junit.Test" } }.sortedBy { it.name }
        val befores = c.methods.filter { m -> m.annotations.any { it.annotationClass.qualifiedName == "org.junit.Before" } }
        for (t in tests) {
            val inst = c.getDeclaredConstructor().newInstance()
            try {
                befores.forEach { it.invoke(inst) }
                t.invoke(inst); passed++
                println("  ✓ ${c.simpleName}.${t.name}")
            } catch (e: java.lang.reflect.InvocationTargetException) {
                failed++
                val msg = "${c.simpleName}.${t.name}: ${e.targetException}"
                failures += msg
                println("  ✗ $msg")
            }
        }
    }
    println("\nИТОГО: пройдено $passed, упало $failed, всего ${passed + failed}")
    if (failed > 0) { println("Падения:"); failures.forEach { println(" - $it") }; kotlin.system.exitProcess(1) }
}
