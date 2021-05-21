package montuno.interpreter

import montuno.*
import montuno.interpreter.scope.NILocal
import montuno.interpreter.scope.NITop
import montuno.syntax.*
import montuno.truffle.PureClosure

fun LocalContext.check(t: PreTerm, want: Val): Term {
    ctx.loc = t.loc
    val v = want.force(true)
    return when {
        t is RHole -> newMeta()
        t is RLet -> {
            val a = check(t.type, VUnit)
            val va = eval(a)
            val tm = check(t.defn, va)
            val vt = eval(tm)
            val u = define(t.loc, t.n, va, vt).check(t.body, want)
            TLet(t.n, a, tm, u)
        }
        t is RLam && v is VPi && t.ni.match(v) ->
            TLam(t.n, v.icit, bind(t.loc, t.n, false, v.bound).check(t.body, v.closure.inst(VLocal(env.lvl))))
        t is RLam && v is VFun && t.ni is NIExpl ->
            TLam(t.n, Icit.Expl, bind(t.loc, t.n, false, v.lhs).check(t.body, v.rhs))
        v is VPi && v.icit == Icit.Impl ->
            TLam(v.name, Icit.Impl, bind(t.loc, v.name, true, v.bound).check(t, v.closure.inst(VLocal(env.lvl))))
        else -> {
            val (tt, has) = infer(MetaInsertion.Yes, t)
            try {
                unify(has, want)
            } catch (e: ElabError) {
                throw ElabError(ctx.loc, "Failed to unify $has and $want")
            }
            tt
        }
    }
}
fun NameOrIcit.match(v: VPi): Boolean = when (this) {
    NIImpl -> v.icit == Icit.Impl
    NIExpl -> v.icit == Icit.Expl
    is NIName -> v.name == n
}

fun LocalContext.insertMetas(mi: MetaInsertion, c: Pair<Term, Val>): Pair<Term, Val> {
    var (t, va) = c
    when (mi) {
        MetaInsertion.No -> {}
        MetaInsertion.Yes -> {
            var vaf = va.force(false)
            while (vaf is VPi && vaf.icit == Icit.Impl) {
                val m = newMeta()
                t = TApp(Icit.Impl, t, m)
                va = vaf.closure.inst(eval(m))
                vaf = va.force(false)
            }
        }
        is MetaInsertion.UntilName -> {
            var vaf = va.force(false)
            while (vaf is VPi && vaf.icit == Icit.Impl) {
                if (vaf.name == mi.n) {
                    return t to va
                }
                val m = newMeta()
                t = TApp(Icit.Impl, t, m)
                va = vaf.closure.inst(eval(m))
                vaf = va.force(false)
            }
            throw ElabError(ctx.loc, "No named arg ${mi.n}")
        }
    }
    return t to va
}

fun LocalContext.inferVar(n: String): Pair<Term, Val> {
    for (ni in env.nameTable[n].asReversed()) {
        return when {
            ni is NITop -> TTop(ni.lvl, ctx.top[ni.lvl]) to ctx.top[ni.lvl].typeV
            ni is NILocal && !ni.inserted -> TLocal(ni.lvl.toIx(env.lvl)) to env.types[ni.lvl.it]
            else -> continue
        }
    }
    throw ElabError(null, "Variable $n out of scope")
}

