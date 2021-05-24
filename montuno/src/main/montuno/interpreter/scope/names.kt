package montuno.interpreter.scope

import montuno.Ix
import montuno.Lvl
import montuno.syntax.Loc
import montuno.syntax.WithLoc
import java.util.*

sealed class NameInfo(val lvl: Lvl) : WithLoc
class NITop(override val loc: Loc, lvl: Lvl) : NameInfo(lvl)
class NILocal(override val loc: Loc, lvl: Lvl, val inserted: Boolean) : NameInfo(lvl)

inline class NameTable(val it: HashMap<String, MutableList<NameInfo>> = hashMapOf()) {
    fun addName(n: String, ni: NameInfo) {
        val l = it.getOrPut(n, { mutableListOf() })
        l.add(ni)
    }
    fun withName(n: String, ni: NameInfo): NameTable {
        val l = it[n]?.toMutableList() ?: mutableListOf()
        l.add(ni)
        val y = HashMap(it)
        y[n] = l
        return NameTable(y)
    }
    operator fun get(n: String): List<NameInfo> = it.getOrDefault(n, listOf())
}

data class NameEnv(val ntbl: NameTable, val it: List<String> = emptyList()) {
    operator fun get(ix: Ix): String = it.getOrNull(it.size - ix.it - 1) ?: throw TypeCastException("Names[$ix] out of bounds")
    operator fun plus(x: String) = NameEnv(ntbl, it + x)
    fun fresh(x: String?): String {
        if (x == null || x == "_") return "_"
        var res: String = x
        var i = 0
        while (res in it || res in ntbl.it) {
            res = x + i
            i++
        }
        return res
    }
}
