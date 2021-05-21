package montuno.interpreter.scope

import com.oracle.truffle.api.CallTarget
import montuno.Rigidity
import montuno.interpreter.Term
import montuno.interpreter.VSpine
import montuno.interpreter.Val
import montuno.syntax.Loc

data class TopEntry(
    val name: String,
    val loc: Loc,
    val callTarget: CallTarget?,
    val defn: Term?,
    val defnV: Val?,
    val type: Term,
    val typeV: Val
) {
    val rigidity: Rigidity get() = if (defn == null) Rigidity.Rigid else Rigidity.Flex
    fun call(spine: VSpine): Val = callTarget?.call(*spine.getVals()) as Val
}

class MetaEntry(val loc: Loc) {
    var term: Term? = null
    var value: Val? = null
    var callTarget: CallTarget? = null
    var solved: Boolean = false
    var unfoldable: Boolean = false
    val rigidity: Rigidity get() = if (solved) Rigidity.Flex else Rigidity.Rigid
    fun call(spine: VSpine): Val = callTarget?.call(*spine.getVals()) as Val
}
