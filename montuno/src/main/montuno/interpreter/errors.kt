package montuno.interpreter

import montuno.interpreter.LocalContext
import montuno.interpreter.Rigidity
import montuno.syntax.Loc

class UnifyError(val reason: String) : RuntimeException(reason)
class RigidError(val reason: String) : Exception(reason)
class FlexRigidError(val rigidity: Rigidity, val reason: String = "") : Exception(reason)
class ElabError(val loc: Loc?, val ctx: LocalContext, val reason: String) : RuntimeException(reason)
