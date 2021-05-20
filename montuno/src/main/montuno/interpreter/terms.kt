package montuno.interpreter

import montuno.common.*

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
data class TTop(val lvl: Lvl, val slot: TopEntry<Term, Val>) : Term() { override fun toString() = "TTop(lvl=${lvl.it})" }
data class TMeta(val meta: Meta, val slot: MetaEntry<Term, Val>) : Term() { override fun toString() = "TMeta(${meta.i}, ${meta.j})" }

sealed class Term {
    fun appSpine(spine: VSpine, lvl: Lvl): Term {
        var x = this
        for ((icit, t) in spine.it.reversedArray()) { x = TApp(icit, x, t.quote(lvl)) }
        return x
    }
    fun eval(env: Array<Val?>): Val = when (this) {
        is TU -> VUnit
        is TNat -> VNat(n)
        is TIrrelevant -> VIrrelevant
        is TLocal -> ix.toLvl(env.size).let { lvl -> env[lvl.it] ?: VLocal(lvl) }
        is TTop -> VTop(lvl, VSpine(), slot)
        is TMeta -> VMeta(meta, VSpine(), slot)
        is TApp -> l.eval(env).app(icit, r.eval(env)) // lazy
        is TLam -> VLam(n, icit, VCl(env, tm))
        is TPi -> VPi(n, icit, arg.eval(env), VCl(env, tm)) // lazy
        is TFun -> VFun(l.eval(env), r.eval(env)) // lazy
        is TLet -> tm.eval(env + v.eval(env))   // lazy
        is TForeign -> TODO("VForeign not implemented")
    }
    fun inline(lvl: Lvl, vs: Array<Val?>) : Term = when (this) {
        is TTop -> this
        is TLocal -> this
        is TMeta -> if (slot.solved && slot.unfoldable) slot.value!!.quote(lvl) else this
        is TLet -> TLet(n, ty.inline(lvl, vs), v.inline(lvl, vs), tm.inline(lvl + 1, vs + null))
        is TApp -> when (val x = l.inlineSp(lvl, vs)) {
            is Either.Left -> x.it.app(icit, r.eval(vs)).quote(lvl)
            is Either.Right -> TApp(icit, x.it, r.inline(lvl, vs))
        }
        is TLam -> TLam(n, icit, tm.inline(lvl + 1, vs + null))
        is TFun -> TFun(l.inline(lvl, vs), r.inline(lvl, vs))
        is TPi -> TPi(n, icit, arg.inline(lvl, vs), tm.inline(lvl + 1, vs + null))
        TU -> this
        TIrrelevant -> this
        is TForeign -> this
        is TNat -> this
    }
    fun inlineSp(lvl: Lvl, vs: Array<Val?>): Either<Val, Term> = when(this) {
        is TMeta -> {
            val it = MontunoPure.top[meta]
            when {
                it.solved && it.unfoldable -> Either.Left(it.value!!)
                else -> Either.Right(this)
            }
        }
        is TApp -> when (val x = l.inlineSp(lvl, vs)) {
            is Either.Left -> Either.Left(x.it.app(icit, r.eval(vs)))
            is Either.Right -> Either.Right(TApp(icit, x.it, r.inline(lvl, vs)))
        }
        else -> Either.Right(this.inline(lvl, vs))
    }
}