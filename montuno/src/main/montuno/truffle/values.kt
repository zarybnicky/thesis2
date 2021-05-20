package montuno.truffle

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.dsl.ImplicitCast
import com.oracle.truffle.api.dsl.TypeCheck
import com.oracle.truffle.api.dsl.TypeSystem
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import montuno.common.*

@TypeSystem(
    VUnit::class,
    VIrrelevant::class,
    VLam::class,
    VPi::class,
    VFun::class,
    VMeta::class,
    VLocal::class,
    VTop::class,

    Boolean::class,
    Int::class,
    Long::class
)
open class Types {
    companion object {
        @CompilerDirectives.TruffleBoundary
        @ImplicitCast fun castLong(value: Int): Long = value.toLong()

        @TypeCheck(VUnit::class) fun isVUnit(value: Any): Boolean = value === VUnit
        @TypeCheck(VIrrelevant::class) fun isVIrrelevant(value: Any): Boolean = value === VIrrelevant
    }
}

sealed class Val : TruffleObject {
    fun quote(lvl: Lvl): Term = todo
}

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
object VUnit : Val() {
    @ExportMessage fun isNull() = true
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "VUnit"
}
@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
object VIrrelevant : Val() {
    @ExportMessage fun isNull() = true
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "VIrrelevant"
}

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class VPi(val closure: TermRootNode, val frame: MaterializedFrame) : Val() {
    @ExportMessage fun isExecutable() = true
    @ExportMessage fun execute(vararg args: Any?): Any? = closure.callTarget.call(args)
}
@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class VLam(val closure: TermRootNode, val frame: MaterializedFrame) : Val() {
    @ExportMessage fun isExecutable() = true
    @ExportMessage fun execute(vararg args: Any?): Any? = closure.callTarget.call(args)
}
@CompilerDirectives.ValueType
class VFun(@JvmField val lhs: Any, @JvmField val rhs: Any) : Val()

@CompilerDirectives.ValueType
class VTop(val head: Lvl, val spine: Array<Any>, val slot: TopEntry<Term, Val>) : Val()
@CompilerDirectives.ValueType
class VLocal(val head: Lvl, val spine: Array<Any>) : Val()
@CompilerDirectives.ValueType
class VMeta(val head: Meta, val spine: Array<Any>, val slot: MetaEntry<Term, Val>) : Val()

@CompilerDirectives.ValueType
class VNat(val n: Int) : Val()