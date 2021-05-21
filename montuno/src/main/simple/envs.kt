package simple

import montuno.Ix

data class Env(val value: Val, val next: Env?)
operator fun Env?.plus(v: Val): Env = Env(v, this)
operator fun Env?.get(n: Ix): Val = if (n.it == 0) this!!.value else this!!.next[n - 1]
fun Env?.len(): Int = if (this == null) 0 else 1 + next.len()

data class TypeEnv(val n: String, val ty: Val, val next: TypeEnv?)
fun TypeEnv?.cons(n: String, ty: Val): TypeEnv = TypeEnv(n, ty, this)
fun TypeEnv?.toNames(): NameEnv? = if (this == null) null else NameEnv(n, next.toNames())
@Throws(TypeCastException::class)
fun TypeEnv?.find(n: String, i: Int = 0): Pair<TVar, Val> = when {
    this == null -> throw TypeCastException("variable out of scope: $n")
    this.n == n -> TVar(Ix(i)) to this.ty
    else -> next.find(n, i + 1)
}

data class NameEnv(val n: String, val next: NameEnv?)
operator fun NameEnv?.plus(n: String) = NameEnv(n, this)
fun NameEnv?.fresh(n: String): String = if (n == "_") "_" else if (contains(n)) "$n'" else n
operator fun NameEnv?.contains(n: String): Boolean = when {
    this == null -> false
    this.n == n -> true
    else -> n in next
}
operator fun NameEnv?.get(n: Ix): String {
    var x = n.it
    var r = this
    while (x > 0) {
        x--
        r = r!!.next
    }
    if (x == 0) return r!!.n
    else throw TypeCastException("Names[$n] out of bounds")
}

