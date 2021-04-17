package montuno.interpreter

import montuno.interpreter.meta.NameInfo
import java.util.*

sealed class Either<out L, out R> {
    data class Left<out L>(val it: L) : Either<L, Nothing>()
    data class Right<out R>(val it: R) : Either<Nothing, R>()
    fun asLeft(): L? = when (this) {
        is Left -> it
        is Right -> null
    }
    fun asRight(): R? = when (this) {
        is Left -> null
        is Right -> it
    }
}

inline class Ix(val it: Int) {
    operator fun plus(i: Int) = Ix(it + i)
    operator fun minus(i: Int) = Ix(it - i)
    override fun toString(): String = "Ix($it)"
}

inline class Lvl(val it: Int) {
    operator fun plus(i: Int) = Lvl(it + i)
    operator fun minus(i: Int) = Lvl(it - i)
    fun toIx(x: Lvl) = Ix(it - x.it - 1)
    override fun toString(): String = "Lvl($it)"
}

data class Meta(val i: Int, val j: Int) : Comparable<Meta> {
    override fun compareTo(other: Meta) = compareValuesBy(this, other, { it.i }, { it.j })
    override fun toString(): String = "Meta($i, $j)"
}

enum class Icit { Expl, Impl }

enum class Rigidity { Rigid, Flex }
fun Rigidity.meld(that: Rigidity) = when (Rigidity.Flex) {
    this -> Rigidity.Flex
    else -> that
}

inline class NameTable(val it: HashMap<String, MutableList<NameInfo>> = hashMapOf()) {
    fun addName(n: String, ni: NameInfo) {
        val l = it.getOrPut(n, { mutableListOf() })
        l.add(ni)
    }
    fun withName(n: String, ni: NameInfo): NameTable {
        val y = HashMap(it)
        val l = it.getOrPut(n, { mutableListOf() })
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

inline class Renaming(val it: Array<Pair<Lvl, Lvl>>)
fun Renaming.apply(x: Lvl): Int = it.find { it.first == x }?.second?.it ?: -1
operator fun Renaming.plus(x: Pair<Lvl, Lvl>) = Renaming(it.plus(x))

inline class LvlSet(val it: BitSet)
fun LvlSet.disjoint(r: Renaming): Boolean = r.it.any { this.it[it.first.it] }
