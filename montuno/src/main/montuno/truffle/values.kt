package montuno.truffle

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.dsl.ImplicitCast
import com.oracle.truffle.api.dsl.TypeCheck
import com.oracle.truffle.api.dsl.TypeSystem
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedTypeException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.ExplodeLoop
import kotlin.jvm.Throws

@TypeSystem(
    Val::class,
    Glued::class,
    GluedVal::class,
    Term::class,
    VClosure::class,
    VU::class,

    Boolean::class,
    Int::class,
    Long::class
)
open class Types {
    companion object {
        @ImplicitCast
        @CompilerDirectives.TruffleBoundary
        fun castLong(value: Int): Long {
            return value.toLong()
        }

        @TypeCheck(VU::class)
        fun isU(value: Any): Boolean {
            return value === VU
        }
    }
}

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
object VU : TruffleObject {
    override fun toString() = "VU"
}

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
data class VClosure (
    @JvmField @CompilerDirectives.CompilationFinal(dimensions = 1) val papArgs: Array<Any?>,
    @JvmField val arity: Int,
    @JvmField val maxArity: Int,
    @JvmField val callTarget: RootCallTarget
) : TruffleObject {
    @ExportMessage
    fun isExecutable() = true

    @ExportMessage
    @ExplodeLoop
    @Throws(ArityException::class, UnsupportedTypeException::class)
    fun execute(vararg arguments: Any?): Any? {
        val len = arguments.size
        if (len > maxArity) throw ArityException.create(maxArity, len)
        return callTarget.call(arguments) //TODO: PAP, exact, overapplied
    }
}
