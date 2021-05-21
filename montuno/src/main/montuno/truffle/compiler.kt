package montuno.truffle

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.source.Source
import montuno.Ix
import montuno.Lvl
import montuno.interpreter.*

abstract class Compiler(val ctx: MontunoContext) {
    abstract fun compile(t: Term): CallTarget
}

class PureCompiler(ctx: MontunoContext): Compiler(ctx) {
    override fun compile(t: Term): CallTarget = EvalRootNode(t, ctx.top.lang).callTarget
}

class TruffleCompiler(
    ctx: MontunoContext,
    private var depth: Lvl = Lvl(0),
    private val fd: FrameDescriptor = FrameDescriptor(),
) : Compiler(ctx) {
    private var argPreamble = arrayOf<Code>()
    init {
        for (lvl in 0 until depth.it){
            val fs = fd.addFrameSlot(lvl)
            argPreamble += CArg(Ix(depth.it - lvl - 1), fs, null)
        }
    }
    override fun compile(t: Term): CallTarget {
        val code = compileTerm(t)
        val root = ClosureRootNode(argPreamble + code, ctx.top.lang, fd).callTarget
        // println(root)
        // root.rootNode.children.forEach { it.accept { node -> println("  $node"); true } }
        return root
    }
    private fun compileTerm(t: Term): Code = when (t) {
        is TPi -> {
            val closure = TruffleCompiler(ctx, depth + 1).compile(t.body)
            CPi(t.name, t.icit, compileTerm(t.bound), closure, null)
        }
        is TLam -> {
            val closure = TruffleCompiler(ctx,depth + 1).compile(t.body)
            CLam(t.name, t.icit, closure, null)
        }
        is TApp -> CApp(t.icit, compileTerm(t.lhs), compileTerm(t.rhs), ctx.top.lang, null)
        is TFun -> CFun(compileTerm(t.lhs), compileTerm(t.rhs), null)
        is TLet -> {
            val fs = fd.addFrameSlot(depth.it)
            depth += 1
            CWriteLocal(fs, compileTerm(t.bound), compileTerm(t.body), null)
        }
        is TLocal -> CReadLocal(fd.findFrameSlot(t.ix.toLvl(depth).it), null)
        is TForeign -> CInvoke(ctx.env.parseInternal(Source.newBuilder(t.lang, t.code, "<eval>").build()))
        is TMeta -> if (t.slot.callTarget != null) CInvoke(t.slot.callTarget!!) else CDerefMeta(t.slot, null)
        is TTop -> CInvoke(t.slot.callTarget!!)
        is TNat -> CConstant(VNat(t.n), null)
        TIrrelevant -> CConstant(VIrrelevant, null)
        TUnit -> CConstant(VUnit, null)
    }
}
