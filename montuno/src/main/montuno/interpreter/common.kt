package montuno.interpreter

import java.util.*

sealed class Either<out L, out R>
class Left<out L>(val it: L) : Either<L, Nothing>()
class Right<out R>(val it: R) : Either<Nothing, R>()

inline class Ix(val it: Int) {
    operator fun plus(i: Int) = Ix(it + i)
    operator fun minus(i: Int) = Ix(it - i)
}

inline class Lvl(val it: Int) {
    operator fun plus(i: Int) = Lvl(it + i)
    operator fun minus(i: Int) = Lvl(it - i)
    fun toIx(x: Lvl) = Ix(it - x.it - 1)
}

typealias LvlSet = BitSet

data class Meta(val i: Int, val j: Int)

enum class Icit { Expl, Impl }

enum class Rigidity { Rigid, Flex }
fun Rigidity.meld(that: Rigidity) = when (Rigidity.Flex) {
    this -> Rigidity.Flex
    else -> that
}

data class Renaming(val map: Map<Lvl, Lvl>)
fun Renaming.apply(x: Lvl) = map.getOrDefault(x, x)
