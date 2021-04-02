package montuno

import com.oracle.truffle.api.source.Source
import com.oracle.truffle.api.source.SourceSection
import montuno.syntax.Loc

data class Ix(val it: Int)
operator fun Ix.plus(i: Int) = Ix(it + i)
operator fun Ix.minus(i: Int) = Ix(it - i)

data class Lvl(val it: Int)
operator fun Lvl.plus(i: Int) = Lvl(it + i)
operator fun Lvl.minus(i: Int) = Lvl(it - i)
fun Lvl.toIx(x: Lvl) = Ix(it - x.it - 1)

data class Env(val value: Val, val next: Env?)
operator fun Env?.plus(v: Val): Env = Env(v, this)
operator fun Env?.get(n: Ix): Val = if (n.it == 0) this!!.value else this!!.next[n - 1]
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
operator fun Names?.plus(n: String) = Names(n, this)
fun Names?.fresh(n: String): String = if (n == "_") "_" else if (contains(n)) "$n'" else n
operator fun Names?.contains(n: String): Boolean = when {
    this == null -> false
    this.n == n -> true
    else -> n in next
}
operator fun Names?.get(n: Ix): String {
    var x = n.it
    var r = this
    while (x > 0) {
        x--
        r = r!!.next
    }
    if (x == 0) return r!!.n
    else throw TypeCastException("Names[$n] out of bounds")
}

fun Source.section(loc: Loc): SourceSection = when (loc) {
    is Loc.Unavailable -> createUnavailableSection()
    is Loc.Range -> createSection(loc.start, loc.length)
    is Loc.Line -> createSection(loc.line)
}
