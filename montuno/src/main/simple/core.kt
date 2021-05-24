package simple

import montuno.Ix
import montuno.Lvl
import montuno.syntax.*

sealed class Term
data class TVar(val ix: Ix) : Term()
data class TNat(val n: Int) : Term()
data class TApp(val arg: Term, val body: Term) : Term()
data class TLam(val arg: String, val body: Term) : Term()
data class TPi(val arg: String, val ty: Term, val body: Term) : Term()
data class TLet(val n: String, val ty: Term, val bind: Term, val body: Term) : Term()
object TU : Term()

sealed class Val
data class VVar(val lvl: Lvl) : Val()
data class VNat(val n: Int) : Val()
data class VApp(val arg: Val, val body: Val) : Val()
data class VLam(val arg: String, val clo: Closure) : Val()
data class VPi(val arg: String, val ty: Val, val clo: Closure) : Val()
object VU : Val()

data class Closure(val env: Env?, val tm: Term)
infix fun Closure.ap(v: Val): Val = tm.eval(Env(v, env))

fun Term.eval(env: Env?): Val = when (this) {
    is TVar -> env[ix]
    is TNat -> VNat(n)
    is TApp -> when (val t = arg.eval(env)) {
        is VLam -> t.clo ap body.eval(env)
        else -> VApp(t, body.eval(env))
    }
    is TLam -> VLam(arg, Closure(env, body))
    is TU -> VU
    is TPi -> VPi(arg, ty.eval(env), Closure(env, body))
    is TLet -> body.eval(Env(bind.eval(env), env))
}

fun Term.nf(env: Env?): Term = eval(env).quote(Lvl(env.len()))

fun Val.quote(cur: Lvl): Term = when (this) {
    is VVar -> TVar(cur.toIx(lvl))
    is VNat -> TNat(n)
    is VApp -> TApp(arg.quote(cur), body.quote(cur))
    is VLam -> TLam(arg, (clo ap VVar(cur)).quote(cur + 1))
    is VU -> TU
    is VPi -> TPi(arg, ty.quote(cur), (clo ap VVar(cur)).quote(cur + 1))
}

fun conv(l: Lvl, t: Val, u: Val): Boolean = when {
    t is VNat && u is VNat -> t.n == u.n
    t is VVar && u is VVar -> t.lvl == u.lvl
    t is VApp && u is VApp -> conv(l, t.arg, u.arg) && conv(l, t.body, u.body)
    t is VLam && u is VLam -> conv(l + 1, t.clo ap VVar(l), t.clo ap VVar(l))
    t is VLam -> conv(l + 1, t.clo ap VVar(l), VApp(u, VVar(l)))
    u is VLam -> conv(l + 1, u.clo ap VVar(l), VApp(t, VVar(l)))
    t is VU && u is VU -> true
    t is VPi && u is VPi -> conv(l, t.ty, u.ty) && conv(l + 1, t.clo ap VVar(l), u.clo ap VVar(l))
    else -> false
}

data class Ctx(val env: Env?, val types: TypeEnv?, var loc: Loc, val l: Lvl)
fun Ctx.bind(n: String, ty: Val): Ctx =
    Ctx(env + VVar(l), types.cons(n, ty), loc, l + 1)
fun Ctx.define(n: String, v: Val, ty: Val): Ctx =
    Ctx(env + v, types.cons(n, ty), loc, l + 1)
fun Ctx.primitive(n: String, t: String, v: String): Ctx {
    val typ = infer(parsePreSyntaxExpr(t)).first.eval(env)
    return define(n, check(parsePreSyntaxExpr(v), typ).eval(env), typ)
}
fun Ctx.show(v: Val): String = v.quote(l).pretty(types.toNames()).toString()
fun Ctx.show(t: Term): String = t.nf(env).pretty(types.toNames()).toString()
inline fun <A> Ctx.withPos(newLoc: Loc, run: Ctx.() -> A): A {
    val oldLoc = this.loc
    this.loc = newLoc
    val a = run()
    this.loc = oldLoc
    return a
}

@Throws(TypeError::class)
fun Ctx.check(r: PreTerm, a: Val): Term = when {
    r is RLam && a is VPi -> withPos(r.loc) { TLam(r.arg.name!!, bind(r.arg.name!!, a.ty).check(r.body, a.clo.ap(VVar(l)))) }
    r is RLet -> withPos(r.loc) {
        val ty = check(r.type!!, VU)
        val vty by lazy { ty.eval(env) }
        val b = check(r.defn, vty)
        val vb by lazy { b.eval(env) }
        TLet(r.n, ty, b, define(r.n, vty, vb).check(r.body, a))
    }
    else -> withPos(r.loc) {
        val (t, tty) = infer(r)
        if (!conv(l, tty, a)) {
            throw TypeError("type mismatch, expected ${show(a)}, instead got ${show(tty)}", loc)
        }
        t
    }
}

@Throws(TypeError::class)
fun Ctx.infer(r: PreTerm): Pair<Term, Val> = when (r) {
    is RVar -> types.find(r.n)
    is RNat -> TNat(r.n) to env[types.find("Nat").first.ix]
    is RApp -> withPos(r.loc) {
        val (t, tty) = infer(r.rator)
        when (tty) {
            is VPi -> {
                val u = check(r.rand, tty.ty)
                TApp(t, u) to tty.clo.ap(u.eval(env))
            }
            else -> throw TypeError("expected a function type, instead inferred:\n${show(tty)}", loc)
        }
    }
    is RHole -> throw TypeError("can't infer type for a hole", loc)
    is RLam -> throw TypeError("can't infer type for a lambda", loc)
    is RU -> TU to VU
    is RPi -> withPos(r.loc) {
        val a = check(r.type, VU)
        val b = bind(r.bind.name!!, a.eval(env)).check(r.body, VU)
        TPi(r.bind.name!!, a, b) to VU
    }
    is RLet -> withPos(r.loc) {
        val a = check(r.type!!, VU)
        val va by lazy { a.eval(env) }
        val t = check(r.defn, va)
        val vt by lazy { t.eval(env) }
        val (u, uty) = define(r.n, vt, va).infer(r.body)
        TLet(r.n, a, t, u) to uty
    }
    is RPair -> TODO()
    is RProj1 -> TODO()
    is RProj2 -> TODO()
    is RProjF -> TODO()
    is RSg -> TODO()
}

class TypeError(message: String, val loc: Loc) : Exception(message) {
    override fun toString(): String = message.orEmpty()
}

fun nfMain(input: String) {
    val ctx = Ctx(null, null, Loc.Unavailable, Lvl(0))
        .primitive("Nat", "*", "*")
        .primitive("id", "(A : *) -> A -> A", "\\A x. x")
        .primitive("const", "(A B : *) -> A -> B -> A", "\\A B x y. x")
    try {
        val (t, a) = ctx.infer(parsePreSyntaxExpr(input))
        print("${ctx.show(t)} : ${ctx.show(a)}")
    } catch (t: TypeError) {
        print("${t}\nin code:\n${t.loc.string(input)}")
    }
}
