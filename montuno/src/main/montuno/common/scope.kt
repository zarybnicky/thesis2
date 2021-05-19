package montuno.common

import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.InvalidArrayIndexException
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedMessageException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import montuno.Ix
import montuno.Lvl
import montuno.syntax.Loc
import montuno.syntax.WithPos
import java.util.HashMap

sealed class NameInfo : WithPos
data class NITop(override val loc: Loc, val lvl: Lvl) : NameInfo()
data class NILocal(override val loc: Loc, val lvl: Lvl, val inserted: Boolean) : NameInfo()

inline class NameTable(val it: HashMap<String, MutableList<NameInfo>> = hashMapOf()) {
    fun addName(n: String, ni: NameInfo) {
        val l = it.getOrPut(n, { mutableListOf() })
        l.add(ni)
    }
    fun withName(n: String, ni: NameInfo): NameTable {
        val y = HashMap(it)
        val l = y.getOrPut(n, { mutableListOf() })
        l.add(ni)
        return NameTable(y)
    }
    inline fun <A> withName(n: String, ni: NameInfo, f: () -> A): A {
        val l = it.getOrPut(n, { mutableListOf() })
        l.add(ni)
        val r = f.invoke()
        l.remove(ni)
        return r
    }
    operator fun get(n: String): List<NameInfo> = it.getOrDefault(n, listOf())
}

data class NameEnv(val ntbl: NameTable, val it: List<String> = emptyList()) {
    operator fun get(ix: Ix): String = it.getOrNull(it.size - ix.it - 1) ?: throw TypeCastException("Names[$ix] out of bounds")
    operator fun plus(x: String) = NameEnv(ntbl, it + x)
    fun fresh(x: String): String {
        if (x == "_") return "_"
        var res = x
        var i = 0
        while (res in it || res in ntbl.it) {
            res = x + i
            i++
        }
        return res
    }
}

sealed class MetaEntry<out T, out V> : WithPos
data class MetaUnsolved(override val loc: Loc) : MetaEntry<Nothing, Nothing>()
data class MetaSolved<T, V>(override val loc: Loc, val v: V, val tm: T, val unfoldable: Boolean) : MetaEntry<T, V>()

data class TopEntry<T>(override val loc: Loc, val name: String, val defn: T?, val type: T) : WithPos

@ExportLibrary(InteropLibrary::class)
class TopLevelScope<T : ITerm, V>(
    private val lang: TruffleLanguage<*>,
    private val ctx: MontunoContext<T, V>,
    private val env: TruffleLanguage.Env,
    val entries: MutableList<TopEntry<Pair<T, V>>> = mutableListOf()
) : TruffleObject {
    @ExportMessage
    fun hasMembers() = true
    @ExportMessage
    fun getMembers(includeInternal: Boolean): TruffleObject = ConstArray(entries.map { it.name }.toTypedArray())
    @ExportMessage
    @Throws(UnsupportedMessageException::class)
    fun invokeMember(member: String, arguments: Array<Any?>): Any {
        return when (member) {
            "leakContext" -> env.asGuestValue(ctx)
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
    fun getLanguage(): Class<TruffleLanguage<*>> = lang.javaClass
    @ExportMessage
    fun toDisplayString(allowSideEffects: Boolean) = "MontunoScope"
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
