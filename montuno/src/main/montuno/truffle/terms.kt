package montuno.truffle

import com.oracle.truffle.api.dsl.CachedContext
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.dsl.TypeSystemReference
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.instrumentation.*
import com.oracle.truffle.api.nodes.Node
import com.oracle.truffle.api.nodes.NodeInfo
import com.oracle.truffle.api.source.SourceSection
import montuno.common.*
import montuno.syntax.Loc
import org.graalvm.polyglot.Context

@ReportPolymorphism
@GenerateWrapper
@NodeInfo(language = "montuno")
@TypeSystemReference(Types::class)
abstract class Term(val loc: Loc?) : Node(), InstrumentableNode {
    constructor(that: Term) : this(that.loc)
    override fun getSourceSection(): SourceSection? = loc?.section(rootNode?.sourceSection?.source)
    override fun isInstrumentable(): Boolean = loc != null
    override fun createWrapper(probe: ProbeNode?): InstrumentableNode.WrapperNode = TermWrapper(this, this, probe)
    override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.ExpressionTag::class.java
    abstract fun pretty(ns: NameEnv): String

    abstract fun execute(frame: VirtualFrame): Any
    open fun executeAny(frame: VirtualFrame): Any = execute(frame)
    open fun executeVUnit(frame: VirtualFrame): VUnit = TypesGen.expectVUnit(execute(frame))
    open fun executeVIrrelevant(frame: VirtualFrame): VIrrelevant = TypesGen.expectVIrrelevant(execute(frame))
    open fun executeVLam(frame: VirtualFrame): VLam = TypesGen.expectVLam(execute(frame))
    open fun executeVPi(frame: VirtualFrame): VPi = TypesGen.expectVPi(execute(frame))
    open fun executeVFun(frame: VirtualFrame): VFun = TypesGen.expectVFun(execute(frame))
    open fun executeVTop(frame: VirtualFrame): VTop = TypesGen.expectVTop(execute(frame))
    open fun executeVMeta(frame: VirtualFrame): VMeta = TypesGen.expectVMeta(execute(frame))
    open fun executeVLocal(frame: VirtualFrame): VLocal = TypesGen.expectVLocal(execute(frame))
    open fun executeNat(frame: VirtualFrame): Int = TypesGen.expectInteger(execute(frame))
}

class TUnit(loc: Loc?) : Term(loc) {
    override fun pretty(ns: NameEnv): String = "*"
    override fun execute(frame: VirtualFrame): Any = VUnit
    override fun executeVUnit(frame: VirtualFrame): VUnit = VUnit
}
class TIrrelevant(loc: Loc?) : Term(loc) {
    override fun pretty(ns: NameEnv): String = "Irr"
    override fun execute(frame: VirtualFrame): Any = VIrrelevant
    override fun executeVIrrelevant(frame: VirtualFrame): VIrrelevant = VIrrelevant
}
class TNat(val n: Int, loc: Loc?) : Term(loc) {
    override fun pretty(ns: NameEnv): String = n.toString()
    override fun execute(frame: VirtualFrame) = n
    override fun executeNat(frame: VirtualFrame) = n
}
abstract class TLet(val n: String, @field:Child var value: Term, @field:Child var type: Term, @field:Child var body: Term, loc: Loc?) : Term(loc) {
    override fun pretty(ns: NameEnv): String = "let $n : ${type.pretty(ns)} = ${body.pretty(ns + n)}"
    override fun execute(frame: VirtualFrame): Any {
        val fd = frame.frameDescriptor
        val fs = fd.findFrameSlot(fd.size)
        frame.setObject(fs, value.execute(frame))
        return body.execute(frame)
    }
}

open class TApp(val icit: Icit, @field:Child var lhs: Term, @field:Child var rhs: Term, loc: Loc?) : Term(loc) {
    override fun pretty(ns: NameEnv): String = "(${lhs.pretty(ns)}) (${rhs.pretty(ns)})"
    override fun hasTag(tag: Class<out Tag>?) = tag == StandardTags.CallTag::class.java || super.hasTag(tag)
    @Child
    private var dispatch: Dispatch = DispatchNodeGen.create()
    override fun execute(frame: VirtualFrame): Any =
        dispatch.executeDispatch(lhs.executeVLam(frame).closure.callTarget, arrayOf(rhs.executeAny(frame)))
}

open class TLam(val n: String, val root: TermRootNode, loc: Loc?) : Term(loc) {
    override fun pretty(ns: NameEnv): String = "\\$n.${root.root.pretty(ns + n)}"
    override fun execute(frame: VirtualFrame) = VLam(root, frame.materialize())
    override fun executeVLam(frame: VirtualFrame): VLam = VLam(root, frame.materialize())
}
open class TPi(val n: String, @field:Child var type: Term, val root: TermRootNode, loc: Loc?) : Term(loc) {
    override fun pretty(ns: NameEnv): String = "($n : ${type.pretty(ns)}) -> ${root.root.pretty(ns + n)}"
    override fun execute(frame: VirtualFrame) = VPi(root, frame.materialize())
    override fun executeVPi(frame: VirtualFrame): VPi = VPi(root, frame.materialize())
}

open class TFun(@field:Child var lhs: Term, @field:Child var rhs: Term, loc: Loc?) : Term(loc) {
    override fun pretty(ns: NameEnv): String = "(${lhs.pretty(ns)}) -> (${rhs.pretty(ns)})"
    override fun execute(frame: VirtualFrame) = VFun(lhs.execute(frame), rhs.execute(frame))
    override fun executeVFun(frame: VirtualFrame): VFun = VFun(lhs.execute(frame), rhs.execute(frame))
}


open class TEval(val lang: String, val code: String, @field:Child var type: Term, loc: Loc?) : Term(loc) {
    override fun pretty(ns: NameEnv): String = "[$lang|$code|${type.pretty(ns)}]"
    override fun execute(frame: VirtualFrame): Any {
        return Context.getCurrent().eval(lang, code)
    }
}
open class TLocal(@Suppress("unused") val ix: Ix, loc: Loc?) : Term(loc) {
    override fun pretty(ns: NameEnv): String = ns[ix]
    override fun execute(frame: VirtualFrame): Any {
        val fd = frame.frameDescriptor
        return frame.getValue(fd.findFrameSlot(ix.toLvl(fd.size).it))
    }
}
abstract class TTop(protected val lvl: Lvl, loc: Loc?) : Term(loc) {
    override fun pretty(ns: NameEnv): String = MontunoTruffle.top.getTopTerm(lvl).pretty(ns)
    @Specialization
    protected fun read(
        frame: VirtualFrame,
        @CachedContext(MontunoTruffle::class) ctx: MontunoTruffleContext,
    ): Any = ctx.getTopTerm(lvl)
}
abstract class TMeta(protected val meta: Meta, loc: Loc?) : Term(loc) {
    override fun pretty(ns: NameEnv): String = "?${meta.i}.${meta.j}"
    @Specialization
    fun read(
        frame: VirtualFrame,
        @CachedContext(MontunoTruffle::class) ctx: MontunoTruffleContext,
    ): Any = ctx.getMetaForce(meta)
}
