package montuno.interpreter

import com.oracle.truffle.api.TruffleLanguage
import montuno.Lvl
import montuno.Meta
import montuno.interpreter.scope.*
import montuno.syntax.Loc
import montuno.truffle.Compiler

class LocalContext(val ctx: MontunoContext, val env: LocalEnv) {
    fun bind(loc: Loc, n: String, inserted: Boolean, ty: Val): LocalContext = LocalContext(ctx, env.bind(loc, n, inserted, ty))
    fun define(loc: Loc, n: String, tm: Val, ty: Val): LocalContext = LocalContext(ctx, env.define(loc, n, tm, ty))

    fun eval(t: Term): Val = t.eval(ctx, env.vals)
    fun quote(v: Val, unfold: Boolean, depth: Lvl): Term = v.quote(depth, unfold)

    fun newMeta() = ctx.metas.newMeta(env.lvl, env.boundLevels)

    fun pretty(t: Term): String = t.pretty(NameEnv(ctx.ntbl)).toString()
    fun inline(t: Term): Term = t.inline(ctx, Lvl(0), env.vals)
    fun force(v: Val, unfold: Boolean): Val = v.force(unfold)

    fun markOccurs(occurs: IntArray, blockIx: Int, t: Term): Unit = when {
        t is TMeta && t.meta.i == blockIx -> occurs[t.meta.j] += 1
        t is TLet -> { markOccurs(occurs, blockIx, t.type); markOccurs(occurs, blockIx, t.bound); markOccurs(occurs, blockIx, t.body) }
        t is TApp -> { markOccurs(occurs, blockIx, t.lhs); markOccurs(occurs, blockIx, t.rhs); }
        t is TLam -> markOccurs(occurs, blockIx, t.body)
        t is TPi -> { markOccurs(occurs, blockIx, t.bound); markOccurs(occurs, blockIx, t.body) }
        t is TFun -> { markOccurs(occurs, blockIx, t.lhs); markOccurs(occurs, blockIx, t.rhs) }
        else -> {}
    }
}

class LocalEnv(
    val nameTable: NameTable,
    val vals: VEnv = VEnv(),
    val types: List<Val> = listOf(),
    val names: List<String> = listOf(),
    val boundLevels: IntArray = IntArray(0)
) {
    val lvl: Lvl get() = Lvl(names.size)
    fun bind(loc: Loc, n: String, inserted: Boolean, ty: Val) = LocalEnv(
        nameTable.withName(n, NILocal(loc, lvl, inserted)),
        vals.skip(),
        types + ty,
        names + n,
        boundLevels.plus(lvl.it)
    )
    fun define(loc: Loc, n: String, tm: Val, ty: Val) = LocalEnv(
        nameTable.withName(n, NILocal(loc, lvl, false)),
        vals + tm,
        types + ty,
        names + n,
        boundLevels
    )
}

class MontunoContext(val env: TruffleLanguage.Env) {
    val top = TopScope(env)
    var ntbl = NameTable()
    var loc: Loc = Loc.Unavailable
    var metas = MetaContext(this)
    lateinit var compiler: Compiler

    fun makeLocalContext() = LocalContext(this, LocalEnv(ntbl))
    fun reset() {
        top.reset()
        metas = MetaContext(this)
        loc = Loc.Unavailable
        ntbl = NameTable()
    }

    fun compileMeta(m: Meta, term: Term, arity: Int) {
        val slot = metas[m]
        slot.solved = true
        slot.unfoldable = term.isUnfoldable()
        slot.term = term
        slot.value = makeLocalContext().eval(term)
        slot.callTarget = compiler.compile(term, arity)
    }
    fun compileTop(name: String, loc: Loc, defn: Term?, type: Term) {
        ntbl.addName(name, NITop(loc, Lvl(top.it.size)))
        val ct = if (defn != null) compiler.compile(defn, defn.arity) else null
        val ctx = makeLocalContext()
        val typeV = ctx.eval(type)
        val defnV = if (defn != null) ctx.eval(defn) else null
        top.it.add(TopEntry(name, loc, ct, defn, defnV, type, typeV))
    }
}
