package montuno.interpreter

typealias GSpine = ArrayDeque<Pair<Icit, Glued>> // LAZY
typealias VSpine = ArrayDeque<Pair<Icit, Val>> // LAZY
inline class VEnv(val it: ArrayDeque<Val?>)  // LAZY
inline class GEnv(val it: ArrayDeque<Glued?>) // LAZY
data class VCl(val env: VEnv, val tm: Term)
data class GCl(val genv: GEnv, val env: VEnv, val tm: Term)
data class GluedTerm(val tm: Term, val gv: Glued)
data class GluedVal(val v: Val, val gv: Glued)

inline fun <A> VEnv.with(x: Lazy<Val>?, f: () -> A) : A {
    it.addFirst(x)
    val ret = run(f)
    it.removeFirst()
    return ret
}

sealed class Head
data class HMeta(val meta: Meta) : Head()
data class HLocal(val ix: Ix) : Head()
data class HTop(val lvl: Lvl) : Head()

sealed class Val
data class VNe(val head: Head, val spine: VSpine) : Val()
data class VLam(val n: String, val icit: Icit, val cl: VCl) : Val()
data class VPi(val n: String, val icit: Icit, val tm: Val, val cl: VCl) : Val()
data class VFun(val a: Lazy<Val>, val b: Lazy<Val>) : Val()
object VU : Val()
object VIrrelevant : Val()

sealed class Glued
data class GNe(val head: Head, val gspine: GSpine, val spine: VSpine) : Glued()
data class GLam(val n: String, val icit: Icit, val cl: GCl) : Glued()
data class GPi(val n: String, val icit: Icit, val ty: Glued, val cl: GCl) : Glued()
data class GFun(val a: Glued, val b: Glued) : Glued()
object GU : Glued()
object GIrrelevant : Glued()

val GVU = GluedVal(VU, GU)
inline fun gLocal(ix: Ix) = GNe(HLocal(ix), ArrayDeque(), ArrayDeque())
inline fun vLocal(ix: Ix) = VNe(HLocal(ix), ArrayDeque())
inline fun gvlocal(ix: Ix) = GluedVal(vLocal(ix), gLocal(ix))
inline fun gTop(lvl: Lvl) = GNe(HTop(lvl), ArrayDeque(), ArrayDeque())
inline fun vTop(lvl: Lvl) = VNe(HTop(lvl), ArrayDeque())
inline fun gMeta(meta: Meta) = GNe(HMeta(meta), ArrayDeque(), ArrayDeque())
inline fun vMeta(meta: Meta) = VNe(HMeta(meta), ArrayDeque())

sealed class PreTerm
data class PTVar(val n: String) : PreTerm()
data class PTLet(val n: String, val ty: PreTerm, val v: PreTerm, val tm: PreTerm) : PreTerm()
data class PTApp(val l: PreTerm, val r: PreTerm, val n: String?, val icit: Icit) : PreTerm()
data class PTLam(val n: String, val arg: String?, val icit: Icit, val tm: PreTerm) : PreTerm()
data class PTPi(val n: String, val icit: Icit, val arg: PreTerm, val tm: PreTerm) : PreTerm()
data class PTFun(val l: PreTerm, val r: PreTerm) : PreTerm()
object PTU : PreTerm()
object PTHole : PreTerm()
data class PTStopMeta(val tm: PreTerm) : PreTerm()

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
