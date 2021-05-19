package montuno.interpreter

import montuno.*
import montuno.common.ITerm
import montuno.common.MetaSolved

internal object uninitializedValue

fun <T> thunkOf(x: T) = InitializedThunk(x)
class Thunk<out T>(initializer: () -> T) : Lazy<T> {
    private var _initializer: (() -> T)? = initializer
    @Suppress("UNCHECKED_CAST")
    private var _value: T = uninitializedValue as T
    override fun isInitialized(): Boolean = _value !== uninitializedValue
    override fun toString(): String = if (isInitialized()) value.toString() else "<thunk>"
    override val value: T get() {
        if (_value === uninitializedValue) {
            _value = _initializer!!()
            _initializer = null
        }
        return _value
    }
}
class InitializedThunk<out T>(override val value: T) : Lazy<T> {
    override fun isInitialized(): Boolean = true
    override fun toString(): String = value.toString()
}

inline class VSpine(val it: Array<Pair<Icit, Val>> = emptyArray()) // lazy ref to Val
fun VSpine.with(x: Pair<Icit, Val>) = VSpine(it.plus(x))

inline class VEnv(val it: Array<Val?> = emptyArray())
fun VEnv.local(lvl: Lvl): Val = it[lvl.it] ?: vLocal(lvl)
fun VEnv.skip() = VEnv(it + null)
fun VEnv.def(x: Val) = VEnv(it + x)
val VEnv.lvl: Lvl get() = Lvl(it.size)

data class VCl(val env: VEnv, val tm: Term) {
    fun inst(v: Val): Val = tm.eval(env.def(v))
}

sealed class Val {
    fun appSpine(sp: VSpine): Val = sp.it.fold(this, { l, r -> l.app(r.first, r.second) })
    fun app(icit: Icit, r: Val) = when (this) {
        is VLam -> cl.inst(r)
        is VNe -> VNe(head, spine.with(icit to r))
        else -> TODO("impossible")
    }
    fun force(unfold: Boolean): Val = when (this) {
        is VNe -> when (head) {
            is HMeta -> MontunoPure.top[head.meta].let {
                if (it is MetaSolved && (it.unfoldable || unfold))
                    it.v.appSpine(spine).force(unfold)
                else this
            }
            is HTop -> if (unfold) MontunoPure.top[head.lvl].defn?.second ?: this else this
            is HLocal -> this
        }
        else -> this
    }
    fun quote(lvl: Lvl, unfold: Boolean = false): Term = when (val v = force(unfold)) {
        is VNe -> {
            var x = when (v.head) {
                is HMeta -> MontunoPure.top[v.head.meta].let {
                    if (it is MetaSolved && (it.unfoldable || unfold))
                        it.v.appSpine(v.spine).quote(lvl)
                    else TMeta(v.head.meta)
                }
                is HLocal -> TLocal(v.head.lvl.toIx(lvl))
                is HTop -> TTop(v.head.lvl)
            }
            for ((icit, t) in v.spine.it.reversedArray()) { x = TApp(icit, x, t.quote(lvl)) }
            x
        }
        is VLam -> TLam(v.n, v.icit, v.cl.inst(vLocal(lvl)).quote(lvl + 1))
        is VPi -> TPi(v.n, v.icit, v.ty.quote(lvl), v.cl.inst(vLocal(lvl)).quote(lvl + 1))
        is VFun -> TFun(v.a.quote(lvl), v.b.quote(lvl))
        is VUnit -> TU
        is VNat -> TNat(v.n)
        is VIrrelevant -> TIrrelevant
    }
}
object VUnit : Val() { override fun toString() = "VUnit" }
object VIrrelevant : Val() { override fun toString() = "VIrrelevant" }
data class VNat(val n: Int) : Val()
data class VLam(val n: String, val icit: Icit, val cl: VCl) : Val()
data class VPi(val n: String, val icit: Icit, val ty: Val, val cl: VCl) : Val()
data class VFun(val a: Val, val b: Val) : Val()
data class VNe(val head: Head, val spine: VSpine) : Val()

fun vLocal(ix: Lvl) = VNe(HLocal(ix), VSpine())
fun vTop(lvl: Lvl) = VNe(HTop(lvl), VSpine())
fun vMeta(meta: Meta) = VNe(HMeta(meta), VSpine())

sealed class Term : ITerm {
    override fun isUnfoldable(): Boolean = when (this) {
        is TLocal -> true
        is TMeta -> true
        is TTop -> true
        is TU -> true
        is TLam -> tm.isUnfoldable()
        else -> false
    }
    private fun evalBox(env: VEnv): Val = when (this) {
        is TLocal -> env.local(ix.toLvl(env.lvl))
        is TTop -> vTop(lvl)
        is TMeta -> vMeta(meta)
        else -> eval(env)
    }
    fun eval(env: VEnv): Val = when (this) {
        is TU -> VUnit
        is TNat -> VNat(n)
        is TIrrelevant -> VIrrelevant
        is TLocal -> env.local(ix.toLvl(env.lvl))
        is TTop -> MontunoPure.top.getTop(lvl)
        is TMeta -> MontunoPure.top.getMeta(meta)
        is TApp -> l.eval(env).app(icit, r.evalBox(env)) // lazy
        is TLam -> VLam(n, icit, VCl(env, tm))
        is TPi -> VPi(n, icit, arg.evalBox(env), VCl(env, tm)) // lazy
        is TFun -> VFun(l.evalBox(env), r.evalBox(env))
        is TLet -> tm.eval(env.def(v.eval(env)))   // lazy
        is TForeign -> TODO("VForeign not implemented")
    }
}
object TU : Term()
object TIrrelevant : Term()
data class TNat(val n: Int) : Term()
data class TLet(val n: String, val ty: Term, val v: Term, val tm: Term) : Term()
data class TApp(val icit: Icit, val l: Term, val r: Term) : Term()
data class TLam(val n: String, val icit: Icit, val tm: Term) : Term()
data class TPi(val n: String, val icit: Icit, val arg: Term, val tm: Term) : Term()
data class TFun(val l: Term, val r: Term) : Term()
data class TForeign(val lang: String, val eval: String, val ty: Term) : Term()
data class TLocal(val ix: Ix) : Term() { override fun toString() = "TLocal(ix=${ix.it})" }
data class TTop(val lvl: Lvl) : Term() { override fun toString() = "TTop(lvl=${lvl.it})" }
data class TMeta(val meta: Meta) : Term() { override fun toString() = "TMeta(${meta.i}, ${meta.j})" }
