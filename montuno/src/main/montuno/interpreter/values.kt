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
    fun app(icit: Icit, r: Val) = when (this) {
        is VPi -> closure.inst(r)
        is VLam -> closure.inst(r)
        is VTop -> VTop(head, spine + SApp(icit, r), slot)
        is VMeta -> VMeta(head, spine + SApp(icit, r), slot)
        is VLocal -> VLocal(head, spine + SApp(icit, r))
        else -> TODO("impossible")
    }

    fun force(unfold: Boolean): Val = when {
        this is VTop && slot.closure != null && unfold -> spine.applyTo(this)
        this is VMeta && slot.solved && (slot.unfoldable || unfold) -> spine.applyTo(this)
        else -> this
    }

    fun quote(lvl: Lvl, unfold: Boolean = false): Term = when (val v = force(unfold)) {
        is VTop ->
            if (v.slot.closure != null) v.spine.applyTo(v).quote(lvl, unfold)
            else rewrapSpine(TTop(v.head, v.slot), v.spine, lvl)
        is VMeta ->
            if (v.slot.solved && (v.slot.unfoldable || unfold)) v.spine.applyTo(v).quote(lvl, unfold)
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
        is VTop -> VTop(head, spine + SProj1, slot)
        is VMeta -> VMeta(head, spine + SProj1, slot)
        is VLocal -> VLocal(head, spine + SProj1)
        else -> TODO("impossible")
    }
    fun proj2(): Val = when (this) {
        is VPair -> right
        is VTop -> VTop(head, spine + SProj2, slot)
        is VMeta -> VMeta(head, spine + SProj2, slot)
        is VLocal -> VLocal(head, spine + SProj2)
        else -> TODO("impossible")
    }
    fun projF(n: String, i: Int): Val = when (this) {
        is VTop -> VTop(head, spine + SProjF(n, i), slot)
        is VMeta -> VMeta(head, spine + SProjF(n, i), slot)
        is VLocal -> VLocal(head, spine + SProjF(n, i))
        is VPair -> if (i == 0) left else right.projF(n, i - 1)
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
sealed class SpineVal
object SProj1 : SpineVal()
object SProj2 : SpineVal()
data class SProjF(val n: String, val i: Int): SpineVal()
data class SApp(val icit: Icit, val v: Val): SpineVal()
inline class VSpine(val it: Array<SpineVal> = emptyArray()) {
    operator fun plus(x: SpineVal) = VSpine(it.plus(x))
    fun applyTo(vi: Val): Val = it.fold(vi) { v, sp -> when (sp) {
        SProj1 -> v.proj1()
        SProj2 -> v.proj2()
        is SProjF -> v.projF(sp.n, sp.i)
        is SApp -> v.app(sp.icit, sp.v)
    } }
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