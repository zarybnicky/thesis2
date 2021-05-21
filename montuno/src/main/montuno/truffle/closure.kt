package montuno.truffle

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.RootNode
import com.oracle.truffle.api.nodes.UnexpectedResultException
import montuno.interpreter.Term
import montuno.interpreter.VEnv
import montuno.interpreter.VUnit
import montuno.interpreter.Val

class EvalRootNode(val tm: Term, lang: TruffleLanguage<*>) : RootNode(lang) {
    init {
        callTarget = Truffle.getRuntime().createCallTarget(this)
    }
    override fun execute(frame: VirtualFrame): Any = tm.eval(VEnv(frame.arguments as Array<Val?>))
}
class ClosureRootNode(@field:Children val nodes: Array<Code>, lang: TruffleLanguage<*>, fd: FrameDescriptor) : RootNode(lang, fd) {
    init {
        callTarget = Truffle.getRuntime().createCallTarget(this)
    }
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any? {
        var res: Any? = VUnit
        for (e in nodes) {
            res = e.execute(frame)
        }
        return res
    }
}

interface Callable {
    fun inst(v: Val): Val
    fun getCallTarget(lang: TruffleLanguage<*>): CallTarget
    fun getArgs(): Array<Any?>
}
data class PureClosure(val env: VEnv, val tm: Term): Callable {
    override fun inst(v: Val) = tm.eval(env + v)
    override fun getCallTarget(lang: TruffleLanguage<*>): CallTarget = EvalRootNode(tm, lang).callTarget
    override fun getArgs() = env.it as Array<Any?>
}
data class TruffleClosure(val callTarget: CallTarget, val env: Array<Any?>): Callable {
    override fun inst(v: Val): Val = callTarget.call(env + v).let { if (it is Val) it else throw UnexpectedResultException(it) }
    override fun getCallTarget(lang: TruffleLanguage<*>): CallTarget = callTarget
    override fun getArgs(): Array<Any?> = env
}

fun buildArgs(frame: MaterializedFrame): Array<Any?> {
    val ret = arrayOfNulls<Any?>(frame.frameDescriptor.size)
    for (fs in frame.frameDescriptor.slots) {
        ret[fs.identifier as Int] = frame.getObject(fs)
    }
    return ret
}
