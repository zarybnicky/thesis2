package montuno.interpreter

fun Term.inlineSp(lvl: Lvl, vs: VEnv): Either<Val, Term> = when(this) {
    is TMeta -> {
        val it = MontunoPure.top[meta]
        when {
            it is MetaSolved && it.unfoldable -> Either.Left(it.tm.eval(emptyVEnv))
            else -> Either.Right(this)
        }
    }
    is TApp -> when (val x = l.inlineSp(lvl, vs)) {
        is Either.Left -> Either.Left(x.it.app(icit, lazy { r.eval(vs) }))
        is Either.Right -> Either.Right(TApp(icit, x.it, r.inline(lvl, vs)))
    }
    else -> Either.Right(this.inline(lvl, vs))
}

fun Term.inline(lvl: Lvl, vs: VEnv) : Term = when (this) {
    is TTop -> this
    is TLocal -> this
    is TMeta -> {
        val it = MontunoPure.top[meta]
        when {
            it is MetaSolved && it.unfoldable -> it.tm.eval(emptyVEnv).quote(lvl)
            else -> this
        }
    }
    is TLet -> TLet(n, ty.inline(lvl, vs), v.inline(lvl, vs), tm.inline(lvl + 1, vs.skip()))
    is TApp -> when (val x = l.inlineSp(lvl, vs)) {
        is Either.Left -> x.it.app(icit, lazy { r.eval(vs) }).quote(lvl)
        is Either.Right -> TApp(icit, x.it, r.inline(lvl, vs))
    }
    is TLam -> TLam(n, icit, tm.inline(lvl + 1, vs.skip()))
    is TFun -> TFun(l.inline(lvl, vs), r.inline(lvl, vs))
    is TPi -> TPi(n, icit, arg.inline(lvl, vs), tm.inline(lvl + 1, vs.skip()))
    TU -> this
    TIrrelevant -> this
    is TForeign -> this
    is TNat -> this
}

fun LocalContext.simplifyMetaBlock() {
    val metas = MontunoPure.top.metas
    if (metas.size == 0) return
    val blockIx = metas.size - 1
    val block = metas[blockIx]
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
            val t = meta.tm.inline(Lvl(0), emptyVEnv)
            val s = MetaSolved(meta.loc, t.gvEval(emptyVEnv, emptyGEnv), t)
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
