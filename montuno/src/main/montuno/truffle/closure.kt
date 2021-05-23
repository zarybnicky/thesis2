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
import montuno.Lvl
import montuno.interpreter.*
import montuno.syntax.Icit
import java.util.*

interface Closure {
    fun execute(vararg args: Any?): Any?
    fun inst(v: Val): Val
}

data class PureClosure(val ctx: MontunoContext, val env: VEnv, val t: Term) : Closure {
    override fun execute(vararg args: Any?): Any? {
        assert(args.isNotEmpty())
        var v = inst(args[0] as Val)
        for (i in 1 until args.size) {
            v = v.app(Icit.Expl, args[i] as Val)
        }
        return v
    }
    override fun inst(v: Val): Val = t.eval(ctx, env + v)
}

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class TruffleClosure (
    @JvmField @CompilerDirectives.CompilationFinal(dimensions = 1) val papArgs: Array<out Any?>,
    @JvmField val type: Term,
    @JvmField val arity: Int,
    @JvmField val maxArity: Int,
    @JvmField val callTarget: CallTarget
) : TruffleObject, Closure {
    //val type get() = targetType.after(papArgs.size)
    init {
        //assert(arity + papArgs.size == (callTarget.rootNode as ClosureRootNode).arity)
        assert(arity <= maxArity - papArgs.size)
    }

    @ExportMessage fun isExecutable() = true

    @ExportMessage
    override fun execute(vararg args: Any?): Any? = callClosure(this, args)
    override fun inst(v: Val) = callClosure(this, arrayOf(v)) as Val
    fun force() = forceClosure(this)
}

fun buildArgs(frame: MaterializedFrame): Array<Any?> {
    val ret = arrayOfNulls<Any?>(frame.frameDescriptor.size)
    for (fs in frame.frameDescriptor.slots) {
        ret[fs.identifier as Int] = frame.getObject(fs)
    }
    return ret
}

@ExplodeLoop
fun forceClosure(cl: TruffleClosure): Any? {
    val baseLvl = cl.maxArity - cl.arity
    val nbeArgs: Array<Val> = Array(cl.arity) { i -> VLocal(Lvl(baseLvl + i)) }
    return cl.callTarget.call(concat(cl.papArgs, nbeArgs))
}

@ExplodeLoop
@Throws(ArityException::class, UnsupportedTypeException::class)
fun callClosure(cl: TruffleClosure, args: Array<out Any?>): Any? {
    //if (args.size > cl.maxArity) throw ArityException.create(cl.maxArity, args.size)
    //arguments.fold(type) { t, it -> (t as Arr).apply { argument.validate(it) }.result }
    val resArgs = concat(cl.papArgs, args)
    return when {
        args.size < cl.arity -> TruffleClosure(resArgs, cl.type, cl.arity - args.size, cl.maxArity, cl.callTarget)
        args.size == cl.arity -> cl.callTarget.call(*resArgs)
        else -> {
            val g = cl.callTarget.call(*Arrays.copyOfRange(resArgs, 0, cl.maxArity))
            val sp = VSpine(Arrays.copyOfRange(resArgs, cl.maxArity, resArgs.size).map { SApp(Icit.Expl, it as Val) }.toTypedArray())
            return sp.applyTo(g as Val)
        }
    }
}

class PureRootNode(val tm: Term, val ctx: MontunoContext, lang: TruffleLanguage<MontunoContext>, fd: FrameDescriptor) : RootNode(lang, fd) {
    init {
        callTarget = Truffle.getRuntime().createCallTarget(this)
    }
    override fun execute(frame: VirtualFrame): Any {
        val args = frame.arguments
        var res = tm.eval(ctx, VEnv(frame.arguments.map { it as Val? }.toTypedArray()))
        for (arg in args) {
            res = res.app(Icit.Expl, arg as Val)
        }
        return res
    }
}
class TruffleRootNode(@field:Children val nodes: Array<Code>, lang: TruffleLanguage<MontunoContext>, fd: FrameDescriptor) : RootNode(lang, fd) {
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