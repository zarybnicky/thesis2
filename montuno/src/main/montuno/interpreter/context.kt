package montuno.interpreter

import com.oracle.truffle.api.TruffleLanguage
import montuno.Lvl
import montuno.Meta
import montuno.interpreter.scope.*
import montuno.syntax.Loc
import montuno.truffle.Compiler

class LocalContext(val ctx: MontunoContext, val env: LocalEnv) {
    fun bind(loc: Loc, n: String?, inserted: Boolean, ty: Val): LocalContext = LocalContext(ctx, env.bind(loc, n, inserted, ty))
    fun define(loc: Loc, n: String, tm: Val, ty: Val): LocalContext = LocalContext(ctx, env.define(loc, n, tm, ty))

    fun eval(t: Term): Val = t.eval(ctx, env.vals)
    fun quote(v: Val, unfold: Boolean, depth: Lvl): Term = v.quote(depth, unfold)

    fun pretty(t: Term): String = t.pretty(NameEnv(ctx.ntbl)).toString()
    fun inline(t: Term): Term = t.inline(ctx, Lvl(0), env.vals)

    fun markOccurs(occurs: IntArray, blockIx: Int, t: Term): Unit = when {
        t is TMeta && t.meta.i == blockIx -> occurs[t.meta.j] += 1
        t is TLet -> { markOccurs(occurs, blockIx, t.type); markOccurs(occurs, blockIx, t.bound); markOccurs(occurs, blockIx, t.body) }
        t is TApp -> { markOccurs(occurs, blockIx, t.lhs); markOccurs(occurs, blockIx, t.rhs); }
        t is TLam -> markOccurs(occurs, blockIx, t.body)
        t is TPi -> { markOccurs(occurs, blockIx, t.bound); markOccurs(occurs, blockIx, t.body) }
        else -> {}
    }
}

class LocalEnv(
    val nameTable: NameTable,
    val vals: VEnv = VEnv(),
    val types: List<Val> = listOf(),
    val names: List<String?> = listOf()
) {
    val lvl: Lvl get() = Lvl(names.size)
    val locals: Array<Boolean> get() {
        val res = mutableListOf<Boolean>()
        for (i in types.indices) res.add(vals.it[i] == null)
        return res.toTypedArray()
    }
    fun bind(loc: Loc, n: String?, inserted: Boolean, ty: Val) = LocalEnv(
        if (n == null) nameTable else nameTable.withName(n, NILocal(loc, lvl, inserted)),
        vals.skip(),
        types + ty,
        names + n
    )
    fun define(loc: Loc, n: String, tm: Val, ty: Val) = LocalEnv(
        nameTable.withName(n, NILocal(loc, lvl, false)),
        vals + tm,
        types + ty,
        names + n
    )
}

class MontunoContext(val env: TruffleLanguage.Env) {
    val top = TopScope(env)
    var ntbl = NameTable()
    var loc: Loc = Loc.Unavailable
    var metas = MetaContext(this)
    val builtins = BuiltinScope(this)
    lateinit var compiler: Compiler

    fun makeLocalContext() = LocalContext(this, LocalEnv(ntbl))
    fun reset() {
        top.reset()
        metas = MetaContext(this)
        loc = Loc.Unavailable
        ntbl = NameTable()
    }
    fun registerMeta(m: Meta, v: Val) {
        val slot = metas[m]
        slot.solved = true
        slot.term = v.quote(Lvl(0), false)
        slot.unfoldable = slot.term!!.isUnfoldable()
        slot.value = v
    }
    fun registerTop(name: String, loc: Loc, defn: Term?, type: Term) {
        ntbl.addName(name, NITop(loc, Lvl(top.it.size)))
        top.it.add(TopEntry(name, loc, defn, defn?.eval(this, VEnv()), type, type.eval(this, VEnv())))
    }
    fun getBuiltin(name: String, loc: Loc = Loc.Unavailable): Pair<Term, Val> {
        val ni = ntbl[name]
        if (ni.isEmpty()) {
            val (body, type) = builtins.getBuiltin(name)
            ntbl.addName(name, NITop(loc, Lvl(top.it.size)))
            top.it.add(TopEntry(name, loc,null, body, type.quote(Lvl(0), false), type))
        }
        return makeLocalContext().inferVar(name)
    }
    fun getBuiltinVal(name: String) = makeLocalContext().inferVar(name).first.eval(this, VEnv())
}
