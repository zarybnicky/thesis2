package montuno.interpreter

fun Term.inlineSp(ctx: TopContext, l: Lvl, vs: VEnv): Either<Val, Term> = when(this) {
    is TMeta -> {
        val it = ctx.metas[meta.i][meta.j]
        when {
            it is MetaSolved && it.unfoldable -> Left(it.gp.tm.eval())
            else -> Right(this)
        }
    }
    is TApp -> {

    }
    else -> Right(this.inline(ctx, l, vs))
}

fun Term.inline(ctx: TopContext, lvl: Lvl, vs: VEnv) : Term = when (this) {
    is TApp -> when (val x = l.inlineSp(ctx, lvl, vs)) {
        is Left -> x.it.app(icit, r.eval()).quote(lvl)
        is Right -> TApp(icit, x.it, r.inline(ctx, lvl, vs))
    }
    is TLet -> TLet(n, ty.inline(ctx, lvl, vs), v.inline(ctx, lvl, vs), vs.with(null) { tm.inline(ctx, lvl + 1, vs) })
    is TPi -> TPi(n, icit, arg.inline(ctx, lvl, vs), tm.inline(ctx, lvl + 1, vs))
    is TLam -> TLam(n, icit, tm.inline(ctx, lvl + 1, vs))
    is TFun -> TFun(l.inline(ctx, lvl, vs), r.inline(ctx, lvl, vs))
    is TMeta -> {
        val it = ctx.metas[meta.i][meta.j]
        when {
            it is MetaSolved && it.unfoldable -> it.gp.tm.eval().quote(l)
            else -> this
        }
    }
    is TTop -> this
    is TLocal -> this
    TU -> this
    TIrrelevant -> this
}

fun Term.eval(env: VEnv): Val = TODO()

fun VCl.inst(v: Val): Val = env.with(v) { tm.eval(env) }

fun Val.app(icit: Icit, r: Val) = when (this) {
    is VLam -> cl.inst(r)
    is VNe -> {
        spine.addFirst(icit to r)
        VNe(head, spine)
    }
    else -> TODO("impossible")
}
