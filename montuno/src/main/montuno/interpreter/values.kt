package montuno.interpreter

import montuno.*
import montuno.common.ITerm

inline class GSpine(val it: Array<Pair<Icit, Glued>> = emptyArray()) // lazy ref to Glued
inline class VSpine(val it: Array<Pair<Icit, Lazy<Val>>> = emptyArray()) // lazy ref to Val
fun GSpine.with(x: Pair<Icit, Glued>) = GSpine(it.plus(x))
fun VSpine.with(x: Pair<Icit, Lazy<Val>>) = VSpine(it.plus(x))

inline class VEnv(val it: Array<Lazy<Val>?> = emptyArray())
fun VEnv.local(lvl: Lvl): Lazy<Val> = it[lvl.it] ?: lazyOf(vLocal(lvl))
fun VEnv.skip() = VEnv(it + null)
fun VEnv.def(x: Lazy<Val>) = VEnv(it + x)
val VEnv.lvl: Lvl get() = Lvl(it.size)
inline class GEnv(val it: Array<Glued?> = emptyArray()) // LAZY
fun GEnv.local(ix: Lvl): Glued = it[ix.it] ?: gLocal(ix)
fun GEnv.skip() = GEnv(it + null)
fun GEnv.def(x: Glued) = GEnv(it + x)

data class VCl(val env: VEnv, val tm: Term)
data class GCl(val genv: GEnv, val env: VEnv, val tm: Term)

data class GluedVal(val v: Lazy<Val>, val g: Glued) {
    override fun toString(): String = "$g" //TODO: ???
}

sealed class Val
object VUnit : Val() { override fun toString() = "VUnit" }
object VIrrelevant : Val() { override fun toString() = "VIrrelevant" }
data class VLam(val n: String, val icit: Icit, val cl: VCl) : Val()
data class VPi(val n: String, val icit: Icit, val ty: Lazy<Val>, val cl: VCl) : Val()
data class VFun(val a: Lazy<Val>, val b: Lazy<Val>) : Val()
data class VNe(val head: Head, val spine: VSpine) : Val()
data class VNat(val n: Int) : Val()

sealed class Glued
object GUnit : Glued() { override fun toString() = "GU" }
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

val GVU = GluedVal(lazyOf(VUnit), GUnit)
fun gLocal(ix: Lvl) = GNe(HLocal(ix), GSpine(), VSpine())
fun vLocal(ix: Lvl) = VNe(HLocal(ix), VSpine())
fun gvLocal(ix: Lvl) = GluedVal(lazyOf(vLocal(ix)), gLocal(ix))
fun gTop(lvl: Lvl) = GNe(HTop(lvl), GSpine(), VSpine())
fun vTop(lvl: Lvl) = VNe(HTop(lvl), VSpine())
fun gMeta(meta: Meta) = GNe(HMeta(meta), GSpine(), VSpine())
fun vMeta(meta: Meta) = VNe(HMeta(meta), VSpine())

sealed class Term : ITerm {
    override fun isUnfoldable(): Boolean = when (this) {
        is TLocal -> true
        is TMeta -> true
        is TTop -> true
        is TU -> true
        is TLam -> tm.isUnfoldable()
        else -> false
    }
}
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
