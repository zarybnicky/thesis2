package montuno.truffle

import com.oracle.truffle.api.TruffleLanguage
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.nodes.UnexpectedResultException
import com.oracle.truffle.api.source.SourceSection
import montuno.Ix
import montuno.interpreter.Types
import montuno.interpreter.Val
import montuno.interpreter.scope.MetaEntry
import montuno.panic
import montuno.syntax.Icit
import montuno.syntax.Loc
import montuno.todo

@ReportPolymorphism
@GenerateWrapper
@NodeInfo(language = "montuno")
@TypeSystemReference(Types::class)
abstract class Code(val loc: Loc?) : Node(), InstrumentableNode {
    constructor(that: Code) : this(that.loc)
    override fun getSourceSection(): SourceSection? = loc?.section(rootNode?.sourceSection?.source)
    override fun isInstrumentable(): Boolean = loc != null
    override fun createWrapper(probe: ProbeNode?): InstrumentableNode.WrapperNode =
        CodeWrapper(this, this, probe)
    override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.ExpressionTag::class.java

    abstract fun execute(frame: VirtualFrame): Any?
    open fun executeAny(frame: VirtualFrame): Any? = execute(frame)
    @Throws(UnexpectedResultException::class)
    open fun executeVal(frame: VirtualFrame): Val = execute(frame).let { if (it is Val) it else throw UnexpectedResultException(it) }
    @Throws(UnexpectedResultException::class)
    open fun executeClosure(frame: VirtualFrame): Closure = execute(frame).let { if (it is Closure) it else throw UnexpectedResultException(it) }
}

open class CConstant(val v: Val, loc: Loc?) : Code(loc) {
    override fun execute(frame: VirtualFrame): Any? = v
    override fun executeVal(frame: VirtualFrame): Val = v
}
open class CDerefMeta(val slot: MetaEntry, loc: Loc?) : Code(loc) {
    override fun execute(frame: VirtualFrame): Any? {
        if (!slot.solved) panic("Unsolved meta in compiled code", null)
        return replace(CInvoke(slot.closure!!)).execute(frame)
    }
}
open class CInvoke(val closure: Closure) : Code(null) {
    private var dispatch: Dispatch = DispatchNodeGen.create()
    override fun execute(frame: VirtualFrame): Any? = todo
        // dispatch.executeDispatch(closure.callTarget, buildArgs(frame.materialize()))
}
open class CReadLocal(val slot: FrameSlot, loc: Loc?) : Code(loc) {
    override fun execute(frame: VirtualFrame): Any = frame.getObject(slot)
}
open class CWriteLocal(val slot: FrameSlot, @field:Child var value: Code, @field:Child var body: Code, loc: Loc?) : Code(loc) {
    override fun execute(frame: VirtualFrame): Any? {
        frame.setObject(slot, value.execute(frame))
        return body.execute(frame)
    }
}
open class CArg(val ix: Ix, val fs: FrameSlot, loc: Loc?) : Code(loc) {
    override fun execute(frame: VirtualFrame): Any? {
        frame.setObject(fs, frame.arguments[frame.arguments.size - ix.it - 1])
        return null
    }
}
open class CLam(val n: String, val icit: Icit, val root: Closure, loc: Loc?) : Code(loc) {
    override fun execute(frame: VirtualFrame): Any? = root.execute(*buildArgs(frame.materialize()))
}
open class CPi(val n: String, val icit: Icit, @field:Child var type: Code, val root: Closure, loc: Loc?) : Code(loc) {
    override fun execute(frame: VirtualFrame): Any? = root.execute(*buildArgs(frame.materialize()))
}
open class CApp(val icit: Icit, @field:Child var lhs: Code, @field:Child var rhs: Code, val lang: TruffleLanguage<*>, loc: Loc?) : Code(loc) {
    override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.CallTag::class.java || super.hasTag(tag)
    @Child private var dispatch: Dispatch = DispatchNodeGen.create()
    override fun execute(frame: VirtualFrame): Any? = todo
        // dispatch.executeDispatch(lhs.executeClosure(frame).callTarget, arrayOf(rhs.execute(frame)))
}