fun LocalContext.infer(mi: MetaInsertion, r: PreTerm): Pair<Term, Val> {
    ctx.loc = r.loc
    return when (r) {
        is RU -> TUnit to VUnit
        is RNat -> TNat(r.n) to inferVar("Nat").first.eval(env.vals)
        is RVar -> insertMetas(mi, inferVar(r.n))
        is RStopMeta -> infer(MetaInsertion.No, r.body)
        is RForeign -> {
            val a = check(r.type, VUnit)
            TForeign(r.lang, r.eval, a) to eval(a)
        }
        is RHole -> {
            val m1 = newMeta()
            val m2 = newMeta()
            m1 to eval(m2)
        }
        is RPi -> {
            val a = check(r.type, VUnit)
            val b = bind(r.loc, r.n, false, eval(a)).check(r.body, VUnit)
            TPi(r.n, r.icit, a, b) to VUnit
        }
        is RFun -> TFun(check(r.l, VUnit), check(r.r, VUnit)) to VUnit
        is RLet -> {
            val a = check(r.type, VUnit)
            val gva = eval(a)
            val t = check(r.defn, gva)
            val gvt = eval(t)
            val (u, gvb) = define(r.loc, r.n, gvt, gva).infer(mi, r.body)
            TLet(r.n, a, t, u) to gvb
        }
        is RApp -> {
            val ins = when (r.ni) {
                is NIName -> MetaInsertion.UntilName(r.ni.n)
                NIImpl -> MetaInsertion.No
                NIExpl -> MetaInsertion.Yes
            }
            val (t, va) = infer(ins, r.rator)
            insertMetas(mi, when (val v = va.force(true)) {
                is VPi -> {
                    when {
                        r.ni is NIExpl && v.icit != Icit.Expl -> { ctx.loc = r.loc; throw ElabError(r.loc, "AppIcit") }
                        r.ni is NIImpl && v.icit != Icit.Impl -> { ctx.loc = r.loc; throw ElabError(r.loc, "AppIcit") }
                    }
                    val u = check(r.rand, v.bound)
                    TApp(v.icit, t, u) to v.closure.inst(eval(u))
                }
                is VFun -> {
                    if (r.ni !is NIExpl) throw ElabError(r.loc, "Icit mismatch")
                    val u = check(r.rand, v.lhs)
                    TApp(Icit.Expl, t, u) to v.rhs
                }
                else -> throw ElabError(r.loc, "function type expected, instead got $v")
            })
        }
        is RLam -> {
            val icit = when (r.ni) {
                NIImpl -> Icit.Impl
                NIExpl -> Icit.Expl
                is NIName -> throw ElabError(r.loc, "named lambda")
            }
            val va = eval(newMeta())
            val (t, vb) = bind(r.loc, r.n, false, va).infer(MetaInsertion.Yes, r.body)
            val b = quote(vb, false, env.lvl + 1)
            insertMetas(mi, TLam(r.n, icit, t) to VPi(r.n, icit, va, PureClosure(env.vals, b)))
        }
    }
}

fun checkTopLevel(top: MontunoContext, e: TopLevel): Any? {
    top.metas.newMetaBlock()
    val ctx = LocalContext(top, LocalEnv(top.ntbl))
    top.loc = e.loc
    return when (e) {
        is RTerm -> when (e.cmd) {
            Pragma.ParseOnly -> e.tm.toString()
            Pragma.Reset -> { top.reset(); null }
            Pragma.Symbols -> top.top.getMembers().it
            Pragma.WholeProgram -> {
                for (i in top.top.it.indices) {
                    for ((j, meta) in top.metas.it[i].withIndex()) {
                        if (!meta.solved) throw UnifyError("Unsolved metablock")
                        if (meta.unfoldable) continue
                        println("  $i.$j = ${ctx.pretty(meta.term!!)}")
                    }
                    val topEntry = top.top.it[i]
                    print("${topEntry.name} : ${ctx.pretty(topEntry.type)}")
                    if (topEntry.defn != null) print(" = ${ctx.pretty(topEntry.defn)}")
                    println()
                }
                null
            }
            Pragma.Raw -> { println(ctx.infer(MetaInsertion.No, e.tm!!)); null }
            Pragma.Nothing -> ctx.pretty(ctx.infer(MetaInsertion.No, e.tm!!).first)
            Pragma.Type -> {
                val (_, ty) = ctx.infer(MetaInsertion.No, e.tm!!)
                println(ctx.pretty(ty.force(false).quote(Lvl(0), false)))
                null
            }
            Pragma.NormalType -> {
                val (_, ty) = ctx.infer(MetaInsertion.No, e.tm!!)
                println(ctx.pretty(ty.force(true).quote(Lvl(0), true)))
                null
            }
            Pragma.Elaborate -> {
                val (tm, _) = ctx.infer(MetaInsertion.No, e.tm!!)
                println(ctx.pretty(tm.eval(VEnv()).force(false).quote(Lvl(0), false)))
                null
            }
            Pragma.Normalize -> {
                val (tm, _) = ctx.infer(MetaInsertion.No, e.tm!!)
                println(ctx.pretty(tm.eval(VEnv()).force(true).quote(Lvl(0), true)))
                null
            }
        }
        is RDecl -> {
            var a = ctx.check(e.ty, VUnit)
            top.metas.simplifyMetaBlock()
            a = ctx.inline(a)
            top.compileTop(e.n, e.loc, null, a)
            return null
        }
        is RDefn -> {
            var a = if (e.ty != null) {
                ctx.check(e.ty, VUnit)
            } else try {
                ctx.quote(ctx.inferVar(e.n).second, false, Lvl(0))
            } catch (e: ElabError) {
                TUnit
            }
            var t = ctx.check(e.tm, ctx.eval(a))
            top.metas.simplifyMetaBlock()
            a = ctx.inline(a)
            t = ctx.inline(t)
            top.compileTop(e.n, e.loc, t, a)
            return null
        }
    }
}
