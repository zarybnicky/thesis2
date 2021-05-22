package montuno.truffle

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.source.Source
import montuno.Ix
import montuno.Lvl
import montuno.interpreter.*

abstract class Compiler(val ctx: MontunoContext) {
    abstract fun compile(t: Term, arity: Int): CallTarget
    fun buildClosure(t: Term, env: Array<Val?>): Closure {
        val bodyArity = t.arity
        return Closure(env, env.size - bodyArity, bodyArity, compile(t, bodyArity))
    }
}

class PureCompiler(ctx: MontunoContext): Compiler(ctx) {
    override fun compile(t: Term, arity: Int): CallTarget = PureRootNode(t, ctx, ctx.top.lang, FrameDescriptor()).callTarget
}

class TruffleCompiler(ctx: MontunoContext) : Compiler(ctx) {
    override fun compile(t: Term, arity: Int): CallTarget {
        val depth = Lvl(0)
        val fd = FrameDescriptor()
        val code = mutableListOf<Code>()
        for (lvl in 0 until arity) {
            val fs = fd.addFrameSlot(lvl)
            code.add(CArg(Ix(depth.it - lvl - 1), fs, null))
        }
        code.add(compileTerm(t, depth, fd))
        return TruffleRootNode(code.toTypedArray(), ctx.top.lang, fd).callTarget
        // println(root)
        // root.rootNode.children.forEach { it.accept { node -> println("  $node"); true } }
    }
    private fun compileTerm(t: Term, depth: Lvl, fd: FrameDescriptor): Code = when (t) {
        is TPi -> CPi(t.name, t.icit, compileTerm(t.bound, depth, fd), buildClosure(t.body, emptyArray()), null)
        is TLam -> CLam(t.name, t.icit, buildClosure(t.body, emptyArray()), null)
        is TApp -> CApp(t.icit, compileTerm(t.lhs, depth, fd), compileTerm(t.rhs, depth, fd), ctx.top.lang, null)
        is TFun -> CFun(compileTerm(t.lhs, depth, fd), compileTerm(t.rhs, depth, fd), null)
        is TLet -> CWriteLocal(fd.addFrameSlot(depth.it), compileTerm(t.bound, depth + 1, fd), compileTerm(t.body, depth + 1, fd), null)
        is TLocal -> CReadLocal(fd.findFrameSlot(t.ix.toLvl(depth).it), null)
        is TForeign -> CInvoke(ctx.env.parseInternal(Source.newBuilder(t.lang, t.code, "<eval>").build()))
        is TMeta -> if (t.slot.callTarget != null) CInvoke(t.slot.callTarget!!) else CDerefMeta(t.slot, null)
        is TTop -> CInvoke(t.slot.callTarget!!)
        is TNat -> CConstant(VNat(t.n), null)
        TIrrelevant -> CConstant(VIrrelevant, null)
        TUnit -> CConstant(VUnit, null)
    }
}
