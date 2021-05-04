package montuno.truffle

import com.oracle.truffle.api.*
import com.oracle.truffle.api.dsl.*
import com.oracle.truffle.api.frame.*
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.profiles.BranchProfile
import com.oracle.truffle.api.source.SourceSection
import montuno.interpreter.Lvl
import montuno.interpreter.NameTable
import montuno.interpreter.meta.*
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
    constructor(that: Term) : this(that.loc)

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
class TU(loc: Loc? = null) : Term(loc) {
    override fun execute(frame: VirtualFrame): Any = VU
}
class TNat(val n: Int, loc: Loc? = null) : Term(loc) {
    override fun execute(frame: VirtualFrame) = n
}
class TString(val n: String, loc: Loc? = null) : Term(loc) {
    override fun execute(frame: VirtualFrame) = n
}
class TLam(
    @field:CompilerDirectives.CompilationFinal(dimensions = 1) val captures: Array<FrameSlot>,
    private val arity: Int,
    @field:CompilerDirectives.CompilationFinal var callTarget: RootCallTarget,
    loc: Loc? = null
) : Term(loc) {
    override fun execute(frame: VirtualFrame) = VClosure(arrayOf(), arity, arity, callTarget)
    override fun executeClosure(frame: VirtualFrame): VClosure = VClosure(arrayOf(), arity, arity, callTarget)
}

//class TLam(val arity: Int, val function: VClosure, loc: Loc? = null) : Term(loc) {
//    @CompilerDirectives.CompilationFinal var isScopeSet = false
//    @Specialization
//    fun getMumblerFunction(virtualFrame: VirtualFrame): Any {
//        if (!isScopeSet) {
//            CompilerDirectives.transferToInterpreterAndInvalidate()
//            function.setLexicalScope(virtualFrame.materialize())
//            isScopeSet = true
//        }
//        return function
//    }
//}
class TApp(
    @field:Child var rator: Term,
    @field:Children val rands: Array<Term>,
    loc: Loc? = null,
    tail_call: Boolean = false
) : Term(loc) {
    @Child private var dispatch: Dispatch = DispatchNodeGen.create()
    @ExplodeLoop private fun executeRands(frame: VirtualFrame): Array<Any?> = rands.map { it.executeAny(frame) }.toTypedArray()
    override fun execute(frame: VirtualFrame): Any = dispatch.executeDispatch(rator.executeClosure(frame).callTarget, executeRands(frame))
    override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.CallTag::class.java || super.hasTag(tag)
}
abstract class TVar(private val slot: FrameSlot, loc: Loc? = null) : Term(loc) {
    @Specialization() //(replaces = ["readInt", "readBoolean"])
    protected fun read(frame: VirtualFrame): Any? = frame.getValue(slot)
    override fun isAdoptable() = false
}

fun toExecutableNode(p: TopLevel, l: Language): Term = when (p) {
    is RDecl -> TODO("RDecl")
    is RDefn -> TODO("RDefn")
    is RTerm -> when (p.cmd) {
        Command.Elaborate -> TODO("Elaborate")
        Command.Normalize -> TODO("Normalize")
        Command.ParseOnly -> TString(p.tm.toString())
        Command.Nothing -> LocalContext(l).toExecutableNode(p.tm)
    }
}

// LocalContext = language, nameTable, frameDescriptor
data class LocalContext(
    val l: Language,
    val ntbl: NameTable = NameTable(),
    val fd: FrameDescriptor = FrameDescriptor(),
    val fdLvl: Lvl = Lvl(0)
)
fun <A> LocalContext.withName(n: String, ni: NameInfo, f: () -> A): A = ntbl.withName(n, ni, f)

