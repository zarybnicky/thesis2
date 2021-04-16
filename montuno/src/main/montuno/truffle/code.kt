package montuno.truffle

import com.oracle.truffle.api.*
import com.oracle.truffle.api.dsl.*
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.profiles.BranchProfile
import com.oracle.truffle.api.source.SourceSection
import montuno.syntax.*

//@TypeSystemReference(Types::class)
//@ReportPolymorphism
//abstract class Builtin(val arity: Int) : Node(), Serializable {
//    abstract fun run(frame: VirtualFrame, args: Array<Any?>): Any?
//    open fun runUnit(frame: VirtualFrame, args: Array<Any?>) { run(frame, args) }
//    open fun runClosure(frame: VirtualFrame, args: Array<Any?>): Closure = TypesGen.expectClosure(run(frame, args))
//    open fun runBoolean(frame: VirtualFrame, args: Array<Any?>): Boolean = TypesGen.expectBoolean(run(frame, args))
//    open fun runInteger(frame: VirtualFrame, args: Array<Any?>): Int = TypesGen.expectInteger(run(frame, args))
//}
//val initialCtx: Ctx = arrayOf(
//    Pair("le", LeNodeGen.create()),
//    Pair("fixNatF", FixNatFNodeGen.create()),
//    Pair("plus", PlusNodeGen.create()),
//    Pair("minus", MinusNodeGen.create()),
//    Pair("eq",EqNodeGen.create()),
//    Pair("mod",ModNodeGen.create()),
//    Pair("div",DivNodeGen.create()),
//    Pair("mult",MultNodeGen.create()),
//    Pair("printId",PrintId())
//)
@ReportPolymorphism
@GenerateWrapper
@NodeInfo(language = "montuno")
@TypeSystemReference(Types::class)
abstract class Term(val loc: Loc?) : Node(), InstrumentableNode {
    public constructor(that: Term) : this(that.loc)

    abstract fun execute(frame: VirtualFrame): Any

    fun executeAny(frame: VirtualFrame): Any = execute(frame)
    open fun executeClosure(frame: VirtualFrame): VClosure = TypesGen.expectVClosure(execute(frame))
    fun executeU(frame: VirtualFrame): VU = TypesGen.expectVU(execute(frame))

    override fun getSourceSection(): SourceSection? = loc?.section(rootNode?.sourceSection?.source)
    override fun isInstrumentable(): Boolean = loc != null
    override fun createWrapper(probe: ProbeNode?): InstrumentableNode.WrapperNode = TermWrapper(this, this, probe)

    override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.ExpressionTag::class.java //TODO: not all are exprs

    fun getLexicalScope(frame: Frame): MaterializedFrame? = frame.arguments.getOrNull(0) as MaterializedFrame?
}
class TU(loc: Loc? = null) : Term(null) {
    override fun execute(frame: VirtualFrame): Any = VU
}
class TNat(val n: Int, loc: Loc? = null) : Term(loc) {
    override fun execute(frame: VirtualFrame) = n
}
class TString(val n: String, loc: Loc? = null) : Term(loc) {
    override fun execute(frame: VirtualFrame) = n
}
class TLam(
    @CompilerDirectives.CompilationFinal(dimensions = 1) val captures: Array<FrameSlot>,
    private val arity: Int,
    @CompilerDirectives.CompilationFinal var callTarget: RootCallTarget,
    loc: Loc? = null
) : Term(loc) {
    override fun execute(frame: VirtualFrame) = VClosure(arrayOf(), arity, callTarget)
    override fun executeClosure(frame: VirtualFrame): VClosure = VClosure(arrayOf(), arity, callTarget)
}

fun toExecutableNode(p: TopLevel): Term = when (p) {
    is RDecl -> TODO()
    is RDefn -> TODO()
    is RTerm -> when (p.cmd) {
        Command.Elaborate -> TODO()
        Command.Normalize -> TODO()
        Command.ParseOnly -> TString(p.tm.toString())
        Command.Nothing -> toExecutableNode(p.tm)
    }
}

fun toExecutableNode(p: PreTerm): Term = when (p) {
    is RU -> TU(p.loc)
    is RVar -> TODO()
    is RNat -> TNat(p.n, p.loc)
    is RLam -> TLam(arrayOf(), 1, Truffle.getRuntime().createCallTarget(toExecutableNode(p.body) as RootNode), p.loc)
    is RApp -> TODO()
    is RLet -> TODO()
    is RFun -> TODO()
    is RPi -> TODO()
    is RHole -> TODO()
    is RStopMeta -> TODO()
    is RForeign -> TODO()
}

@TypeSystemReference(Types::class)
class ProgramRootNode(
    l: TruffleLanguage<*>?,
    fd: FrameDescriptor,
    @Children val nodes: Array<Term>,
    private val executionFrame: MaterializedFrame
) : RootNode(l, fd) {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        var res: Any = VU
        for (n in nodes) {
            res = n.execute(executionFrame)
        }
        return res
    }
}

//TODO: ClosureRootNode, FunctionRootNode, BuiltinRootNode

//open class App(
//    @field:Child var rator: Code,
//    @field:Children val rands: Array<Code>,
//    loc: Loc? = null,
//    tail_call: Boolean = false
//) : Code(loc) {
//    @Child private var dispatch: Dispatch = DispatchNodeGen.create()
//
//    @ExplodeLoop
//    private fun executeRands(frame: VirtualFrame): Array<Any?> = rands.map { it.executeAny(frame) }.toTypedArray()
//
//    override fun execute(frame: VirtualFrame): Any {
//        val fn = rator.executeClosure(frame)
//        return dispatch.executeDispatch(fn.callTarget, executeRands(frame))
//    }
//
//    override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.CallTag::class.java || super.hasTag(tag)
//}
//
//abstract class Var protected constructor(private val slot: FrameSlot, loc: Loc? = null) : Code(loc) {
//    @Specialization() //(replaces = ["readInt", "readBoolean"])
//    protected fun read(frame: VirtualFrame): Any? = frame.getValue(slot)
//    override fun isAdoptable() = false
//}

@ReportPolymorphism
abstract class Dispatch : Node() {
    abstract fun executeDispatch(callTarget: CallTarget, ys: Array<Any?>): Any

    @Specialization(guards = [
        "callTarget == cachedCallTarget"
    ], limit = "3")
    fun callDirect(callTarget: CallTarget, ys: Array<Any?>?,
                   @Cached("callTarget") cachedCallTarget: CallTarget,
                   @Cached("create(cachedCallTarget)") callNode: DirectCallNode
    ): Any? {
        return callNode.call(ys)
    }

    @Specialization
    fun callIndirect(callTarget: CallTarget, ys: Array<Any?>?,
                     @Cached("create()") callNode: IndirectCallNode
    ): Any? {
        return callNode.call(callTarget, ys)
    }
}

class IndirectCallerNode() : Node() {
    @Child private var callNode: IndirectCallNode = IndirectCallNode.create()

    private val normalCallProfile = BranchProfile.create()
    private val tailCallProfile = BranchProfile.create()

    fun call(frame: VirtualFrame, callTarget: RootCallTarget, args: Array<Any?>, tail_call: Boolean): Any? {
        return callNode.call(callTarget, args)
    }

    companion object {
        @JvmStatic fun create() = IndirectCallerNode()
    }
}
