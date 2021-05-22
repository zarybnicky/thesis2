package montuno.interpreter

import montuno.*
import montuno.interpreter.scope.NILocal
import montuno.interpreter.scope.NITop
import montuno.syntax.*

fun LocalContext.check(t: PreTerm, want: Val): Term {
    ctx.loc = t.loc
    val v = want.force(true)
    return when {
        t is RLam && v is VPi && t.arg.match(v) -> {
            val inner = bind(t.loc, t.arg.name, false, v.bound)
            val body = inner.check(t.body, v.closure.inst(VLocal(env.lvl)))
            TLam(t.arg.name, v.icit, v.bound.quote(env.lvl), body)
        }
        v is VPi && v.icit == Icit.Impl -> {
            val inner = bind(t.loc, v.name, true, v.bound)
            val body = inner.check(t, v.closure.inst(VLocal(env.lvl)))
            TLam(v.name, Icit.Impl, v.bound.quote(env.lvl), body)
        }
        t is RHole -> newMeta()
        t is RLet -> {
            val a = if (t.type == null) newMeta() else check(t.type, VUnit)
            val va = eval(a)
            val tm = check(t.defn, va)
            val vt = eval(tm)
            val u = define(t.loc, t.n, va, vt).check(t.body, want)
            TLet(t.n, a, tm, u)
        }
        t is RPair && v is VSg -> {
            val lhs = check(t.lhs, v.bound)
            val rhs = check(t.rhs, v.closure.inst(eval(lhs)))
            TPair(lhs, rhs)
        }
        else -> {
            val (tt, has) = infer(MetaInsertion.Yes, t)
            unify(has, want)
            tt
        }
    }
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
        is RVar -> insertMetas(mi, inferVar(r.n))
        is RLet -> {
            val a = if (r.type == null) newMeta() else check(r.type, VUnit)
            val gva = eval(a)
            val t = check(r.defn, gva)
            val gvt = eval(t)
            val (u, gvb) = define(r.loc, r.n, gvt, gva).infer(mi, r.body)
            TLet(r.n, a, t, u) to gvb
        }
        is RPi -> {
            val n = r.bind.name
            val a = check(r.type, VUnit)
            val b = bind(r.loc, n, false, eval(a)).check(r.body, VUnit)
            TPi(n, r.icit, a, b) to VUnit
        }
        is RApp -> {
            val icit = if (r.arg.icit == Icit.Expl) Icit.Expl else Icit.Impl
            val (t, va) = infer(r.arg.metaInsertion, r.rator)
            val v = va.force(true)
            if (v !is VPi) throw ElabError(r.loc, "function type expected, instead got $v")
            // else https://github.com/AndrasKovacs/setoidtt/blob/621aef2c4ae5a6acb418fa1153575b21e2dc48d2/setoidtt/src/Elaboration.hs#L228
            if (v.icit != icit) throw ElabError(r.loc, "Icit mismatch")
            val u = check(r.rand, v.bound)
            insertMetas(mi, TApp(v.icit, t, u) to v.closure.inst(eval(u)))
        }
        is RLam -> {
            val n = r.bind.name
            val icit = r.arg.icit ?: throw ElabError(r.loc, "named lambda")
            val a = newMeta()
            val va = eval(a)
            val (t, vb) = bind(r.loc, n, false, va).infer(MetaInsertion.Yes, r.body)
            val b = quote(vb, false, env.lvl + 1)
            insertMetas(mi, TLam(n, icit, a, t) to VPi(n, icit, va, ctx.compiler.buildClosure(b, emptyArray())))
        }
        is RSg -> {
            val n = r.bind.name
            val a = check(r.type, VUnit)
            val b = bind(r.loc, n, false, eval(a)).check(r.body, VUnit)
            TSg(n, a, b) to VUnit
        }
        is RPair -> {
            val (t, va) = infer(mi, r.lhs)
            val (u, vb) = infer(mi, r.rhs)
            val b = quote(vb, false, env.lvl)
            TPair(t, u) to VSg(null, va, ctx.compiler.buildClosure(b, emptyArray()))
        }
        is RProj1 -> {
            val (t, va) = infer(mi, r.body)
            val v = va.force(true)
            if (v !is VSg) throw ElabError(r.loc, "sigma type expected, instead got $v")
            TProj1(t) to v.bound
        }
        is RProj2 -> {
            val (t, va) = infer(mi, r.body)
            val v = va.force(true)
            if (v !is VSg) throw ElabError(r.loc, "sigma type expected, instead got $v")
            TProj2(t) to v.closure.inst(eval(t).proj1())
        }
        is RProjF -> {
            val (t, va) = infer(mi, r.body)
            val sg: VSg = todo
            /*
    let go :: S.Tm -> V.Val -> V.Ty -> Int -> IO Infer
        go topT t sg i = case forceFUE cxt sg of
          V.Sg x a au b bu
            | NName topX == x -> pure $ Infer (S.ProjField topT x i) a au
            | otherwise       -> go topT (vProj2 t) (b $$$ unS (vProj2 t)) (i + 1)
          _ -> elabError cxt topmostT $ NoSuchField topX */
            TProjF(r.field, t) to sg.bound
        }

        is RU -> TUnit to VUnit
        is RNat -> TNat(r.n) to inferVar("Nat").first.eval(ctx, env.vals)
        is RForeign -> {
            val a = check(r.type, VUnit)
            TForeign(r.lang, r.eval, a) to eval(a)
        }
        is RHole -> {
            val m1 = newMeta()
            val m2 = newMeta()
            m1 to eval(m2)
        }
    }
}

fun checkTopLevel(top: MontunoContext, e: TopLevel): Any? {
    top.metas.newMetaBlock()
    val ctx = LocalContext(top, LocalEnv(top.ntbl))
    top.loc = e.loc
    return when (e) {
        is RTerm -> when (e.cmd) {
            Pragma.PARSE -> e.tm.toString()
            Pragma.RESET -> { top.reset(); null }
            Pragma.SYMBOLS -> top.top.getMembers().it
            Pragma.PRINT -> {
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
            Pragma.RAW -> { println(ctx.infer(MetaInsertion.No, e.tm!!)); null }
            Pragma.NOTHING -> ctx.pretty(ctx.infer(MetaInsertion.No, e.tm!!).first)
            Pragma.TYPE -> {
                val (_, ty) = ctx.infer(MetaInsertion.No, e.tm!!)
                ctx.pretty(ty.force(false).quote(Lvl(0), false)).let { println(it); it }
            }
            Pragma.NORMAL_TYPE -> {
                val (_, ty) = ctx.infer(MetaInsertion.No, e.tm!!)
                ctx.pretty(ty.force(true).quote(Lvl(0), true)).let { println(it); it }
            }
            Pragma.ELABORATE -> {
                val (tm, _) = ctx.infer(MetaInsertion.No, e.tm!!)
                ctx.pretty(tm.eval(top, VEnv()).force(false).quote(Lvl(0), false)).let { println(it); it }
            }
            Pragma.NORMALIZE -> {
                val (tm, _) = ctx.infer(MetaInsertion.No, e.tm!!)
                ctx.pretty(tm.eval(top, VEnv()).force(true).quote(Lvl(0), true)).let { println(it); it }
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
