package montuno.interpreter

import montuno.Either
import montuno.Ix
import montuno.Lvl
import montuno.Meta
import montuno.interpreter.scope.MetaEntry
import montuno.interpreter.scope.TopEntry
import montuno.syntax.Icit

object TUnit : Term()
object TIrrelevant : Term()
data class TNat(val n: Int) : Term()
data class TLet(val name: String, val type: Term, val bound: Term, val body: Term) : Term()
data class TApp(val icit: Icit, val lhs: Term, val rhs: Term) : Term()
data class TLam(val name: String?, val icit: Icit, val type: Term, val body: Term) : Term()
data class TPi(val name: String?, val icit: Icit, val bound: Term, val body: Term) : Term()
data class TPair(val lhs: Term, val rhs: Term) : Term()
data class TProjF(val name: String, val body: Term, val i: Int) : Term()
data class TProj1(val body: Term) : Term()
data class TProj2(val body: Term) : Term()
data class TSg(val name: String?, val bound: Term, val body: Term) : Term()
data class TForeign(val lang: String, val code: String, val type: Term) : Term()
data class TLocal(val ix: Ix) : Term() { override fun toString() = "TLocal(ix=${ix.it})" }
data class TTop(val lvl: Lvl, val slot: TopEntry) : Term() { override fun toString() = "TTop(lvl=${lvl.it})" }
data class TMeta(val meta: Meta, val slot: MetaEntry) : Term() { override fun toString() = "TMeta(${meta.i}, ${meta.j})" }

fun rewrapSpine(term: Term, spine: VSpine, lvl: Lvl): Term {
    var x = term
    for (sp in spine.it.reversedArray()) x = when (sp) {
        SProj1 -> TProj1(x)
        SProj2 -> TProj2(x)
        is SProjF -> TProjF(sp.n, x, sp.i)
        is SApp -> TApp(sp.icit, x, sp.v.quote(lvl))
    }
    return x
}

sealed class Term {
    val arity: Int get() = when (this) {
        is TLam -> 1 + body.arity
        is TPi -> 1 + body.arity
        else -> 0
    }

    fun eval(ctx: MontunoContext, env: VEnv): Val = when (this) {
        is TUnit -> VUnit
        is TNat -> VNat(n)
        is TIrrelevant -> VIrrelevant
        is TLocal -> env[ix]
        is TTop -> VTop(lvl, VSpine(), slot)
        is TMeta -> VMeta(meta, VSpine(), slot)
        is TApp -> lhs.eval(ctx, env).app(icit, rhs.eval(ctx, env)) // lazy
        is TLam -> VLam(name, icit, type.eval(ctx, env), ctx.compiler.buildClosure(body, body, env.it))
        is TPi -> VPi(name, icit, bound.eval(ctx, env), ctx.compiler.buildClosure(body, body, env.it)) // lazy
        is TLet -> body.eval(ctx, env + bound.eval(ctx, env))   // lazy
        is TForeign -> TODO("VForeign not implemented")
        is TPair -> TODO()
        is TProj1 -> TODO()
        is TProj2 -> TODO()
        is TProjF -> TODO()
        is TSg -> TODO()
    }

    fun inline(ctx:MontunoContext, lvl: Lvl, vs: VEnv) : Term = when (this) {
        is TTop -> this
        is TLocal -> this
        is TMeta -> if (slot.solved && slot.unfoldable) slot.term!! else this
        is TLet -> TLet(name, type.inline(ctx, lvl, vs), bound.inline(ctx, lvl, vs), body.inline(ctx, lvl + 1, vs.skip()))
        is TApp -> when (val x = lhs.inlineSp(ctx, lvl, vs)) {
            is Either.Left -> x.it.app(icit, rhs.eval(ctx, vs)).quote(lvl)
            is Either.Right -> TApp(icit, x.it, rhs.inline(ctx, lvl, vs))
        }
        is TLam -> TLam(name, icit, type.inline(ctx, lvl, vs), body.inline(ctx, lvl + 1, vs.skip()))
        is TPi -> TPi(name, icit, bound.inline(ctx, lvl, vs), body.inline(ctx, lvl + 1, vs.skip()))
        TUnit -> this
        TIrrelevant -> this
        is TForeign -> this
        is TNat -> this
        is TPair -> TODO()
        is TProj1 -> TODO()
        is TProj2 -> TODO()
        is TProjF -> TODO()
        is TSg -> TODO()
    }

    private fun inlineSp(ctx: MontunoContext, lvl: Lvl, vs: VEnv): Either<Val, Term> = when(this) {
        is TMeta -> when {
            slot.solved && slot.unfoldable -> Either.Left(slot.value!!)
            else -> Either.Right(this)
        }
        is TApp -> when (val x = lhs.inlineSp(ctx, lvl, vs)) {
            is Either.Left -> Either.Left(x.it.app(icit, rhs.eval(ctx, vs)))
            is Either.Right -> Either.Right(TApp(icit, x.it, rhs.inline(ctx, lvl, vs)))
        }
        else -> Either.Right(this.inline(ctx, lvl, vs))
    }

    fun isUnfoldable(): Boolean = when (this) {
        is TLocal -> true
        is TMeta -> true
        is TTop -> true
        is TUnit -> true
        is TLam -> body.isUnfoldable()
        else -> false
    }
}