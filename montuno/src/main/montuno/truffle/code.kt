package montuno.truffle

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerDirectives
import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.dsl.*
import com.oracle.truffle.api.frame.FrameDescriptor
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.interop.InteropLibrary
import com.oracle.truffle.api.interop.TruffleObject
import com.oracle.truffle.api.library.ExportLibrary
import com.oracle.truffle.api.nodes.*
import com.oracle.truffle.api.profiles.BranchProfile
import com.oracle.truffle.api.source.SourceSection
import montuno.syntax.Loc
import java.io.Serializable
import java.math.BigInteger

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
@NodeInfo(language = "core", description = "core nodes")
@TypeSystemReference(Types::class)
abstract class Code(val loc: Loc?) : Node(), InstrumentableNode {
    constructor(that: Code) : this(that.loc)

    abstract fun execute(frame: VirtualFrame): Any?

    open fun executeAny(frame: VirtualFrame): Any? = execute(frame)

    open fun executeClosure(frame: VirtualFrame): Closure = TypesGen.expectClosure(execute(frame))

    open fun executeInteger(frame: VirtualFrame): Int = TypesGen.expectInteger(execute(frame))

    open fun executeBoolean(frame: VirtualFrame): Boolean = TypesGen.expectBoolean(execute(frame))

    open fun executeUnit(frame: VirtualFrame) { execute(frame) }

    override fun getSourceSection(): SourceSection? = loc?.section(rootNode?.sourceSection?.source)
    override fun isInstrumentable() = loc !== null

    override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.ExpressionTag::class.java
    override fun createWrapper(probe: ProbeNode): InstrumentableNode.WrapperNode = CodeWrapper(this, this, probe)

    open class App(
        @field:Child var rator: Code,
        @field:Children val rands: Array<Code>,
        loc: Loc? = null,
        tail_call: Boolean = false
    ) : Code(loc) {
        @Child private var dispatch: Dispatch = DispatchNodeGen.create()

        @ExplodeLoop
        private fun executeRands(frame: VirtualFrame): Array<Any?> = rands.map { it.executeAny(frame) }.toTypedArray()

        private fun executeFn(frame: VirtualFrame, fn: Closure): Any? {
            return dispatch.executeDispatch(fn.callTarget, executeRands(frame))
        }

        override fun execute(frame: VirtualFrame): Any? {
            return executeFn(frame, rator.executeClosure(frame))
        }

        override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.CallTag::class.java || super.hasTag(tag)
    }

    @NodeInfo(shortName = "Lambda")
    class Lam(
        private val closureFrameDescriptor: FrameDescriptor?,
        @CompilerDirectives.CompilationFinal(dimensions = 1) val captures: Array<FrameSlot>,
        private val arity: Int,
        @field:CompilerDirectives.CompilationFinal
        internal var callTarget: RootCallTarget,
        loc: Loc? = null
    ) : Code(loc) {
        override fun execute(frame: VirtualFrame) = Closure(arrayOf(), arity, callTarget)
        override fun executeClosure(frame: VirtualFrame): Closure = Closure(arrayOf(), arity, callTarget)
    }

    class LitBool(val value: Boolean, loc: Loc? = null) : Code(loc) {
        override fun execute(frame: VirtualFrame) = value
        override fun executeBoolean(frame: VirtualFrame) = value
    }

    class LitInt(val value: Int, loc: Loc? = null) : Code(loc) {
        override fun execute(frame: VirtualFrame) = value
        override fun executeInteger(frame: VirtualFrame) = value
    }

    abstract class Var protected constructor(private val slot: FrameSlot, loc: Loc? = null) : Code(loc) {
        @Specialization() //(replaces = ["readInt", "readBoolean"])
        protected fun read(frame: VirtualFrame): Any? = frame.getValue(slot)

        override fun isAdoptable() = false
    }
}

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

@CompilerDirectives.ValueType
@ExportLibrary(InteropLibrary::class)
data class Closure (
    @JvmField @CompilerDirectives.CompilationFinal(dimensions = 1) val papArgs: Array<Any?>,
    @JvmField val arity: Int,
    @JvmField val callTarget: RootCallTarget
) : TruffleObject

@TypeSystem(
    Closure::class,
    Boolean::class,
    Int::class,
    Long::class
)
open class Types {
    companion object {
        @ImplicitCast
        @CompilerDirectives.TruffleBoundary
        fun castLong(value: Int): Long {
            return value.toLong()
        }
    }
}