package montuno.truffle

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.frame.FrameDescriptor
import montuno.Lvl
import montuno.interpreter.*
import montuno.syntax.Icit

abstract class Compiler(val ctx: MontunoContext) {
    abstract fun buildClosure(t: Term, env: VEnv): Closure
}

class PureCompiler(ctx: MontunoContext): Compiler(ctx) {
    override fun buildClosure(t: Term, env: VEnv): Closure = PureClosure(ctx, env, t)
}

class TruffleCompiler(ctx: MontunoContext) : Compiler(ctx) {
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
        is TSg -> { // not correct
            val (heads, body) = stripHeads(t.body)
            CClosure(t.name, Icit.Expl, compileTerm(t.bound, depth, fd), false, ctx, heads, compile(body, depth + 1), null)
        }
        is TApp -> CApp(t.icit, compileTerm(t.lhs, depth, fd), compileTerm(t.rhs, depth, fd), null)
        is TLet -> CWriteLocal(fd.addFrameSlot(depth.it), compileTerm(t.bound, depth + 1, fd), compileTerm(t.body, depth + 1, fd), null)
        is TLocal -> CReadLocal(fd.findFrameSlot(t.ix.toLvl(depth).it), null)
        is TMeta -> CDerefMeta(t.slot, null)
        is TTop -> CConstant(t.slot.defnV!!, null)
        is TNat -> CConstant(VNat(t.n), null)
        is TBool -> CConstant(VBool(t.n), null)
        TUnit -> CConstant(VUnit, null)
        is TPair -> CPair(compileTerm(t.lhs, depth, fd), compileTerm(t.rhs, depth, fd), null)
        is TProj1 -> CProj1(compileTerm(t.body, depth, fd), null)
        is TProj2 -> CProj2(compileTerm(t.body, depth, fd), null)
        is TProjF -> CProjF(compileTerm(t.body, depth, fd), t.name, t.i, null)
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
