package montuno.interpreter.scope

import montuno.Rigidity
import montuno.interpreter.Term
import montuno.interpreter.Val
import montuno.syntax.Loc
import montuno.truffle.Closure

data class TopEntry(
    val name: String,
    val loc: Loc,
    val closure: Closure?,
    val defn: Term?,
    val defnV: Val?,
    val type: Term,
    val typeV: Val
) {
    val rigidity: Rigidity get() = if (defn == null) Rigidity.Rigid else Rigidity.Flex
}

class MetaEntry(val loc: Loc, val type: Val) {
    var term: Term? = null
    var value: Val? = null
    var closure: Closure? = null
    var solved: Boolean = false
    var unfoldable: Boolean = false
    val rigidity: Rigidity get() = if (solved) Rigidity.Flex else Rigidity.Rigid
}
