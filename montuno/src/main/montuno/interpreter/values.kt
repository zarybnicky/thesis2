package montuno.interpreter

inline class GSpine(val it: Array<Pair<Icit, Glued>>) // lazy ref to Glued
inline class VSpine(val it: Array<Pair<Icit, Lazy<Val>>>) // lazy ref to Val
fun GSpine.with(x: Pair<Icit, Glued>) = GSpine(it.plus(x))
fun VSpine.with(x: Pair<Icit, Lazy<Val>>) = VSpine(it.plus(x))

inline class VEnv(val it: Array<Lazy<Val>?>)
fun VEnv.local(lvl: Lvl): Lazy<Val> = it[lvl.it] ?: lazyOf(vLocal(lvl))
fun VEnv.skip() = VEnv(it + null)
fun VEnv.def(x: Lazy<Val>) = VEnv(it + x)
val VEnv.lvl: Lvl get() = Lvl(it.size)
inline class GEnv(val it: Array<Glued?>) // LAZY
fun GEnv.local(ix: Lvl): Glued = it[ix.it] ?: gLocal(ix)
fun GEnv.skip() = GEnv(it + null)
fun GEnv.def(x: Glued) = GEnv(it + x)

inline class NameEnv(val it: List<String> = listOf()) {
    operator fun get(ix: Ix): String = it.getOrNull(ix.it) ?: throw TypeCastException("Names[$ix] out of bounds")
    operator fun plus(x: String) = NameEnv(listOf(x) + it)
    fun fresh(x: String): String {
        if (x == "_") return "_"
        val ntbl = MontunoPure.top.ntbl
        var res = x
        var i = 0
        while (res in it || res in ntbl.it) {
            res = x + i
            i++
        }
        return res
    }
}

data class VCl(val env: VEnv, val tm: Term)
data class GCl(val genv: GEnv, val env: VEnv, val tm: Term)

data class GluedTerm(val tm: Term, val gv: GluedVal)
data class GluedVal(val v: Lazy<Val>, val g: Glued) {
    override fun toString(): String = "$g" //TODO: ???
}

val emptyGEnv = GEnv(arrayOf())
val emptyVEnv = VEnv(arrayOf())
val emptyGSpine = GSpine(arrayOf())
val emptyVSpine = VSpine(arrayOf())

sealed class Head
data class HMeta(val meta: Meta) : Head() { override fun toString(): String = "HMeta(${meta.i}, ${meta.j})" }
data class HLocal(val lvl: Lvl) : Head() { override fun toString(): String = "HLocal(lvl=${lvl.it})" }
data class HTop(val lvl: Lvl) : Head() { override fun toString(): String = "HTop(lvl=${lvl.it})" }

sealed class Val
object VU : Val() { override fun toString() = "VU" }
object VIrrelevant : Val() { override fun toString() = "VIrrelevant" }
data class VLam(val n: String, val icit: Icit, val cl: VCl) : Val()
data class VPi(val n: String, val icit: Icit, val ty: Lazy<Val>, val cl: VCl) : Val()
data class VFun(val a: Lazy<Val>, val b: Lazy<Val>) : Val()
data class VNe(val head: Head, val spine: VSpine) : Val()
data class VNat(val n: Int) : Val()

sealed class Glued
object GU : Glued() { override fun toString() = "GU" }
object GIrrelevant : Glued() { override fun toString() = "GIrrelevant" }
data class GLam(val n: String, val icit: Icit, val cl: GCl) : Glued()
data class GPi(val n: String, val icit: Icit, val ty: GluedVal, val cl: GCl) : Glued()
data class GFun(val a: GluedVal, val b: GluedVal) : Glued()
data class GNe(val head: Head, val gspine: GSpine, val spine: VSpine) : Glued() {
    override fun toString(): String = when (head) {
        is HLocal -> "GNeLocal(ix=${head.lvl.it}, gspine=[${gspine.it.joinToString(", ")}])"
        is HMeta -> "GNeMeta(meta=${head.meta.i}.${head.meta.j}, gspine=[${gspine.it.joinToString(", ")}])"
        is HTop -> "GNeTop(lvl=${head.lvl.it}, gspine=[${gspine.it.joinToString(", ")}])"
    }
}
data class GNat(val n: Int) : Glued()

val GVU = GluedVal(lazyOf(VU), GU)
fun gLocal(ix: Lvl) = GNe(HLocal(ix), emptyGSpine, emptyVSpine)
fun vLocal(ix: Lvl) = VNe(HLocal(ix), emptyVSpine)
fun gvLocal(ix: Lvl) = GluedVal(lazyOf(vLocal(ix)), gLocal(ix))
fun gTop(lvl: Lvl) = GNe(HTop(lvl), emptyGSpine, emptyVSpine)
fun vTop(lvl: Lvl) = VNe(HTop(lvl), emptyVSpine)
fun gMeta(meta: Meta) = GNe(HMeta(meta), emptyGSpine, emptyVSpine)
fun vMeta(meta: Meta) = VNe(HMeta(meta), emptyVSpine)

sealed class Term
object TU : Term()
object TIrrelevant : Term()
data class TNat(val n: Int) : Term()
data class TLet(val n: String, val ty: Term, val v: Term, val tm: Term) : Term()
data class TApp(val icit: Icit, val l: Term, val r: Term) : Term()
data class TLam(val n: String, val icit: Icit, val tm: Term) : Term()
data class TPi(val n: String, val icit: Icit, val arg: Term, val tm: Term) : Term()
data class TFun(val l: Term, val r: Term) : Term()
data class TForeign(val lang: String, val eval: String, val ty: Term) : Term()
data class TLocal(val ix: Ix) : Term() { override fun toString() = "TLocal(ix=${ix.it})" }
data class TTop(val lvl: Lvl) : Term() { override fun toString() = "TTop(lvl=${lvl.it})" }
data class TMeta(val meta: Meta) : Term() { override fun toString() = "TMeta(${meta.i}, ${meta.j})" }

fun Term.isUnfoldable(): Boolean = when (this) {
    is TLocal -> true
    is TMeta -> true
    is TTop -> true
    is TU -> true
    is TLam -> tm.isUnfoldable()
    else -> false
}
