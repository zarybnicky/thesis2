package montuno.truffle

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RootNode
import montuno.ElabError
import montuno.Ix
import montuno.Lvl
import montuno.interpreter.*
import montuno.syntax.Icit
import montuno.syntax.Loc
import montuno.todo

abstract class Compiler(val ctx: MontunoContext) {
    abstract fun buildClosure(t: Term, env: VEnv): Closure
    open fun getBuiltin(name: String): Pair<Val, Val> = when (name) {
        "Nat" -> {
            // Nat : Unit = (N : Unit) -> (N -> N) -> N -> N
            val nToN0 = TPi(null, Icit.Expl, TLocal(Ix(0)), TLocal(Ix(1)))
            val nToN1 = TPi(null, Icit.Expl, TLocal(Ix(1)), TLocal(Ix(2)))
            val type = TPi("N", Icit.Expl, TUnit, TLam(null, Icit.Expl, nToN0, nToN1))
            type.eval(ctx, VEnv()) to VUnit
        }
        else -> throw ElabError(Loc.Unavailable, "Unknown built-in $name")
    }
}

class PureCompiler(ctx: MontunoContext): Compiler(ctx) {
    override fun buildClosure(t: Term, env: VEnv): Closure = PureClosure(ctx, env, t)
}

@TypeSystemReference(Types::class)
class BuiltinRootNode(@field:Child var node: Builtin, lang: TruffleLanguage<*>) : RootNode(lang) {
    override fun execute(frame: VirtualFrame): Any? = node.run(frame, frame.arguments)
    init {
        callTarget = Truffle.getRuntime().createCallTarget(this)
    }
}
@TypeSystemReference(Types::class)
@ReportPolymorphism
abstract class Builtin : Node() {
    abstract fun run(frame: VirtualFrame, args: Array<Any?>): Any?
}
abstract class Builtin1() : Builtin() {
    abstract fun execute(value: Any?): Any?
    override fun run(frame: VirtualFrame, args: Array<Any?>): Any? = execute(args[args.size - 1])
}
abstract class Builtin2() : Builtin() {
    abstract fun execute(left: Any?, right: Any?): Any?
    override fun run(frame: VirtualFrame, args: Array<Any?>): Any? = execute(args[args.size - 2], args[args.size - 1])
}
abstract class SuccBuiltin() : Builtin1() {
    @Specialization
    fun succ(value: Any?): Val = VNat((value as VNat).n + 1)
}

class TruffleCompiler(ctx: MontunoContext) : Compiler(ctx) {
    private val builtinRoots: Map<String, CallTarget> = mapOf(
        "succ" to BuiltinRootNode(SuccBuiltinNodeGen.create(), ctx.top.lang).callTarget
    )
    override fun getBuiltin(name: String): Pair<Val, Val> = when (name) {
        "zero" -> VNat(0) to getBuiltin("Nat").first
        "succ" -> {
            val nat = getBuiltin("Nat").first
            val cl = TruffleClosure(ctx, emptyArray(), emptyArray(), 1, builtinRoots["succ"]!!)
            val body = VLam("x", Icit.Expl, nat, cl)
            body to VLam(null, Icit.Expl, nat, ConstClosure(1, nat))
        }
        else -> super.getBuiltin(name)
    }
    override fun buildClosure(t: Term, env: VEnv): Closure {
        val (heads, bodyTerm) = stripHeads(t)
        val body = compile(bodyTerm, Lvl(env.it.size + heads.size + 1))
        return TruffleClosure(ctx, env.it, heads, heads.size + 1, body)
    }
    private fun compile(t: Term, depth: Lvl): CallTarget {
        val fd = FrameDescriptor()
        val code = mutableListOf<Code>()
        for (lvl in 0 until depth.it) {
            code.add(CArg(lvl, fd.addFrameSlot(lvl), null))
        }
        code.add(compileTerm(t, depth, fd))
        return TruffleRootNode(code.toTypedArray(), ctx.top.lang, fd).callTarget
    }
    private fun compileTerm(t: Term, depth: Lvl, fd: FrameDescriptor): Code = when (t) {
        is TPi -> {
            val (heads, body) = stripHeads(t.body)
            CClosure(t.name, t.icit, compileTerm(t.bound, depth, fd), true, ctx, heads, compile(body, depth + 1), null)
        }
        is TLam -> {
            val (heads, body) = stripHeads(t.body)
            CClosure(t.name, t.icit, compileTerm(t.type, depth, fd), false, ctx, heads, compile(body, depth + 1), null)
        }
        is TApp -> CApp(t.icit, compileTerm(t.lhs, depth, fd), compileTerm(t.rhs, depth, fd), null)
        is TLet -> CWriteLocal(fd.addFrameSlot(depth.it), compileTerm(t.bound, depth + 1, fd), compileTerm(t.body, depth + 1, fd), null)
        is TLocal -> CReadLocal(fd.findFrameSlot(t.ix.toLvl(depth).it), null)
        is TMeta -> CDerefMeta(t.slot, null)
        is TTop -> CConstant(t.slot.defnV!!, null)
        is TNat -> CConstant(VNat(t.n), null)
        TUnit -> CConstant(VUnit, null)
        is TPair -> CPair(compileTerm(t.lhs, depth, fd), compileTerm(t.rhs, depth, fd), null)
        is TProj1 -> CProj1(compileTerm(t.body, depth, fd), null)
        is TProj2 -> CProj2(compileTerm(t.body, depth, fd), null)
        is TProjF -> CProjF(compileTerm(t.body, depth, fd), t.name, t.i, null)
        is TSg -> todo  // CSg(t.name, compileTerm(t.bound, depth, fd), ctx, compile(t.body, depth + 1), null)
    }
}

fun stripHeads(t: Term): Pair<Array<ClosureHead>, Term> {
    val heads = mutableListOf<ClosureHead>()
    var x = t
    while (true) {
        x = when (x) {
            is TLam -> { heads.add(ClosureHead(false, x.name, x.icit, x.type)); x.body }
            is TPi -> { heads.add(ClosureHead(true, x.name, x.icit, x.bound)); x.body }
            else -> return heads.toTypedArray() to x
        }
    }
}
