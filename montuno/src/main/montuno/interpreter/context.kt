package montuno.interpreter

import montuno.syntax.Loc
import montuno.syntax.WithPos

sealed class MetaEntry : WithPos
data class MetaUnsolved(override val loc: Loc) : MetaEntry()
data class MetaSolved(override val loc: Loc, val gp: GluedTerm, val unfoldable: Boolean) : MetaEntry()

class TopContext {
    val topEntries: MutableList<TopEntry> = mutableListOf()
    val metas: MutableList<MutableList<MetaEntry>> = mutableListOf()
    var currentPos: Loc = Loc.Unavailable

    operator fun get(lvl: Lvl) = topEntries[lvl.it]
    fun rigidity(lvl: Lvl) = if (topEntries[lvl.it].def == null) Rigidity.Rigid else Rigidity.Flex

    operator fun get(meta: Meta) = metas[meta.i][meta.j]
    fun rigidity(meta: Meta) = if (metas[meta.i][meta.j] is MetaUnsolved) Rigidity.Rigid else Rigidity.Flex

    fun write(meta: Meta, m: MetaEntry) {
        val l = metas.getOrElse(meta.i) { val l = mutableListOf<MetaEntry>(); metas[meta.i] = l; l }
        l[meta.j] = m
    }

    operator fun invoke(pos: Loc) {
        currentPos = pos
    }
    inline fun <T> withPos(pos: Loc, x: TopContext.() -> T): T {
        val oldPos = currentPos
        currentPos = pos
        val r = x.invoke(this)
        currentPos = oldPos
        return r
    }
}

class NameTable {
    val x: HashMap<String, MutableList<NameInfo>> = hashMapOf()
    fun addName(n: String, ni: NameInfo) {
        val l = x.getOrDefault(n, mutableListOf())
        l.add(ni)
        x[n] = l
    }
}

class LocalContext {
    var curLvl = Lvl(0)
    val gVals: ArrayDeque<Glued?> = ArrayDeque()
    val vVals: ArrayDeque<Val?> = ArrayDeque()
    val types: MutableList<GluedVal> = mutableListOf()
    val nameTable: NameTable = NameTable()
    val names: MutableList<String> = mutableListOf()
    val boundIndices: MutableList<Lvl> = mutableListOf()
    fun localBind(loc: Loc, n: String, inserted: Boolean, gv: GluedVal) {
        gVals += null
        vVals += null
        types += gv
        nameTable.addName(n, NILocal(loc, curLvl, inserted))
        names += n
        boundIndices += curLvl
        curLvl += 1
    }
    fun localBindSrc(loc: Loc, n: String, gv: GluedVal) = localBind(loc, n, false, gv)
    fun localBindIns(loc: Loc, n: String, gv: GluedVal) = localBind(loc, n, true, gv)
    fun localDefine(loc: Loc, n: String, gv: GluedVal, gvty: GluedVal) {
        gVals += gv.gv
        vVals += gv.v
        types += gvty
        nameTable.addName(n, NILocal(loc, curLvl, false))
        names += n
        curLvl += 1
    }
}

sealed class NameInfo : WithPos
data class NITop(override val loc: Loc, val lvl: Lvl) : NameInfo()
data class NILocal(override val loc: Loc, val lvl: Lvl, val inserted: Boolean) : NameInfo()

data class TopEntry(
    override val loc: Loc,
    val name: String,
    val def: GluedTerm?, // postulate == null, else definition
    val type: GluedTerm
) : WithPos