fun LocalContext.toExecutableNode(p: PreTerm): Term {
    return when (p) {
        is RU -> TU(p.loc)
        is RVar -> {
            for (ni in ntbl[p.n].asReversed()) when {
                ni is NITop -> TODO("Call Top")
                ni is NILocal && !ni.inserted -> return TVarNodeGen.create(fd.findFrameSlot(ni.lvl), p.loc)
            }
            throw RuntimeException("Variable ${p.n} out of scope")
        }
        is RNat -> TNat(p.n, p.loc)
        is RLam -> {
            fd.addFrameSlot(fdLvl, FrameSlotKind.Object)
            val root = FunctionRootNode(l, fd, arrayOf(withName(p.n, NILocal(p.loc, fdLvl, false)) { toExecutableNode(p.body) }))
            TLam(arrayOf(), 1, Truffle.getRuntime().createCallTarget(root), p.loc)
        }
        is RApp -> TApp(toExecutableNode(p.rand), arrayOf(toExecutableNode(p.rator)), p.loc, false)
        is RLet -> TODO("RLet")
        is RFun -> TODO("RFun")
        is RPi -> TODO("RPi")
        is RHole -> TODO("RHole")
        is RStopMeta -> TODO("RStopMeta")
        is RForeign -> TODO("RForeign")
    }
}

@TypeSystemReference(Types::class)
class FunctionRootNode(
    l: TruffleLanguage<*>?,
    fd: FrameDescriptor,
    @field:Children val nodes: Array<Term>
): RootNode(l, fd) {
    @ExplodeLoop
    override fun execute(frame: VirtualFrame): Any {
        var res: Any = VU
        for (n in nodes) {
            res = n.execute(frame)
        }
        return res
    }
}

@TypeSystemReference(Types::class)
class ProgramRootNode(
    l: TruffleLanguage<*>?,
    fd: FrameDescriptor,
    @field:Children val nodes: Array<Term>,
    private val executionFrame: MaterializedFrame
) : RootNode(l, fd) {
    val target = Truffle.getRuntime().createCallTarget(this)
    override fun isCloningAllowed() = true
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

abstract class Issue : Node() {
    abstract fun execute(frame: VirtualFrame, foo: Unit, newFrame: MaterializedFrame)
    @Specialization(guards=["newFrame.getArguments().length == 2"])
    fun do2Args(frame: VirtualFrame, foo: Unit, newFrame: MaterializedFrame) {}
    @Specialization(guards=["newFrame.getArguments().length == 3"])
    fun do3Args(frame: VirtualFrame, foo: Unit, newFrame: MaterializedFrame) {}
}

// @Specialisation(guards = "f.arity < args.length")
//curry(Function f, Object[] args,
//    @Cached("createCallNode()") CallNode callNode) {
//  callNode.curry(f, args);
//}
//
//@Specialisation(guards = "f.arity == args.length")
//exact(Function f, Object[] args,
//    @Cached("createCallNode()") CallNode callNode) {
//  callNode.call(f, args);
//}
//
//@ExplodeLoop
//@Specialisation(guards = {"f.arity > args.length", "f.arity == cachedArity" "args.length == cachedArgsLength"})
//over(Function f, Object[] args,
//    @Cached("f.arity") int cachedArity,
//    @Cached("args.length") int cachedArgsLength,
//    @Cached("createCallNode()") CallNode callNode) {
//  Function f = callNode.call(f, Arrays.copyOfRange(args, 0, cachedArity));
//
//  // Can explode becuase cachedArity and cachedArgsLength are now constant
//  for (int n = cachedArity; n < cachedArgsLength - 1) {
//    f = callNode.call(f, args[n]);
//  }
//
//  return callNode.call(f, args[cachedArgsLength - 1]);
//}
//
//@Specialisation(guards = {"f.arity > args.length"}, contains="over")
//overFallback(Function f, Object[] args,
//    @Cached("createCallNode()") CallNode callNode) {
//  Function f = callNode.call(f, Arrays.copyOfRange(args, 0, cachedArity));
//
//  // Can't explode - these will need to be indirect calls which can't be inlined
//  for (int n = cachedArity; n < cachedArgsLength - 1) {
//    f = callNode.call(f, args[n]);
//  }
//
//  return callNode.call(f, args[cachedArgsLength - 1]);
//}