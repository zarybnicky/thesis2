package montuno.truffle

import com.oracle.truffle.api.RootCallTarget
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.dsl.Cached
import com.oracle.truffle.api.dsl.ReportPolymorphism
import com.oracle.truffle.api.dsl.Specialization
import com.oracle.truffle.api.nodes.NodeInfo

@NodeInfo(shortName = "Eval", description = "Evaluates code passed to it as string")
@ReportPolymorphism
abstract class EvalNode constructor(val shouldCaptureResultScope: Boolean) : BaseNode() {
    abstract fun execute(
        callerInfo: CallerInfo?,
        state: Any?,
        expression: org.enso.interpreter.runtime.data.text.Text?
    ): Stateful?

    fun parseExpression(scope: LocalScope, moduleScope: ModuleScope?, expression: String): RootCallTarget {
        val localScope: LocalScope = scope.createChild()
        val inlineContext: InlineContext = InlineContext.fromJava(localScope, moduleScope, getTailStatus())
        var expr: ExpressionNode = Language.currentContext.get().getCompiler().runInline(expression, inlineContext)
        if (expr == null) {
            throw RuntimeException("Invalid code passed to `eval`: $expression")
        }
        if (shouldCaptureResultScope) {
            expr = CaptureResultScopeNode.build(expr)
        }
        val framedNode: ClosureRootNode = ClosureRootNode.build(
            lookupLanguageReference(org.enso.interpreter.Language::class.java).get(),
            localScope,
            moduleScope,
            expr,
            null,
            "<eval>"
        )
        return Truffle.getRuntime().createCallTarget(framedNode)
    }

    @Specialization(
        guards = [
            "expression == cachedExpression",
            "callerInfo.getLocalScope() == cachedCallerInfo.getLocalScope()",
            "callerInfo.getModuleScope() == cachedCallerInfo.getModuleScope()"
        ],
        limit = "10",
    )
    fun doCached(
        callerInfo: CallerInfo,
        state: Any?,
        expression: org.enso.interpreter.runtime.data.text.Text?,
        @Cached("expression") cachedExpression: org.enso.interpreter.runtime.data.text.Text?,
        @Cached("build()") toJavaStringNode: ToJavaStringNode?,
        @Cached("toJavaStringNode.execute(expression)") expressionStr: String?,
        @Cached("callerInfo") cachedCallerInfo: CallerInfo?,
        @Cached("parseExpression(callerInfo.getLocalScope(), callerInfo.getModuleScope(), expressionStr)") cachedCallTarget: RootCallTarget?,
        @Cached("build()") thunkExecutorNode: ThunkExecutorNode
    ): Stateful {
        val thunk = Thunk(cachedCallTarget, callerInfo.getFrame())
        return thunkExecutorNode.executeThunk(thunk, state, getTailStatus())
    }

    @Specialization
    fun doUncached(
        callerInfo: CallerInfo,
        state: Any?,
        expression: org.enso.interpreter.runtime.data.text.Text?,
        @Cached("build()") thunkExecutorNode: ThunkExecutorNode,
        @Cached("build()") toJavaStringNode: ToJavaStringNode
    ): Stateful {
        val callTarget = parseExpression(
            callerInfo.getLocalScope(),
            callerInfo.getModuleScope(),
            toJavaStringNode.execute(expression)
        )
        val thunk = Thunk(callTarget, callerInfo.getFrame())
        return thunkExecutorNode.executeThunk(thunk, state, getTailStatus())
    }
}

fun eval() = EvalNodeGen.create(false)