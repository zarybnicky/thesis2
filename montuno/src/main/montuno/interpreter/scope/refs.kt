package montuno.interpreter.scope

import montuno.Meta
import montuno.interpreter.Term
import montuno.interpreter.Val
import montuno.syntax.Loc

data class TopEntry(
    val name: String,
    val loc: Loc,
    val defn: Term?,
    val defnV: Val?,
    val type: Term,
    val typeV: Val
)
class MetaEntry(val loc: Loc, val meta: Meta, val type: Val) {
    var term: Term? = null
    var value: Val? = null
    var solved: Boolean = false
    var unfoldable: Boolean = false
}
