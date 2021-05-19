package montuno.truffle

import montuno.*
import montuno.common.NameEnv
import montuno.syntax.*

fun checkTopLevel(top: MontunoTruffleContext, e: TopLevel): Any? {
    val ctx = LocalContext(top.ntbl)
    return when (e) {
        is RTerm -> when (e.cmd) {
            Pragma.ParseOnly -> e.tm.toString()
            Pragma.Reset -> { top.reset(); null }
            Pragma.Symbols -> top.topScope.entries.map { it.name }.toTypedArray()
            Pragma.WholeProgram -> { top.printElaborated(); null }
            Pragma.Nothing -> {
                val (tm, _) = ctx.infer(MetaInsertion.No, e.tm!!)
                tm.pretty(NameEnv(top.ntbl))
            }
            Pragma.Type -> {
                val (_, ty) = ctx.infer(MetaInsertion.No, e.tm!!)
                ty.quote(Lvl(0)).pretty(NameEnv(top.ntbl))
            }
            Pragma.NormalType -> {
                val (_, ty) = ctx.infer(MetaInsertion.No, e.tm!!)
                ty.quote(Lvl(0)).pretty(NameEnv(top.ntbl))
            }
            Pragma.Elaborate -> {
                val (tm, _) = ctx.infer(MetaInsertion.No, e.tm!!)
                tm.pretty(NameEnv(top.ntbl))
            }
            Pragma.Normalize -> {
                val (tm, _) = ctx.infer(MetaInsertion.No, e.tm!!)
                tm.pretty(NameEnv(top.ntbl))
            }
        }
        is RDecl -> todo
        is RDefn -> todo
    }
}
