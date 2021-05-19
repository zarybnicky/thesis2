package montuno

import java.util.*

data class Ix(val it: Int) {
    operator fun plus(i: Int) = Ix(it + i)
    operator fun minus(i: Int) = Ix(it - i)
    fun toLvl(depth: Int) = Lvl(it - it - 1)
    fun toLvl(depth: Lvl) = Lvl(depth.it - it - 1)
    override fun toString(): String = "Ix($it)"
}

data class Lvl(val it: Int) {
    operator fun plus(i: Int) = Lvl(it + i)
    operator fun minus(i: Int) = Lvl(it - i)
    fun toIx(depth: Int) = Ix(it - it - 1)
    fun toIx(depth: Lvl) = Ix(depth.it - it - 1)
    override fun toString(): String = "Lvl($it)"
}

data class Meta(val i: Int, val j: Int) : Comparable<Meta> {
    override fun compareTo(other: Meta) = compareValuesBy(this, other, { it.i }, { it.j })
    override fun toString(): String = "Meta($i, $j)"
}

sealed class Head
data class HMeta(val meta: Meta) : Head() { override fun toString(): String = "HMeta(${meta.i}, ${meta.j})" }
data class HLocal(val lvl: Lvl) : Head() { override fun toString(): String = "HLocal(lvl=${lvl.it})" }
data class HTop(val lvl: Lvl) : Head() { override fun toString(): String = "HTop(lvl=${lvl.it})" }

enum class Icit { Expl, Impl }

enum class Rigidity { Rigid, Flex }

fun Rigidity.meld(that: Rigidity) = when (Rigidity.Flex) {
    this -> Rigidity.Flex
    else -> that
}

sealed class MetaInsertion {
    data class UntilName(val n: String) : MetaInsertion()
    object Yes : MetaInsertion()
    object No : MetaInsertion()
}

inline class Renaming(val it: Array<Pair<Lvl, Lvl>>) {
    fun apply(x: Lvl): Lvl? = it.find { it.first == x }?.second
    operator fun plus(x: Pair<Lvl, Lvl>) = Renaming(it.plus(x))
}

inline class LvlSet(val it: BitSet) {
    fun disjoint(r: Renaming): Boolean = r.it.any { this.it[it.first.it] }
}

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