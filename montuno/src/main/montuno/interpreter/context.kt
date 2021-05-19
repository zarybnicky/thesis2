package montuno.interpreter

import com.oracle.truffle.api.TruffleLanguage
import montuno.*
import montuno.common.*
import montuno.syntax.Loc

class MontunoPureContext(lang: TruffleLanguage<*>, env: TruffleLanguage.Env) : MontunoContext<Term, GluedVal>(lang, env) {
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

    fun getMetaGlued(meta: Meta): Glued = when (val m = metas[meta.i][meta.j]) {
        is MetaSolved -> m.v.g
        else -> gMeta(meta)
    }
    fun getMetaForce(meta: Meta): Val = when (val m = metas[meta.i][meta.j]) {
        is MetaSolved -> m.v.v.value
        else -> vMeta(meta)
    }
    fun getTopGlued(lvl: Lvl): Glued = when (val top = topScope.entries[lvl.it].defn) {
        null -> gTop(lvl)
        else -> top.second.g
    }

    operator fun set(meta: Meta, tm: Term) {
        this[meta] = MetaSolved(loc, LocalContext(ntbl).gvEval(tm), tm, tm.isUnfoldable())
    }
    fun addTopLevel(n: String, l: Loc, t: Term?, a: Term) {
        val ctx = LocalContext(ntbl)
        val gva = a to ctx.gvEval(a)
        val gvt = if (t != null) t to ctx.gvEval(t) else null
        ntbl.addName(n, NITop(l, Lvl(topScope.entries.size)))
        topScope.entries.add(TopEntry(l, n, gvt, gva))
    }
}
class LocalContext(
    val nameTable: NameTable,
    val gVals: GEnv = GEnv(),
    val vVals: VEnv = VEnv(),
    val types: List<GluedVal> = listOf(),
    val names: List<String> = listOf(),
    val boundLevels: IntArray = IntArray(0)
) {
    val lvl: Lvl get() = Lvl(names.size)

    fun localBind(loc: Loc, n: String, inserted: Boolean, gv: GluedVal): LocalContext = LocalContext(
        nameTable.withName(n, NILocal(loc, lvl, inserted)),
        gVals.skip(),
        vVals.skip(),
        types + gv,
        names + n,
        boundLevels.plus(lvl.it)
    )
    fun localBindSrc(loc: Loc, n: String, gv: GluedVal) = localBind(loc, n, false, gv)
    fun localBindIns(loc: Loc, n: String, gv: GluedVal) = localBind(loc, n, true, gv)
    fun localDefine(loc: Loc, n: String, gv: GluedVal, gvty: GluedVal) = LocalContext(
        nameTable.withName(n, NILocal(loc, lvl, false)),
        gVals.def(gv.g),
        vVals.def(gv.v),
        types + gvty,
        names + n,
        boundLevels
    )
}