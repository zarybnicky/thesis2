package montuno.interpreter

import com.oracle.truffle.api.CallTarget
import com.oracle.truffle.api.CompilerAsserts
import com.oracle.truffle.api.Truffle
import com.oracle.truffle.api.TruffleLanguage
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
    override fun initializeContext(context: MontunoPureContext) {}
    override fun isThreadAccessAllowed(thread: Thread, singleThreaded: Boolean) = true
    override fun isObjectOfLanguage(obj: Any): Boolean = false
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
        for (e in pre) {
            val ret = checkTopLevel(e)
            if (ret != null) print(ret)
        }
        return true
    }
}
