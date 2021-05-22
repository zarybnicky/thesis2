package montuno.interpreter

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.dsl.ImplicitCast
import com.oracle.truffle.api.dsl.TypeCheck
import com.oracle.truffle.api.dsl.TypeSystem
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import montuno.Ix
import montuno.Lvl
import montuno.Meta
import montuno.interpreter.scope.MetaEntry
import montuno.interpreter.scope.TopEntry
import montuno.syntax.Icit
import montuno.todo
import montuno.truffle.Closure

@TypeSystem(
    VUnit::class,
    VIrrelevant::class,
    VLam::class,
    VPi::class,
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
        @JvmStatic @ImplicitCast fun castLong(value: Int): Long = value.toLong()

        @TypeCheck(VUnit::class)
        @JvmStatic fun isVUnit(value: Any): Boolean = value === VUnit
        @TypeCheck(VIrrelevant::class)
        @JvmStatic fun isVIrrelevant(value: Any): Boolean = value === VIrrelevant
    }
}

sealed class Val : TruffleObject {
    fun appSpine(sp: VSpine): Val = sp.it.fold(this) { l, r -> l.app(r.first, r.second) }
    fun app(icit: Icit, r: Val) = when (this) {
        is VLam -> closure.inst(r)
        is VTop -> VTop(head, spine + (icit to r), slot)
        is VMeta -> VMeta(head, spine + (icit to r), slot)
        is VLocal -> VLocal(head, spine + (icit to r))
        else -> TODO("impossible")
    }

    fun force(unfold: Boolean): Val = when {
        this is VTop && slot.callTarget != null && unfold -> slot.call(spine)
        this is VMeta && slot.solved && (slot.unfoldable || unfold) -> slot.call(spine)
        else -> this
    }

    fun quote(lvl: Lvl, unfold: Boolean = false): Term = when (val v = force(unfold)) {
        is VTop ->
            if (v.slot.callTarget != null) v.slot.call(v.spine).quote(lvl, unfold)
            else rewrapSpine(TTop(v.head, v.slot), v.spine, lvl)
        is VMeta ->
            if (v.slot.solved && (v.slot.unfoldable || unfold)) v.slot.call(v.spine).quote(lvl, unfold)
            else rewrapSpine(TMeta(v.head, v.slot), v.spine, lvl)
        is VLam -> TLam(v.name, v.icit, v.bound.quote(lvl, unfold), v.closure.inst(VLocal(lvl)).quote(lvl + 1, unfold))
        is VPi -> TPi(v.name, v.icit, v.bound.quote(lvl, unfold), v.closure.inst(VLocal(lvl)).quote(lvl + 1, unfold))
        is VLocal -> rewrapSpine(TLocal(v.head.toIx(lvl)), v.spine, lvl)
        is VNat -> TNat(v.n)
        is VUnit -> TUnit
        is VIrrelevant -> TIrrelevant
        is VPair -> TODO()
        is VSg -> TODO()
    }

    fun replaceSpine(spine: VSpine) = when (this) {
        is VLocal -> VLocal(head, spine)
        is VTop -> VTop(head, spine, slot)
        is VMeta -> VMeta(head, spine, slot)
        else -> this
    }

    fun proj1(): Val = when (this) {
        is VPair -> left
        is VTop -> VTop(head, spine + todo, slot)
        is VMeta -> VMeta(head, spine + todo, slot)
        is VLocal -> VLocal(head, spine + todo)
        else -> TODO("impossible")
    }
    fun proj2(): Val = when (this) {
        is VPair -> right
        is VTop -> VTop(head, spine + todo, slot)
        is VMeta -> VMeta(head, spine + todo, slot)
        is VLocal -> VLocal(head, spine + todo)
        else -> TODO("impossible")
    }
}

inline class VEnv(val it: Array<Val?> = emptyArray()) {
    operator fun plus(v: Val) = VEnv(it + v)
    fun skip() = VEnv(it + null)
    operator fun get(lvl: Lvl) = it[lvl.it] ?: VLocal(lvl, VSpine())
    operator fun get(ix: Ix) = ix.toLvl(it.size).let { lvl -> it[lvl.it] ?: VLocal(lvl, VSpine()) }
}

// lazy ref to Val
inline class VSpine(val it: Array<Pair<Icit, Val>> = emptyArray()) {
    operator fun plus(x: Pair<Icit, Val>) = VSpine(it.plus(x))
    fun getVals() = it.map { it.second }.toTypedArray()
}

// neutrals
@CompilerDirectives.ValueType
class VTop(val head: Lvl, val spine: VSpine, val slot: TopEntry) : Val()

@CompilerDirectives.ValueType
class VLocal(val head: Lvl, val spine: VSpine = VSpine()) : Val()

@CompilerDirectives.ValueType
class VMeta(val head: Meta, val spine: VSpine, val slot: MetaEntry) : Val()

// canonical
@CompilerDirectives.ValueType
class VPair(val left: Val, val right: Val) : Val()

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class VSg(val name: String?, val bound: Val, val closure: Closure) : Val() {
    @ExportMessage fun isExecutable() = true
    @ExportMessage fun execute(vararg args: Any?): Any? = closure.execute(args)
}

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class VPi(val name: String?, val icit: Icit, val bound: Val, val closure: Closure) : Val() {
    @ExportMessage fun isExecutable() = true
    @ExportMessage fun execute(vararg args: Any?): Any? = closure.execute(args)
}
@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class VLam(val name: String?, val icit: Icit, val bound: Val, val closure: Closure) : Val() {
    @ExportMessage fun isExecutable() = true
    @ExportMessage fun execute(vararg args: Any?): Any? = closure.execute(args)
}


@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
object VUnit : Val() {
    @ExportMessage fun isNull() = true
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "VUnit"
    override fun toString(): String = "VUnit"
}
@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
object VIrrelevant : Val() {
    @ExportMessage fun isNull() = true
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "VIrrelevant"
    override fun toString(): String = "VIrrelevant"
}
@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
data class VNat(val n: Int) : Val() {
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "VNat($n)"
}