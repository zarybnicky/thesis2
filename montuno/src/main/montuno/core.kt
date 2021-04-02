package montuno

import montuno.syntax.*

sealed class Term
data class TVar(val ix: Ix) : Term()
data class TLitNat(val n: Int) : Term()
data class TApp(val arg: Term, val body: Term) : Term()
data class TLam(val arg: String, val body: Term) : Term()
object TStar : Term()
object TNat : Term()
data class TPi(val arg: String, val ty: Term, val body: Term) : Term()
data class TLet(val n: String, val ty: Term, val bind: Term, val body: Term) : Term()

sealed class Val
data class VVar(val lvl: Lvl) : Val()
data class VLitNat(val n: Int) : Val()
data class VApp(val arg: Val, val body: Val) : Val()
data class VLam(val arg: String, val clo: Closure) : Val()
object VStar : Val()
object VNat : Val()
data class VPi(val arg: String, val ty: Val, val clo: Closure) : Val()

data class Closure(val env: Env?, val tm: Term)
infix fun Closure.ap(v: Val): Val = tm.eval(Env(v, env))

fun Term.eval(env: Env?): Val = when (this) {
    is TVar -> env.ix(ix)
    is TLitNat -> VLitNat(n)
    is TApp -> when (val t = arg.eval(env)) {
        is VLam -> t.clo ap body.eval(env)
        else -> VApp(t, body.eval(env))
    }
    is TLam -> VLam(arg, Closure(env, body))
    is TStar -> VStar
    is TNat -> VNat
    is TPi -> VPi(arg, ty.eval(env), Closure(env, body))
    is TLet -> body.eval(Env(bind.eval(env), env))
}

fun Term.nf(env: Env?): Term = eval(env).quote(Lvl(env.len()))

fun Val.quote(cur: Lvl): Term = when (this) {
    is VVar -> TVar(cur.toIx(lvl))
    is VLitNat -> TLitNat(n)
    is VApp -> TApp(arg.quote(cur), body.quote(cur))
    is VLam -> TLam(arg, clo.ap(VVar(cur)).quote(cur.inc()))
    is VStar -> TStar
    is VNat -> TNat
    is VPi -> TPi(arg, ty.quote(cur), clo.ap(VVar(cur)).quote(cur.inc()))
}

fun conv(l: Lvl, t: Val, u: Val): Boolean = when {
    t is VLitNat && u is VLitNat -> t.n == u.n
    t is VVar && u is VVar -> t.lvl == u.lvl
    t is VApp && u is VApp -> conv(l, t.arg, u.arg) && conv(l, t.body, u.body)
    t is VLam && u is VLam -> conv(l.inc(), t.clo.ap(VVar(l)), t.clo.ap(VVar(l)))
    t is VLam -> conv(l.inc(), t.clo.ap(VVar(l)), VApp(u, VVar(l)))
    u is VLam -> conv(l.inc(), u.clo.ap(VVar(l)), VApp(t, VVar(l)))
    t is VStar && u is VStar -> true
    t is VNat && u is VNat -> true
    t is VPi && u is VPi -> conv(l, t.ty, u.ty) && conv(l.inc(), t.clo.ap(VVar(l)), u.clo.ap(VVar(l)))
    else -> false
}

data class Ctx(val env: Env?, val types: Types?, var loc: Loc, val l: Lvl)
fun Ctx.bind(n: String, ty: Val): Ctx =
    Ctx(env.cons(VVar(l)), types.cons(n, ty), loc, l.inc())
fun Ctx.define(n: String, v: Val, ty: Val): Ctx =
    Ctx(env.cons(v), types.cons(n, ty), loc, l.inc())
fun Ctx.primitive(n: String, t: String, v: String): Ctx {
    val typ = infer(parseExpr(t)).first.eval(env)
    return define(n, check(parseExpr(v), typ).eval(env), typ)
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
fun Ctx.check(r: Raw, a: Val): Term = when {
    r is RLam && a is VPi -> withPos(r.loc) { TLam(r.arg, bind(r.arg, a.ty).check(r.body, a.clo.ap(VVar(l)))) }
    r is RLet -> withPos(r.loc) {
        val ty = check(r.ty, VStar)
        val vty by lazy { ty.eval(env) }
        val b = check(r.bind, vty)
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
fun Ctx.infer(r: Raw): Pair<Term, Val> = when (r) {
    is RVar -> types.find(r.n)
    is RLitNat -> TLitNat(r.n) to VNat
    is RApp -> withPos(r.loc) {
        val (t, tty) = infer(r.arg)
        when (tty) {
            is VPi -> {
                val u = check(r.body, tty.ty)
                TApp(t, u) to tty.clo.ap(u.eval(env))
            }
            else -> throw TypeError("expected a function type, instead inferred:\n${show(tty)}", loc)
        }
    }
    is RLam -> throw TypeError("can't infer type for lambda", loc)
    is RStar -> TStar to VStar
    is RNat -> TNat to VStar
    is RPi -> withPos(r.loc) {
        val a = check(r.ty, VStar)
        val b = bind(r.arg, a.eval(env)).check(r.body, VStar)
        TPi(r.arg, a, b) to VStar
    }
    is RLet -> withPos(r.loc) {
        val a = check(r.ty, VStar)
        val va by lazy { a.eval(env) }
        val t = check(r.bind, va)
        val vt by lazy { t.eval(env) }
        val (u, uty) = define(r.n, vt, va).infer(r.body)
        TLet(r.n, a, t, u) to uty
    }
}

fun topToExpr(x: Raw, xs: List<RawTop>): Raw = xs.foldRight(x, { l, r -> when (l) {
    is RDefn -> RLet(l.loc, l.n, l.ty ?: RVar(Loc.Unavailable, "_"), l.tm, r)
    is RDecl -> TODO()
    is RElab -> r
}})

fun nfMain(input: String) {
    val raw = topToExpr(parseExpr("id ({A B} -> A -> B -> A) const"), parseModule(input))
    val ctx = Ctx(null, null, Loc.Line(0), Lvl(0))
        .primitive("id", "(A : *) -> A -> A", "\\A x. x")
        .primitive("const", "(A B : *) -> A -> B -> A", "\\A B x y. x")
    try {
        val (t, a) = ctx.infer(raw)
        print("${ctx.show(t)} : ${ctx.show(a)}")
    } catch (t: TypeError) {
        print("${t}\nin code:\n${t.loc.string(input)}")
    }
}

const val ex0 = "foo : * = *. bar : * = id id."

const val ex1 = "%nf id ((A B : *) -> A -> B -> A) const"

val ex2 = """
    Nat' : * = (N : *) -> (N -> N) -> N -> N.
    five : Nat' = \N s z. s (s (s (s (s z)))).
    add  : Nat' -> Nat' -> Nat' = \a b N s z. a N s (b N s z).
    mul  : Nat' -> Nat' -> Nat' = \a b N s z. a N (b N s) z.
    ten      : Nat' = add five five.
    hundred  : Nat' = mul ten ten.
    thousand : Nat' = mul ten hundred.
    %nf thousand
""".trimIndent()

val ex4 = """
    Vec : * → Nat → * = λ a n. (V : Nat → *) → V 0 → ((n : Nat) → a → V n → V (n + 1)) → V n.
    vnil : (a : *) → Vec a 0 = λ V n c. n.
    vcons : (a n : *) → a → Vec a n → Vec a (n + 1) = λ a as V n c. c a (as V n c).
    %nf vec1 = vcons true (vcons false (vcons true vnil)).
""".trimIndent()

const val ex3 = "%nf id Nat 5"

fun main() = nfMain(ex1)