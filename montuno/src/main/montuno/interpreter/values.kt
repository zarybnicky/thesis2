package montuno.interpreter

import montuno.common.*

inline class VSpine(val it: Array<Pair<Icit, Val>> = emptyArray()) // lazy ref to Val
operator fun VSpine.plus(x: Pair<Icit, Val>) = VSpine(it.plus(x))

data class VCl(val env: Array<Val?>, val tm: Term)

object VUnit : Val() { override fun toString() = "VUnit" }
object VIrrelevant : Val() { override fun toString() = "VIrrelevant" }
data class VNat(val n: Int) : Val()
data class VLam(val n: String, val icit: Icit, val cl: VCl) : Val() {
    fun inst(v: Val) = cl.tm.eval(cl.env + v)
}
data class VPi(val n: String, val icit: Icit, val ty: Val, val cl: VCl) : Val() {
    fun inst(v: Val) = cl.tm.eval(cl.env + v)
}
data class VFun(val a: Val, val b: Val) : Val()
data class VLocal(val head: Lvl, val spine: VSpine = VSpine()) : Val()
data class VMeta(val head: Meta, val spine: VSpine, val slot: MetaEntry<Term, Val>) : Val()
data class VTop(val head: Lvl, val spine: VSpine, val slot: TopEntry<Term, Val>) : Val()

sealed class Val {
    fun appSpine(sp: VSpine): Val = sp.it.fold(this, { l, r -> l.app(r.first, r.second) })
    fun app(icit: Icit, r: Val) = when (this) {
        is VLam -> inst(r)
        is VTop -> VTop(head, spine + (icit to r), slot)
        is VMeta -> VMeta(head, spine + (icit to r), slot)
        is VLocal -> VLocal(head, spine + (icit to r))
        else -> TODO("impossible")
    }

    fun force(unfold: Boolean): Val = when (this) {
        is VTop -> if (unfold) MontunoPure.top[head].defnV ?: this else this
        is VMeta -> MontunoPure.top[head].let {
            if (it.solved && (it.unfoldable || unfold))
                it.value!!.appSpine(spine).force(unfold)
            else this
        }
        else -> this
    }

    fun quote(lvl: Lvl, unfold: Boolean = false): Term = when (val v = force(unfold)) {
        is VLocal -> TLocal(v.head.toIx(lvl)).appSpine(v.spine, lvl)
        is VTop -> TTop(v.head, v.slot).appSpine(v.spine, lvl)
        is VMeta -> {
            if (v.slot.solved && (v.slot.unfoldable || unfold))
                v.slot.value!!.appSpine(v.spine).quote(lvl)
            else TMeta(v.head, v.slot).appSpine(v.spine, lvl)
        }
        is VLam -> TLam(v.n, v.icit, v.inst(VLocal(lvl)).quote(lvl + 1))
        is VPi -> TPi(v.n, v.icit, v.ty.quote(lvl), v.inst(VLocal(lvl)).quote(lvl + 1))
        is VFun -> TFun(v.a.quote(lvl), v.b.quote(lvl))
        is VUnit -> TU
        is VNat -> TNat(v.n)
        is VIrrelevant -> TIrrelevant
    }

    fun replaceSpine(spine: VSpine) = when (this) {
        is VLocal -> VLocal(head, spine)
        is VTop -> VTop(head, spine, slot)
        is VMeta -> VMeta(head, spine, slot)
        else -> this
    }
}
