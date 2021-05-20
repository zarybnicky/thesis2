package montuno.interpreter

import com.oracle.truffle.api.TruffleLanguage
import montuno.common.*
import montuno.syntax.Loc
import montuno.syntax.PreTerm

class MontunoPureContext(lang: TruffleLanguage<*>, env: TruffleLanguage.Env) : TopLevelContext<Term, Val>() {
    override val topScope = TopLevelScope(lang, this, env)
    override fun makeLocalContext(): LocalLevelContext<Term, Val> = LocalContext(this, LocalEnv(ntbl, emptyArray()))
    override val valFactory: ValFactory<Term, Val> = object : ValFactory<Term, Val> {
        override fun top(lvl: Lvl, slot: TopEntry<Term, Val>) = VTop(lvl, VSpine(), slot)
        override fun meta(meta: Meta, slot: MetaEntry<Term, Val>) = VMeta(meta, VSpine(), slot)
        override fun unit() = VUnit
        override fun nat(n: Int) = VNat(n)
        override fun local(ix: Lvl): Val = VLocal(ix)
    }
    override val termFactory: TermFactory<Term, Val> = object : TermFactory<Term, Val> {
        override fun meta(meta: Meta, slot: MetaEntry<Term, Val>) = TMeta(meta, slot)
        override fun local(ix: Ix) = TLocal(ix)
        override fun app(icit: Icit, l: Term, r: Term) = TApp(icit, l, r)
        override fun unit() = TU
        override fun nat(n: Int) = TNat(n)
        override fun top(lvl: Lvl, slot: TopEntry<Term, Val>): Term = TTop(lvl, slot)
    }
}

class LocalContext(
    override val top: TopLevelContext<Term, Val>,
    override val env: LocalEnv<Val>
) : LocalLevelContext<Term, Val>() {
    override fun eval(t: Term): Val = t.eval(env.vals)
    override fun quote(v: Val, unfold: Boolean, depth: Lvl): Term = v.quote(depth, unfold)
    override fun force(v: Val, unfold: Boolean): Val = v.force(unfold)
    override fun inline(t: Term): Term = t.inline(Lvl(0), emptyArray())
    override fun infer(mi: MetaInsertion, e: PreTerm): Pair<Term, Val> = infer(this, mi, e)
    override fun inferVar(n: String): Pair<Term, Val> = inferVar(this, n)
    override fun check(e: PreTerm, v: Val): Term = check(this, e, v)
    override fun pretty(t: Term): String = t.pretty(NameEnv(top.ntbl)).toString()
    override fun markOccurs(occurs: IntArray, blockIx: Int, t: Term): Unit = when {
        t is TMeta && t.meta.i == blockIx -> occurs[t.meta.j] += 1
        t is TLet -> { markOccurs(occurs, blockIx, t.ty); markOccurs(occurs, blockIx, t.v); markOccurs(occurs, blockIx, t.tm) }
        t is TApp -> { markOccurs(occurs, blockIx, t.l); markOccurs(occurs, blockIx, t.r); }
        t is TLam -> markOccurs(occurs, blockIx, t.tm)
        t is TPi -> { markOccurs(occurs, blockIx, t.arg); markOccurs(occurs, blockIx, t.tm) }
        t is TFun -> { markOccurs(occurs, blockIx, t.l); markOccurs(occurs, blockIx, t.r) }
        else -> {}
    }
    override fun isUnfoldable(t: Term): Boolean = when (t) {
        is TLocal -> true
        is TMeta -> true
        is TTop -> true
        is TU -> true
        is TLam -> isUnfoldable(t.tm)
        else -> false
    }

    override fun bind(loc: Loc, n: String, inserted: Boolean, ty: Val): LocalLevelContext<Term, Val> = LocalContext(top, env.bind(loc, n, inserted, ty))
    override fun define(loc: Loc, n: String, tm: Val, ty: Val): LocalLevelContext<Term, Val> = LocalContext(top, env.define(loc, n, tm, ty))
}
