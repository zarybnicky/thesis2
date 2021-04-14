package montuno.interpreter.meta

import montuno.interpreter.Lvl
import montuno.interpreter.Meta
import montuno.interpreter.Rigidity
import montuno.syntax.Loc
import montuno.syntax.WithPos

sealed class MetaEntry : WithPos
data class MetaUnsolved(override val loc: Loc) : MetaEntry()
data class MetaSolved(override val loc: Loc, val gv: GluedVal, val tm: Term, val unfoldable: Boolean) : MetaEntry()

class TopContext {
    var topEntries: MutableList<TopEntry> = mutableListOf()
    var metas: MutableList<MutableList<MetaEntry>> = mutableListOf()
    var currentPos: Loc = Loc.Unavailable

    operator fun get(lvl: Lvl) = topEntries[lvl.it]
    fun rigidity(lvl: Lvl) = if (topEntries[lvl.it].defn == null) Rigidity.Rigid else Rigidity.Flex

    operator fun get(meta: Meta) = metas[meta.i][meta.j]
    operator fun set(meta: Meta, tm: Term) {
        val (i, j) = meta
        metas[i][j] = MetaSolved(currentPos, tm.gvEval(this, emptyVEnv, emptyGEnv), tm, tm.isUnfoldable())
    }
    operator fun set(meta: Meta, m: MetaEntry) {
        val (i, j) = meta
        assert(metas[i].size == j)
        metas[i].add(m)
    }
    fun rigidity(meta: Meta) = if (metas[meta.i][meta.j] is MetaUnsolved) Rigidity.Rigid else Rigidity.Flex

    operator fun invoke(pos: Loc) {
        currentPos = pos
    }
}

inline class NameTable(val it: HashMap<String, List<NameInfo>> = hashMapOf()) {
    fun addName(n: String, ni: NameInfo) {
        it[n] = it.getOrDefault(n, listOf()) + ni
    }
    fun withName(n: String, ni: NameInfo): NameTable {
        val y = HashMap(it)
        y[n] = y.getOrDefault(n, listOf()) + ni
        return NameTable(y)
    }
}

data class LocalContext(
    val top: TopContext,
    val nameTable: NameTable,
    val lvl: Lvl = Lvl(0),
    val gVals: GEnv = emptyGEnv,
    val vVals: VEnv = emptyVEnv,
    val types: Array<GluedVal> = arrayOf(),
    val names: Array<String> = arrayOf(),
    val boundIndices: IntArray = IntArray(0)
)

fun LocalContext.localBind(loc: Loc, n: String, inserted: Boolean, gv: GluedVal): LocalContext = LocalContext(
    top,
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
    top,
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
