package montuno.truffle

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import montuno.common.*
import montuno.syntax.Loc
import montuno.syntax.PreTerm

class MontunoTruffleContext(lang: TruffleLanguage<*>, env: TruffleLanguage.Env) : TopLevelContext<Term, Val>() {
    override val topScope = TopLevelScope(lang, this, env)

    fun getTopTerm(lvl: Lvl): Term = topScope.entries[lvl.it].defn!!
    fun getTopVal(lvl: Lvl): Val = when (val top = topScope.entries[lvl.it].defn) {
        null -> valFactory.top(lvl, topScope.entries[lvl.it])
        else -> todo //top.first.callTarget.call()
    }

    fun getMetaForce(meta: Meta): Any = metas[meta.i][meta.j].let { if (it.solved) it.value!! else VMeta(meta, emptyArray(), it) }

    override fun makeLocalContext(): LocalLevelContext<Term, Val> = LocalContext(this, LocalEnv(ntbl, emptyArray()))
    override val valFactory: ValFactory<Term, Val> = object : ValFactory<Term, Val> {
        override fun top(lvl: Lvl, slot: TopEntry<Term, Val>) = VTop(lvl, emptyArray(), slot)
        override fun meta(meta: Meta, slot: MetaEntry<Term, Val>) = VMeta(meta, emptyArray(), slot)
        override fun unit() = VUnit
        override fun nat(n: Int) = VNat(n)
        override fun local(ix: Lvl): Val = VLocal(ix, emptyArray())
    }
    override val termFactory: TermFactory<Term, Val> = object : TermFactory<Term, Val> {
        override fun meta(meta: Meta, slot: MetaEntry<Term, Val>) = TMetaNodeGen.create(meta, null)
        override fun local(ix: Ix) = TLocal(ix, null)
        override fun app(icit: Icit, l: Term, r: Term) = TApp(icit, l, r, null)
        override fun unit() = TUnit(null)
        override fun nat(n: Int) = TNat(n, null)
        override fun top(lvl: Lvl, slot: TopEntry<Term, Val>): Term = TTopNodeGen.create(lvl, null)
    }
}

data class LocalContext(
    override val top: MontunoTruffleContext,
    override val env: LocalEnv<Val>,
    val fd: FrameDescriptor = FrameDescriptor(),
    val fdLvl: Lvl = Lvl(0),
) : LocalLevelContext<Term, Val>() {
    override fun infer(mi: MetaInsertion, pre: PreTerm): Pair<Term, Val> = todo
    override fun inferVar(n: String): Pair<Term, Val> = todo
    override fun check(e: PreTerm, v: Val): Term = todo
    override fun eval(t: Term): Val = todo
    override fun pretty(t: Term): String = todo
    override fun inline(t: Term): Term = todo
    override fun isUnfoldable(t: Term): Boolean = todo
    override fun markOccurs(occurs: IntArray, blockIx: Int, t: Term) = todo
    override fun quote(t: Val, unfold: Boolean, depth: Lvl): Term = todo
    override fun force(t: Val, unfold: Boolean): Val = todo

    override fun bind(loc: Loc, n: String, inserted: Boolean, ty: Val): LocalLevelContext<Term, Val> =
        LocalContext(top, env.bind(loc, n, inserted, ty))
    override fun define(loc: Loc, n: String, tm: Val, ty: Val): LocalLevelContext<Term, Val> =
        LocalContext(top, env.define(loc, n, tm, ty))
}
