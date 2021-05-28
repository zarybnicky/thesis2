package montuno

data class Ix(val it: Int) {
    operator fun plus(i: Int) = Ix(it + i)
    operator fun minus(i: Int) = Ix(it - i)
    fun toLvl(depth: Int) = Lvl(depth - it - 1)
    fun toLvl(depth: Lvl) = Lvl(depth.it - it - 1)
    override fun toString(): String = "Ix($it)"
}

data class Lvl(val it: Int) {
    operator fun plus(i: Int) = Lvl(it + i)
    operator fun minus(i: Int) = Lvl(it - i)
    fun toIx(depth: Int): Ix = Ix(depth - it - 1)
    fun toIx(depth: Lvl): Ix = Ix(depth.it - it - 1)
    override fun toString(): String = "Lvl($it)"
}

data class Meta(val i: Int, val j: Int) : Comparable<Meta> {
    override fun compareTo(other: Meta) = compareValuesBy(this, other, { it.i }, { it.j })
    override fun toString(): String = "Meta($i, $j)"
}

sealed class MetaInsertion {
    data class UntilName(val n: String) : MetaInsertion()
    object Yes : MetaInsertion()
    object No : MetaInsertion()
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