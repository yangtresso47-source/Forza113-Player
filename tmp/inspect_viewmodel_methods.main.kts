import androidx.lifecycle.ViewModel

fun main() {
    ViewModel::class.java.declaredMethods
        .sortedBy { it.name }
        .forEach { println(it.name + "(" + it.parameterCount + ")") }
}
