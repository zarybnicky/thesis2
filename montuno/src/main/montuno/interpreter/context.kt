package montuno.interpreter


import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import montuno.syntax.Loc
import montuno.syntax.WithPos

class MontunoPureContext(@Suppress("unused") val env: TruffleLanguage.Env) {
    val topScope = TopLevelScope()
    var metas: MutableList<MutableList<MetaEntry>> = mutableListOf()
    var loc: Loc = Loc.Unavailable
    var ntbl = NameTable()

    fun reset() {
        topScope.entries.removeAll { true }
        metas = mutableListOf()
        loc = Loc.Unavailable
        ntbl = NameTable()
    }

    fun printElaborated() {
        for ((i, topMeta) in metas.zip(topScope.entries).withIndex()) {
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

    fun rigidity(lvl: Lvl) = if (topScope.entries[lvl.it].defn == null) Rigidity.Rigid else Rigidity.Flex
    fun rigidity(meta: Meta) = if (metas[meta.i][meta.j] is MetaUnsolved) Rigidity.Rigid else Rigidity.Flex

    operator fun get(lvl: Lvl) = topScope.entries[lvl.it]
    operator fun get(meta: Meta) = metas[meta.i][meta.j]

    fun getMetaGlued(meta: Meta): Glued = when (val m = metas[meta.i][meta.j]) {
        is MetaSolved -> m.gv.g
        else -> gMeta(meta)
    }
    fun getMetaForce(meta: Meta): Val = when (val m = metas[meta.i][meta.j]) {
        is MetaSolved -> m.gv.v.value
        else -> vMeta(meta)
    }
    fun getTopGlued(lvl: Lvl): Glued = when (val top = topScope.entries[lvl.it].defn) {
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
        MontunoPure.top.ntbl.addName(n, NITop(l, Lvl(MontunoPure.top.topScope.entries.size)))
        MontunoPure.top.topScope.entries.add(TopEntry(l, n, gvt, gva))
    }
}

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class ConstArray(val it: Array<Any>) : TruffleObject {
    @ExportMessage
    fun hasArrayElements() = true
    @ExportMessage
    fun getArraySize() = it.size
    @ExportMessage
    fun isArrayElementReadable(i: Long) = i < it.size
    @ExportMessage
    fun isArrayElementModifiable(i: Long) = false
    @ExportMessage
    fun isArrayElementInsertable(i: Long) = false
    @ExportMessage
    @Throws(InvalidArrayIndexException::class)
    fun readArrayElement(i: Long): Any =
        if (i < it.size) it[i.toInt()] else throw InvalidArrayIndexException.create(i)
    @ExportMessage
    @Throws(UnsupportedMessageException::class)
    fun writeArrayElement(i: Long, o: Any): Any = throw UnsupportedMessageException.create()
}

@ExportLibrary(InteropLibrary::class)
class TopLevelScope(val entries: MutableList<TopEntry> = mutableListOf()) : TruffleObject {
    @ExportMessage
    fun hasMembers() = true
    @ExportMessage
    fun getMembers(includeInternal: Boolean): TruffleObject = ConstArray(entries.map { it.name }.toTypedArray())
    @ExportMessage
    @Throws(UnsupportedMessageException::class)
    fun invokeMember(member: String, arguments: Array<Any?>): Any {
        return when (member) {
            "leakContext" -> MontunoPure.top.env.asGuestValue(MontunoPure.top)
            else -> throw UnsupportedMessageException.create()
        }
    }
    @ExportMessage
    fun isMemberInvocable(member: String) = member == "leakContext"
    @ExportMessage
    fun isScope(): Boolean = true
    @ExportMessage
    fun hasScopeParent() = false
    @ExportMessage
    @Throws(UnsupportedMessageException::class)
    fun getScopeParent(): Any = UnsupportedMessageException.create()
    @ExportMessage
    fun hasLanguage() = true
    @ExportMessage
    fun getLanguage(): Class<MontunoPure> = MontunoPure::class.java
    @ExportMessage
    fun toDisplayString(allowSideEffects: Boolean) = "MontunoScope"
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
