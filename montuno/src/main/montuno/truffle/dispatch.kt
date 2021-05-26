package montuno.truffle

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.frame.MaterializedFrame
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.DirectCallNode
import com.oracle.truffle.api.nodes.IndirectCallNode
import com.oracle.truffle.api.nodes.Node

@ReportPolymorphism
abstract class Dispatch : Node() {
    abstract fun executeDispatch(callTarget: CallTarget, ys: Array<Any?>): Any

    @Specialization(guards = ["callTarget == cachedCallTarget"], limit = "3")
    fun callDirect(
        callTarget: CallTarget,
        ys: Array<Any?>,
        @Cached("callTarget") cachedCallTarget: CallTarget,
        @Cached("create(cachedCallTarget)") callNode: DirectCallNode
    ): Any? = callNode.call(*ys)

    @Specialization
    fun callIndirect(
        callTarget: CallTarget,
        ys: Array<Any?>,
        @Cached("create()") callNode: IndirectCallNode
    ): Any? = callNode.call(callTarget, *ys)
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