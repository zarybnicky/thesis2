package montuno.truffle

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.frame.FrameDescriptor
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
import montuno.interpreter.*
import montuno.syntax.Icit
import java.util.*

interface Closure {
    fun inst(v: Val): Val = execute(v)
    fun execute(vararg args: Any?): Val
    val arity: Int
}

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
data class ConstClosure(override val arity: Int, val value: Val) : TruffleObject, Closure {
    @ExportMessage fun isExecutable() = true
    @ExportMessage override fun execute(vararg args: Any?): Val = when {
        args.size < arity -> VLam(null, Icit.Expl, VUnit, ConstClosure(arity - args.size, value))
        args.size == arity -> value
        else -> (arity until args.size - 1).fold(value) { l, i -> l.app(Icit.Expl, args[i] as Val) }
    }
}

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
data class PureClosure(val ctx: MontunoContext, val env: VEnv, val body: Term) : TruffleObject, Closure {
    override val arity: Int = 1
    @ExportMessage fun isExecutable() = true
    @ExportMessage override fun execute(vararg args: Any?): Val {
        if (args.isEmpty()) {
            return body.eval(ctx, env)
        }
        return body.eval(ctx, VEnv(concat(env.it, args.map { it as Val }.toTypedArray())))
    }
}

data class ClosureHead(val isPi: Boolean, val name: String?, val icit: Icit, val bound: Term)

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
class TruffleClosure (
    @JvmField val ctx: MontunoContext,
    @JvmField @CompilerDirectives.CompilationFinal(dimensions = 1) val papArgs: Array<out Any?>,
    @JvmField val heads: Array<ClosureHead>,
    @JvmField val maxArity: Int,
    @JvmField val callTarget: CallTarget
) : TruffleObject, Closure {
    override val arity: Int = heads.size + 1
    @ExportMessage fun isExecutable() = true
    @Throws(ArityException::class, UnsupportedTypeException::class)
    @ExportMessage override fun execute(vararg args: Any?): Val = when {
        args.isEmpty() -> TODO("impossible")
        args.size < arity -> {
            val newArgs = concat(papArgs, args)
            val head = heads[args.size - 1]
            val remaining = heads.copyOfRange(args.size, heads.size)
            val closure = TruffleClosure(ctx, newArgs, remaining, maxArity, callTarget)
            when {
                head.isPi -> VPi(head.name, head.icit, head.bound.eval(ctx, VEnv(newArgs)), closure)
                else -> VLam(head.name, head.icit, head.bound.eval(ctx, VEnv(newArgs)), closure)
            }
        }
        args.size == arity -> callTarget.call(*concat(papArgs, args)) as Val
        else -> {
            val resArgs = concat(papArgs, args)
            val g = callTarget.call(*Arrays.copyOfRange(resArgs, 0, maxArity)) as Val
            val rest = Arrays.copyOfRange(resArgs, maxArity, resArgs.size).map { SApp(Icit.Expl, it as Val) }
            VSpine(rest.toTypedArray()).applyTo(g)
        }
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