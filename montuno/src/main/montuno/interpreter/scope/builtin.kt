package montuno.interpreter.scope

import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.RootNode
import montuno.interpreter.*
import montuno.syntax.Icit
import montuno.truffle.*

@TypeSystemReference(Types::class)
class BuiltinRootNode(@field:Child var node: BuiltinNode, lang: TruffleLanguage<*>) : RootNode(lang) {
    override fun execute(frame: VirtualFrame): Any? = node.run(frame, frame.arguments)
    fun getClosure(ctx: MontunoContext) = TruffleClosure(ctx, emptyArray(), emptyArray(), 1, callTarget)
    init {
        callTarget = Truffle.getRuntime().createCallTarget(this)
    }
}

// arity -> TLams
@TypeSystemReference(Types::class)
@ReportPolymorphism
abstract class BuiltinNode : Node() {
    abstract fun run(frame: VirtualFrame, args: Array<Any?>): Any?
}
abstract class Builtin1 : BuiltinNode() {
    abstract fun execute(value: Any?): Any?
    override fun run(frame: VirtualFrame, args: Array<Any?>): Any? = execute(args[args.size - 1])
    @Specialization fun forceTh(value: VThunk) = execute(value.value)
    @Specialization fun forceTt(value: VTop) = execute(value.forceUnfold())
    @Specialization fun forceM(value: VMeta) = execute(value.forceUnfold())
}
abstract class Builtin2 : BuiltinNode() {
    abstract fun execute(left: Any?, right: Any?): Any?
    override fun run(frame: VirtualFrame, args: Array<Any?>): Any? = execute(args[args.size - 2], args[args.size - 1])
    @Specialization fun forceLTh(value: VThunk, r: Any) = execute(value.value, r)
    @Specialization fun forceLTt(value: VTop, r: Any) = execute(value.forceUnfold(), r)
    @Specialization fun forceLM(value: VMeta, r: Any) = execute(value.forceUnfold(), r)
    @Specialization fun forceRTh(r: Any, value: VThunk) = execute(r, value.value)
    @Specialization fun forceRTt(r: Any, value: VTop) = execute(r, value.forceUnfold())
    @Specialization fun forceRM(r: Any, value: VMeta) = execute(r, value.forceUnfold())
}
abstract class SuccBuiltin : Builtin1() {
    @Specialization fun succ(value: VNat): Val = VNat(value.n + 1)
}
abstract class AddBuiltin : Builtin2() {
    @Specialization fun add(l: VNat, r: VNat): Val = VNat(l.n + r.n)
}
abstract class EqnBuiltin : Builtin2() {
    @Specialization fun eq(l: VNat, r: VNat): Val = VBool(l.n == r.n)
}

enum class Builtin { Nat, zero, succ, eqn, Bool, True, False }

class BuiltinScope(val ctx: MontunoContext) {
    fun getBuiltin(n: String): Pair<Val?, Val> = Builtin.valueOf(n).let { getVal(it) to getType(it) }
    fun getType(t: Builtin): Val = types.computeIfAbsent(t) { createBuiltinType(it) }
    private fun getVal(t: Builtin): Val? = values.computeIfAbsent(t) { createBuiltinValue(it) }

    private val values: MutableMap<Builtin, Val?> = mutableMapOf()
    private val types: MutableMap<Builtin, Val> = mutableMapOf()

    // utilities for creating builtins
    private fun const(v: Val): RootNode = TruffleRootNode(arrayOf(CConstant(v, null)), ctx.top.lang, FrameDescriptor())
    private fun fromHeads(ct: RootNode, vararg hs: ConstHead): Val {
        val h = hs.first()
        val hRest = hs.drop(1).toTypedArray()
        return h.toVal(h.bound, ConstClosure(emptyArray(), hRest, hRest.size + 1, ct.callTarget))
    }

    private fun createBuiltinValue(n: Builtin): Val? = when (n) {
        Builtin.Nat -> null
        Builtin.Bool -> null
        Builtin.True -> VBool(true)
        Builtin.False -> VBool(false)
        Builtin.zero -> VNat(0)
        Builtin.succ -> fromHeads(
            BuiltinRootNode(SuccBuiltinNodeGen.create(), ctx.top.lang),
            ConstHead(false, "x", Icit.Expl, getType(Builtin.Nat))
        )
        Builtin.eqn -> fromHeads(
            BuiltinRootNode(EqnBuiltinNodeGen.create(), ctx.top.lang),
            ConstHead(false, "x", Icit.Expl, getType(Builtin.Nat)),
            ConstHead(false, "y", Icit.Expl, getType(Builtin.Nat))
        )
    }
    private fun createBuiltinType(n: Builtin): Val = when (n) {
        Builtin.Nat -> VUnit
        Builtin.Bool -> VUnit
        Builtin.True -> getType(Builtin.Nat)
        Builtin.False -> getType(Builtin.Nat)
        Builtin.zero -> getType(Builtin.Nat)
        Builtin.succ -> fromHeads(
            const(getType(Builtin.Nat)),
            ConstHead(true, null, Icit.Expl, getType(Builtin.Nat))
        )
        Builtin.eqn -> fromHeads(
            const(getType(Builtin.Nat)),
            ConstHead(true, null, Icit.Expl, getType(Builtin.Nat)),
            ConstHead(true, null, Icit.Expl, getType(Builtin.Nat))
        )
    }
}
