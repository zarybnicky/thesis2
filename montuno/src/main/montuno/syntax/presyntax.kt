package montuno.syntax

import montuno.interpreter.Icit

sealed class TopLevel : WithPos
data class RDecl(override val loc: Loc, val n: String, val ty: PreTerm) : TopLevel()
data class RDefn(override val loc: Loc, val n: String, val ty: PreTerm?, val tm: PreTerm) : TopLevel()
data class RTerm(override val loc: Loc, val cmd: Pragma, val tm: PreTerm?) : TopLevel()

sealed class PreTerm : WithPos

data class RVar (override val loc: Loc, val n: String) : PreTerm() { override fun toString(): String = "RVar($n)" }
data class RApp (override val loc: Loc, val ni: NameOrIcit, val rator: PreTerm, val rand: PreTerm) : PreTerm()
data class RLam (override val loc: Loc, val n: String, val ni: NameOrIcit, val body: PreTerm) : PreTerm()
data class RFun (override val loc: Loc, val l: PreTerm, val r: PreTerm) : PreTerm()
data class RPi  (override val loc: Loc, val n: String, val icit: Icit, val type: PreTerm, val body: PreTerm) : PreTerm()
data class RLet (override val loc: Loc, val n: String, val type: PreTerm, val defn: PreTerm, val body: PreTerm) : PreTerm()
data class RU   (override val loc: Loc) : PreTerm()
data class RHole(override val loc: Loc) : PreTerm()

data class RNat(override val loc: Loc, val n: Int) : PreTerm()
data class RForeign(override val loc: Loc, val lang: String, val eval: String, val type: PreTerm) : PreTerm()
data class RStopMeta(override val loc: Loc, val body: PreTerm) : PreTerm()