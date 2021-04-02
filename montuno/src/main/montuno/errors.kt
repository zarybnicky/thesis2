package montuno

import montuno.syntax.Loc

class TypeError(message: String, val loc: Loc) : Exception(message) {
    override fun toString(): String = message.orEmpty()
}

//class NeutralException(val type: Type, val term: Neutral) : SlowPathException() {
//    fun get() = NeutralValue(type, term)
//
//    fun apply(rands: Array<out Any?>): Nothing = neutral(
//        rands.indices.fold(type) { currentType, _ -> (currentType as Type.Arr).result },
//        term.apply(rands)
//    )
//
//    companion object { const val serialVersionUID : Long = 1L }
//}
//@Throws(NeutralException::class)
//fun neutral(type: Type, term: Neutral) : Nothing = throw NeutralException(type, term)

//internal class TODO() : RuntimeException() {
//    companion object { const val serialVersionUID : Long = 1L }
//    override fun toString(): String {
//        val it = stackTrace?.getOrNull(0)
//        when {
//            it == null -> super.toString()
//            else -> it.error(Severity.todo, it.fileName, it.lineNumber, null, null, it.methodName)
//        }
//    }
//}
//
//val todo: Nothing get() {
//    CompilerDirectives.transferToInterpreter()
//    throw TODO().also { it.stackTrace = it.stackTrace.trim() }
//}
//
//
//internal class Panic(message: String? = null) : RuntimeException(message) {
//    internal constructor(message: String? = null, cause: Throwable?): this(message) {
//        initCause(cause)
//    }
//    companion object { const val serialVersionUID : Long = 1L }
//    override fun toString(): String = stackTrace?.getOrNull(0)?.let {
//        Pretty.ppString {
//            error(Severity.panic, it.fileName, it.lineNumber, null, null, message)
//        }
//    } ?: super.toString()
//}
