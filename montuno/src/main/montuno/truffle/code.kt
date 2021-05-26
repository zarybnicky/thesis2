package montuno.truffle

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.FrameSlot
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.nodes.UnexpectedResultException
import com.oracle.truffle.api.source.SourceSection
import montuno.interpreter.*
import montuno.interpreter.scope.MetaEntry
import montuno.syntax.Icit
import montuno.syntax.Loc

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
    open fun executeVal(frame: VirtualFrame): Val = execute(frame).let {
        if (it is Val) it else {
            throw UnexpectedResultException(it)
        }
    }
    @Throws(UnexpectedResultException::class)
    open fun executeClosure(frame: VirtualFrame): Closure = execute(frame).let { if (it is Closure) it else throw UnexpectedResultException(it) }
}

open class CConstant(val v: Val, loc: Loc?) : Code(loc) {
    override fun execute(frame: VirtualFrame): Any? = v
    override fun executeVal(frame: VirtualFrame): Val = v
}
open class CDerefMeta(val slot: MetaEntry, loc: Loc?) : Code(loc) {
    override fun execute(frame: VirtualFrame): Val = when {
        !slot.solved -> VMeta(slot.meta, VSpine(), slot)
        else -> {
            replace(CConstant(slot.value!!, loc))
            slot.value!!
        }
    }
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
open class CArg(val i: Int, val fs: FrameSlot, loc: Loc?) : Code(loc) {
    override fun execute(frame: VirtualFrame): Any? {
        frame.setObject(fs, frame.arguments[i])
        return null
    }
}
open class CClosure(
    val n: String?,
    val icit: Icit,
    @field:Child var type: Code,
    val isPi: Boolean,
    val ctx: MontunoContext,
    val heads: Array<ClosureHead>,
    val callTarget: CallTarget,
    loc: Loc?
) : Code(loc) {
    override fun execute(frame: VirtualFrame): Val {
        val env = buildArgs(frame.materialize())
        val cl = TruffleClosure(ctx, env, heads, heads.size, callTarget)
        return if (isPi) VPi(n, icit, type.executeVal(frame), cl) else VLam(n, icit, type.executeVal(frame), cl)
    }
}
open class CApp(val icit: Icit, @field:Child var lhs: Code, @field:Child var rhs: Code, loc: Loc?) : Code(loc) {
    override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.CallTag::class.java || super.hasTag(tag)
    override fun execute(frame: VirtualFrame): Any? = lhs.executeVal(frame).app(icit, VThunk { rhs.executeVal(frame) })
}
open class CPair(@field:Child var lhs: Code, @field:Child var rhs: Code, loc: Loc?) : Code(loc) {
    override fun execute(frame: VirtualFrame): Any? = VPair(lhs.executeVal(frame), rhs.executeVal(frame))
}
open class CProj1(@field:Child var body: Code, loc: Loc?) : Code(loc) {
    override fun execute(frame: VirtualFrame): Any? = body.executeVal(frame).proj1()
}
open class CProj2(@field:Child var body: Code, loc: Loc?) : Code(loc) {
    override fun execute(frame: VirtualFrame): Any? = body.executeVal(frame).proj2()
}
open class CProjF(@field:Child var body: Code, val name: String, val i: Int, loc: Loc?) : Code(loc) {
    override fun execute(frame: VirtualFrame): Any? = body.executeVal(frame).projF(name, i)
}

fun buildArgs(frame: MaterializedFrame): Array<Any?> {
    val ret = arrayOfNulls<Any?>(frame.frameDescriptor.size)
    for (fs in frame.frameDescriptor.slots) {
        ret[fs.identifier as Int] = frame.getObject(fs)
    }
    return ret
}
