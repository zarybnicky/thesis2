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
    VNat::class,
    VBool::class,
    VLam::class,
    VPi::class,
    VSg::class,
    VPair::class,
    VMeta::class,
    VLocal::class,
    VTop::class,
    VThunk::class,

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
    }
}

sealed class Val : TruffleObject {
    val arity: Int get() = when (this) {
        is VLam -> 1 + closure.arity
        is VPi -> 1 + closure.arity
        is VThunk -> value.arity
        else -> 0
    }
    fun app(icit: Icit, r: Val): Val = when (this) {
        is VPi -> closure.inst(r)
        is VLam -> closure.inst(r)
        is VTop -> VTop(head, spine + SApp(icit, r), slot)
        is VMeta -> VMeta(head, spine + SApp(icit, r), slot)
        is VLocal -> VLocal(head, spine + SApp(icit, r))
        is VThunk -> value.app(icit, r)
        else -> TODO("impossible $this")
    }

    fun forceUnfold(): Val = when {
        this is VTop && slot.defnV != null -> spine.applyTo(slot.defnV).forceUnfold()
        this is VMeta && slot.solved -> spine.applyTo(slot.value!!).forceUnfold()
        this is VThunk -> value.forceUnfold()
        else -> this
    }

    fun forceMeta(): Val = when {
        this is VMeta && slot.solved && slot.unfoldable -> spine.applyTo(slot.value!!).forceMeta()
        this is VThunk -> value.forceMeta()
        else -> this
    }

    fun quote(lvl: Lvl, unfold: Boolean): Term = when (val v = if (unfold) forceUnfold() else forceMeta()) {
        is VTop -> rewrapSpine(TTop(v.head, v.slot), v.spine, lvl, unfold)
        is VMeta -> rewrapSpine(TMeta(v.head, v.slot, emptyArray()), v.spine, lvl, unfold)
        is VLocal -> rewrapSpine(TLocal(v.head.toIx(lvl)), v.spine, lvl, unfold)
        is VLam -> TLam(v.name, v.icit, v.bound.quote(lvl, unfold), v.closure.inst(VLocal(lvl)).quote(lvl + 1, unfold))
        is VPi -> TPi(v.name, v.icit, v.bound.quote(lvl, unfold), v.closure.inst(VLocal(lvl)).quote(lvl + 1, unfold))
        is VSg -> TSg(v.name, v.bound.quote(lvl, unfold), v.closure.inst(VLocal(lvl)).quote(lvl + 1, unfold))
        is VPair -> TPair(v.left.quote(lvl, unfold), v.right.quote(lvl, unfold))
        is VNat -> TNat(v.n)
        is VBool -> TBool(v.n)
        is VUnit -> TUnit
        is VThunk -> v.value.quote(lvl, unfold)
    }

    fun proj1(): Val = when (this) {
        is VPair -> left
        is VTop -> VTop(head, spine + SProj1, slot)
        is VMeta -> VMeta(head, spine + SProj1, slot)
        is VLocal -> VLocal(head, spine + SProj1)
        is VThunk -> value.proj1()
        else -> TODO("impossible")
    }
    fun proj2(): Val = when (this) {
        is VPair -> right
        is VTop -> VTop(head, spine + SProj2, slot)
        is VMeta -> VMeta(head, spine + SProj2, slot)
        is VLocal -> VLocal(head, spine + SProj2)
        is VThunk -> value.proj2()
        else -> TODO("impossible")
    }
    fun projF(n: String, i: Int): Val = when (this) {
        is VTop -> VTop(head, spine + SProjF(n, i), slot)
        is VMeta -> VMeta(head, spine + SProjF(n, i), slot)
        is VLocal -> VLocal(head, spine + SProjF(n, i))
        is VPair -> if (i == 0) left else right.projF(n, i - 1)
        is VThunk -> value.projF(n, i)
        else -> TODO("impossible")
    }

    fun appLocals(env: VEnv, locals: Array<Boolean>): Val {
        var res = this
        for (i in locals.indices) {
            if (locals[locals.size - i - 1]) {
                res = res.app(Icit.Expl, env[Ix(i)])
            }
        }
        return res
    }
}

inline class VEnv(val it: Array<Any?> = emptyArray()) {
    operator fun plus(v: Val) = VEnv(it + v)
    fun skip() = VEnv(it + null)
    operator fun get(lvl: Lvl): Val = it[lvl.it] as Val? ?: VLocal(lvl, VSpine())
    operator fun get(ix: Ix): Val {
        val lvl = ix.toLvl(it.size)
        return it[lvl.it] as Val? ?: VLocal(lvl, VSpine())
    }
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

@CompilerDirectives.ValueType
class VThunk private constructor() : Val(), Lazy<Val> {
    private var _initializer: (() -> Val)? = null
    private var _value: Val? = null
    constructor(initializer: () -> Val) : this() {
        this._initializer = initializer
        this._value = null
    }
    constructor(value: Val) : this() {
        this._initializer = null
        this._value = value
    }
    override val value: Val get() {
        if (_value == null) {
            _value = _initializer!!()
            _initializer = null
        }
        return _value!!
    }
    override fun isInitialized() = _value != null
}

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
data class VNat(val n: Int) : Val() {
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "VNat($n)"
}

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
data class VBool(val n: Boolean) : Val() {
    @ExportMessage fun toDisplayString(allowSideEffects: Boolean) = "VBool($n)"
}