package montuno.common

import montuno.syntax.Loc
import montuno.syntax.WithPos
import java.util.*

class LocalEnv<V>(
    val nameTable: NameTable,
    val vals: Array<V?>,
    val types: List<V> = listOf(),
    val names: List<String> = listOf(),
    val boundLevels: IntArray = IntArray(0)
) {
    val lvl: Lvl get() = Lvl(names.size)
    fun bindSrc(loc: Loc, n: String, ty: V) = bind(loc, n, false, ty)
    fun bindIns(loc: Loc, n: String, ty: V) = bind(loc, n, true, ty)
    fun bind(loc: Loc, n: String, inserted: Boolean, ty: V) = LocalEnv(
        nameTable.withName(n, NILocal(loc, lvl, inserted)),
        vals + null,
        types + ty,
        names + n,
        boundLevels.plus(lvl.it)
    )
    fun define(loc: Loc, n: String, tm: V, ty: V) = LocalEnv(
        nameTable.withName(n, NILocal(loc, lvl, false)),
        vals + tm,
        types + ty,
        names + n,
        boundLevels
    )
}

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

class MetaEntry<T, V>(val loc: Loc) {
    var term: T? = null
    var value: V? = null
    var solved: Boolean = false
    var unfoldable: Boolean = false
    fun solve(v: V, t: T, u: Boolean) {
        term = t
        value = v
        solved = true
        unfoldable = u
    }
}
