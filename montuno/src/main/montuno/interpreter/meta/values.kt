package montuno.interpreter.meta

import montuno.interpreter.Icit
import montuno.interpreter.Ix
import montuno.interpreter.Lvl
import montuno.interpreter.Meta

inline class GSpine(val it: Array<Pair<Icit, Glued>>) // lazy ref to Glued
inline class VSpine(val it: Array<Pair<Icit, Lazy<Val>>>) // lazy ref to Val
inline class VEnv(val it: Array<Lazy<Val>?>)
inline class GEnv(val it: Array<Glued?>) // LAZY
data class VCl(val env: VEnv, val tm: Term)
data class GCl(val genv: GEnv, val env: VEnv, val tm: Term)
data class GluedTerm(val tm: Term, val gv: GluedVal)
data class GluedVal(val v: Lazy<Val>, val g: Glued)

val emptyGEnv = GEnv(arrayOf())
val emptyVEnv = VEnv(arrayOf())
val emptyGSpine = GSpine(arrayOf())
val emptyVSpine = VSpine(arrayOf())

fun GSpine.with(x: Pair<Icit, Glued>) = GSpine(it.plus(x))
fun VSpine.with(x: Pair<Icit, Lazy<Val>>) = VSpine(it.plus(x))
fun GEnv.skip() = GEnv(it.plus(null))
fun GEnv.def(x: Glued) = GEnv(it.plus(x))
fun VEnv.skip() = VEnv(it.plus(null))
fun VEnv.def(x: Lazy<Val>) = VEnv(it.plus(x))

sealed class Head
data class HMeta(val meta: Meta) : Head()
data class HLocal(val ix: Ix) : Head()
data class HTop(val lvl: Lvl) : Head()

sealed class Val
data class VNe(val head: Head, val spine: VSpine) : Val()
data class VLam(val n: String, val icit: Icit, val cl: VCl) : Val()
data class VPi(val n: String, val icit: Icit, val ty: Lazy<Val>, val cl: VCl) : Val()
data class VFun(val a: Lazy<Val>, val b: Lazy<Val>) : Val()
object VU : Val() { override fun toString() = "VU" }
object VIrrelevant : Val() { override fun toString() = "VIrrelevant" }

sealed class Glued
data class GNe(val head: Head, val gspine: GSpine, val spine: VSpine) : Glued()
data class GLam(val n: String, val icit: Icit, val cl: GCl) : Glued()
data class GPi(val n: String, val icit: Icit, val ty: GluedVal, val cl: GCl) : Glued()
data class GFun(val a: GluedVal, val b: GluedVal) : Glued()
object GU : Glued() { override fun toString() = "GU" }
object GIrrelevant : Glued() { override fun toString() = "GIrrelevant" }

val GVU = GluedVal(lazyOf(VU), GU)
fun gLocal(ix: Ix) = GNe(HLocal(ix), emptyGSpine, emptyVSpine)
fun vLocal(ix: Ix) = VNe(HLocal(ix), emptyVSpine)
fun gvLocal(ix: Ix) = GluedVal(lazyOf(vLocal(ix)), gLocal(ix))
fun gTop(lvl: Lvl) = GNe(HTop(lvl), emptyGSpine, emptyVSpine)
fun vTop(lvl: Lvl) = VNe(HTop(lvl), emptyVSpine)
fun gMeta(meta: Meta) = GNe(HMeta(meta), emptyGSpine, emptyVSpine)
fun vMeta(meta: Meta) = VNe(HMeta(meta), emptyVSpine)

sealed class Term
data class TLocal(val ix: Ix) : Term()
data class TTop(val lvl: Lvl) : Term()
data class TMeta(val meta: Meta) : Term()
data class TLet(val n: String, val ty: Term, val v: Term, val tm: Term) : Term()
data class TApp(val icit: Icit, val l: Term, val r: Term) : Term()
data class TLam(val n: String, val icit: Icit, val tm: Term) : Term()
data class TPi(val n: String, val icit: Icit, val arg: Term, val tm: Term) : Term()
data class TFun(val l: Term, val r: Term) : Term()
object TIrrelevant : Term()
object TU : Term()
data class TForeign(val lang: String, val eval: String) : Term()