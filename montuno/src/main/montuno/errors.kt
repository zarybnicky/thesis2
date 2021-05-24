package montuno

import com.oracle.truffle.api.CompilerDirectives

import montuno.syntax.Loc

class UnifyError(val reason: String) : RuntimeException(reason)
class ElabError(val loc: Loc?, val reason: String) : RuntimeException(reason)

internal class Panic(message: String? = null) : RuntimeException(message) {
    internal constructor(message: String? = null, cause: Throwable?): this(message) {
        initCause(cause)
    }
    companion object { const val serialVersionUID : Long = 1L }
}
fun panic(msg: String, base: Throwable?): Nothing {
    CompilerDirectives.transferToInterpreter()
    throw Panic(msg, base)
}
val todo: Nothing get() {
    CompilerDirectives.transferToInterpreter()
    throw TODO("TODO")
}