package montuno.truffle

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.dsl.ImplicitCast
import com.oracle.truffle.api.dsl.TypeCheck
import com.oracle.truffle.api.dsl.TypeSystem
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary

@TypeSystem(
    VClosure::class,
    VU::class,
    Boolean::class,
    String::class,
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

object VU

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
data class VClosure (
    @JvmField @CompilerDirectives.CompilationFinal(dimensions = 1) val papArgs: Array<Any?>,
    @JvmField val arity: Int,
    @JvmField val callTarget: RootCallTarget
) : TruffleObject
