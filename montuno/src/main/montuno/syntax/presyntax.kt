package montuno.syntax

sealed class TopLevel : WithLoc
data class RDecl(override val loc: Loc, val n: String, val ty: PreTerm) : TopLevel()
data class RDefn(override val loc: Loc, val n: String, val ty: PreTerm?, val tm: PreTerm) : TopLevel()
data class RTerm(override val loc: Loc, val cmd: Pragma, val tm: PreTerm?) : TopLevel()

sealed class PreTerm : WithLoc

data class RVar (override val loc: Loc, val n: String) : PreTerm() { override fun toString(): String = "RVar($n)" }
data class RApp (override val loc: Loc, val arg: ArgInfo, val rator: PreTerm, val rand: PreTerm) : PreTerm()
data class RLam (override val loc: Loc, val arg: ArgInfo, val bind: Binding, val body: PreTerm) : PreTerm()
data class RPair(override val loc: Loc, val lhs: PreTerm, val rhs: PreTerm) : PreTerm()
data class RLet (override val loc: Loc, val n: String, val type: PreTerm?, val defn: PreTerm, val body: PreTerm) : PreTerm()
data class RPi  (override val loc: Loc, val bind: Binding, val icit: Icit, val type: PreTerm, val body: PreTerm) : PreTerm()
data class RSg  (override val loc: Loc, val bind: Binding, val type: PreTerm, val body: PreTerm) : PreTerm()
data class RProjF(override val loc: Loc, val body: PreTerm, val field: String) : PreTerm()
data class RProj1(override val loc: Loc, val body: PreTerm) : PreTerm()
data class RProj2(override val loc: Loc, val body: PreTerm) : PreTerm()

//data class RForeign(override val loc: Loc, val lang: String, val eval: String, val type: PreTerm) : PreTerm()

data class RU   (override val loc: Loc) : PreTerm()
data class RHole(override val loc: Loc) : PreTerm()
data class RNat(override val loc: Loc, val n: Int) : PreTerm()
