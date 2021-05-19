package montuno.interpreter

import com.oracle.truffle.api.TruffleLanguage
import montuno.*
import montuno.common.*
import montuno.syntax.Loc

class MontunoPureContext(lang: TruffleLanguage<*>, env: TruffleLanguage.Env) : MontunoContext<Term, Val>(lang, env) {
    fun printElaborated() {
        for ((i, topMeta) in metas.zip(topScope.entries).withIndex()) {
            val (metaBlock, topEntry) = topMeta
            for ((j, meta) in metaBlock.withIndex()) {
                if (meta !is MetaSolved) throw UnifyError("Unsolved metablock")
                if (meta.unfoldable) continue
                println("  $i.$j = ${meta.tm.pretty(NameEnv(ntbl))}")
            }
            when (topEntry.defn) {
                null -> println("${topEntry.name} : ${topEntry.type.first.pretty(NameEnv(ntbl))}")
                else -> {
                    println("${topEntry.name} : ${topEntry.type.first.pretty(NameEnv(ntbl))}")
                    println("${topEntry.name} = ${topEntry.defn.first.pretty(NameEnv(ntbl))}")
                }
            }
        }
    }

    fun getMeta(meta: Meta): Val = when (val m = metas[meta.i][meta.j]) {
        is MetaSolved -> m.v
        else -> vMeta(meta)
    }
    fun getTop(lvl: Lvl): Val = when (val top = topScope.entries[lvl.it].defn) {
        null -> vTop(lvl)
        else -> top.second
    }

    operator fun set(meta: Meta, tm: Term) {
        this[meta] = MetaSolved(loc, LocalContext(ntbl).eval(tm), tm, tm.isUnfoldable())
    }
    fun addTopLevel(n: String, l: Loc, t: Term?, a: Term) {
        val ctx = LocalContext(ntbl)
        val gva = a to ctx.eval(a)
        val gvt = if (t != null) t to ctx.eval(t) else null
        ntbl.addName(n, NITop(l, Lvl(topScope.entries.size)))
        topScope.entries.add(TopEntry(l, n, gvt, gva))
    }
}
class LocalContext(
    val nameTable: NameTable,
    val vals: VEnv = VEnv(),
    val types: List<Val> = listOf(),
    val names: List<String> = listOf(),
    val boundLevels: IntArray = IntArray(0)
) {
    val lvl: Lvl get() = Lvl(names.size)

    fun localBindSrc(loc: Loc, n: String, ty: Val) = localBind(loc, n, false, ty)
    fun localBindIns(loc: Loc, n: String, ty: Val) = localBind(loc, n, true, ty)
    fun localBind(loc: Loc, n: String, inserted: Boolean, ty: Val): LocalContext = LocalContext(
        nameTable.withName(n, NILocal(loc, lvl, inserted)),
        vals.skip(),
        types + ty,
        names + n,
        boundLevels.plus(lvl.it)
    )
    fun localDefine(loc: Loc, n: String, tm: Val, ty: Val) = LocalContext(
        nameTable.withName(n, NILocal(loc, lvl, false)),
        vals.def(tm),
        types + ty,
        names + n,
        boundLevels
    )
}