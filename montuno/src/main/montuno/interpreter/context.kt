package montuno.interpreter

import com.oracle.truffle.api.TruffleLanguage
import montuno.syntax.Loc
import montuno.syntax.WithPos

class MontunoPureContext(@Suppress("unused") val env: TruffleLanguage.Env) {
    var topEntries: MutableList<TopEntry> = mutableListOf()
    var metas: MutableList<MutableList<MetaEntry>> = mutableListOf()
    var loc: Loc = Loc.Unavailable
    var ntbl = NameTable()

    fun reset() {
        topEntries = mutableListOf()
        metas = mutableListOf()
        loc = Loc.Unavailable
        ntbl = NameTable()
    }

    fun printBindings() {
        for (topEntry in topEntries) {
            println("${topEntry.name} : ${topEntry.type.tm.pretty()}")
        }
    }

    fun printElaborated() {
        for ((i, topMeta) in metas.zip(topEntries).withIndex()) {
            val (metaBlock, topEntry) = topMeta
            for ((j, meta) in metaBlock.withIndex()) {
                if (meta !is MetaSolved) throw UnifyError("Unsolved metablock")
                if (meta.unfoldable) continue
                println("  $i.$j = ${meta.tm.pretty()}")
            }
            when (topEntry.defn) {
                null -> println("${topEntry.name} : ${topEntry.type.tm.pretty()}")
                else -> {
                    println("${topEntry.name} : ${topEntry.type.tm.pretty()}")
                    println("${topEntry.name} = ${topEntry.defn.tm.pretty()}")
                }
            }
        }
    }

    fun rigidity(lvl: Lvl) = if (topEntries[lvl.it].defn == null) Rigidity.Rigid else Rigidity.Flex
    fun rigidity(meta: Meta) = if (metas[meta.i][meta.j] is MetaUnsolved) Rigidity.Rigid else Rigidity.Flex

    operator fun get(lvl: Lvl) = topEntries[lvl.it]
    operator fun get(meta: Meta) = metas[meta.i][meta.j]

    fun getMetaGlued(meta: Meta): Glued = when (val m = metas[meta.i][meta.j]) {
        is MetaSolved -> m.gv.g
        else -> gMeta(meta)
    }
    fun getMetaForce(meta: Meta): Val = when (val m = metas[meta.i][meta.j]) {
        is MetaSolved -> m.gv.v.value
        else -> vMeta(meta)
    }
    fun getTopGlued(lvl: Lvl): Glued = when (val top = topEntries[lvl.it].defn) {
        null -> gTop(lvl)
        else -> top.gv.g
    }

    operator fun set(meta: Meta, tm: Term) { metas[meta.i][meta.j] = MetaSolved(loc, tm.gvEval(emptyVEnv, emptyGEnv), tm) }
    operator fun set(meta: Meta, m: MetaEntry) {
        val (i, j) = meta
        assert(metas[i].size == j)
        metas[i].add(m)
    }
    fun addTopLevel(n: String, l: Loc, t: Term?, a: Term) {
        val ctx = LocalContext(MontunoPure.top.ntbl)
        val gva = GluedTerm(a, ctx.gvEval(a))
        val gvt = if (t != null) GluedTerm(t, ctx.gvEval(t)) else null
        MontunoPure.top.ntbl.addName(n, NITop(l, Lvl(MontunoPure.top.topEntries.size)))
        MontunoPure.top.topEntries.add(TopEntry(l, n, gvt, gva))
    }
}

sealed class MetaEntry : WithPos
data class MetaUnsolved(override val loc: Loc) : MetaEntry()
data class MetaSolved(override val loc: Loc, val gv: GluedVal, val tm: Term, val unfoldable: Boolean = tm.isUnfoldable()) : MetaEntry()

data class LocalContext(
    val nameTable: NameTable,
    val lvl: Lvl = Lvl(0),
    val gVals: GEnv = emptyGEnv,
    val vVals: VEnv = emptyVEnv,
    val types: Array<GluedVal> = arrayOf(),
    val names: Array<String> = arrayOf(),
    val boundIndices: IntArray = IntArray(0)
)

fun LocalContext.localBind(loc: Loc, n: String, inserted: Boolean, gv: GluedVal): LocalContext = LocalContext(
    nameTable.withName(n, NILocal(loc, lvl, inserted)),
    lvl + 1,
    gVals.skip(),
    vVals.skip(),
    types + gv,
    names + n,
    boundIndices.plus(lvl.it)
)
fun LocalContext.localBindSrc(loc: Loc, n: String, gv: GluedVal) = localBind(loc, n, false, gv)
fun LocalContext.localBindIns(loc: Loc, n: String, gv: GluedVal) = localBind(loc, n, true, gv)
fun LocalContext.localDefine(loc: Loc, n: String, gv: GluedVal, gvty: GluedVal) = LocalContext(
    nameTable.withName(n, NILocal(loc, lvl, false)),
    lvl + 1,
    gVals.def(gv.g),
    vVals.def(gv.v),
    types + gvty,
    names + n,
    boundIndices
)

sealed class NameInfo : WithPos
data class NITop(override val loc: Loc, val lvl: Lvl) : NameInfo()
data class NILocal(override val loc: Loc, val lvl: Lvl, val inserted: Boolean) : NameInfo()

data class TopEntry(
    override val loc: Loc,
    val name: String,
    val defn: GluedTerm?, // postulate == null, else definition
    val type: GluedTerm
) : WithPos
