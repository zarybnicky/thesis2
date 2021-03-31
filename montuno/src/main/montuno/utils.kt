package montuno

import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection

data class Ix(val it: Int)
fun Ix.dec() = Ix(it - 1)

data class Lvl(val it: Int)
fun Lvl.toIx(x: Lvl) = Ix(it - x.it - 1)
fun Lvl.inc() = Lvl(it + 1)

data class Env(val value: Val, val next: Env?)
fun Env?.cons(v: Val): Env = Env(v, this)
fun Env?.ix(n: Ix): Val = if (n.it == 0) this!!.value else this!!.next!!.ix(n.dec())
fun Env?.len(): Int = if (this == null) 0 else 1 + next.len()

data class Types(val n: String, val ty: Val, val next: Types?)
fun Types?.cons(n: String, ty: Val): Types = Types(n, ty, this)
fun Types?.toNames(): Names? = if (this == null) null else Names(n, next.toNames())
@Throws(TypeCastException::class)
fun Types?.find(n: String, i: Int = 0): Pair<Term, Val> = when {
    this == null -> throw TypeCastException("variable out of scope: $n")
    this.n == n -> TVar(Ix(i)) to this.ty
    else -> next.find(n, i + 1)
}

data class Names(val n: String, val next: Names?)
fun Names?.ix(n: Ix): String {
    var x = n.it
    var r = this
    while (x > 0) {
        x--
        r = r!!.next
    }
    if (x == 0) return r!!.n
    else throw TypeCastException("Names[$n] out of bounds")
}
fun Names?.cons(n: String) = Names(n, this)
fun Names?.fresh(n: String): String = if (n == "_") "_" else if (elem(n)) "$n'" else n
fun Names?.len(): Int = if (this == null) 0 else 1 + next.len()
fun Names?.pretty(): String = if (this == null) "" else "$n, ${next.pretty()}"
fun Names?.elem(n: String): Boolean = when {
    this == null -> false
    this.n == n -> true
    else -> next.elem(n)
}

sealed class Loc {
    object Unavailable : Loc();
    data class Range(val start: Int, val length: Int) : Loc()
    data class Line(val line: Int) : Loc()

    fun string(source: String): String = when (this) {
        is Unavailable -> "<unavailable>"
        is Range -> source.subSequence(start, start + length).toString()
        is Line -> source.lineSequence().elementAt(line)
    }
    fun section(source: Source): SourceSection = when (this) {
        is Unavailable -> source.createUnavailableSection()
        is Range -> source.createSection(start, length)
        is Line -> source.createSection(line)
    }

    infix fun with(r: Raw) = RSrcPos(this, r)
}

fun Source.section(loc: Loc): SourceSection = loc.section(this)
