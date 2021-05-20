package montuno.common

import montuno.syntax.*

fun <T, V> inferVar(ctx: LocalLevelContext<T, V>, n: String): Pair<T, V> {
    val terms = ctx.top.termFactory
    for (ni in ctx.env.nameTable[n].asReversed()) {
        return when {
            ni is NITop -> terms.top(ni.lvl, ctx.top[ni.lvl]) to ctx.top[ni.lvl].typeV
            ni is NILocal && !ni.inserted -> terms.local(ni.lvl.toIx(ctx.env.lvl)) to ctx.env.types[ni.lvl.it]
            else -> continue
        }
    }
    throw ElabError(null, "Variable $n out of scope")
}

fun <T, V> checkTopLevel(top: TopLevelContext<T, V>, e: TopLevel): Any? {
    top.newMetaBlock()
    val ctx = top.makeLocalContext()
    top.loc = e.loc
    return when (e) {
        is RTerm -> when (e.cmd) {
            Pragma.ParseOnly -> e.tm.toString()
            Pragma.Reset -> { top.reset(); null }
            Pragma.Symbols -> top.getSymbols()
            Pragma.WholeProgram -> { top.printElaborated(); null }
            Pragma.Nothing -> ctx.pretty(ctx.infer(MetaInsertion.No, e.tm!!).first)
            Pragma.Type -> {
                val (_, ty) = ctx.infer(MetaInsertion.No, e.tm!!)
                ctx.pretty(ctx.quote(ctx.force(ty, false), false))
            }
            Pragma.NormalType -> {
                val (_, ty) = ctx.infer(MetaInsertion.No, e.tm!!)
                ctx.pretty(ctx.quote(ctx.force(ty, true), true))
            }
            Pragma.Elaborate -> {
                val (tm, _) = ctx.infer(MetaInsertion.No, e.tm!!)
                ctx.pretty(ctx.quote(ctx.force(ctx.eval(tm), false), false))
            }
            Pragma.Normalize -> {
                val (tm, _) = ctx.infer(MetaInsertion.No, e.tm!!)
                ctx.pretty(ctx.quote(ctx.force(ctx.eval(tm), true),true))
            }
        }
        is RDecl -> {
            var a = ctx.check(e.ty, top.valFactory.unit())
            top.simplifyMetaBlock()
            a = ctx.inline(a)
            top.addTopLevel(e.n, e.loc, null, a)
            return null
        }
        is RDefn -> {
            var a: T = if (e.ty != null) {
                ctx.check(e.ty, top.valFactory.unit())
            } else try {
                ctx.quote(ctx.inferVar(e.n).second, false)
            } catch (e: ElabError) {
                ctx.top.termFactory.unit()
            }
            var t = ctx.check(e.tm, ctx.eval(a))
            top.simplifyMetaBlock()
            a = ctx.inline(a)
            t = ctx.inline(t)
            top.addTopLevel(e.n, e.loc, t, a)
            return null
        }
    }
}
