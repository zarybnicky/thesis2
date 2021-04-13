package montuno.interpreter.meta

import montuno.interpreter.*

fun Term.inlineSp(top: TopContext, lvl: Lvl, vs: VEnv): Either<Val, Term> = when(this) {
    is TMeta -> {
        val it = top[meta]
        when {
            it is MetaSolved && it.unfoldable -> Left(it.tm.eval(top, emptyVEnv))
            else -> Right(this)
        }
    }
    is TApp -> when (val x = l.inlineSp(top, lvl, vs)) {
        is Left -> Left(x.it.app(top, icit, lazy { r.eval(top, vs) }))
        is Right -> Right(TApp(icit, x.it, r.inline(top, lvl, vs)))
    }
    else -> Right(this.inline(top, lvl, vs))
}

fun Term.inline(top: TopContext, lvl: Lvl, vs: VEnv) : Term = when (this) {
    is TTop -> this
    is TLocal -> this
    is TMeta -> {
        val it = top[meta]
        when {
            it is MetaSolved && it.unfoldable -> it.tm.eval(top, emptyVEnv).quote(top, lvl)
            else -> this
        }
    }
    is TLet -> TLet(n, ty.inline(top, lvl, vs), v.inline(top, lvl, vs), tm.inline(top, lvl + 1, vs.skip()))
    is TApp -> when (val x = l.inlineSp(top, lvl, vs)) {
        is Left -> x.it.app(top, icit, lazy { r.eval(top, vs) }).quote(top, lvl)
        is Right -> TApp(icit, x.it, r.inline(top, lvl, vs))
    }
    is TLam -> TLam(n, icit, tm.inline(top, lvl + 1, vs.skip()))
    is TFun -> TFun(l.inline(top, lvl, vs), r.inline(top, lvl, vs))
    is TPi -> TPi(n, icit, arg.inline(top, lvl, vs), tm.inline(top, lvl + 1, vs.skip()))
    TU -> this
    TIrrelevant -> this
    is TForeign -> this
}

fun LocalContext.simplifyMetaBlock(top: TopContext) {
    if (top.metas.size == 0) return
    val blockIx = top.metas.size - 1
    val block = top.metas[blockIx]
    val occurs = IntArray(block.size) { 0 }
    fun markOccurs(tm: Term): Unit = when {
        tm is TMeta && tm.meta.i == blockIx -> occurs[tm.meta.j] += 1
        tm is TLet -> { markOccurs(tm.ty); markOccurs(tm.v); markOccurs(tm.tm) }
        tm is TApp -> { markOccurs(tm.l); markOccurs(tm.r); }
        tm is TLam -> markOccurs(tm.tm)
        tm is TPi -> { markOccurs(tm.arg); markOccurs(tm.tm) }
        tm is TFun -> { markOccurs(tm.l); markOccurs(tm.r) }
        else -> {}
    }

    // 1. Inline already inlinable metas in block, check for unsolved metas
    // 2. Mark metas which don't occur in other metas as inlinable
    for ((ix, meta) in block.withIndex()) when (meta) {
        is MetaUnsolved -> throw ElabError(meta.loc, this, "Unsolved meta ${Meta(blockIx, ix)}")
        is MetaSolved -> {
            val t = meta.tm.inline(top, Lvl(0), emptyVEnv)
            val s = MetaSolved(meta.loc, t.gvEval(top, emptyVEnv, emptyGEnv), t, meta.unfoldable)
            block[ix] = s
            markOccurs(s.tm)
        }
    }
    for ((ix, meta) in block.withIndex()) when (meta) {
        is MetaUnsolved -> throw ElabError(meta.loc, this, "Unsolved meta ${Meta(blockIx, ix)}")
        is MetaSolved -> if (!meta.unfoldable && occurs[ix] == 0) {
            block[ix] = MetaSolved(meta.loc, meta.gv, meta.tm, true)
        }
    }
}
