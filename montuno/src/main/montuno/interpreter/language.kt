package montuno.interpreter

import com.oracle.truffle.api.*
import com.oracle.truffle.api.frame.VirtualFrame
import com.oracle.truffle.api.nodes.RootNode
import montuno.syntax.TopLevel
import montuno.syntax.parsePreSyntax

@TruffleLanguage.Registration(
    id = MontunoPure.LANGUAGE_ID,
    name = "MontunoPure",
    version = "0.1",
    interactive = true,
    internal = false,
    defaultMimeType = MontunoPure.MIME_TYPE,
    characterMimeTypes = [MontunoPure.MIME_TYPE],
    contextPolicy = TruffleLanguage.ContextPolicy.SHARED,
)
class MontunoPure : TruffleLanguage<MontunoPureContext>() {
    override fun createContext(env: Env): MontunoPureContext = MontunoPureContext(env)
    override fun isThreadAccessAllowed(thread: Thread, singleThreaded: Boolean) = true
    override fun isObjectOfLanguage(obj: Any): Boolean = false
    override fun getScope(ctx: MontunoPureContext) = ctx.topScope
    override fun parse(request: ParsingRequest): CallTarget {
        CompilerAsserts.neverPartOfCompilation()
        val root = ProgramRootNode(this, parsePreSyntax(request.source))
        return Truffle.getRuntime().createCallTarget(root)
    }

    companion object {
        val top: MontunoPureContext get() = getCurrentContext(MontunoPure::class.java)
        const val LANGUAGE_ID = "montuno-pure"
        const val MIME_TYPE = "application/x-montuno-pure"
    }
}

class ProgramRootNode(lang: MontunoPure, private val pre: List<TopLevel>) : RootNode(lang) {
    override fun execute(frame: VirtualFrame?): Any {
        var ret: Any = true
        for (e in pre) {
            checkTopLevel(e).let { if (it != null) ret = it }
        }
        return ret
    }
}
