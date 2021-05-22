package montuno.truffle

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.interop.ArityException
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.interop.UnsupportedTypeException
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.library.ExportMessage
import com.oracle.truffle.api.nodes.ExplodeLoop
import com.oracle.truffle.api.nodes.NodeUtil.concat
import com.oracle.truffle.api.nodes.RootNode
import montuno.syntax.Icit
import montuno.Lvl
import montuno.interpreter.*
import java.util.*

sealed class ClosureRootNode(lang: TruffleLanguage<MontunoContext>, fd: FrameDescriptor) : RootNode(lang, fd) {
    init {
        callTarget = Truffle.getRuntime().createCallTarget(this)
    }
}
class PureRootNode(val tm: Term, val ctx: MontunoContext, lang: TruffleLanguage<MontunoContext>, fd: FrameDescriptor) : ClosureRootNode(lang, fd) {
    override fun execute(frame: VirtualFrame): Any {
        val args = frame.arguments
        println("$tm , ${frame.arguments.size}")
        var res = tm.eval(ctx, VEnv(frame.arguments as Array<Val?>))
        for (arg in args) {println("$tm, $arg")
            res = res.app(Icit.Expl, arg as Val)
        }
        return res
    }
}
class TruffleRootNode(@field:Children val nodes: Array<Code>, lang: TruffleLanguage<MontunoContext>, fd: FrameDescriptor) : RootNode(lang, fd) {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any? {
        var res: Any? = VUnit
        for (e in nodes) {
            res = e.execute(frame)
        }
        return res
    }
}

fun buildArgs(frame: MaterializedFrame): Array<Any?> {
    val ret = arrayOfNulls<Any?>(frame.frameDescriptor.size)
    for (fs in frame.frameDescriptor.slots) {
        ret[fs.identifier as Int] = frame.getObject(fs)
    }
    return ret
}

@ExplodeLoop
@Throws(ArityException::class, UnsupportedTypeException::class)
fun callClosure(cl: Closure, args: Array<out Any?>): Any? {
    if (args.size > cl.maxArity) throw ArityException.create(cl.maxArity, args.size)
    //arguments.fold(type) { t, it -> (t as Arr).apply { argument.validate(it) }.result }
    val resArgs = concat(cl.papArgs, args)
    return when {
        args.size < cl.arity -> Closure(resArgs, cl.arity - args.size, cl.maxArity, cl.callTarget)
        args.size == cl.arity -> cl.callTarget.call(resArgs)
        else -> {
            val g = cl.callTarget.call(Arrays.copyOfRange(resArgs, 0, cl.maxArity))
            (g as Closure).execute(Arrays.copyOfRange(resArgs, cl.maxArity, resArgs.size))
        }
    }
}
@ExplodeLoop
fun forceClosure(cl: Closure): Any? {
    val baseLvl = cl.maxArity - cl.arity
    val nbeArgs: Array<Val> = Array(cl.arity) { i -> VLocal(Lvl(baseLvl + i)) }
    return cl.callTarget.call(concat(cl.papArgs, nbeArgs))
}

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class Closure (
    @JvmField @CompilerDirectives.CompilationFinal(dimensions = 1) val papArgs: Array<out Any?>,
    @JvmField val arity: Int,
    @JvmField val maxArity: Int,
    //private val targetType: Type,
    @JvmField val callTarget: CallTarget
) : TruffleObject {
    //val type get() = targetType.after(papArgs.size)
    init {
        //assert(arity + papArgs.size == (callTarget.rootNode as ClosureRootNode).arity)
        assert(arity <= maxArity - papArgs.size)
    }

    @ExportMessage fun isExecutable() = true

    @ExportMessage fun execute(vararg args: Any?): Any? = callClosure(this, args)
    fun inst(v: Val) = callClosure(this, arrayOf(v)) as Val
    fun force() = forceClosure(this)
}
